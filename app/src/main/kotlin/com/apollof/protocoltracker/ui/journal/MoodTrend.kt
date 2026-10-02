package com.apollof.protocoltracker.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.apollof.protocoltracker.domain.model.MoodPoint
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.TrendChart
import com.apollof.protocoltracker.ui.components.TrendLine
import com.apollof.protocoltracker.ui.components.TrendMark
import com.apollof.protocoltracker.ui.components.TrendPoint
import com.apollof.protocoltracker.ui.components.TrendSeries
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.ZoneId

/** Shown under the mood chart until a point is selected. */
internal const val MOOD_TREND_CAPTION = "Mood from your symptom logs, 1 low to 10 great"

/**
 * The dev Symptoms view's mood chart (2 or more [points] from `moodTrend`): dots only (no line between sparse ratings),
 * a fixed 1–10 scale. Tap or slide to read a rating; the caption then names its day.
 */
@Composable
internal fun MoodTrendBlock(points: List<MoodPoint>, zone: ZoneId, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    var selected by remember(points) { mutableStateOf<Instant?>(null) }
    val series = remember(points) { listOf(TrendSeries(points.map { TrendPoint(it.at, it.mood.toDouble()) }, TrendLine.NONE, TrendMark.DOT)) }
    val first = points.first()
    val last = points.last()
    val description = "Mood chart, ${points.size} ratings from ${first.at.atZone(zone).format(Formats.dayMonth)} to " +
        "${last.at.atZone(zone).format(Formats.date)}, latest ${last.mood} of 10"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TrendChart(series, first.at, last.at, description, selectedAt = selected, onSelect = { selected = it }, yRange = 1.0..10.0)
        Text(
            points.firstOrNull { it.at == selected }?.let { "${Formats.dateTime(it.at, zone)} · mood ${it.mood}/10" } ?: MOOD_TREND_CAPTION,
            style = TrackerType.caption, color = c.muted,
        )
    }
}
