package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class LogicalDayTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()
    private val four = LocalTime.of(4, 0)

    @Test
    fun timesBeforeTheDayStartBelongToTheDayBefore() {
        assertEquals(LocalDate.parse("2026-09-25"), logicalDate(at("2026-09-26", "01:30"), zone, four))
        assertEquals(LocalDate.parse("2026-09-26"), logicalDate(at("2026-09-26", "04:00"), zone, four))
        assertEquals(LocalDate.parse("2026-09-26"), logicalDate(at("2026-09-26", "23:59"), zone, four))
    }

    @Test
    fun midnightIsTheCalendarDay() {
        assertEquals(LocalDate.parse("2026-09-26"), logicalDate(at("2026-09-26", "00:00"), zone, LocalTime.MIDNIGHT))
        assertEquals(LocalDate.parse("2026-09-25"), logicalDate(at("2026-09-25", "23:59"), zone, LocalTime.MIDNIGHT))
    }

    @Test
    fun aDayStartInTheSpringGapMovesForward() {
        // 29 Mar 2026: clocks jump from 02:00 to 03:00 in Amsterdam.
        val two = LocalTime.of(2, 0)
        assertEquals(at("2026-03-29", "03:00"), logicalDayStart(LocalDate.parse("2026-03-29"), zone, two))
        assertEquals(LocalDate.parse("2026-03-28"), logicalDate(at("2026-03-29", "01:59"), zone, two))
        assertEquals(LocalDate.parse("2026-03-29"), logicalDate(at("2026-03-29", "03:00"), zone, two))
    }

    @Test
    fun aDayStartInTheAutumnOverlapUsesTheEarlierHour() {
        // 25 Oct 2026: 02:00–03:00 happens twice; the day starts at the first 02:00.
        val two = LocalTime.of(2, 0)
        val start = logicalDayStart(LocalDate.parse("2026-10-25"), zone, two)
        assertEquals(LocalDate.parse("2026-10-24"), logicalDate(start.minusSeconds(1), zone, two))
        assertEquals(LocalDate.parse("2026-10-25"), logicalDate(start.plusSeconds(3600), zone, two))
    }

    @Test
    fun slotTimesCarryTheDayStart() {
        val night = SlotTimes(dayStart = four)
        assertEquals(LocalDate.parse("2026-09-25"), night.dateOf(at("2026-09-26", "02:00"), zone))
        assertEquals(true, night.isOn(at("2026-09-26", "02:00"), LocalDate.parse("2026-09-25"), zone))
        assertEquals(false, night.isOn(at("2026-09-26", "04:00"), LocalDate.parse("2026-09-25"), zone))
        // Logs for the agenda start at the 25th's calendar midnight minus 48 h.
        assertEquals(at("2026-09-23", "00:00"), agendaLogsFrom(at("2026-09-26", "02:00"), zone, night))
        assertEquals(LocalDate.parse("2026-09-21"), weekStartOf(LocalDate.parse("2026-09-27")))
    }

    private val evening = PlanItem("e", null, "te", Amount(250.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.EVENING))))
    private fun eveningOf(date: String) = occurrences(emptyList(), listOf(evening), at(date, "00:00"), at(date, "23:59"), zone, IntervalAnchors.NONE).single()
    private fun log(status: LogStatus, takenAt: Instant) = DoseLog(
        "l", "e", "te", eveningOf("2026-09-25").key, eveningOf("2026-09-25").at, takenAt, Amount(250.0, DoseUnit.MG), null, status,
        snapshot = DoseSnapshot("Test E", "Test E", CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null), createdAt = takenAt,
    )

    @Test
    fun theSheetStartsAtNowForYesterdaysEveningDoseBeforeTheDayStart() {
        val occ = eveningOf("2026-09-25")
        val now = at("2026-09-26", "01:00")
        // At 1:00 with the day starting at 4:00, Today is still the 25th: the dose is taken now.
        assertEquals(now, sheetStartTime(occ, null, now, LocalDate.parse("2026-09-25"), backfill = false))
        // At midnight's day start the 25th is yesterday: an earlier dose starts at its planned time.
        assertEquals(occ.at, sheetStartTime(occ, null, now, LocalDate.parse("2026-09-26"), backfill = false))
        // Opened from a past day: always the planned time.
        assertEquals(occ.at, sheetStartTime(occ, null, now, LocalDate.parse("2026-09-25"), backfill = true))
    }

    @Test
    fun theSheetKeepsATakenTimeButNotASkip() {
        val occ = eveningOf("2026-09-25")
        val now = at("2026-09-26", "01:00")
        val taken = at("2026-09-25", "21:10")
        assertEquals(taken, sheetStartTime(occ, log(LogStatus.TAKEN, taken), now, LocalDate.parse("2026-09-25"), backfill = false))
        assertEquals(now, sheetStartTime(occ, log(LogStatus.SKIPPED, occ.at), now, LocalDate.parse("2026-09-25"), backfill = false))
    }
}
