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
import com.apollof.protocoltracker.domain.model.BpWeek
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.TrendChart
import com.apollof.protocoltracker.ui.components.TrendLine
import com.apollof.protocoltracker.ui.components.TrendMark
import com.apollof.protocoltracker.ui.components.TrendPoint
import com.apollof.protocoltracker.ui.components.TrendSeries
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Shown under the chart until a point is selected; systolic is always the upper line, so no legend. */
internal const val BP_TREND_CAPTION = "Each point is a 7-day average"

/**
 * The dev Blood pressure card's trend (2 or more [weeks] from `bloodPressureWeeks`): systolic solid with dots,
 * diastolic dashed with rings, no band (a "healthy" range would be advice), x from the oldest week to now. Tap or slide
 * to read a week; the caption then describes it. The selection is local and resets when the weeks change.
 */
@Composable
internal fun BpTrendBlock(weeks: List<BpWeek>, zone: ZoneId, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    var selected by remember(weeks) { mutableStateOf<Instant?>(null) }
    // Week k ends 7k days before now, so every week gives the same now.
    val now = weeks.last().let { it.end.plus(Duration.ofDays(7L * it.weeksAgo)) }
    val from = weeks.first().end
    val series = remember(weeks) {
        listOf(
            TrendSeries(weeks.map { TrendPoint(it.end, it.systolic.toDouble()) }, TrendLine.SOLID, TrendMark.DOT),
            TrendSeries(weeks.map { TrendPoint(it.end, it.diastolic.toDouble()) }, TrendLine.DASHED, TrendMark.RING),
        )
    }
    val latest = weeks.last()
    val description = "Blood pressure chart, 7-day averages, ${weeks.size} points from " +
        "${from.atZone(zone).format(Formats.dayMonth)} to ${now.atZone(zone).format(Formats.date)}, " +
        "latest ${latest.systolic}/${latest.diastolic} mmHg"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TrendChart(series, from, now, description, selectedAt = selected, onSelect = { selected = it })
        Text(
            weeks.firstOrNull { it.end == selected }?.describe(zone) ?: BP_TREND_CAPTION,
            style = TrackerType.caption, color = c.muted,
        )
    }
}
