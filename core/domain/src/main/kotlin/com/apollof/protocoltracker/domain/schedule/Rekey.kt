package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.timings
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/*
 * Exact-time doses are keyed by calendar date and planned clock time (`timeOccurrenceKey`), so a taken dose stays taken
 * when the time zone or the item's time changes. Both functions return only the logs whose key changes, with the new
 * key; a key another log already holds is never given out, so the DB's one-log-per-key rule holds.
 */

/**
 * Logs keyed by instant (`itemId@epochSecond`, earlier versions) whose item still plans an exact time at that instant
 * in [zone]: they move to the date-and-time key. Interval doses (every X hours) keep their instant keys, and logs that
 * match no planned time stay as they are.
 */
fun rekeyInstantLogs(items: List<PlanItem>, logs: List<DoseLog>, zone: ZoneId): List<DoseLog> {
    val byId = items.associateBy { it.id }
    val used = logs.mapNotNullTo(HashSet()) { it.occurrenceKey }
    return logs.mapNotNull { log ->
        val ref = log.occurrenceKey?.let(::parseOccurrenceKey) as? OccurrenceRef.Timed ?: return@mapNotNull null
        val item = byId[ref.itemId] ?: return@mapNotNull null
        val date = ref.at.atZone(zone).toLocalDate()
        val time = item.exactTimes().firstOrNull { ZonedDateTime.of(date, it, zone).toInstant() == ref.at } ?: return@mapNotNull null
        val key = timeOccurrenceKey(item.id, date, time)
        if (used.add(key)) log.copy(occurrenceKey = key) else null
    }
}

/**
 * Logs of [after] that follow a change of its exact times from [before]: each removed time moves to an added one,
 * in time order, when as many times were removed as added (08:00 → 08:30 moves every 08:00 log to 08:30). Times that
 * stay keep their logs. When the counts differ, which dose became which is unclear and nothing moves.
 */
fun rekeyForTimeEdit(before: PlanItem, after: PlanItem, logs: List<DoseLog>): List<DoseLog> {
    if (before.id != after.id) return emptyList()
    val old = before.exactTimes()
    val new = after.exactTimes()
    val removed = (old - new.toSet()).sorted()
    val added = (new - old.toSet()).sorted()
    if (removed.isEmpty() || removed.size != added.size) return emptyList()
    val moves = removed.zip(added).toMap()
    val moving = logs.mapNotNull { log ->
        val ref = log.occurrenceKey?.let(::parseOccurrenceKey) as? OccurrenceRef.AtTime ?: return@mapNotNull null
        if (ref.itemId != after.id) return@mapNotNull null
        val to = moves[ref.time] ?: return@mapNotNull null
        log to timeOccurrenceKey(after.id, ref.date, to)
    }
    val movingIds = moving.mapTo(HashSet()) { it.first.id }
    val staying = logs.filter { it.id !in movingIds }.mapNotNullTo(HashSet()) { it.occurrenceKey }
    return moving.filter { (_, key) -> key !in staying }.map { (log, key) -> log.copy(occurrenceKey = key) }
}

/** The item's exact clock times, to the minute (the precision of the key). */
private fun PlanItem.exactTimes(): List<LocalTime> =
    schedule.timings.filterIsInstance<Timing.At>().map { it.time.truncatedTo(ChronoUnit.MINUTES) }.distinct()
