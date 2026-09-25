package com.apollof.protocoltracker.data

import androidx.room.withTransaction
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.data.db.toDomain
import com.apollof.protocoltracker.data.db.toEntity
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.ImportResult
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.schedule.Occurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

/** Single entry point for reading and writing tracker data. All mutations are transactional. */
class TrackerRepository(
    private val db: TrackerDatabase,
    private val clock: () -> Instant = Instant::now,
) {
    val compounds: Flow<List<Compound>> = db.compounds().observeAll().map { list -> list.map { it.toDomain() } }
    val phases: Flow<List<Phase>> = db.phases().observeAll().map { list -> list.map { it.toDomain() } }
    val items: Flow<List<PlanItem>> = db.items().observeAll().map { list -> list.map { it.toDomain() } }
    val allLogs: Flow<List<DoseLog>> = db.logs().observeAll().map { list -> list.map { it.toDomain() } }

    /** Change trigger for dose logs, cheaper than observing all rows. */
    val logChanges: Flow<Int> = db.logs().observeCount()

    val protocol: Flow<Protocol> = combine(phases, items, compounds) { p, i, c -> Protocol(p, i, c.associateBy { it.id }) }

    fun logsSince(from: Instant): Flow<List<DoseLog>> =
        db.logs().observeSince(from.toEpochMilli()).map { list -> list.map { it.toDomain() } }

    suspend fun protocolNow(): Protocol = Protocol(
        db.phases().getAll().map { it.toDomain() },
        db.items().getAll().map { it.toDomain() },
        db.compounds().getAll().map { it.toDomain() }.associateBy { it.id },
    )

    suspend fun logsSinceNow(from: Instant): List<DoseLog> = db.logs().getSince(from.toEpochMilli()).map { it.toDomain() }

    /** Adds presets that are missing; never overwrites user edits to existing ones. */
    suspend fun seedPresets() {
        val existing = db.compounds().ids().toSet()
        val missing = Presets.all.filter { it.id !in existing }
        if (missing.isNotEmpty()) db.compounds().upsert(missing.map { it.toEntity() })
    }

    // --- Logging -------------------------------------------------------------------------------

    /**
     * Records a scheduled occurrence as taken or skipped. Re-logging the same occurrence replaces
     * the earlier entry (the occurrence key is unique), keeping its id.
     */
    suspend fun logOccurrence(
        occurrence: Occurrence,
        status: LogStatus,
        takenAt: Instant = occurrence.at,
        amount: Amount = occurrence.item.dose,
        note: String = "",
    ): DoseLog = writeOccurrence(occurrence, status, takenAt, amount, note, replace = true)!!

    /**
     * Logs only if the occurrence has no entry yet; returns null when it was already confirmed.
     * Notification and widget actions can be stale and must never overwrite a recorded dose.
     */
    suspend fun logOccurrenceIfAbsent(occurrence: Occurrence, status: LogStatus, takenAt: Instant): DoseLog? =
        writeOccurrence(occurrence, status, takenAt, occurrence.item.dose, "", replace = false)

    private suspend fun writeOccurrence(
        occurrence: Occurrence,
        status: LogStatus,
        takenAt: Instant,
        amount: Amount,
        note: String,
        replace: Boolean,
    ): DoseLog? = db.withTransaction {
        val existing = db.logs().byOccurrence(occurrence.key)
        if (existing != null && !replace) return@withTransaction null
        val compound = db.compounds().get(occurrence.item.compoundId)?.toDomain()
            ?: error("Compound missing for plan item")
        val log = DoseLog(
            id = existing?.id ?: newId(), planItemId = occurrence.item.id, compoundId = compound.id,
            occurrenceKey = occurrence.key, scheduledAt = occurrence.at, takenAt = takenAt, amount = amount,
            status = status, note = note, snapshot = snapshotOf(compound, occurrence.item.formulation), createdAt = clock(),
        )
        db.logs().upsert(listOf(log.toEntity()))
        log
    }

    suspend fun logUnscheduled(compound: Compound, amount: Amount, formulation: Formulation, takenAt: Instant, note: String = ""): DoseLog {
        val log = DoseLog(
            id = newId(), planItemId = null, compoundId = compound.id, occurrenceKey = null, scheduledAt = null,
            takenAt = takenAt, amount = amount, status = LogStatus.TAKEN, note = note,
            snapshot = snapshotOf(compound, formulation), createdAt = clock(),
        )
        db.logs().upsert(listOf(log.toEntity()))
        return log
    }

    suspend fun updateLog(log: DoseLog) = db.logs().upsert(listOf(log.toEntity()))

    /** Deletes a log and returns it so the caller can offer undo via [restoreLog]. */
    suspend fun deleteLog(id: String): DoseLog? = db.withTransaction {
        val existing = db.logs().get(id)?.toDomain()
        db.logs().delete(id)
        existing
    }

    suspend fun restoreLog(log: DoseLog) = db.logs().upsert(listOf(log.toEntity()))

    private fun snapshotOf(compound: Compound, formulation: Formulation) = DoseSnapshot(
        compoundName = compound.name, group = compound.group, baseUnit = compound.baseUnit, pk = compound.pk,
        formulation = Formulation(
            perMl = formulation.perMl ?: compound.defaultFormulation.perMl,
            perTablet = formulation.perTablet ?: compound.defaultFormulation.perTablet,
        ),
    )

    // --- Plan ----------------------------------------------------------------------------------

    suspend fun savePhase(phase: Phase) = db.phases().upsert(listOf(phase.toEntity()))

    suspend fun deletePhase(id: String) = db.withTransaction {
        db.items().deleteForPhase(id)
        db.phases().delete(id)
    }

    suspend fun saveItem(item: PlanItem) = db.items().upsert(listOf(item.toEntity()))

    suspend fun deleteItem(id: String) = db.items().delete(id)

    suspend fun saveCompound(compound: Compound) = db.compounds().upsert(listOf(compound.toEntity()))

    /** Removes an unused compound; compounds referenced by plans or history are archived instead. */
    suspend fun deleteCompound(compound: Compound) = db.withTransaction {
        val used = db.items().countForCompound(compound.id) + db.logs().countForCompound(compound.id) > 0
        if (used) db.compounds().upsert(listOf(compound.copy(archived = true).toEntity()))
        else db.compounds().delete(compound.id)
    }

    // --- Backup / import -----------------------------------------------------------------------

    suspend fun exportBackup(): Backup = db.withTransaction {
        Backup(
            exportedAt = clock(),
            compounds = db.compounds().getAll().map { it.toDomain() },
            phases = db.phases().getAll().map { it.toDomain() },
            items = db.items().getAll().map { it.toDomain() },
            logs = db.logs().getAll().map { it.toDomain() },
        )
    }

    /** Replaces all data with [backup]. */
    suspend fun restoreBackup(backup: Backup) = db.withTransaction {
        db.logs().clear(); db.items().clear(); db.phases().clear(); db.compounds().clear()
        db.compounds().upsert(backup.compounds.map { it.toEntity() })
        db.phases().upsert(backup.phases.map { it.toEntity() })
        db.items().upsert(backup.items.map { it.toEntity() })
        db.logs().upsert(backup.logs.map { it.toEntity() })
    }

    /** Merges a legacy import. IDs are source-derived, so repeating an import updates in place. */
    suspend fun applyImport(result: ImportResult) = db.withTransaction {
        db.compounds().upsert(result.compounds.map { it.toEntity() })
        db.phases().upsert(result.phases.map { it.toEntity() })
        db.items().upsert(result.items.map { it.toEntity() })
        db.logs().upsert(result.logs.map { it.toEntity() })
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
