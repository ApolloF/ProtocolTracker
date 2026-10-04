package com.apollof.protocoltracker.domain.pk

import java.time.Duration
import kotlin.math.max

/**
 * The time window of the Levels charts and how a day limit ([com.apollof.protocoltracker.domain.entitlement.Feature.LEVELS_RANGE])
 * bounds it. A window is a range in days divided by a zoom factor, placed one third before now and two thirds after,
 * then moved by a pan offset. `maxDays` is the day limit: null means none, 0 means Levels is off.
 */
object LevelWindowRules {
    const val MIN_ZOOM = 0.25
    const val MAX_ZOOM = 12.0

    /** Window start and end in epoch milliseconds. */
    data class Bounds(val fromMs: Long, val toMs: Long)

    fun spanMs(rangeDays: Long, zoom: Double): Double = Duration.ofDays(rangeDays).toMillis() / zoom

    /** Default window: one third history, two thirds ahead, so upcoming changes are visible; then panned by [centerOffsetMs]. */
    fun bounds(nowMs: Long, rangeDays: Long, zoom: Double, centerOffsetMs: Long): Bounds {
        val span = spanMs(rangeDays, zoom)
        val center = nowMs + (span / 6).toLong() + centerOffsetMs
        return Bounds(center - (span / 2).toLong(), center + (span / 2).toLong())
    }

    /** Whether a range of [rangeDays] may be chosen. */
    fun rangeAllowed(rangeDays: Long, maxDays: Int?): Boolean = maxDays == null || rangeDays <= maxDays

    /**
     * The range in force: [chosen] when allowed, else the longest allowed of [ranges], else the shortest (a limit
     * shorter than every range keeps the shortest and narrows it by zoom, [clampZoom]).
     */
    fun effectiveRange(ranges: List<Long>, chosen: Long, maxDays: Int?): Long = when {
        rangeAllowed(chosen, maxDays) -> chosen
        else -> ranges.filter { rangeAllowed(it, maxDays) }.maxOrNull() ?: ranges.min()
    }

    /** [zoom] within its bounds, and never wider than [maxDays]. */
    fun clampZoom(zoom: Double, rangeDays: Long, maxDays: Int?): Double {
        val bounded = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        return if (maxDays == null || maxDays <= 0) bounded else max(bounded, rangeDays.toDouble() / maxDays)
    }

    /** [offsetMs] so the window starts no earlier than [maxDays] before now; panning ahead is never limited. */
    fun clampOffset(offsetMs: Long, rangeDays: Long, zoom: Double, maxDays: Int?): Long {
        if (maxDays == null || maxDays <= 0) return offsetMs
        val span = spanMs(rangeDays, zoom)
        // bounds(): from = now + span/6 + offset - span/2 must be >= now - maxDays.
        val earliest = (span / 2).toLong() - (span / 6).toLong() - Duration.ofDays(maxDays.toLong()).toMillis()
        return max(offsetMs, earliest)
    }

    /**
     * Groups with a chart on the overview, in [groups] order. Without a limit ([maxCharts] null): those in use now
     * ([current]) and those the user opened. With one: [selected] first (any group, so a chip of one not in use now
     * switches the chart), then those in use now, at most [maxCharts] but always one.
     */
    fun overviewCharts(groups: List<String>, current: Set<String>, opened: Set<String>, selected: String?, maxCharts: Int?): List<String> {
        if (maxCharts == null) return groups.filter { it in current || it in opened }
        val chosen = selected?.takeIf { it in groups }
        return (listOfNotNull(chosen) + groups.filter { it in current }).distinct().take(max(1, maxCharts))
    }
}
