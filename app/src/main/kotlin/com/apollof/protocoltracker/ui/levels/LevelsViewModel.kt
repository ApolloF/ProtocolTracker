package com.apollof.protocoltracker.ui.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.pk.Compare
import com.apollof.protocoltracker.domain.pk.CompareBaseline
import com.apollof.protocoltracker.domain.pk.CompareResult
import com.apollof.protocoltracker.domain.pk.GroupSeries
import com.apollof.protocoltracker.domain.pk.LevelGroup
import com.apollof.protocoltracker.domain.pk.LevelMetrics
import com.apollof.protocoltracker.domain.pk.LevelMode
import com.apollof.protocoltracker.domain.pk.Levels
import com.apollof.protocoltracker.domain.pk.SteadyState
import com.apollof.protocoltracker.domain.pk.labPoints
import com.apollof.protocoltracker.domain.pk.levelDisplay
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.timeline.Timeline
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.components.Formats
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LevelRange(val label: String, val days: Long) {
    W2("2W", 14), M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365)
}

data class LevelWindow(val range: LevelRange = LevelRange.M1, val mode: LevelMode = LevelMode.COMBINED, val centerOffsetMs: Long = 0, val zoom: Double = 1.0)

data class PhaseBand(val name: String, val colorArgb: Long, val startMs: Long, val endMs: Long)

data class GroupView(val name: String, val series: GroupSeries, val metrics: LevelMetrics?, val measured: List<MeasuredPoint> = emptyList())

/** A compound in the jump bar: name, colour and the current estimate. */
data class GroupChip(val name: String, val colorArgb: Long, val now: String?)

/** A recent dose of the focused group (detail screen). */
data class DoseLine(val compound: String, val amount: String, val whenLabel: String)

/** Compare mode (experimental): the chosen groups on one percentage chart. */
data class CompareUi(
    val baseline: CompareBaseline,
    /** Groups in use that can be compared; [excluded] are left out. */
    val choices: List<GroupChip>,
    val excluded: Set<String>,
    val result: CompareResult,
)

data class LevelsState(
    val loading: Boolean = true,
    /** Groups in use now, in plan order: one chart each and a chip in the jump bar. */
    val current: List<GroupChip> = emptyList(),
    /** Groups not in use now (other phases, paused, old doses); a chart only when opened. */
    val others: List<LevelGroup> = emptyList(),
    val opened: Set<String> = emptySet(),
    /** Compounds in use without reliable level data. */
    val unplottable: List<String> = emptyList(),
    val window: LevelWindow = LevelWindow(),
    val fromMs: Long = 0,
    val toMs: Long = 0,
    val nowMs: Long = 0,
    val views: List<GroupView> = emptyList(),
    val bands: List<PhaseBand> = emptyList(),
    /** Detail screen only: recent taken doses of the focused group, newest first. */
    val doses: List<DoseLine> = emptyList(),
    /** Compare mode is switched on in Settings > Experimental. */
    val compareAvailable: Boolean = false,
    /** Null when showing separate charts. */
    val compare: CompareUi? = null,
    /** Experimental scrubbing: drag along a chart to read values and see logs near that time. */
    val scrub: Boolean = false,
    val haptics: Boolean = true,
    /** Doses and journal entries for the logs shown near the cursor. */
    val timeline: Timeline = Timeline(emptyList(), emptyList()),
) {
    val empty: Boolean get() = current.isEmpty() && others.isEmpty()
}

/**
 * Levels overview, or the detail of one group when [focus] is set (larger chart, metrics and recent doses).
 */
class LevelsViewModel(private val c: AppContainer, private val focus: String? = null) : ViewModel() {
    private val window = MutableStateFlow(LevelWindow())
    private val opened = MutableStateFlow<Set<String>>(emptySet())
    private val compareOn = MutableStateFlow(false)

    private data class Inputs(
        val protocol: Protocol,
        val logs: List<DoseLog>,
        val journal: List<JournalEntry>,
        val groups: List<LevelGroup>,
        val unplottable: List<String>,
        val slotTimes: SlotTimes,
        val settings: Settings,
        val timeline: Timeline,
    )

    private val inputs = combine(c.repository.protocol, c.repository.allLogs, c.repository.journal, c.settings.settings) { protocol, logs, journal, settings ->
        Inputs(
            protocol, logs, journal,
            Levels.groups(protocol.compounds, logs, protocol.phases, protocol.items, c.clock(), c.zone()),
            Levels.unplottable(protocol.compounds, logs, protocol.items),
            settings.slotTimes,
            settings,
            Timeline(logs, journal),
        )
    }

    /** Compare references depend on data and settings only; null when compare mode is off. */
    private val compareRefs = combine(inputs, compareOn) { input, on ->
        val s = input.settings
        if (focus != null || !s.experimentalCompare || !on) return@combine null
        Compare.references(
            input.groups.filter { it.current && it.name !in s.compareExcluded }, s.compareBaseline, s.compareAnchor,
            input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, c.clock(), c.zone(), input.slotTimes,
        )
    }.flowOn(Dispatchers.Default)

    /** Metrics don't depend on the visible window, so they are recomputed only when data or mode change. */
    private val metrics: Flow<Map<String, LevelMetrics?>> = combine(inputs, window.map { it.mode }) { input, mode ->
        val now = c.clock()
        input.groups.filter { focus == null || it.name == focus }.associate { g ->
            val m = Levels.metrics(g.name, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, mode, now, c.zone(), input.slotTimes)
            val scale = Levels.scale(g.name, input.protocol.compounds, input.logs, input.protocol.items)
            g.name to m?.let { if (scale == null) it else it.scaled(levelDisplay(scale, g.name, input.settings.labUnits).factor) }
        }
    }.flowOn(Dispatchers.Default)

