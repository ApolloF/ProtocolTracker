package com.apollof.protocoltracker.domain.model

import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One result of a marker with the draw it came from. */
data class MarkerPoint(val at: Instant, val result: MarkerResult, val lab: String, val entryId: String)

/**
 * Every result of the marker [key] across all bloodwork, oldest first (same-instant draws by entry id). Draws without
 * the marker give nothing; `other:` keys work the same way. Flag the points with [MarkerResult.flag].
 */
fun markerHistory(journal: List<JournalEntry>, key: String): List<MarkerPoint> =
    journal.filterIsInstance<JournalEntry.Bloodwork>()
        .sortedWith(compareBy<JournalEntry.Bloodwork>({ it.at }, { it.id }))
        .mapNotNull { d -> d.result(key)?.let { MarkerPoint(d.at, it, d.lab, d.id) } }

/** The points a chart can draw: known markers with an exact value (a censored "<0.1" is listed, never plotted). */
fun plottable(points: List<MarkerPoint>): List<MarkerPoint> =
    points.filter { it.result.qualifier == null && BloodMarkers.find(it.result.marker) != null }

/**
 * Latest result of one unlisted test (a key no [BloodMarkers] entry has, such as `other:ferritine`), drawn at [at].
 * [name] is the name the lab printed; the value and unit stay as printed, and only a lab range flags it.
 */
data class UnlistedTrend(val key: String, val at: Instant, val result: MarkerResult) {
    val name: String get() = result.name ?: key
}

/** Latest result per unlisted test, by name. */
fun unlistedTrends(journal: List<JournalEntry>): List<UnlistedTrend> =
    journal.filterIsInstance<JournalEntry.Bloodwork>()
        .sortedWith(compareByDescending<JournalEntry.Bloodwork> { it.at }.thenBy { it.id })
        .flatMap { d -> d.results.filter { BloodMarkers.find(it.marker) == null }.map { UnlistedTrend(it.marker, d.at, it) } }
        .distinctBy { it.key }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/**
 * What the marker sheet shows for one key: every result newest first ([results]); for a known marker with 2 or more
 * plottable results also the chart's points, oldest first ([plotted]), and the shaded [band]: the latest plotted
 * result's lab range. Without one, no band: the app never draws a range of its own. Otherwise no chart and no band.
 */
data class MarkerSheetData(val key: String, val results: List<MarkerPoint>, val plotted: List<MarkerPoint>, val band: RefRange?) {
    /** Some result is listed but not plotted (a censored "<5"); only said under a chart. */
    val leftOut: Boolean get() = plotted.isNotEmpty() && plotted.size < results.size

    /** A row shows its own range only where it differs from the band. */
    fun showsRange(point: MarkerPoint): Boolean = point.result.labRange() != band
}

fun markerSheetData(journal: List<JournalEntry>, key: String): MarkerSheetData {
    val history = markerHistory(journal, key)
    val plotted = plottable(history).takeIf { it.size >= 2 }.orEmpty()
    val latest = plotted.lastOrNull()?.result
    return MarkerSheetData(key, history.reversed(), plotted, latest?.labRange())
}

/**
 * How long ago the latest draw at or before [now] was, by calendar days in [zone]: "today", "yesterday", "N days ago"
 * up to 13 days, "N weeks ago" up to 181 days, then "N months ago" and "N years ago". Null without a past draw.
 */
fun lastDrawAge(draws: List<Instant>, now: Instant, zone: ZoneId): String? {
    val latest = draws.filter { it <= now }.maxOrNull() ?: return null
    val day = latest.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    val months = Period.between(day, today).toTotalMonths()
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days <= 13 -> "$days days ago"
        days <= 181 -> "${days / 7} weeks ago"
        months < 12 -> "$months months ago"
        else -> (months / 12).let { if (it == 1L) "1 year ago" else "$it years ago" }
    }
}
