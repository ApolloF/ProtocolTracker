package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.units.formatNumber
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun formatTimes(times: List<LocalTime>): String = times.sorted().joinToString(", ") { it.format(timeFormat) }

/** Readable schedule, e.g. "Mon, Thu · 09:00", "Every 3.5 days", "Every other day · 08:00". */
fun describeSchedule(schedule: Schedule, locale: Locale = Locale.getDefault()): String = when (schedule) {
    is Schedule.Daily -> "Daily · ${formatTimes(schedule.times)}"
    is Schedule.Weekdays -> if (schedule.days.size == 7) "Daily · ${formatTimes(schedule.times)}" else
        schedule.days.sortedBy(DayOfWeek::getValue)
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) } + " · ${formatTimes(schedule.times)}"
    is Schedule.EveryNDays -> when (schedule.n) {
        1 -> "Daily · ${formatTimes(schedule.times)}"
        2 -> "Every other day · ${formatTimes(schedule.times)}"
        7 -> "Weekly · ${formatTimes(schedule.times)}"
        else -> "Every ${schedule.n} days · ${formatTimes(schedule.times)}"
    }
    is Schedule.EveryHours -> {
        val h = schedule.hours
        if (h >= 24 && (h % 12.0) == 0.0) "Every ${formatNumber(h / 24.0, 1)} days" else "Every ${formatNumber(h, 1)} h"
    }
    Schedule.AsNeeded -> "As needed"
}

/** Average doses per week, or null for as-needed. Used for weekly totals. */
fun dosesPerWeek(schedule: Schedule): Double? = when (schedule) {
    is Schedule.Daily -> 7.0 * schedule.times.size
    is Schedule.Weekdays -> schedule.days.size.toDouble() * schedule.times.size
    is Schedule.EveryNDays -> 7.0 / schedule.n * schedule.times.size
    is Schedule.EveryHours -> 168.0 / schedule.hours
    Schedule.AsNeeded -> null
}
