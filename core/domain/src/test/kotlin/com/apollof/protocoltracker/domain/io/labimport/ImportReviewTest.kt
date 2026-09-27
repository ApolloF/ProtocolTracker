package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.BackupCodec
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The owner's choices, the saved entries and the entries an import saves (import doc §8-9). */
class ImportReviewTest {
    private val today = LocalDate.of(2026, 9, 27)
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val now = Instant.parse("2026-09-27T10:00:00Z")

    private fun draft(text: String) = assertIs<ImportRead.Found>(BloodworkImport.read(text, today)).draft

    private fun review(
        d: ImportDraft,
        choices: Map<Int, Choice> = emptyMap(),
        existing: List<JournalEntry.Bloodwork> = emptyList(),
        zone: ZoneId = this.zone,
    ) = Review.of(d, choices, existing, zone)

    private fun ids(): () -> String {
        var n = 0
        return { "e${++n}" }
    }

    private fun Review.row(name: String) = draws.flatMap { it.rows }.single { it.row.printed.name == name }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, z: ZoneId = zone) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, z).toInstant()

    private fun entry(id: String, at: Instant, vararg results: MarkerResult) =
        JournalEntry.Bloodwork(id, at, results.toList(), createdAt = now)

    @Test
    fun aCleanReportSavesOneEntry() {
        val r = review(draft(Fixtures.F01))
        assertTrue(r.canSave)
        assertEquals(20, r.resultsToSave)
        assertEquals(1, r.drawsToSave)
        assertNull(r.line)
        assertEquals("Save 20 results", r.saveLabel)
        val e = r.entries(zone, now, ids()).single()
        assertEquals("e1", e.id)
        assertEquals(at(2025, 3, 12, 8, 15), e.at)
        assertEquals("Saltro", e.lab)
        assertEquals("", e.note)
        assertEquals(now, e.createdAt)
        val known = BloodMarkers.all.map { it.key }.filter { k -> e.results.any { it.marker == k } }
        assertEquals(known + listOf("other:vrij_t4", "other:ferritine"), e.results.map { it.marker }, "table order, then report order")
    }

    @Test
    fun drawsWithoutATimeSaveAtNoonOnTheirDay() {
        val d = draft(Fixtures.F02)
        val r = review(d)
        assertEquals("Save 2 draws", r.saveLabel)
        assertEquals("2 blood draws saved", ImportMessages.saved(r.drawsToSave))
        assertEquals("Bloodwork saved", ImportMessages.saved(1))
        val (june, march) = r.entries(zone, now, ids())
        assertEquals(at(2025, 6, 2, 8, 40), june.at)
        assertEquals(at(2025, 3, 12, 12), march.at)
        assertEquals(listOf("e1", "e2"), listOf(june.id, march.id))
        val honolulu = ZoneId.of("Pacific/Honolulu")
        val local = review(d, zone = honolulu).entries(honolulu, now, ids())[1].at.atZone(honolulu)
        assertEquals(LocalDate.of(2025, 3, 12), local.toLocalDate())
        assertEquals(LocalTime.NOON, local.toLocalTime())
    }

    @Test
    fun aTapLeavesAResultOutAndKeepsIt() {
        val d = draft(Fixtures.F01)
        val t = d.draws[0].rows[0]
        val out = review(d, mapOf(t.id to Choice.LEAVE_OUT))
        assertEquals(RowState.LEFT_OUT, out.row("Testosteron totaal").state)
        assertTrue(out.row("Testosteron totaal").toggles)
        assertEquals(19, out.resultsToSave)
        assertTrue(out.entries(zone, now, ids()).single().results.none { it.marker == "total_testosterone" })
        val kept = review(d, mapOf(t.id to Choice.KEEP))
        assertEquals(RowState.READY, kept.row("Testosteron totaal").state)
        assertEquals(20, kept.resultsToSave)
    }

    @Test
    fun aReImportSkipsSavedResultsAndLeavesOutOtherValuesOfThatDay() {
        val d = draft(Fixtures.F01)
        val existing = listOf(
            entry(
                "native", at(2025, 3, 12, 10),
                MarkerResult("total_testosterone", 530.656), MarkerResult("hemoglobin", 15.0),
            ),
            entry("web:bloodwork:2025-03-12", at(2025, 3, 12, 12), MarkerResult("hematocrit", 49.0)),
            entry("next day", at(2025, 3, 13, 8), MarkerResult("shbg", 40.0)),
        )
        val r = review(d, existing = existing)
        assertEquals(RowState.ALREADY_SAVED, r.row("Testosteron totaal").state, "same value at another time")
        assertEquals(RowState.ALREADY_SAVED, r.row("Hematocriet").state, "a web draw of that date")
        val hb = r.row("Hemoglobine")
        assertEquals(RowState.SAME_DAY, hb.state)
        assertEquals("Saved earlier this day: 9.31 mmol/l.", hb.text, "the earlier value in the row's unit")
        assertTrue(hb.toggles)
        assertEquals(RowState.READY, r.row("SHBG").state, "another day")
        assertEquals(2, r.alreadySaved)
        assertEquals(17, r.resultsToSave)
        assertEquals("Save 17 results", r.saveLabel)

        val kept = review(d, mapOf(hb.row.id to Choice.KEEP), existing)
        assertEquals(RowState.READY, kept.row("Hemoglobine").state)
        assertEquals(hb.text, kept.row("Hemoglobine").text)
        assertEquals(18, kept.resultsToSave)
        assertEquals(RowState.SAME_DAY, review(d, mapOf(hb.row.id to Choice.LEAVE_OUT), existing).row("Hemoglobine").state)
    }

    @Test
    fun theSameAnswerTwiceSavesNothing() {
        val d = draft(Fixtures.F02)
        val saved = review(d).entries(zone, now, ids())
        val again = review(d, existing = saved)
        assertFalse(again.canSave)
        assertEquals(0, again.resultsToSave)
        assertEquals(8, again.alreadySaved)
        assertEquals("Everything here is already saved.", again.line)
        assertEquals("Save", again.saveLabel, "a disabled button names no count")
    }

    @Test
    fun theLineAboveSave() {
        assertEquals("Hemoglobin and AST (GOT) are left out.", review(draft(Fixtures.N02)).line)
        assertEquals("2 results are left out.", review(draft(Fixtures.F18)).line, "two values for one marker")
        assertEquals("6 results are left out.", review(draft(Fixtures.N16)).line)
        assertEquals("Hemoglobin is left out.", ImportMessages.leftOut(listOf("Hemoglobin")))
        val qualitative = review(draft(Fixtures.F21))
        assertEquals("Nothing to save. No result has a number.", qualitative.line)
        assertFalse(qualitative.canSave)
        assertEquals(2, qualitative.notImported)
        val undated = review(draft("protocoltracker-bloodwork-1\ndate: ?\nshbg | SHBG | 31 | nmol/l | 18 - 54 |\nend"))
        assertEquals("Nothing to save.", undated.line)
        assertFalse(undated.canSave)
    }

    @Test
    fun drawsWithAnUncertainDateSaveNothing() {
        val r = review(draft(Fixtures.N08))
        assertEquals(listOf(true, true, true, true, false), r.draws.map { it.leftOut })
        assertTrue(r.draws.take(4).flatMap { it.rows }.none { it.toggles })
        assertEquals(1, r.resultsToSave)
        assertEquals("Save 1 result", r.saveLabel)
        assertNull(r.line)
        val e = r.entries(zone, now, ids()).single()
        assertEquals(at(2025, 3, 14, 12), e.at)
        assertEquals(listOf("fsh"), e.results.map { it.marker })
    }

    @Test
    fun theEntryNoteHoldsNotesOfSavedRowsAndRowsWithoutANumber() {
        assertEquals("PSA totaal: onleesbaar (vlek op de foto)", review(draft(Fixtures.F03)).entries(zone, now, ids()).single().note)
        val d = draft(
            """
            protocoltracker-bloodwork-1
            date: 2025-04-01 | Afnamedatum: 01-04-2025
            shbg | SHBG | 31 | nmol/l | 18 - 54 | hemolytisch
            lh | LH | 4,1 | E/l | 1,7 - 8,6 | na inspanning
            other | HBsAg | negatief | | |
            end
            """.trimIndent(),
        )
        val lh = d.draws[0].rows.single { it.printed.name == "LH" }
        val e = review(d, mapOf(lh.id to Choice.LEAVE_OUT)).entries(zone, now, ids()).single()
        assertEquals("SHBG: hemolytisch\nHBsAg: negatief", e.note)
    }

    /** Import doc §13.2: every fixture, with rows left out and kept, saves valid entries that survive a backup. */
    @Test
    fun everyFixtureSavesEntriesThatSurviveABackup() {
        val fixtures = listOf(
            Fixtures.F01, Fixtures.F02, Fixtures.F03, Fixtures.F08, Fixtures.F09, Fixtures.F18, Fixtures.F20,
            Fixtures.F21, Fixtures.N02, Fixtures.N08, Fixtures.N15, Fixtures.N16, Fixtures.N18, Fixtures.N22,
        )
        var saved = 0
        for (text in fixtures) {
            val d = draft(text)
            val rows = d.draws.flatMap { it.rows }
            // Every other row left out; every row kept against a same-day entry holding other values.
            val alternate = rows.filterIndexed { i, _ -> i % 2 == 1 }.associate { it.id to Choice.LEAVE_OUT }
            val sameDay = d.draws.filter { it.date != null }.mapNotNull { draw ->
                val results = draw.rows.mapNotNull { (it.read as? RowRead.Ready)?.result?.let { r -> r.copy(value = r.value * 1.5 + 1) } }
                if (results.isEmpty()) null else entry("old${draw.id}", at(draw.date!!.year, draw.date!!.monthValue, draw.date!!.dayOfMonth, 7), *results.toTypedArray())
            }
            val reviews = listOf(
                review(d),
                review(d, alternate),
                review(d, rows.associate { it.id to Choice.KEEP }, sameDay),
            )
            for (r in reviews.filter { it.canSave }) {
                val entries = r.entries(zone, now, ids())
                assertEquals(r.resultsToSave, entries.sumOf { it.results.size }, text.lines().first())
                val backup = Backup(
                    exportedAt = now, compounds = emptyList(), phases = emptyList(), items = emptyList(),
                    logs = emptyList(), journal = entries,
                )
                assertEquals(entries, BackupCodec.decode(BackupCodec.encode(backup)).journal)
                saved += entries.size
            }
        }
        assertTrue(saved > 20)
    }
}
