package com.apollof.protocoltracker.domain.timeline

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class EarlierTimeTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun aLaterClockTimeMeansYesterday() {
        val now = at("2026-10-02T00:30")
        assertEquals(at("2026-10-01T23:00"), atOrBefore(LocalDate.of(2026, 10, 2), LocalTime.of(23, 0), now, zone))
    }

    @Test
    fun anEarlierClockTimeStaysToday() {
        val now = at("2026-10-02T12:00")
        assertEquals(at("2026-10-02T10:00"), atOrBefore(LocalDate.of(2026, 10, 2), LocalTime.of(10, 0), now, zone))
        assertEquals(now, atOrBefore(LocalDate.of(2026, 10, 2), LocalTime.of(12, 0), now, zone), "now itself is not after now")
    }

    @Test
    fun aPastDayKeepsItsDate() {
        val now = at("2026-10-02T08:00")
        assertEquals(at("2026-09-30T21:00"), atOrBefore(LocalDate.of(2026, 9, 30), LocalTime.of(21, 0), now, zone))
    }

    @Test
    fun aDaylightSavingGapNeverLandsAhead() {
        // 2026-03-29 02:00-03:00 does not exist in Amsterdam; 02:30 resolves to 03:30, after 01:00.
        val now = at("2026-03-29T01:00")
        assertEquals(LocalDateTime.of(2026, 3, 28, 2, 30).atZone(zone).toInstant(), atOrBefore(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), now, zone))
    }
}
