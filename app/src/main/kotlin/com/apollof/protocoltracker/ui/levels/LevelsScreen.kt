package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.apollof.protocoltracker.ui.components.Segmented
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LevelsScreen(onOpenSettings: () -> Unit) {
    val vm = appViewModel { LevelsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Levels") },
                actions = {
                    IconButton(onClick = vm::resetView) { Icon(Icons.Outlined.Today, contentDescription = "Back to now") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        if (!state.loading && state.groups.isEmpty()) {
            EmptyState("Nothing to plot", "Add compounds to your plan or log a dose. Levels are estimated from doses and half-lives.", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Segmented(LevelRange.entries, state.window.range, { it.label }) { vm.setRange(it) }
                    Segmented(
                        listOf(LevelMode.COMBINED, LevelMode.RECORDED, LevelMode.PLANNED), state.window.mode,
                        { when (it) { LevelMode.COMBINED -> "Logged + plan"; LevelMode.RECORDED -> "Logged"; LevelMode.PLANNED -> "Plan only" } },
                    ) { vm.setMode(it) }
                    if (state.groups.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.groups.forEach { g ->
                            FilterChip(selected = g !in state.hidden, onClick = { vm.toggleGroup(g) }, label = { Text(g) })
                        }
                    }
                }
            }
            items(state.views, key = { it.name }) { view ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            ColorDot(view.series.colorArgb)
                            Text(view.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("est. ${view.series.unit.label} in body", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LevelChart(view.series, state.fromMs, state.toMs, state.nowMs, state.bands, onPan = vm::pan, onZoom = vm::zoom)
                        view.metrics?.let { Metrics(it, view.series.unit.label) }
                    }
                }
            }
            item(key = "note") {
                Text(
                    "Estimates from one-compartment half-life models. Shape and relative changes are meaningful; absolute values are not blood levels. Drag to pan, pinch to zoom, tap to read.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metrics(m: LevelMetrics, unit: String) {
    val zone = ZoneId.systemDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("Now", "${formatNumber(m.current, 1)} $unit")
        m.steadyState?.let {
            Metric("Steady range", "${formatNumber(it.trough, 0)}–${formatNumber(it.peak, 0)} $unit")
            Metric("Steady avg", "${formatNumber(it.average, 0)} $unit")
        }
        Metric("90% of steady", Formats.halfLife(m.timeTo90.toMinutes() / 60.0))
        m.clearsAt?.let { Metric("Below 10%", it.atZone(zone).format(Formats.dayShort)) }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
