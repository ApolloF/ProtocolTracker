package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Duration
import java.time.Instant
import kotlin.math.sin

/** Design-review harness for [TrendChart]: a bloodwork series with a band, a one-sided band, and 26 weeks of BP with a selection. */
@Composable
internal fun TrendChartSamples(now: Instant) {
    fun daysAgo(n: Long) = now.minus(Duration.ofDays(n))
    val c = Tracker.colors
    Column(
        Modifier.fillMaxSize().background(c.surface).padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val hct = listOf(330L to 47.1, 250L to 50.2, 160L to 53.4, 70L to 51.0, 3L to 49.3).map { (d, v) -> TrendPoint(daysAgo(d), v) }
        val (hFrom, hTo) = TrendAxis.xWindow(hct.map { it.at }, Duration.ofDays(60))
        Text("Hematocrit, band 40–52", style = TrackerType.caption, color = c.muted)
        TrendChart(listOf(TrendSeries(hct, TrendLine.SOLID, TrendMark.DIAMOND)), hFrom, hTo, "Hematocrit chart", band = TrendBand(40.0, 52.0))

        val hdl = listOf(TrendPoint(daysAgo(23), 0.8), TrendPoint(daysAgo(3), 1.1))
        val (dFrom, dTo) = TrendAxis.xWindow(hdl.map { it.at }, Duration.ofDays(60))
        Text("HDL, band from 1.0 up", style = TrackerType.caption, color = c.muted)
        TrendChart(listOf(TrendSeries(hdl, TrendLine.SOLID, TrendMark.DIAMOND)), dFrom, dTo, "HDL chart", band = TrendBand(1.0, null))

        val weeks = (25 downTo 0).map { w -> daysAgo(w * 7L) }
        val sys = weeks.mapIndexed { i, at -> TrendPoint(at, 126 + 6 * sin(i / 3.0)) }
        val dia = weeks.mapIndexed { i, at -> TrendPoint(at, 81 + 4 * sin(i / 3.0 + 1)) }
        Text("Blood pressure, 26 weeks, one selected", style = TrackerType.caption, color = c.muted)
        TrendChart(
            listOf(TrendSeries(sys, TrendLine.SOLID, TrendMark.DOT), TrendSeries(dia, TrendLine.DASHED, TrendMark.RING)),
            weeks.first(), now, "Blood pressure chart", selectedAt = weeks[20], onSelect = {},
        )
    }
}
