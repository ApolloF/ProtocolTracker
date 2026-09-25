package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.InstantS
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.validate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Serializable
data class Backup(
    val format: String = FORMAT,
    val exportedAt: InstantS,
    val compounds: List<Compound>,
    val phases: List<Phase>,
    val items: List<PlanItem>,
    val logs: List<DoseLog>,
    val journal: List<JournalEntry> = emptyList(),
) {
    companion object {
        const val FORMAT = "protocoltracker-backup-2"
        /** Format of app versions before the redesign; not restorable. */
        const val FORMAT_V1 = "protocoltracker-backup-1"
    }
}

object BackupCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup {
        val format = runCatching { (json.parseToJsonElement(text).jsonObject["format"] as? JsonPrimitive)?.content }.getOrNull()
            ?: throw ImportFormatException("Not a ProtocolTracker backup file")
        if (format == Backup.FORMAT_V1) throw ImportFormatException("This backup is from an older version and can't be restored")
        if (format != Backup.FORMAT) throw ImportFormatException("Unsupported backup version: $format")
        val backup = try { json.decodeFromString(Backup.serializer(), text) } catch (e: Exception) {
            throw ImportFormatException("The backup file is damaged")
        }
        val compoundIds = backup.compounds.mapTo(HashSet()) { it.id }
        val phaseIds = backup.phases.mapTo(HashSet()) { it.id }
        backup.items.forEach {
            if (it.compoundId !in compoundIds) throw ImportFormatException("Backup references a missing compound")
            if (it.phaseId != null && it.phaseId !in phaseIds) throw ImportFormatException("Backup references a missing phase")
            // Validate before restore clears existing data; amounts, kinetics and readings are checked on deserialization.
            val problems = it.validate()
            if (problems.isNotEmpty()) throw ImportFormatException("Backup has an invalid plan item: ${problems.first()}")
        }
        backup.phases.forEach {
            if (it.endDate != null && it.endDate < it.startDate) throw ImportFormatException("Backup has a phase ending before it starts")
        }
        return backup
    }
}
