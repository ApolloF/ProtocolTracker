package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.domain.units.formatNumber
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** "Pre-workout" or "09:00". */
fun describeTiming(timing: Timing): String = when (timing) {
    is Timing.Slot -> timing.slot.label
    is Timing.At -> timing.time.format(DisplayFormat.current.time)
}

/** Timings in day order: parts of the day by their order, exact times by clock time, "Any time" last. */
fun describeTimings(timings: List<Timing>): String = timings.sortedWith(timingOrder).joinToString(", ", transform = ::describeTiming)

private val timingOrder = compareBy<Timing>(
    { it is Timing.Slot && it.slot == DaySlot.ANY_TIME },
    { (it as? Timing.Slot)?.slot?.defaultTime ?: (it as Timing.At).time },
    { (it as? Timing.Slot)?.slot?.ordinal ?: -1 },
)

/** Days only: "Mon, Thu", "Daily", "Every 3 days", "Every 84 h", "As needed". */
fun describeDays(schedule: Schedule, locale: Locale = Locale.getDefault()): String = when (schedule) {
    is Schedule.Daily -> "Daily"
    is Schedule.Weekdays -> if (schedule.days.size == 7) "Daily" else
        schedule.days.sortedBy(DayOfWeek::getValue).joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    is Schedule.EveryNDays -> when (schedule.n) {
        1 -> "Daily"
        2 -> "Every other day"
        7 -> "Weekly"
        else -> "Every ${schedule.n} days"
    }
    is Schedule.EveryHours -> {
        val h = schedule.hours
        if (h >= 24 && (h % 12.0) == 0.0) "Every ${formatNumber(h / 24.0, 1)} days" else "Every ${formatNumber(h, 1)} h"
    }
    Schedule.AsNeeded -> "As needed"
}

/** Readable schedule, e.g. "Mon, Thu · Any time", "Daily · Morning, Pre-workout", "Every 3.5 days". */
fun describeSchedule(schedule: Schedule, locale: Locale = Locale.getDefault()): String {
    val days = describeDays(schedule, locale)
    return when (schedule) {
        is Schedule.Daily -> "$days · ${describeTimings(schedule.timings)}"
        is Schedule.Weekdays -> "$days · ${describeTimings(schedule.timings)}"
        is Schedule.EveryNDays -> "$days · ${describeTimings(schedule.timings)}"
        is Schedule.EveryHours, Schedule.AsNeeded -> days
    }
}
