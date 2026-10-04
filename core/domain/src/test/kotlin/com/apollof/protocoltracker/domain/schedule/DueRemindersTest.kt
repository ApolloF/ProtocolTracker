package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** The reminders a phone that was off or a clock jump skipped (review 2026-10, M1). */
class DueRemindersTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val day = LocalDate.parse("2026-10-05")
    private fun at(time: String) = ZonedDateTime.of(day, LocalTime.parse(time), zone).toInstant()
    private val eight = PlanItem("e", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.At(LocalTime.of(8, 0)))), startDate = day)
    private val anyTime = PlanItem("a", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = day)
    private val quiet = eight.copy(id = "q", remind = false)

    private fun due(after: String, until: String, confirmed: Set<String> = emptySet()) =
        dueReminders(emptyList(), listOf(eight, anyTime, quiet), confirmed, at(after), at(until), zone, IntervalAnchors.NONE).map { it.key }

    @Test
    fun theRemindersBetweenTheMarkAndNowAreDueOnce() {
        // Off from 07:30 to 09:00: the 08:00 reminder was never posted.
        assertEquals(listOf("e@2026-10-05/T0800"), due("07:30", "09:00"))
        // From 09:00 on, nothing again until the any-time reminder at 19:00.
        assertEquals(emptyList(), due("09:00", "18:59"))
        assertEquals(listOf("a@2026-10-05/ANY_TIME"), due("09:00", "19:00"))
    }

    @Test
    fun confirmedDosesAndDosesWithoutRemindersAreNotDue() {
        assertEquals(emptyList(), due("07:30", "09:00", confirmed = setOf("e@2026-10-05/T0800")))
        // The mark itself is handled already; a clock set back gives nothing.
        assertEquals(emptyList(), due("08:00", "09:00"))
        assertEquals(emptyList(), due("09:00", "08:30"))
    }
}
