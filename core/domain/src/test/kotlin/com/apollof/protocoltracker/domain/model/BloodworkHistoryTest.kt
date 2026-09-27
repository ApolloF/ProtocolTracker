package com.apollof.protocoltracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BloodworkHistoryTest {
    private val ams = ZoneId.of("Europe/Amsterdam")
    private val now = LocalDateTime.of(2026, 9, 26, 10, 0).atZone(ams).toInstant()

    private fun draw(id: String, at: Instant, vararg results: MarkerResult, lab: String = "") =
        JournalEntry.Bloodwork(id, at, results.toList(), lab = lab, createdAt = at)

    private fun daysAgo(d: Long): Instant = now.minusSeconds(d * 86_400)

    @Test
    fun historyIsOldestFirstWithLabAndEntryAndSkipsDrawsWithoutTheMarker() {
        val journal = listOf(
            draw("c", daysAgo(10), MarkerResult("hematocrit", 49.0), lab = "Saltro"),
            draw("a", daysAgo(90), MarkerResult("hematocrit", 46.0), MarkerResult("estradiol", 30.0), lab = "Star-shl"),
            draw("b", daysAgo(40), MarkerResult("estradiol", 25.0)),
            JournalEntry.Note("n", daysAgo(5), "hematocrit", daysAgo(5)),
            JournalEntry.BloodPressure("bp", daysAgo(5), 120, 80, createdAt = daysAgo(5)),
        )
        val hct = markerHistory(journal, "hematocrit")
        assertEquals(listOf("a", "c"), hct.map { it.entryId })
        assertEquals(listOf(46.0, 49.0), hct.map { it.result.value })
        assertEquals(listOf("Star-shl", "Saltro"), hct.map { it.lab })
        assertEquals(listOf(daysAgo(90), daysAgo(10)), hct.map { it.at })
        assertEquals(emptyList(), markerHistory(journal, "no-such-marker"))
        assertEquals(emptyList(), markerHistory(listOf(journal[3], journal[4]), "hematocrit"))
    }

    @Test
    fun sameInstantDrawsComeOutByEntryId() {
        val at = daysAgo(3)
        val journal = listOf(
            draw("z", at, MarkerResult("psa", 1.0)),
            draw("m", at, MarkerResult("psa", 2.0)),
            draw("b", at, MarkerResult("psa", 3.0)),
        )
        assertEquals(listOf("b", "m", "z"), markerHistory(journal, "psa").map { it.entryId })
        assertEquals(listOf("b", "m", "z"), markerHistory(journal.reversed(), "psa").map { it.entryId })
    }

    @Test
    fun unlistedKeysWorkButAreNotPlottable() {
        val other = MarkerResult("other:ferritine", 120.0, name = "Ferritine", unit = "µg/L")
        val journal = listOf(
            draw("a", daysAgo(30), other),
            draw("b", daysAgo(2), other.copy(value = 140.0), MarkerResult("estradiol", 40.0, qualifier = MarkerResult.BELOW)),
            draw("c", daysAgo(1), MarkerResult("estradiol", 55.0)),
        )
        val points = markerHistory(journal, "other:ferritine")
        assertEquals(listOf(120.0, 140.0), points.map { it.result.value })
        assertEquals(emptyList(), plottable(points))
        val e2 = markerHistory(journal, "estradiol")
        assertEquals(2, e2.size)
        assertEquals(listOf("c"), plottable(e2).map { it.entryId })
    }

    @Test
    fun markerTrendsIsUnchanged() {
        val journal = listOf(
            draw("a", daysAgo(90), MarkerResult("hematocrit", 46.0)),
            draw("b", daysAgo(10), MarkerResult("hematocrit", 49.0)),
        )
        val t = markerTrends(journal).single()
        assertEquals(49.0, t.value)
        assertEquals(46.0, t.previous)
        assertEquals(daysAgo(90), t.previousAt)
    }

    private fun age(date: LocalDate, today: LocalDate = LocalDate.of(2026, 9, 26)): String? =
        lastDrawAge(listOf(date.atTime(8, 0).atZone(ams).toInstant()), today.atTime(10, 0).atZone(ams).toInstant(), ams)

    @Test
    fun wording() {
        val today = LocalDate.of(2026, 9, 26)
        assertEquals("today", age(today))
        assertEquals("yesterday", age(today.minusDays(1)))
        assertEquals("2 days ago", age(today.minusDays(2)))
        assertEquals("13 days ago", age(today.minusDays(13)))
        assertEquals("2 weeks ago", age(today.minusDays(14)))
        assertEquals("2 weeks ago", age(today.minusDays(20)))
        assertEquals("3 weeks ago", age(today.minusDays(21)))
        assertEquals("25 weeks ago", age(today.minusDays(181)))
        assertEquals("6 months ago", age(LocalDate.of(2026, 3, 26)))
        assertEquals("11 months ago", age(LocalDate.of(2025, 10, 1)))
        assertEquals("1 year ago", age(LocalDate.of(2025, 9, 26)))
        assertEquals("2 years ago", age(today.minusDays(800)))
    }

    @Test
    fun theLatestPastDrawCountsAndFutureDrawsAreIgnored() {
        val draws = listOf(daysAgo(40), daysAgo(3), now.plusSeconds(86_400))
        assertEquals("3 days ago", lastDrawAge(draws, now, ams))
        assertEquals("today", lastDrawAge(listOf(now), now, ams))
        assertNull(lastDrawAge(listOf(now.plusSeconds(86_400)), now, ams))
        assertNull(lastDrawAge(emptyList(), now, ams))
    }

    @Test
    fun aLateDrawIsYesterdayJustAfterMidnightInAnyZone() {
        for (zone in listOf(ams, ZoneOffset.ofHours(-10), ZoneOffset.UTC)) {
            val drawn = LocalDateTime.of(2026, 9, 25, 23, 30).atZone(zone).toInstant()
            val at = LocalDateTime.of(2026, 9, 26, 0, 10).atZone(zone).toInstant()
            assertEquals("yesterday", lastDrawAge(listOf(drawn), at, zone), zone.toString())
            assertTrue(lastDrawAge(listOf(drawn), drawn, zone) == "today")
        }
    }
}