    private fun LevelMetrics.scaled(f: Double): LevelMetrics = if (f == 1.0) this else copy(
        current = current * f,
        steadyState = steadyState?.let { SteadyState(it.peak * f, it.trough * f, it.average * f) },
    )

    val state: StateFlow<LevelsState> = combine(inputs, combine(metrics, compareRefs, ::Pair), window, opened) { input, (metrics, refs), w, openedGroups ->
        val now = c.clock()
        val zone = c.zone()
        val span = Duration.ofDays(w.range.days).toMillis() / w.zoom
        // Default window: one third history, two thirds ahead, so upcoming changes are visible.
        val center = now.toEpochMilli() + (span / 6).toLong() + w.centerOffsetMs
        val from = Instant.ofEpochMilli(center - (span / 2).toLong())
        val to = Instant.ofEpochMilli(center + (span / 2).toLong())
        val shown = input.groups.filter { g ->
            if (focus != null) g.name == focus else g.current || g.name in openedGroups
        }
        // Separate charts are not drawn while comparing.
        val views = if (refs != null) emptyList() else shown.mapNotNull { g ->
            val raw = Levels.series(
                g.name, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, w.mode, from, to, now, zone, input.slotTimes,
                points = if (focus != null) 900 else 600, colorArgb = g.colorArgb,
            ) ?: return@mapNotNull null
            val display = levelDisplay(raw.scale, g.name, input.settings.labUnits)
            val series = raw.inUnit(display)
            // Lab results on the curve are part of bloodwork, still a dev feature.
            val measured = if (!BuildConfig.DEV_FEATURES) emptyList() else labPoints(g.name, raw.scale, display, input.journal).map { p ->
                MeasuredPoint(p.at.toEpochMilli(), p.value, "Lab ${formatNumber(p.value, if (p.value < 10) 1 else 0)} ${series.unitLabel} · ${Formats.dayMonth.format(p.at.atZone(zone))}")
            }
            GroupView(g.name, series, metrics[g.name], measured)
        }
        val unitByGroup = views.associate { it.name to it.series.unitLabel }
        val chips = input.groups.filter { it.current }.map { g ->
            val m = metrics[g.name]
            GroupChip(g.name, g.colorArgb, m?.let { "${formatNumber(it.current, if (it.current < 10) 1 else 0)} ${unitByGroup[g.name].orEmpty()}".trim() })
        }
        val timeline = PhaseTimeline(input.protocol.phases)
        val bands = timeline.phases.map { p ->
            val end = timeline.effectiveEnd(p)?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
            PhaseBand(p.name, p.colorArgb, p.startDate.atStartOfDay(zone).toInstant().toEpochMilli(), end)
        }.filter { it.endMs > from.toEpochMilli() && it.startMs < to.toEpochMilli() }
        val doses = if (focus == null) emptyList() else input.logs
            .filter { it.status == LogStatus.TAKEN && it.snapshot.group == focus }
            .sortedByDescending { it.takenAt }.take(12)
            .map { log ->
                DoseLine(
                    log.snapshot.displayName,
                    describeDose(log.amount, log.snapshot.baseUnit, log.snapshot.formulation),
                    "${Formats.relativeDay(log.takenAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())} ${Formats.time(log.takenAt, zone)}",
                )
            }
        val compare = refs?.let {
            CompareUi(
                baseline = input.settings.compareBaseline,
                choices = chips,
                excluded = input.settings.compareExcluded,
                result = Compare.series(it, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, w.mode, from, to, now, zone, input.slotTimes),
            )
        }
        LevelsState(
            scrub = input.settings.experimentalScrub,
            haptics = input.settings.scrubHaptics,
            timeline = input.timeline,
            compareAvailable = focus == null && input.settings.experimentalCompare,
            compare = compare,
            loading = false,
            current = chips,
            others = input.groups.filter { !it.current },
            opened = openedGroups,
            unplottable = input.unplottable,
            window = w, fromMs = from.toEpochMilli(), toMs = to.toEpochMilli(), nowMs = now.toEpochMilli(),
            views = views, bands = bands, doses = doses,
        )
    }.conflate().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelsState())

    fun setCompare(on: Boolean) { compareOn.value = on }
    fun setCompareBaseline(baseline: CompareBaseline) = viewModelScope.launch { c.settings.update { it.copy(compareBaseline = baseline) } }
    fun setCompareAnchor(group: String) = viewModelScope.launch { c.settings.update { it.copy(compareAnchor = group) } }
    fun toggleCompareGroup(group: String) = viewModelScope.launch {
        c.settings.update { s -> s.copy(compareExcluded = if (group in s.compareExcluded) s.compareExcluded - group else s.compareExcluded + group) }
    }

    fun setRange(range: LevelRange) = window.update { LevelWindow(range = range, mode = it.mode) }
    fun setMode(mode: LevelMode) = window.update { it.copy(mode = mode) }

    /** Shows or hides the chart of a group that is not in use now. */
    fun toggleOther(group: String) = opened.update { if (group in it) it - group else it + group }
    fun resetView() = window.update { it.copy(centerOffsetMs = 0, zoom = 1.0) }

    /** [fraction] of the visible span; positive moves later in time. */
    fun pan(fraction: Float) = window.update { w ->
        val span = Duration.ofDays(w.range.days).toMillis() / w.zoom
        w.copy(centerOffsetMs = w.centerOffsetMs + (span * fraction).toLong())
    }

    fun zoom(factor: Float) = window.update { it.copy(zoom = (it.zoom * factor).coerceIn(0.25, 12.0)) }
}
