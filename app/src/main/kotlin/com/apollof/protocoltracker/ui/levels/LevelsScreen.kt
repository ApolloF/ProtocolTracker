package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.pk.LevelMetrics
import com.apollof.protocoltracker.domain.pk.LevelMode
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.ScreenHeader
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.SettingsButton
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelsScreen(onOpenSettings: () -> Unit) {
    val vm = appViewModel { LevelsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Tracker.colors

    Scaffold(containerColor = c.bg) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.section, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            item(key = "header") {
                ScreenHeader("Levels", eyebrow = "Estimated") {
                    IconButton(onClick = vm::resetView, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Today, contentDescription = "Back to now", tint = c.ink)
                    }
                    SettingsButton(onOpenSettings)
                }
            }
            if (!state.loading && state.groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState("Nothing to plot", "Add compounds to your plan or log a dose. Levels are estimated from doses, time to peak and half-life.")
                }
                if (state.unplottable.isNotEmpty()) item(key = "unplottable") { UnplottableNote(state.unplottable) }
                return@LazyColumn
            }
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Segmented(LevelRange.entries, state.window.range, { it.label }) { vm.setRange(it) }
                    Segmented(
                        listOf(LevelMode.COMBINED, LevelMode.RECORDED, LevelMode.PLANNED), state.window.mode,
                        { when (it) { LevelMode.COMBINED -> "Logged + plan"; LevelMode.RECORDED -> "Logged"; LevelMode.PLANNED -> "Plan only" } },
                    ) { vm.setMode(it) }
                    if (state.groups.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.groups.forEach { g -> QuickChip(g, g !in state.hidden) { vm.toggleGroup(g) } }
                    }
                }
            }
            items(state.views, key = { it.name }) { view ->
                LedgerCard {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(view.series.colorArgb)
                            Text(view.name, style = TrackerType.title, color = c.ink, modifier = Modifier.weight(1f))
                            Text("est. ${view.series.scale.label}", style = TrackerType.numericSmall, color = c.muted)
                        }
                        LevelChart(view.series, state.fromMs, state.toMs, state.nowMs, state.bands, onPan = vm::pan, onZoom = vm::zoom)
                        view.metrics?.let { Metrics(it, view.series.scale.label) }
                    }
                }
            }
            if (state.unplottable.isNotEmpty()) item(key = "unplottable") { UnplottableNote(state.unplottable) }
            item(key = "note") {
                Text(
                    "Estimates in the style of Steroid Plotter: each dose rises to its peak, then halves every half-life. " +
                        "Values in ng/dL or ng/mL come from published peak concentrations; curves marked relative show active amount only. " +
                        "Individual blood levels differ. Drag to pan, pinch to zoom, tap to read.",
                    style = TrackerType.caption, color = c.muted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metrics(m: LevelMetrics, unit: String) {
    val zone = ZoneId.systemDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Metric("NOW", "${formatNumber(m.current, 1)} $unit")
        m.steadyState?.let {
            Metric("STEADY RANGE", "${formatNumber(it.trough, 0)}–${formatNumber(it.peak, 0)} $unit")
            Metric("STEADY AVG", "${formatNumber(it.average, 0)} $unit")
        }
        Metric("90% OF STEADY", Formats.halfLife(m.timeTo90.toMinutes() / 60.0))
        m.clearsAt?.let { Metric("BELOW 10%", it.atZone(zone).format(Formats.dayShort)) }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    val c = Tracker.colors
    Column(Modifier.background(c.band, RoundedCornerShape(Radii.medium)).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(label, style = TrackerType.overline, color = c.accentText)
        Text(value, style = TrackerType.numericSmall.copy(fontSize = TrackerType.bodySmall.fontSize), color = c.ink)
    }
}

@Composable
private fun UnplottableNote(names: List<String>, modifier: Modifier = Modifier) {
    Text(
        "No reliable level data: ${names.joinToString(", ")}. These are logged but not plotted.",
        style = TrackerType.caption, color = Tracker.colors.muted, modifier = modifier.fillMaxWidth(),
    )
}
