package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The plan card's schedule text and figures: every schedule kind and dose unit. */
class ScheduleTextTest {
    private val day = LocalDate.parse("2026-09-01")
    private val morning = Timing.Slot(DaySlot.MORNING)

    @BeforeTest
    fun twentyFourHour() {
        DisplayFormat.current = DisplayFormat()
    }

    @AfterTest
    fun reset() {
        DisplayFormat.current = DisplayFormat()
    }

    @Test
    fun daysNameEveryScheduleKind() {
        fun days(s: Schedule) = describeDays(s, Locale.UK)
        assertEquals("Daily", days(Schedule.Daily(listOf(morning))))
        assertEquals("Daily", days(Schedule.Weekdays(DayOfWeek.entries.toSet(), listOf(morning))))
        assertEquals("Mon, Thu", days(Schedule.Weekdays(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY), listOf(morning))))
        assertEquals("Daily", days(Schedule.EveryNDays(1, day, listOf(morning))))
        assertEquals("Every other day", days(Schedule.EveryNDays(2, day, listOf(morning))))
        assertEquals("Weekly", days(Schedule.EveryNDays(7, day, listOf(morning))))
        assertEquals("Every 10 days", days(Schedule.EveryNDays(10, day, listOf(morning))))
        // Whole and half days read as days; anything else stays in hours.
        assertEquals("Every 3.5 days", days(Schedule.EveryHours(84.0, Instant.EPOCH)))
        assertEquals("Every 2 days", days(Schedule.EveryHours(48.0, Instant.EPOCH)))
        assertEquals("Every 1.5 days", days(Schedule.EveryHours(36.0, Instant.EPOCH)))
        assertEquals("Every 30 h", days(Schedule.EveryHours(30.0, Instant.EPOCH)))
        assertEquals("Every 8 h", days(Schedule.EveryHours(8.0, Instant.EPOCH)))
        assertEquals("As needed", days(Schedule.AsNeeded))
    }

    @Test
    fun timingsRunInDayOrderWithAnyTimeLast() {
        val timings = listOf(Timing.Slot(DaySlot.ANY_TIME), Timing.At(LocalTime.of(14, 0)), Timing.Slot(DaySlot.EVENING), morning, Timing.At(LocalTime.of(6, 30)))
        assertEquals("06:30, Morning, 14:00, Evening, Any time", describeTimings(timings))
    }

    @Test
    fun scheduleTextJoinsDaysAndTimes() {
        assertEquals("Mon · Morning", describeSchedule(Schedule.Weekdays(setOf(DayOfWeek.MONDAY), listOf(morning)), Locale.UK))
        assertEquals("Every 3 days · Morning", describeSchedule(Schedule.EveryNDays(3, day, listOf(morning)), Locale.UK))
        assertEquals("Every 3.5 days", describeSchedule(Schedule.EveryHours(84.0, Instant.EPOCH), Locale.UK))
        assertEquals("As needed", describeSchedule(Schedule.AsNeeded, Locale.UK))
    }

    @Test
    fun tabletsInTabletsShowTheMassAndTheDailyTotal() {
        val anastrozole = Presets.byId("preset:anastrozole")!!
        val item = PlanItem(
            "ai", null, anastrozole.id, Amount(0.5, DoseUnit.TABLET),
            schedule = Schedule.Daily(listOf(morning, Timing.Slot(DaySlot.EVENING))),
        )
        val f = planFigures(item, anastrozole, Locale.UK)
        assertEquals("PER DOSE", f.perDoseLabel)
        assertEquals("0.5 mg", f.perDose)
        assertEquals("TABS" to "0.5 × 1 mg", f.detailLabel to f.detail)
        assertEquals("1 mg" to "per day", f.total to f.totalLabel)
        assertEquals("1 mg tabs", f.strength)
        assertNull(f.dosesPerWeek)
    }

    @Test
    fun aVolumeWithoutAStrengthStaysAPerDoseFigure() {
        val anastrozole = Presets.byId("preset:anastrozole")!!
        val item = PlanItem("ai", null, anastrozole.id, Amount(0.5, DoseUnit.ML), schedule = Schedule.Daily(listOf(morning)))
        val f = planFigures(item, anastrozole, Locale.UK)
        assertEquals("0.5 mL", f.perDose)
        assertEquals("0.5 mL" to "per dose", f.total to f.totalLabel)
        assertNull(f.detail)
    }

    @Test
    fun anIntervalDoseAddsUpToAWeek() {
        val testC = Presets.byId("preset:test-cyp")!!
        val item = PlanItem(
            "t", null, testC.id, Amount(100.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 200.0),
            Schedule.EveryNDays(3, day, listOf(morning)),
        )
        val f = planFigures(item, testC, Locale.UK)
        assertEquals("PER INJECTION", f.perDoseLabel)
        assertEquals("VOLUME" to "0.5 mL", f.detailLabel to f.detail)
        assertEquals("233.3 mg" to "per week", f.total to f.totalLabel)
        assertEquals("200 mg/mL", f.strength)
        assertEquals("Every 3 days", f.days)
        assertEquals("Morning", f.timing)
    }

    @Test
    fun aDailyVolumeTotalsTheMassPerDay() {
        val testC = Presets.byId("preset:test-cyp")!!
        val item = PlanItem("t", null, testC.id, Amount(0.25, DoseUnit.ML), formulation = Formulation(perMl = 200.0), schedule = Schedule.Daily(listOf(morning)))
        val f = planFigures(item, testC, Locale.UK)
        assertEquals("50 mg", f.perDose)
        assertEquals("50 mg" to "per day", f.total to f.totalLabel)
    }
}
