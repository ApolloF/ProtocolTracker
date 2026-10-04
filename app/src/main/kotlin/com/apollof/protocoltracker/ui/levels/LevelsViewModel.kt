package com.apollof.protocoltracker.ui.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.LEVELS_FEATURES
import com.apollof.protocoltracker.domain.entitlement.Resolved
import com.apollof.protocoltracker.domain.entitlement.available
import com.apollof.protocoltracker.domain.entitlement.maxCount
import com.apollof.protocoltracker.domain.entitlement.maxDays
import com.apollof.protocoltracker.domain.entitlement.trialEndsAt
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.pk.LevelAdjustments
import com.apollof.protocoltracker.domain.pk.GroupSeries
import com.apollof.protocoltracker.domain.pk.LevelGroup
import com.apollof.protocoltracker.domain.pk.LevelMetrics
import com.apollof.protocoltracker.domain.pk.LevelMode
import com.apollof.protocoltracker.domain.pk.LevelWindowRules
import com.apollof.protocoltracker.domain.pk.Levels
import com.apollof.protocoltracker.domain.pk.SteadyState
import com.apollof.protocoltracker.domain.pk.labPoints
import com.apollof.protocoltracker.domain.pk.levelDisplay
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.schedule.dateOf
import com.apollof.protocoltracker.domain.timeline.Timeline
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.minuteTicker
import com.apollof.protocoltracker.ui.components.Formats
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LevelRange(val label: String, val days: Long) {
    W2("2W", 14), M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365)
}

data class LevelWindow(val range: LevelRange = LevelRange.M1, val mode: LevelMode = LevelMode.COMBINED, val centerOffsetMs: Long = 0, val zoom: Double = 1.0) {
    /**
     * This window within [access]: the longest allowed range, no wider and no further back than its days
     * ([LevelWindowRules]), and Logged + plan when the other modes are left out.
     */
    fun limitedTo(access: LevelsAccess): LevelWindow {
        val days = LevelWindowRules.effectiveRange(LevelRange.entries.map { it.days }, range.days, access.maxDays)
        val z = LevelWindowRules.clampZoom(zoom, days, access.maxDays)
        return LevelWindow(
            range = LevelRange.entries.first { it.days == days },
            mode = if (access.modes) mode else LevelMode.COMBINED,
            centerOffsetMs = LevelWindowRules.clampOffset(centerOffsetMs, days, z, access.maxDays),
            zoom = z,
        )
    }
}

/** What the feature gate gives Levels now (FeatureGate); everything while unlocked or in the trial. */
data class LevelsAccess(
    /** End of the Levels trial while it runs. */
    val trialEndsAt: Instant? = null,
    /** How far back and how wide the window reaches: null no limit, 0 no charts. */
    val maxDays: Int? = null,
    /** Charts at once on the overview; null no limit. */
    val maxCharts: Int? = null,
    /** Logged and Plan only on the detail screen. */
    val modes: Boolean = true,
    /** Lab results on the curves. */
    val labs: Boolean = true,
) {
    /** No charts: a message and the way to Pro instead. */
    val locked: Boolean get() = maxDays == 0

    fun rangeAllowed(range: LevelRange): Boolean = LevelWindowRules.rangeAllowed(range.days, maxDays)

    companion object {
        fun of(resolved: Map<Feature, Resolved>) = LevelsAccess(
            trialEndsAt = trialEndsAt(LEVELS_FEATURES.map { resolved.getValue(it) }),
            maxDays = resolved.getValue(Feature.LEVELS_RANGE).maxDays,
            maxCharts = resolved.getValue(Feature.LEVELS_MULTI_COMPOUND).maxCount,
            modes = resolved.getValue(Feature.LEVELS_PLANNED_VS_LOGGED).available,
            labs = resolved.getValue(Feature.LEVELS_LAB_OVERLAY).available,
        )
    }
}

data class PhaseBand(val name: String, val colorArgb: Long, val startMs: Long, val endMs: Long)

/** One group's chart. [adjustPercent] is the user's adjustment (Adjust level); the series and metrics already include it. */
data class GroupView(
    val name: String,
    val series: GroupSeries,
    val metrics: LevelMetrics?,
    val measured: List<MeasuredPoint> = emptyList(),
    val adjustPercent: Int = 0,
    /** Lab results in view that the gate leaves off the curve ([measured] is then empty). */
    val hiddenLabs: Boolean = false,
) {
    /** "+15%" next to the group, so an adjusted estimate is never shown unmarked; null when not adjusted. */
    val adjustLabel: String? get() = LevelAdjustments.label(adjustPercent)
}

/** A compound in the jump bar: name, colour and the current estimate. */
data class GroupChip(val name: String, val colorArgb: Long, val now: String?)

/** A recent dose of the focused group (detail screen). */
data class DoseLine(val compound: String, val amount: String, val whenLabel: String)

