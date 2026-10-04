package com.apollof.protocoltracker.data

import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.TakenDose
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
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.schedule.AgendaStatus
import com.apollof.protocoltracker.domain.schedule.buildDay
import com.apollof.protocoltracker.domain.schedule.occurrenceKey
import com.apollof.protocoltracker.domain.schedule.occurrences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE).single()
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
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE).single()
        val recorded = repo.logOccurrence(occ, LogStatus.TAKEN, note = "left glute")
        assertEquals(null, repo.logOccurrenceIfAbsent(occ, LogStatus.SKIPPED, now))
        assertEquals(listOf(recorded), repo.allLogs.first())
    }

    @Test
    fun reLoggingKeepsTheSiteUnlessSetOrSkipped() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE).single()
        assertNull(repo.logOccurrence(occ, LogStatus.TAKEN).site)
        assertEquals("vg_r", repo.logOccurrence(occ, LogStatus.TAKEN, site = SiteWrite.Set("vg_r")).site)
        assertEquals("vg_r", repo.logOccurrence(occ, LogStatus.TAKEN, note = "sore").site)
        assertEquals("vg_r", repo.allLogs.first().single().site)
        assertNull(repo.logOccurrence(occ, LogStatus.TAKEN, site = SiteWrite.Set(null)).site)
        assertNull(repo.logOccurrence(occ, LogStatus.TAKEN, site = SiteWrite.Set(" ")).site)
        repo.logOccurrence(occ, LogStatus.TAKEN, site = SiteWrite.Set("delt_l"))
        assertNull(repo.logOccurrence(occ, LogStatus.SKIPPED).site)
        assertNull(repo.allLogs.first().single().site)
    }

    @Test
    fun newLogsStoreTheGivenSiteAndStaleActionsKeepTheRecordedOne() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val (monday, thursday) = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE)
        val compound = repo.compounds.first().first { it.id == item.compoundId }
        assertNull(repo.logUnscheduled(compound, Amount(50.0, DoseUnit.MG), item.formulation, now).site)
        assertEquals("glute_l", repo.logUnscheduled(compound, Amount(50.0, DoseUnit.MG), item.formulation, now, site = SiteWrite.Set("glute_l")).site)
        assertEquals("vg_l", repo.logOccurrenceIfAbsent(monday, LogStatus.TAKEN, now, site = "vg_l")?.site)
        assertNull(repo.logOccurrenceIfAbsent(monday, LogStatus.TAKEN, now, site = "vg_r"))
        assertNull(repo.logOccurrenceIfAbsent(thursday, LogStatus.TAKEN, now)?.site)
        val logs = repo.allLogsNow()
        assertEquals(setOf(null, "glute_l"), logs.filter { it.occurrenceKey == null }.map { it.site }.toSet())
        assertEquals("vg_l", logs.single { it.occurrenceKey == monday.key }.site)
        assertNull(logs.single { it.occurrenceKey == thursday.key }.site)
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
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE).single()
        repo.logOccurrence(occ, LogStatus.TAKEN, site = SiteWrite.Set("delt_r"))
        repo.saveJournal(JournalEntry.Note("n", now, "note", now))
        val backup = repo.exportBackup()
        assertEquals(listOf("delt_r"), backup.logs.map { it.site })
        repo.deletePhase(phase.id)
        assertTrue(repo.items.first().isEmpty())
        repo.restoreBackup(backup)
        assertEquals(backup.copy(exportedAt = now), repo.exportBackup())
    }

    @Test
    fun anchorsHoldOnlyTakenScheduledDoses() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occs = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE)
        repo.logOccurrence(occs[0], LogStatus.TAKEN, takenAt = now)
        repo.logOccurrence(occs[1], LogStatus.SKIPPED)
        repo.logUnscheduled(repo.compounds.first().first { it.id == item.compoundId }, Amount(10.0, DoseUnit.MG), Formulation(), now)
        val expected = listOf(TakenDose(item.id, occs[0].key, now))
        assertEquals(expected, repo.anchorsNow().forItem(item.id))
        assertEquals(IntervalAnchors(expected), repo.anchors.first())
    }

    @Test
    fun adjustedDoseKeepsThePlannedAmount() = runTest {
        repo.seedPresets(); repo.savePhase(phase); repo.saveItem(item)
        val occ = occurrences(listOf(phase), listOf(item), Instant.parse("2026-09-21T00:00:00Z"), Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC, IntervalAnchors.NONE).single()
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

    // A two-draw lab report as the bloodwork import saves it: one entry per draw date.
    private val march = JournalEntry.Bloodwork(
        "draw-march", Instant.parse("2026-03-12T07:40:00Z"),
        listOf(MarkerResult("total_testosterone", 695.0, refLow = 248.0, refHigh = 836.0), MarkerResult("hematocrit", 49.0, refLow = 40.0, refHigh = 50.0)),
        lab = "Saltro", createdAt = now,
    )
    private val june = JournalEntry.Bloodwork(
        "draw-june", Instant.parse("2026-06-18T08:05:00Z"),
        listOf(MarkerResult("estradiol", 11.0, qualifier = "<"), MarkerResult("other:lymfocyten_pct", 31.0, name = "Lymfocyten %", unit = "%")),
        lab = "Saltro", createdAt = now,
    )
    private val bp = JournalEntry.BloodPressure("bp", now, 128, 82, 64, "", now)

    /** Makes SQLite abort any write that touches the entry [id], to show what a failed batch leaves behind. */
    private fun failWritesOf(id: String, event: String) = db.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER fail_$event BEFORE $event ON journal WHEN ${if (event == "DELETE") "OLD" else "NEW"}.id = '$id' " +
            "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
    )

    @Test
    fun savingTheSameListTwiceAddsNoDuplicates() = runTest {
        repo.saveJournal(listOf(march, june))
        repo.saveJournal(listOf(march, june.copy(note = "Fasting")))
        assertEquals(listOf(june.copy(note = "Fasting"), march), repo.journal.first())
    }

    @Test
    fun aFailedBatchSaveLeavesNothingBehind() = runTest {
        failWritesOf("draw-june", "INSERT")
        assertNotNull(runCatching { repo.saveJournal(listOf(march, june)) }.exceptionOrNull())
        assertEquals(emptyList(), repo.journal.first())
    }

    @Test
    fun batchDeleteReturnsTheEntriesForUndoAndIsAllOrNothing() = runTest {
        repo.saveJournal(listOf(march, june, bp))
        failWritesOf("draw-june", "DELETE")
        assertNotNull(runCatching { repo.deleteJournal(listOf("draw-march", "draw-june")) }.exceptionOrNull())
        assertEquals(3, repo.journal.first().size)

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_DELETE")
        val removed = repo.deleteJournal(listOf("draw-june", "missing", "draw-march", "draw-june"))
        assertEquals(listOf(march, june), removed) // oldest first, unknown and repeated ids ignored
        assertEquals(listOf(bp), repo.journal.first())
        repo.saveJournal(removed)
        assertEquals(listOf(bp, june, march), repo.journal.first())
        assertEquals(emptyList(), repo.deleteJournal(emptyList()))
    }

    @Test
    fun theJournalEmitsOncePerBatch() = runTest {
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repo.journal.collect { list -> emissions.send(list.map { it.id }) } }
        assertEquals(emptyList(), emissions.receive())
        repo.saveJournal(listOf(march, june, bp))
        assertEquals(listOf("bp", "draw-june", "draw-march"), emissions.receive())
        repo.deleteJournal(listOf("draw-march", "draw-june"))
        assertEquals(listOf("bp"), emissions.receive())
        // Neither batch produced a partial or repeated emission.
        assertNull(withContext(Dispatchers.Default) { withTimeoutOrNull(500) { emissions.receive() } })
        collector.cancel()
    }

    /** Entries as the web history import makes them: unknown symptom keys and `other:` results without a unit survive. */
    @Test
    fun webHistoryEntriesRoundTripThroughTheDatabase() = runTest {
        val at = Instant.parse("2026-08-11T12:00:00Z")
        val web = listOf(
            JournalEntry.Symptoms("web:symptom:301", at, listOf("high_e2", "bloating", "acne"), mood = 6, hairShedding = 2, note = "Oily skin", createdAt = at),
            JournalEntry.Bloodwork(
                "web:bloodwork:2026-06-05", Instant.parse("2026-06-05T10:00:00Z"),
                listOf(
                    MarkerResult("hematocrit", 48.0, refLow = 40.0, refHigh = 50.0),
                    MarkerResult("other:vitamin_d", 75.0, name = "vitamin d"),
                    MarkerResult("other:prolactin", 210.0, name = "prolactin"),
                ),
                note = "Fasted, 8:30", createdAt = Instant.parse("2026-06-05T10:00:00Z"),
            ),
            JournalEntry.BloodPressure("web:log:2210", now, 131, 84, null, "", now),
        )
        repo.saveJournal(web)
        assertEquals(web.sortedByDescending { it.at }, repo.journal.first())
        assertEquals(web.toSet(), repo.journalNow().toSet())
    }

    @Test
    fun batchesLargerThanTheSqliteVariableLimitWork() = runTest {
        val notes = (0 until 1_001).map { JournalEntry.Note("n$it", now.minusSeconds(it * 60L), "Note $it", now) }
        repo.saveJournal(notes)
        assertEquals(1_001, repo.journal.first().size)
        assertEquals(notes.reversed(), repo.deleteJournal(notes.map { it.id }))
        assertEquals(emptyList(), repo.journal.first())
    }

    private val timed = PlanItem(
        "t", null, "preset:test-cyp", Amount(20.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 200.0),
        Schedule.Daily(listOf(Timing.At(LocalTime.of(8, 0)))), startDate = LocalDate.parse("2026-09-20"),
    )
    private val yesterday = LocalDate.parse("2026-09-24")

    private fun dayStatus(item: PlanItem, logs: List<com.apollof.protocoltracker.domain.model.DoseLog>) =
        buildDay(emptyList(), listOf(item), logs, yesterday, yesterday.plusDays(1), ZoneOffset.UTC, IntervalAnchors.NONE)
            .groups.single().entries.single().status

    @Test
    fun editingAnExactTimeKeepsEarlierDosesTaken() = runTest {
        repo.seedPresets()
        repo.saveItem(timed)
        val occ = occurrences(
            emptyList(), listOf(timed), yesterday.atStartOfDay(ZoneOffset.UTC).toInstant(), yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneOffset.UTC, IntervalAnchors.NONE,
        ).single()
        repo.logOccurrence(occ, LogStatus.TAKEN)

        val edited = timed.copy(schedule = Schedule.Daily(listOf(Timing.At(LocalTime.of(8, 30)))))
        repo.saveItem(edited)
        assertEquals(AgendaStatus.TAKEN, dayStatus(edited, repo.allLogsNow()))
    }

    @Test
    fun instantKeysOfEarlierVersionsMoveOnStartAndOnRestore() = runTest {
        val utc = TrackerRepository(db, { ZoneOffset.UTC }) { now }
        utc.seedPresets()
        utc.saveItem(timed)
        val occ = occurrences(
            emptyList(), listOf(timed), yesterday.atStartOfDay(ZoneOffset.UTC).toInstant(), yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneOffset.UTC, IntervalAnchors.NONE,
        ).single()
        val old = utc.logOccurrence(occ, LogStatus.TAKEN).copy(occurrenceKey = occurrenceKey("t", occ.at))
        utc.restoreLog(old)
        assertEquals(AgendaStatus.MISSED, dayStatus(timed, utc.allLogsNow()))

        utc.rekeyExactTimeLogs()
        assertEquals(listOf("t@2026-09-24/T0800"), utc.allLogsNow().map { it.occurrenceKey })
        assertEquals(AgendaStatus.TAKEN, dayStatus(timed, utc.allLogsNow()))

        val backup = utc.exportBackup()
        utc.restoreBackup(backup.copy(logs = listOf(old)))
        assertEquals(listOf("t@2026-09-24/T0800"), utc.allLogsNow().map { it.occurrenceKey })
    }
}
