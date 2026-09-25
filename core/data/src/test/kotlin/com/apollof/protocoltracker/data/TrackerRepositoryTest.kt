package com.apollof.protocoltracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.schedule.occurrences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TrackerRepositoryTest {
    private lateinit var db: TrackerDatabase
    private lateinit var repo: TrackerRepository
    private val now = Instant.parse("2026-09-25T10:00:00Z")

    private val phase = Phase("p", "Cruise", LocalDate.parse("2026-09-01"), null, 0xFF2E7D6B)
    private val item = PlanItem(
        "i", "p", "preset:test-cyp", Amount(200.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
        Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(Timing.Slot(DaySlot.ANY_TIME))),
    )

    @Before
    fun setUp() {
        db = TrackerDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = TrackerRepository(db) { now }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedsPresetsWithoutOverwritingEdits() = runTest {
        repo.seedPresets()
        val edited = repo.compounds.first().first { it.id == "preset:test-cyp" }.copy(commonName = "My test C", edited = true)
        repo.saveCompound(edited)
        repo.seedPresets()
        val all = repo.compounds.first()
        assertEquals(Presets.all.size, all.size)
        assertEquals("My test C", all.first { it.id == edited.id }.commonName)
    }

    @Test
    fun seedingRefreshesUneditedPresetsAndKeepsArchive() = runTest {
        repo.seedPresets()
        val stale = repo.compounds.first().first { it.id == "preset:oxandrolone" }
        repo.saveCompound(stale.copy(sourceNote = "old data", archived = true))
        repo.seedPresets()
        val refreshed = repo.compounds.first().first { it.id == stale.id }
        assertEquals(Presets.byId(stale.id)!!.sourceNote, refreshed.sourceNote)
        assertTrue(refreshed.archived)
    }

    @Test
    fun relogReplacesSameOccurrenceAndUndoRestores() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC).single()
        val first = repo.logOccurrence(occ, LogStatus.TAKEN)
        assertEquals(Amount(100.0, DoseUnit.MG), first.amount) // 200 mg/week over Mon + Thu
        assertEquals(first.amount, first.plannedAmount)
        val second = repo.logOccurrence(occ, LogStatus.SKIPPED)
        assertEquals(first.id, second.id)
        val logs = repo.allLogs.first()
        assertEquals(1, logs.size)
        assertEquals(LogStatus.SKIPPED, logs.single().status)
        assertEquals(200.0, logs.single().snapshot.formulation.perMl)

        val deleted = assertNotNull(repo.deleteLog(first.id))
        assertTrue(repo.allLogs.first().isEmpty())
        repo.restoreLog(deleted)
        assertEquals(listOf(deleted), repo.allLogs.first())
    }

    @Test
    fun staleActionNeverOverwritesRecordedDose() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC).single()
        val recorded = repo.logOccurrence(occ, LogStatus.TAKEN, note = "left glute")
        assertEquals(null, repo.logOccurrenceIfAbsent(occ, LogStatus.SKIPPED, now))
        assertEquals(listOf(recorded), repo.allLogs.first())
    }

    @Test
    fun deletingUsedCompoundArchivesIt() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val compound = repo.compounds.first().first { it.id == item.compoundId }
        repo.deleteCompound(compound)
        assertTrue(repo.compounds.first().first { it.id == compound.id }.archived)
        val unused = repo.compounds.first().first { it.id == "preset:anastrozole" }
        repo.deleteCompound(unused)
        assertTrue(repo.compounds.first().none { it.id == unused.id })
    }

    @Test
    fun backupRestoresExactly() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC).single()
        repo.logOccurrence(occ, LogStatus.TAKEN)
        repo.saveJournal(JournalEntry.Note("n", now, "note", now))
        val backup = repo.exportBackup()
        repo.deletePhase(phase.id)
        assertTrue(repo.items.first().isEmpty())
        repo.restoreBackup(backup)
        assertEquals(backup.copy(exportedAt = now), repo.exportBackup())
    }

    @Test
    fun adjustedDoseKeepsThePlannedAmount() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC).single()
        val log = repo.logOccurrence(occ, LogStatus.TAKEN, amount = Amount(150.0, DoseUnit.MG))
        assertEquals(Amount(100.0, DoseUnit.MG), log.plannedAmount)
        assertTrue(log.adjusted)
        assertEquals(item, repo.items.first().single()) // the plan itself is unchanged
    }

    @Test
    fun journalEntriesSaveDeleteAndRestore() = runTest {
        val bp = JournalEntry.BloodPressure("bp", now, 128, 82, 64, "", now)
        val note = JournalEntry.Note("n", now.plusSeconds(60), "Lower-back pumps", now)
        repo.saveJournal(bp); repo.saveJournal(note)
        assertEquals(listOf(note, bp), repo.journal.first())
        val removed = assertNotNull(repo.deleteJournal("bp"))
        assertEquals(listOf(note), repo.journal.first())
        repo.saveJournal(removed)
        assertEquals(2, repo.journal.first().size)
    }
}