data class LevelsState(
    val loading: Boolean = true,
    /** Groups in use now, in plan order: one chart each and a chip in the jump bar. */
    val current: List<GroupChip> = emptyList(),
    /** Groups not in use now (other phases, paused, old doses); a chart only when opened. */
    val others: List<LevelGroup> = emptyList(),
    /** Groups whose chart is open; one at a time, the one shown. */
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
    /** Doses and journal entries for the logs shown near the cursor. */
    val timeline: Timeline = Timeline(emptyList(), emptyList()),
    /** Reading panel: lab units for bloodwork lines, and common names by compound id for the short dose name. */
    val labUnits: LabUnits = LabUnits.CONVENTIONAL,
    val commonNames: Map<String, String> = emptyMap(),
    val access: LevelsAccess = LevelsAccess(),
) {
    val empty: Boolean get() = current.isEmpty() && others.isEmpty()

    /** The charts show the default window around now (not panned or zoomed), so "Back to now" has nothing to do. */
    val atDefault: Boolean get() = window.centerOffsetMs == 0L && window.zoom == 1.0

    /** The overview shows one chart at a time: the jump bar and the chips switch it instead of scrolling. */
    val oneAtATime: Boolean get() = access.maxCharts != null
}

/**
 * Levels overview, or the detail of one group when [focus] is set (larger chart, metrics and recent doses).
 * What the gate limits (range, charts at once, modes, lab results) is applied here; a tap on a limited option opens
 * the [paywall] instead.
 */
class LevelsViewModel(private val c: AppContainer, private val focus: String? = null) : ViewModel() {
    private val window = MutableStateFlow(LevelWindow())
    private val opened = MutableStateFlow<Set<String>>(emptySet())
    /** The chart picked in the jump bar or the chips while the overview shows one at a time. */
    private val selected = MutableStateFlow<String?>(null)
    private val _paywall = MutableStateFlow<Feature?>(null)
    /** The feature whose paywall is open after a tap on something the gate limits. */
    val paywall: StateFlow<Feature?> = _paywall

    private val accessFlow = c.gate.all.map { LevelsAccess.of(it) }.distinctUntilChanged()
    private val access: StateFlow<LevelsAccess?> = accessFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val shownWindow = combine(window, accessFlow) { w, a -> w.limitedTo(a) to a }
    private val picks = combine(opened, selected) { o, s -> o to s }

