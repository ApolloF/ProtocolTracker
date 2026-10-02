package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextDoseTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val start = LocalDate.of(2026, 9, 1)

    private fun weekly(id: String, day: DayOfWeek, slot: DaySlot = DaySlot.MORNING, enabled: Boolean = true) = PlanItem(
        id, null, "preset:tirzepatide", Amount(5.0, DoseUnit.MG),
        schedule = Schedule.Weekdays(setOf(day), listOf(Timing.Slot(slot))), startDate = start, enabled = enabled,
    )

    @Test
    fun theEarliestDoseOfAnyItem() {
        val from = LocalDateTime.of(2026, 10, 2, 12, 0).atZone(zone).toInstant() // a Friday
        val next = nextOccurrence(emptyList(), listOf(weekly("a", DayOfWeek.THURSDAY), weekly("b", DayOfWeek.MONDAY, DaySlot.EVENING)), from, zone, IntervalAnchors.NONE)
        assertEquals("b" to LocalDate.of(2026, 10, 5), next?.item?.id to next?.localDate)
    }

    @Test
    fun pausedAndAsNeededItemsDoNotCount() {
        val from = LocalDateTime.of(2026, 10, 2, 12, 0).atZone(zone).toInstant()
        val asNeeded = weekly("c", DayOfWeek.MONDAY).copy(schedule = Schedule.AsNeeded)
        assertNull(nextOccurrence(emptyList(), listOf(weekly("a", DayOfWeek.MONDAY, enabled = false), asNeeded), from, zone, IntervalAnchors.NONE))
    }
}
