package com.apollof.protocoltracker.ui.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.pk.GroupSeries
import com.apollof.protocoltracker.domain.pk.LevelMetrics
import com.apollof.protocoltracker.domain.pk.LevelMode
import com.apollof.protocoltracker.domain.pk.Levels
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant

enum class LevelRange(val label: String, val days: Long) {
    W2("2W", 14), M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365)
}

data class LevelWindow(val range: LevelRange = LevelRange.M1, val mode: LevelMode = LevelMode.COMBINED, val centerOffsetMs: Long = 0, val zoom: Double = 1.0)

data class PhaseBand(val name: String, val colorArgb: Long, val startMs: Long, val endMs: Long)

data class GroupView(val name: String, val series: GroupSeries, val metrics: LevelMetrics?)

data class LevelsState(
    val loading: Boolean = true,
    val groups: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val window: LevelWindow = LevelWindow(),
    val fromMs: Long = 0,
    val toMs: Long = 0,
    val nowMs: Long = 0,
    val views: List<GroupView> = emptyList(),
    val bands: List<PhaseBand> = emptyList(),
)

class LevelsViewModel(private val c: AppContainer) : ViewModel() {
    private val window = MutableStateFlow(LevelWindow())
    private val hidden = MutableStateFlow<Set<String>>(emptySet())

    private data class Inputs(val protocol: Protocol, val logs: List<DoseLog>, val groups: List<String>)

    private val inputs = combine(c.repository.protocol, c.repository.allLogs) { protocol, logs ->
        val used = (protocol.items.mapNotNull { protocol.compounds[it.compoundId]?.group } + logs.map { it.snapshot.group }).distinct().sorted()
        Inputs(protocol, logs, used)
    }

    /** Metrics don't depend on the visible window, so they are recomputed only when data or mode change. */
    private val metrics = combine(inputs, window.map { it.mode }) { input, mode ->
        val now = c.clock()
        input.groups.associateWith { g ->
            Levels.metrics(g, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, mode, now, c.zone())
        }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<LevelsState> = combine(inputs, metrics, window, hidden) { input, metrics, w, hiddenGroups ->
        val now = c.clock()
        val span = Duration.ofDays(w.range.days).toMillis() / w.zoom
        // Default window: one third history, two thirds ahead, so upcoming changes are visible.
        val center = now.toEpochMilli() + (span / 6).toLong() + w.centerOffsetMs
        val from = Instant.ofEpochMilli(center - (span / 2).toLong())
        val to = Instant.ofEpochMilli(center + (span / 2).toLong())
        val views = input.groups.filter { it !in hiddenGroups }.mapNotNull { g ->
            val series = Levels.series(g, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, w.mode, from, to, now, c.zone())
                ?: return@mapNotNull null
            GroupView(g, series, metrics[g])
        }
        val timeline = PhaseTimeline(input.protocol.phases)
        val zone = c.zone()
        val bands = timeline.phases.map { p ->
            val end = timeline.effectiveEnd(p)?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
            PhaseBand(p.name, p.colorArgb, p.startDate.atStartOfDay(zone).toInstant().toEpochMilli(), end)
        }.filter { it.endMs > from.toEpochMilli() && it.startMs < to.toEpochMilli() }
        LevelsState(false, input.groups, hiddenGroups, w, from.toEpochMilli(), to.toEpochMilli(), now.toEpochMilli(), views, bands)
    }.conflate().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelsState())

    fun setRange(range: LevelRange) = window.update { LevelWindow(range = range, mode = it.mode) }
    fun setMode(mode: LevelMode) = window.update { it.copy(mode = mode) }
    fun toggleGroup(group: String) = hidden.update { if (group in it) it - group else it + group }
    fun resetView() = window.update { it.copy(centerOffsetMs = 0, zoom = 1.0) }

    /** [fraction] of the visible span; positive moves later in time. */
    fun pan(fraction: Float) = window.update { w ->
        val span = Duration.ofDays(w.range.days).toMillis() / w.zoom
        w.copy(centerOffsetMs = w.centerOffsetMs + (span * fraction).toLong())
    }

    fun zoom(factor: Float) = window.update { it.copy(zoom = (it.zoom * factor).coerceIn(0.25, 12.0)) }
}