    init {
        // Opening Levels starts its trial; a trial already started keeps its start.
        viewModelScope.launch { c.gate.startTrials(LEVELS_FEATURES) }
    }

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
            Levels.groups(protocol.compounds, logs, protocol.phases, protocol.items, c.clock(), c.zone(), settings.slotTimes),
            Levels.unplottable(protocol.compounds, logs, protocol.items),
            settings.slotTimes,
            settings,
            Timeline(logs, journal),
        )
    }

    /** Metrics don't depend on the visible window, so they are recomputed only when data or mode change. */
    private val metrics: Flow<Map<String, LevelMetrics?>> = combine(inputs, shownWindow.map { it.first.mode }.distinctUntilChanged()) { input, mode ->
        val now = c.clock()
        input.groups.filter { focus == null || it.name == focus }.associate { g ->
            val adjust = input.settings.levelAdjustments
            val m = Levels.metrics(g.name, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, mode, now, c.zone(), input.slotTimes, adjust)
            val scale = Levels.scale(g.name, input.protocol.compounds, input.logs, input.protocol.items)
            g.name to m?.let { if (scale == null) it else it.scaled(levelDisplay(scale, g.name, input.settings.labUnits).factor) }
        }
    }.flowOn(Dispatchers.Default)

    private fun LevelMetrics.scaled(f: Double): LevelMetrics = if (f == 1.0) this else copy(
        current = current * f,
        steadyState = steadyState?.let { SteadyState(it.peak * f, it.trough * f, it.average * f) },
    )

    // Each minute "now" moves on, so the default window stays around the current time.
    val state: StateFlow<LevelsState> = combine(inputs, metrics, shownWindow, picks, minuteTicker(c.clock)) { input, metrics, (w, access), (openedGroups, chosen), now ->
        val zone = c.zone()
        val bounds = LevelWindowRules.bounds(now.toEpochMilli(), w.range.days, w.zoom, w.centerOffsetMs)
        val from = Instant.ofEpochMilli(bounds.fromMs)
        val to = Instant.ofEpochMilli(bounds.toMs)
        val names = if (focus != null) listOf(focus) else LevelWindowRules.overviewCharts(
            input.groups.map { it.name }, input.groups.filter { it.current }.mapTo(HashSet()) { it.name }, openedGroups, chosen, access.maxCharts,
        )
        val shown = names.mapNotNull { n -> input.groups.firstOrNull { it.name == n } }
        val views = shown.mapNotNull { g ->
            val raw = Levels.series(
                g.name, input.protocol.compounds, input.logs, input.protocol.phases, input.protocol.items, w.mode, from, to, now, zone, input.slotTimes,
                points = if (focus != null) 900 else 600, colorArgb = g.colorArgb, adjustments = input.settings.levelAdjustments,
            ) ?: return@mapNotNull null
            val display = levelDisplay(raw.scale, g.name, input.settings.labUnits)
            val series = raw.inUnit(display)
            // Lab results on the curve.
            val measured = labPoints(g.name, raw.scale, display, input.journal).map { p ->
                MeasuredPoint(p.at.toEpochMilli(), p.value, "Lab ${formatNumber(p.value, if (p.value < 10) 1 else 0)} ${series.unitLabel} · ${Formats.dayMonth.format(p.at.atZone(zone))}")
            }
            val hiddenLabs = !access.labs && measured.any { it.atMs in bounds.fromMs..bounds.toMs }
            GroupView(g.name, series, metrics[g.name], if (access.labs) measured else emptyList(), input.settings.levelAdjustments.percent(g.name), hiddenLabs)
        }
        val unitByGroup = views.associate { it.name to it.series.unitLabel }
        val chips = input.groups.filter { it.current }.map { g ->
            val m = metrics[g.name]
            val adjusted = LevelAdjustments.label(input.settings.levelAdjustments.percent(g.name))?.let { " · $it" }.orEmpty()
            GroupChip(g.name, g.colorArgb, m?.let { "${formatNumber(it.current, if (it.current < 10) 1 else 0)} ${unitByGroup[g.name].orEmpty()}".trim() + adjusted })
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
                    "${Formats.relativeDay(input.slotTimes.dateOf(log.takenAt, zone), input.slotTimes.dateOf(now, zone))} ${Formats.time(log.takenAt, zone)}",
                )
            }
        LevelsState(
            timeline = input.timeline,
            labUnits = input.settings.labUnits,
            commonNames = input.protocol.compounds.mapValues { it.value.commonName },
            loading = false,
            current = chips,
            others = input.groups.filter { !it.current },
            // One at a time, the chip of the chart shown is the selected one.
            opened = if (access.maxCharts != null) names.toSet() else openedGroups,
            unplottable = input.unplottable,
            window = w, fromMs = from.toEpochMilli(), toMs = to.toEpochMilli(), nowMs = now.toEpochMilli(),
            views = views, bands = bands, doses = doses,
            access = access,
        )
    }.conflate().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelsState())

    private suspend fun accessNow(): LevelsAccess = access.filterNotNull().first()

    /** Switches the range, or opens the paywall for a range longer than the gate allows. */
    fun setRange(range: LevelRange) {
        viewModelScope.launch {
            if (accessNow().rangeAllowed(range)) window.update { LevelWindow(range = range, mode = it.mode) } else _paywall.value = Feature.LEVELS_RANGE
        }
    }

    /** Switches the mode, or opens the paywall for Logged and Plan only when the gate leaves them out. */
    fun setMode(mode: LevelMode) {
        viewModelScope.launch {
            if (mode == LevelMode.COMBINED || accessNow().modes) window.update { it.copy(mode = mode) } else _paywall.value = Feature.LEVELS_PLANNED_VS_LOGGED
        }
    }

    /** Shows or hides the chart of a group that is not in use now; one at a time, shows its chart instead of the current one. */
    fun toggleOther(group: String) {
        if (access.value?.maxCharts != null) select(group) else opened.update { if (group in it) it - group else it + group }
    }

    /** One chart at a time: shows [group]'s chart. */
    fun select(group: String) {
        selected.value = group
    }

    fun showPaywall(feature: Feature) {
        _paywall.value = feature
    }

    fun dismissPaywall() {
        _paywall.value = null
    }

    fun resetView() = window.update { it.copy(centerOffsetMs = 0, zoom = 1.0) }

    /** Keeps the stored window within the gate, so a pan past the limit never builds up an offset to undo. */
    private fun updateWindow(transform: (LevelWindow) -> LevelWindow) = window.update { w ->
        val next = transform(w)
        access.value?.let { next.limitedTo(it) } ?: next
    }

    /** [fraction] of the visible span; positive moves later in time. */
    fun pan(fraction: Float) = updateWindow { w ->
        val span = LevelWindowRules.spanMs(w.range.days, w.zoom)
        w.copy(centerOffsetMs = w.centerOffsetMs + (span * fraction).toLong())
    }

    fun zoom(factor: Float) = updateWindow { it.copy(zoom = LevelWindowRules.clampZoom(it.zoom * factor, it.range.days, maxDays = null)) }

    /** Scales [group]'s estimate by [percent] (0 = as estimated); kept in the settings, so backups carry it. */
    fun setAdjustment(group: String, percent: Int) {
        viewModelScope.launch { c.settings.update { it.copy(levelAdjustments = it.levelAdjustments.with(group, percent)) } }
    }
}
