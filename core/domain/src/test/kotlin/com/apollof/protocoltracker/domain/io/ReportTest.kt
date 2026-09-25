package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.schedule.planFigures
import com.apollof.protocoltracker.domain.schedule.slotOccurrenceKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val testE = Presets.byId("preset:test-enan")!!
    private val anavar = Presets.byId("preset:oxandrolone")!!
    private val phase = Phase("p", "Summer cut", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-10-23"), 0)
    private val injections = PlanItem(
        "te", "p", testE.id, Amount(500.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 250.0),
        Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(Timing.Slot(DaySlot.ANY_TIME))),
    )
    private val orals = PlanItem(
        "av", "p", anavar.id, Amount(50.0, DoseUnit.MG), formulation = Formulation(perTablet = 10.0),
        schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.PRE_WORKOUT))),
    )
    private val protocol = Protocol(listOf(phase), listOf(injections, orals), mapOf(testE.id to testE, anavar.id to anavar))

    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()

    private val logs = listOf(
        DoseLog(
            "l1", "av", anavar.id, slotOccurrenceKey("av", LocalDate.parse("2026-09-24"), DaySlot.PRE_WORKOUT), at("2026-09-24", "17:00"),
            at("2026-09-24", "16:40"), Amount(60.0, DoseUnit.MG), Amount(50.0, DoseUnit.MG), LogStatus.TAKEN, "felt fine",
            DoseSnapshot(anavar.displayName, anavar.group, anavar.category, anavar.baseUnit, anavar.pk, Formulation(perTablet = 10.0)),
            at("2026-09-24", "16:40"),
        ),
    )
    private val journal = listOf(
        JournalEntry.BloodPressure("bp", at("2026-09-24", "07:40"), 128, 82, 64, "", at("2026-09-24", "07:40")),
        JournalEntry.Note("n", at("2026-09-24", "18:05"), "Lower-back pumps | cut sets", at("2026-09-24", "18:05")),
    )

    private fun report() = ReportBuilder.build(
        protocol, logs, journal, LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-25"), at("2026-09-25", "12:00"), zone, locale = Locale.ENGLISH,
    )

    @Test
    fun planCardFiguresSplitWeeklyDoses() {
        val f = planFigures(injections, testE, Locale.ENGLISH)
        assertEquals("500 mg", f.total)
        assertEquals("per week", f.totalLabel)
        assertEquals("250 mg", f.perDose)
        assertEquals("1.0 mL", f.detail)
        assertEquals("Mon, Thu", f.days)
        assertEquals("Any time", f.timing)
        assertEquals("250 mg/mL", f.strength)
        assertEquals("2 / week", f.dosesPerWeek)
        val o = planFigures(orals, anavar, Locale.ENGLISH)
        assertEquals("50 mg" to "per day", o.total to o.totalLabel)
        assertEquals("5 × 10 mg", o.detail)
    }

    @Test
    fun reportListsDosesMissedReadingsAndNotesByDay() {
        val r = report()
        val day = r.days.single { it.date == LocalDate.parse("2026-09-24") }
        val lines = day.entries.map(MarkdownReport::line)
        assertEquals(
            listOf(
                "07:40 · Blood pressure · 128/82 mmHg · pulse 64",
                "12:00 · Test E (testosterone enanthate) · 250 mg · 1.0 mL · missed · any time",
                "16:40 · Anavar (oxandrolone) · 60 mg · 6 tab · taken · planned 50 mg · 5 tab · pre-workout · note: felt fine",
                "18:05 · Note · Lower-back pumps | cut sets",
            ),
            lines,
        )
        // Today's open doses are not reported as missed.
        assertFalse(r.days.any { d -> d.date == LocalDate.parse("2026-09-25") && d.entries.any { it is ReportEntry.Dose && it.status == "missed" } })
        assertEquals(128, r.bloodPressure!!.averageSystolic)
    }

    @Test
    fun markdownAndHtmlContainPlanAndEscapeText() {
        val r = report()
        val md = MarkdownReport.render(r)
        assertTrue("| Test E (testosterone enanthate) | Injectable steroid | 500 mg per week | 250 mg · 1.0 mL | Mon, Thu · Any time | – |" in md, md)
        assertTrue("### 2026-09-24 (Thursday)" in md)
        val html = HtmlReport.render(r)
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue("Lower-back pumps | cut sets" in html)
        assertFalse("<script" in html)
        val tricky = HtmlReport.render(r.copy(days = listOf(com.apollof.protocoltracker.domain.io.ReportDay(LocalDate.EPOCH, listOf(ReportEntry.Note(LocalTime.NOON, "<b>x</b>"))))))
        assertTrue("&lt;b&gt;x&lt;/b&gt;" in tricky)
    }
}
