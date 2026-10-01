package com.apollof.protocoltracker.domain.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The moment a picked clock [time] stands for: [time] on [day] when that is not after [now], otherwise the latest
 * moment at [time] not after [now] (today, or yesterday). "Earlier…" pickers keep a date and ask only for the time,
 * so 23:00 picked at 00:30 means yesterday 23:00, never 22.5 hours ahead.
 */
fun atOrBefore(day: LocalDate, time: LocalTime, now: Instant, zone: ZoneId): Instant {
    val picked = day.atTime(time).atZone(zone).toInstant()
    if (!picked.isAfter(now)) return picked
    val today = now.atZone(zone).toLocalDate()
    val sameDay = today.atTime(time).atZone(zone).toInstant()
    return if (!sameDay.isAfter(now)) sameDay else today.minusDays(1).atTime(time).atZone(zone).toInstant()
}
