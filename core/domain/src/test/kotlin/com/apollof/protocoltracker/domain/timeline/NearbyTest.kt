package com.apollof.protocoltracker.domain.timeline

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearbyTest {
    private val t0 = Instant.parse("2026-09-01T08:00:00Z")
    private fun day(n: Double): Instant = t0.plusSeconds((n * 86_400).toLong())

    private fun dose(id: String, at: Instant, group: String, status: LogStatus = LogStatus.TAKEN) = DoseLog(
        id, "i", "c", null, null, at, Amount(125.0, DoseUnit.MG), null, status,
        snapshot = DoseSnapshot(group, group, CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null), createdAt = at,
    )

    private val logs = listOf(
        dose("t1", day(0.0), "Testosterone"),
        dose("n1", day(1.0), "Nandrolone"),
        dose("t2", day(3.5), "Testosterone"),
        dose("t3", day(4.0), "Testosterone", LogStatus.SKIPPED),
    )
    private val journal = listOf(
        JournalEntry.BloodPressure("bp", day(0.5), 130, 80, null, "", day(0.5)),
        JournalEntry.Note("note", day(3.0), "Tired", day(3.0)),
        JournalEntry.Symptoms("s", day(20.0), listOf("acne"), createdAt = day(20.0)),
    )
    private val timeline = Timeline(logs, journal)

    @Test
    fun itemsWithinTheWindowNearestFirstForTheGroupOnly() {
        val near = timeline.near(day(3.2).toEpochMilli(), "Testosterone")
        assertTrue(near.withinWindow)
        // Nandrolone and the skipped dose are left out; the note (0.2 d) comes before t2 (0.3 d) and t1 is > 2 days away.
        assertEquals(listOf("note", "t2"), near.items.map { it.id() })
        assertEquals("t1", near.lastDose?.id)
        assertEquals("bp", near.bloodPressure?.id)
    }

    @Test
    fun nearestItemWhenNothingIsClose() {
        val near = timeline.near(day(14.0).toEpochMilli(), "Testosterone")
        assertFalse(near.withinWindow)
        // t2 is 10.5 days back, the symptom log 6 days ahead.
        assertEquals(listOf("s"), near.items.map { it.id() })
        assertEquals("t2", near.lastDose?.id)
    }

    @Test
    fun bloodPressureIsCarriedForwardForAMonth() {
        assertEquals("bp", timeline.near(day(30.0).toEpochMilli(), null).bloodPressure?.id)
        assertNull(timeline.near(day(31.0).toEpochMilli(), null).bloodPressure)
        assertNull(timeline.near(day(0.4).toEpochMilli(), null).bloodPressure)
    }

    @Test
    fun rangeHelpersAreInclusive() {
        val times = longArrayOf(10, 20, 20, 30)
        assertEquals(1..2, Timeline.range(times, 20, 20))
        assertEquals(0..3, Timeline.range(times, 0, 100))
        assertTrue(Timeline.range(times, 21, 29).isEmpty())
        assertEquals(-1, Timeline.lastAtOrBefore(times, 5))
        assertEquals(4, Timeline.firstAfter(times, 30))
    }

    @Test
    fun marksAreTheGroupsDosesAndAllEntries() {
        val marks = timeline.marks("Testosterone")
        assertEquals(listOf(day(0.0), day(0.5), day(3.0), day(3.5), day(20.0)).map { it.toEpochMilli() }, marks.toList())
        assertTrue(timeline.crosses(marks, day(2.9).toEpochMilli(), day(3.1).toEpochMilli()))
        assertTrue(timeline.crosses(marks, day(3.1).toEpochMilli(), day(2.9).toEpochMilli()))
        assertFalse(timeline.crosses(marks, day(1.0).toEpochMilli(), day(2.0).toEpochMilli()))
        assertFalse(timeline.crosses(marks, day(3.0).toEpochMilli(), day(3.0).toEpochMilli()))
    }

    @Test
    fun windowCanBeChanged() {
        val near = timeline.near(day(3.2).toEpochMilli(), "Testosterone", window = Duration.ofDays(4))
        assertEquals(listOf("note", "t2", "bp", "t1"), near.items.map { it.id() })
    }

    private fun NearbyItem.id() = when (this) {
        is NearbyItem.Dose -> log.id
        is NearbyItem.Entry -> entry.id
    }
}
