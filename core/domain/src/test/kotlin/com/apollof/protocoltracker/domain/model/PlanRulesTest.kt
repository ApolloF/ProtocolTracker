package com.apollof.protocoltracker.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the item editor refuses, and how a weekly dose splits over each schedule kind. */
class PlanRulesTest {
    private val day = LocalDate.parse("2026-09-01")
    private val morning = Timing.Slot(DaySlot.MORNING)

    @Test
    fun everyScheduleKindChecksItsOwnFields() {
        assertEquals(listOf("Choose when to take it"), Schedule.Daily(emptyList()).validate())
        assertEquals(listOf("Each time of day can be used once"), Schedule.Daily(listOf(morning, morning)).validate())
        assertEquals(
            listOf("At most 24 doses per day"),
            Schedule.Daily((0..24).map { Timing.At(LocalTime.of(it % 24, if (it == 24) 30 else 0)) }).validate(),
        )
        assertEquals(listOf("Choose at least one weekday"), Schedule.Weekdays(emptySet(), listOf(morning)).validate())
        assertEquals(listOf("Choose when to take it", "Interval must be 1–3650 days"), Schedule.EveryNDays(0, day, emptyList()).validate())
        assertEquals(emptyList(), Schedule.EveryNDays(3650, day, listOf(morning)).validate())
        for (hours in listOf(0.5, 24.0 * 365 + 1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(listOf("Interval must be 1 h – 1 year"), Schedule.EveryHours(hours, Instant.EPOCH).validate(), "$hours h")
        }
        assertEquals(emptyList(), Schedule.EveryHours(1.0, Instant.EPOCH).validate())
        assertEquals(emptyList(), Schedule.AsNeeded.validate())
    }

    @Test
    fun aWeeklyDoseNeedsARepeatingScheduleAndAnAmountUnit() {
        fun item(schedule: Schedule, unit: DoseUnit) = PlanItem("i", null, "c", Amount(250.0, unit), DoseBasis.PER_WEEK, schedule = schedule)
        assertEquals(listOf("A weekly dose needs a schedule"), item(Schedule.AsNeeded, DoseUnit.MG).validate())
        assertEquals(listOf("Enter the weekly dose in mg, mcg or IU"), item(Schedule.Daily(listOf(morning)), DoseUnit.ML).validate())
        assertEquals(listOf("Enter the weekly dose in mg, mcg or IU"), item(Schedule.Daily(listOf(morning)), DoseUnit.TABLET).validate())
        assertEquals(emptyList(), item(Schedule.Daily(listOf(morning)), DoseUnit.IU).validate())
    }

    @Test
    fun anEndBeforeTheStartIsRefused() {
        val item = PlanItem("i", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(morning)))
        assertEquals(listOf("End date is before start date"), item.copy(startDate = day, endDate = day.minusDays(1)).validate())
        assertEquals(emptyList(), item.copy(startDate = day, endDate = day).validate())
        assertEquals(emptyList(), item.copy(endDate = day).validate())
    }

    @Test
    fun aWeeklyDoseSplitsOverEachScheduleKind() {
        fun perDose(schedule: Schedule) =
            PlanItem("i", null, "c", Amount(210.0, DoseUnit.MG), DoseBasis.PER_WEEK, schedule = schedule).dosePerOccurrence().value
        assertEquals(15.0, perDose(Schedule.Daily(listOf(morning, Timing.Slot(DaySlot.EVENING)))), 1e-9)
        assertEquals(70.0, perDose(Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), listOf(morning))), 1e-9)
        assertEquals(90.0, perDose(Schedule.EveryNDays(3, day, listOf(morning))), 1e-9)
        assertEquals(105.0, perDose(Schedule.EveryHours(84.0, Instant.EPOCH)), 1e-9)
        // As needed has no weekly count: the amount is used as it is.
        assertEquals(210.0, perDose(Schedule.AsNeeded), 1e-9)
        assertNull(Schedule.AsNeeded.dosesPerWeek())
        assertEquals(emptyList(), Schedule.EveryHours(84.0, Instant.EPOCH).timings)
    }
}
