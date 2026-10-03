package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.followsLastDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/*
 * The logical day: with "Day starts at" 4:00, 01:30 on Tuesday still belongs to Monday, so a dose taken before bed
 * counts for the day it was planned. Occurrences keep their own calendar date (and key); only which day is "today",
 * and on which day a log was made, follow the day start.
 */

/** When [date] starts: [dayStart] on that date (a time in a DST gap moves forward). */
fun logicalDayStart(date: LocalDate, zone: ZoneId, dayStart: LocalTime): Instant = ZonedDateTime.of(date, dayStart, zone).toInstant()

/** The day [at] belongs to: the calendar date, or the one before when [at] is earlier than that date's [dayStart]. */
fun logicalDate(at: Instant, zone: ZoneId, dayStart: LocalTime): LocalDate {
    val date = at.atZone(zone).toLocalDate()
    return if (at < logicalDayStart(date, zone, dayStart)) date.minusDays(1) else date
}

fun SlotTimes.dateOf(at: Instant, zone: ZoneId): LocalDate = logicalDate(at, zone, dayStart)

fun SlotTimes.dayStartOf(date: LocalDate, zone: ZoneId): Instant = logicalDayStart(date, zone, dayStart)

/** True when [at] falls within the logical day [date]. */
fun SlotTimes.isOn(at: Instant, date: LocalDate, zone: ZoneId): Boolean = at >= dayStartOf(date, zone) && at < dayStartOf(date.plusDays(1), zone)

/** The earliest log [buildAgenda] needs at [now]: the start of today's calendar date minus the missed-dose lookback. */
fun agendaLogsFrom(now: Instant, zone: ZoneId, slotTimes: SlotTimes): Instant =
    slotTimes.dateOf(now, zone).atStartOfDay(zone).toInstant().minus(AgendaWindows.missedLookback)

/**
 * Where the log sheet's time starts for a scheduled dose: a taken dose at its recorded time; a dose of today (by the
 * day start), or one whose interval restarts from the last dose, now; an earlier dose, or one opened from a past day
 * ([backfill]), at its planned time. A skipped dose has no time of its own, so it starts like an unlogged one.
 */
fun sheetStartTime(occ: Occurrence, existing: DoseLog?, now: Instant, today: LocalDate, backfill: Boolean): Instant = when {
    existing != null && existing.status == LogStatus.TAKEN -> existing.takenAt
    backfill -> occ.at
    occ.localDate == today || occ.item.schedule.followsLastDose -> now
    else -> occ.at
}

/** The Monday of [date]'s week. */
fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
