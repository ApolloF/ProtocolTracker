package com.apollof.protocoltracker.domain.model

import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Average blood pressure of the 7 days ending at [end] ([weeksAgo] 0 is the last 7 days, ending now). [systolic] and
 * [diastolic] are rounded means, as on the Blood pressure card.
 */
data class BpWeek(val weeksAgo: Int, val end: Instant, val systolic: Int, val diastolic: Int, val readings: Int) {
    /** "Last 7 days · 127/81 mmHg · 9 readings", or "7 days to 19 Sep · 125/80 mmHg · 12 readings" for older weeks. */
    fun describe(zone: ZoneId): String {
        val span = if (weeksAgo == 0) "Last 7 days"
        else "7 days to ${DisplayFormat.current.dayMonth.format(end.minusMillis(1).atZone(zone))}"
        return "$span · $systolic/$diastolic mmHg · $readings ${if (readings == 1) "reading" else "readings"}"
    }
}

/** How many weeks back [bloodPressureWeeks] looks. */
const val BP_TREND_WEEKS = 26

private val WEEK: Duration = Duration.ofDays(7)

/**
 * 7-day averages of the blood pressure readings in [journal], counted back from [now], oldest first; weeks without
 * readings are left out. Week 0 holds every reading at or after `now − 7 d` (the card's own filter, so a reading timed
 * later today counts); week k holds `[now − 7(k+1) d, now − 7k d)`. Readings older than [maxWeeks] weeks are ignored.
 * Show a chart only with 2 or more weeks.
 */
fun bloodPressureWeeks(journal: List<JournalEntry>, now: Instant, maxWeeks: Int = BP_TREND_WEEKS): List<BpWeek> {
    val start = now.minus(WEEK)
    val buckets = journal.filterIsInstance<JournalEntry.BloodPressure>().groupBy { r ->
        if (r.at >= start) 0L else Duration.between(r.at, now).minusNanos(1).dividedBy(WEEK)
    }
    return buckets.filterKeys { it < maxWeeks }.toSortedMap(reverseOrder()).map { (k, readings) ->
        BpWeek(
            weeksAgo = k.toInt(),
            end = now.minus(WEEK.multipliedBy(k)),
            systolic = Math.round(readings.map { it.systolic }.average()).toInt(),
            diastolic = Math.round(readings.map { it.diastolic }.average()).toInt(),
            readings = readings.size,
        )
    }
}
