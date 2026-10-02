package com.apollof.protocoltracker.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** One mood rating (1 low, 10 great) from a symptom log. */
data class MoodPoint(val at: Instant, val mood: Int)

/** How far back [moodTrend] looks from the latest rating. */
const val MOOD_TREND_DAYS = 90L

/**
 * Mood ratings for the Symptoms chart: every symptom log with a mood in the [MOOD_TREND_DAYS] days up to the latest
 * one, oldest first. Empty when the ratings fall on fewer than 2 days, so a lone rating draws no chart.
 */
fun moodTrend(journal: List<JournalEntry>, zone: ZoneId): List<MoodPoint> {
    val rated = journal.filterIsInstance<JournalEntry.Symptoms>().mapNotNull { e -> e.mood?.let { MoodPoint(e.at, it) } }
    val latest = rated.maxOfOrNull { it.at } ?: return emptyList()
    val from = latest.minus(Duration.ofDays(MOOD_TREND_DAYS))
    val points = rated.filter { it.at >= from }.sortedBy { it.at }
    return if (points.map { it.at.atZone(zone).toLocalDate() }.distinct().size < 2) emptyList() else points
}
