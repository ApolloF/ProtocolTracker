package com.apollof.protocoltracker.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.MarkerFlag
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.MarkerSheetData
import com.apollof.protocoltracker.domain.model.flag
import com.apollof.protocoltracker.domain.model.labRange
import com.apollof.protocoltracker.domain.model.printedLabRange
import com.apollof.protocoltracker.domain.model.printedValue
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.TrendAxis
import com.apollof.protocoltracker.ui.components.TrendBand
import com.apollof.protocoltracker.ui.components.TrendChart
import com.apollof.protocoltracker.ui.components.TrendLine
import com.apollof.protocoltracker.ui.components.TrendMark
import com.apollof.protocoltracker.ui.components.TrendPoint
import com.apollof.protocoltracker.ui.components.TrendSeries
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Duration
import java.time.ZoneId

/**
 * Every result of one marker (dev), read only: with 2 or more plottable results a static chart (the shaded band is the
 * latest plotted result's range, and the caption says whose), then all results newest first, each flagged against its
 * own range and showing that range only where it differs from the band. Unlisted tests: the list only, as printed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerSheet(data: MarkerSheetData, units: LabUnits, onDismiss: () -> Unit) {
    val c = Tracker.colors
    val zone = remember { ZoneId.systemDefault() }
    val marker = BloodMarkers.find(data.key)
    val name = marker?.name ?: data.results.firstOrNull()?.result?.name ?: data.key
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = c.ink)
            if (marker != null && data.plotted.isNotEmpty()) {
                val (from, to) = remember(data) { TrendAxis.xWindow(data.results.map { it.at }, Duration.ofDays(60)) }
                fun shown(v: Double) = marker.fromStored(v, units)
                val points = data.plotted.map { TrendPoint(it.at, shown(it.result.value)) }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TrendChart(
                        listOf(TrendSeries(points, TrendLine.SOLID, TrendMark.DIAMOND)), from, to,
                        description = "$name chart, ${points.size} results",
                        band = data.band?.let { TrendBand(it.low?.let(::shown), it.high?.let(::shown)) },
                    )
                    data.band?.let { marker.rangeText(it, units) }?.let {
                        val whose = if (data.bandFromLab) "lab range of the latest result" else "typical adult male range"
                        Text("Shaded: $whose, $it", style = TrackerType.caption, color = c.muted)
                    }
                    if (data.leftOut) Text("Results with < or > are listed, not plotted.", style = TrackerType.caption, color = c.muted)
                }
            }
            Column {
                SectionLabel("All results")
                data.results.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    ResultRow(
                        title = listOf(p.at.atZone(zone).format(Formats.date), p.lab.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
                        meta = p.result.takeIf { data.showsRange(p) }?.refText(units),
                        value = p.result.valueText(units),
                        flag = p.result.flag(),
                    )
                }
            }
        }
    }
}

/** [this] result's value as shown: a known marker in [units] with its < or >, an unlisted test as printed. */
internal fun MarkerResult.valueText(units: LabUnits): String = BloodMarkers.find(marker)?.formatResult(this, units) ?: printedValue()

/** "ref 40–50 % (lab)" for a lab range, "ref 40–52 %" for the typical one; null without a range. */
internal fun MarkerResult.refText(units: LabUnits): String? {
    val known = BloodMarkers.find(marker) ?: return printedLabRange()?.let { "ref $it (lab)" }
    return labRange()?.let { known.rangeText(it, units) }?.let { "ref $it (lab)" } ?: known.referenceText(units)?.let { "ref $it" }
}

/**
 * One result: [title] and [meta] on the left, [value] and the flag as text on the right. With [onClick] the row opens
 * the marker's results over time and ends in a chevron; [inset] pads the row inside its tap area.
 */
@Composable
internal fun ResultRow(title: String, meta: String?, value: String, flag: MarkerFlag?, inset: Dp = 0.dp, onClick: (() -> Unit)? = null) {
    val c = Tracker.colors
    val tap = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = "Show results over time", onClick = onClick)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(tap).padding(horizontal = inset, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TrackerType.bodySmall, color = c.ink)
            meta?.let { Text(it, style = TrackerType.caption, color = c.muted) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = NumericStyle, color = c.ink)
            if (flag != null) Text(
                flag.label, style = TrackerType.caption.copy(fontWeight = if (flag == MarkerFlag.NORMAL) FontWeight.Normal else FontWeight.SemiBold),
                color = if (flag == MarkerFlag.NORMAL) c.muted else c.warn,
            )
        }
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.muted)
    }
}
