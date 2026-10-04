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
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.schedule.SlotTimes
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

    private fun report(logs: List<DoseLog> = this.logs) = ReportBuilder.build(
        protocol, logs, journal, LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-25"), at("2026-09-25", "12:00"), zone, locale = Locale.ENGLISH,
    )

    @Test
    fun missedAndAdherenceCountFromTheFirstDoseLog() {
        // A month of plan days before the first log (Sep 24): with countFrom only the days since then count.
        fun missed(r: Report) = r.days.flatMap { d -> d.entries.filterIsInstance<ReportEntry.Dose>().filter { it.status == "missed" }.map { d.date } }
        val wide = ReportBuilder.build(
            protocol, logs, journal, LocalDate.parse("2026-08-24"), LocalDate.parse("2026-09-25"), at("2026-09-25", "12:00"), zone, locale = Locale.ENGLISH,
        )
        val fromFirst = ReportBuilder.build(
            protocol, logs, journal, LocalDate.parse("2026-08-24"), LocalDate.parse("2026-09-25"), at("2026-09-25", "12:00"), zone,
            locale = Locale.ENGLISH, countFrom = LocalDate.parse("2026-09-24"),
        )
        assertTrue(missed(wide).any { it < LocalDate.parse("2026-09-24") })
        assertTrue(missed(fromFirst).isNotEmpty() && missed(fromFirst).all { it >= LocalDate.parse("2026-09-24") })
        val anavarScheduled = fromFirst.adherence.single { it.compound.startsWith("Anavar") }.scheduled
        assertEquals(1, anavarScheduled, "only Sep 24 counts (Sep 25 is today)")
    }

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
        val gel = planFigures(orals, anavar.copy(route = Route.TOPICAL), Locale.ENGLISH)
        assertEquals(null to null, gel.detail to gel.strength, "a gel is not counted in tablets")
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
    fun entriesBeforeTheDayStartAreListedLastUnderTheDayBefore() {
        val late = JournalEntry.Note("late", at("2026-09-25", "01:30"), "Late night", at("2026-09-25", "01:30"))
        val r = ReportBuilder.build(
            protocol, logs, journal + late, LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-25"), at("2026-09-25", "12:00"), zone,
            slotTimes = SlotTimes(dayStart = LocalTime.of(4, 0)), locale = Locale.ENGLISH,
        )
        val day = r.days.single { it.date == LocalDate.parse("2026-09-24") }
        assertEquals("01:30 · Note · Late night", MarkdownReport.line(day.entries.last()))
        assertFalse(r.days.any { d -> d.date == LocalDate.parse("2026-09-25") && d.entries.any { it is ReportEntry.Note } })
    }

    @Test
    fun aSkipIsReportedOnItsPlannedDayWithoutAnAmount() {
        // Skipped on the 25th (stored the old way, at the moment of skipping) for the dose planned on the 24th.
        val skip = logs.single().copy(id = "s", takenAt = at("2026-09-25", "09:30"), status = LogStatus.SKIPPED, note = "")
        val r = report(listOf(skip))
        val line = r.days.single { it.date == LocalDate.parse("2026-09-24") }.entries.filterIsInstance<ReportEntry.Dose>()
            .single { it.status == "skipped" }.let(MarkdownReport::line)
        assertEquals("17:00 · Anavar (oxandrolone) · skipped · pre-workout", line)
        assertTrue(HtmlReport.render(r).contains("<td class=\"num\">–</td><td class=\"status skipped\">"))
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

    @Test
    fun injectionSitesAreNotReported() {
        val sited = report(logs + logs.map { it.copy(id = "l2", site = "delt_l", takenAt = at("2026-09-24", "20:00"), createdAt = at("2026-09-24", "20:00")) })
        val plain = report(logs + logs.map { it.copy(id = "l2", takenAt = at("2026-09-24", "20:00"), createdAt = at("2026-09-24", "20:00")) })
        assertEquals(MarkdownReport.render(plain), MarkdownReport.render(sited))
        assertEquals(HtmlReport.render(plain), HtmlReport.render(sited))
    }
}
