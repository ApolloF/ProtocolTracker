package com.apollof.protocoltracker.ui.levels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.billing.PaywallSheet
import com.apollof.protocoltracker.billing.TrialBanner
import com.apollof.protocoltracker.data.Motion
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.pk.LevelAdjustments
import com.apollof.protocoltracker.domain.pk.LevelMetrics
import com.apollof.protocoltracker.domain.pk.LevelMode
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.AccentTextButton
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.FitRow
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.ScreenHeader
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.SettingsButton
import com.apollof.protocoltracker.ui.theme.Motions
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ESTIMATE_NOTE = "Estimates: each dose rises to its peak, then halves every half-life. " +
    "Values in ng/dL or ng/mL come from published peak concentrations; curves marked relative show active amount only. " +
    "Individual blood levels differ."

private const val GESTURE_NOTE = "Slide a finger along a chart to read values and see what was logged then. Two fingers pan and zoom."

private const val LOCKED_TITLE = "Level charts need Pro"
private const val LOCKED_BODY = "Your doses and plan stay as they are. Pro shows the estimated level curves."

/**
 * Levels overview: one chart per compound in use, in plan order. The bar at the top jumps to a compound's chart;
 * a chart's title opens its detail. Compounds not in use now are listed below and open on demand.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LevelsScreen(onOpenSettings: () -> Unit, onOpenGroup: (String) -> Unit, onOpenPlan: () -> Unit = {}) {
    val vm = appViewModel { LevelsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val paywall by vm.paywall.collectAsStateWithLifecycle()
    val c = Tracker.colors
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var barHeight by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf<ChartCursor?>(null) }
    val motion = Motions.current

    Scaffold(containerColor = c.bg) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = list,
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "header") {
                ScreenHeader("Levels", Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.section), eyebrow = "Estimated") {
                    BackToNowButton(visible = !state.atDefault || cursor != null) { vm.resetView(); cursor = null }
                    SettingsButton(onOpenSettings)
                }
            }
            state.access.trialEndsAt?.let { endsAt ->
                item(key = "trial") { TrialBanner(endsAt, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) }
            }
            if (!state.loading && state.empty) {
                item(key = "empty") {
                    // The empty state offers the way to the plan, like Today's.
                    EmptyState(
                        "Nothing to plot", "Add compounds to your plan or log a dose. Levels are estimated from doses, time to peak and half-life.",
                        actionLabel = "Open plan", onAction = onOpenPlan,
                    )
                }
                if (state.unplottable.isNotEmpty()) item(key = "unplottable") { UnplottableNote(state.unplottable, Modifier.padding(horizontal = Spacing.screen)) }
                return@LazyColumn
            }
            if (state.access.locked) {
                item(key = "locked") { LockedLevels { vm.showPaywall(Feature.LEVELS_RANGE) } }
                if (state.unplottable.isNotEmpty()) item(key = "unplottable") { UnplottableNote(state.unplottable, Modifier.padding(horizontal = Spacing.screen)) }
                item(key = "note") { Text(ESTIMATE_NOTE, style = TrackerType.caption, color = c.muted, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) }
                return@LazyColumn
            }
            stickyHeader(key = "jump") {
                if (state.current.size > 1) JumpBar(state.current, state.opened.takeIf { state.oneAtATime }, Modifier.onSizeChanged { barHeight = it.height }) { name ->
                    // One chart at a time: the bar switches it. Otherwise charts follow the header, the banner, this bar and the controls.
                    if (state.oneAtATime) vm.select(name) else {
                        val i = state.views.indexOfFirst { it.name == name }
                        val first = FIRST_CHART_INDEX + (if (state.access.trialEndsAt != null) 1 else 0)
                        if (i >= 0) scope.launch {
                            if (motion == Motion.OFF) list.scrollToItem(first + i, -barHeight) else list.animateScrollToItem(first + i, -barHeight)
                        }
                    }
                }
            }
            item(key = "controls") {
                Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // The overview stays on Logged + plan; the mode row lives on the detail screen.
                    Segmented(LevelRange.entries, state.window.range, { it.label }) { vm.setRange(it); cursor = null }
                    RangeLimitNote(state.access.maxDays)
                }
            }
            state.views.forEach { view ->
                item(key = "g-${view.name}") {
                    GroupCard(view, state, vm, cursor, { cursor = it }, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) { onOpenGroup(view.name) }
                }
            }
            if (state.oneAtATime && state.current.size + state.others.size > 1) item(key = "one-chart") {
                OneChartNote(Modifier.padding(horizontal = Spacing.screen)) { vm.showPaywall(Feature.LEVELS_MULTI_COMPOUND) }
            }
            if (state.others.isNotEmpty()) item(key = "others") {
                OthersSection(state, vm::toggleOther, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md))
            }
            if (state.unplottable.isNotEmpty()) item(key = "unplottable") {
                UnplottableNote(state.unplottable, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
            }
            item(key = "note") {
                Text("$ESTIMATE_NOTE $GESTURE_NOTE", style = TrackerType.caption, color = c.muted, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
            }
        }
    }
    paywall?.let { PaywallSheet(it, vm::dismissPaywall) }
}

/** Header, jump bar and controls come before the first chart (and the trial banner when shown). */
private const val FIRST_CHART_INDEX = 3

/** Levels without charts (the gate gives no days): what is missing and the way to Pro. */
@Composable
private fun LockedLevels(onShowPro: () -> Unit) {
    EmptyState(LOCKED_TITLE, LOCKED_BODY, actionLabel = "See Pro", onAction = onShowPro)
}

/** Under the range buttons when the gate limits the days: which ranges open the paywall. */
@Composable
private fun RangeLimitNote(maxDays: Int?) {
    if (maxDays == null || maxDays <= 0) return
    Text("Without Pro, charts reach back $maxDays days. Longer ranges need Pro.", style = TrackerType.caption, color = Tracker.colors.muted)
}

/** The overview shows one chart at a time without Pro. */
@Composable
private fun OneChartNote(modifier: Modifier = Modifier, onShowPro: () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text("One chart at a time. Tap a compound to switch.", style = TrackerType.caption, color = Tracker.colors.muted)
        AccentTextButton("All charts at once with Pro", onShowPro)
    }
}

private fun modeLabel(mode: LevelMode) = when (mode) {
    LevelMode.COMBINED -> "Logged + plan"
    LevelMode.RECORDED -> "Logged"
    LevelMode.PLANNED -> "Plan only"
}

/** Compounds in use with their current estimate; tapping one scrolls to its chart, or one at a time ([shown] set) shows it. */
@Composable
private fun JumpBar(chips: List<GroupChip>, shown: Set<String>?, modifier: Modifier = Modifier, onJump: (String) -> Unit) {
    val c = Tracker.colors
    Column(modifier.fillMaxWidth().background(c.bg)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(chips, key = { it.name }) { chip ->
                val shape = RoundedCornerShape(Radii.medium)
                val isShown = shown != null && chip.name in shown
                val tap = if (shown == null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Go to chart") { onJump(chip.name) }
                } else {
                    Modifier.selectable(isShown, role = Role.Tab, onClick = { onJump(chip.name) })
                }
                Row(
                    Modifier.heightIn(min = 48.dp).clip(shape).background(c.surface).border(if (isShown) 2.dp else 1.dp, if (isShown) c.accent else c.line, shape)
                        .then(tap)
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(chip.colorArgb, size = 10.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Column {
                        Text(chip.name, style = TrackerType.label, color = c.ink, maxLines = 1)
                        chip.now?.let { Text(it, style = TrackerType.micro, color = c.muted, maxLines = 1) }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun GroupCard(
    view: GroupView,
    state: LevelsState,
    vm: LevelsViewModel,
    cursor: ChartCursor?,
    onCursor: (ChartCursor) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val c = Tracker.colors
    LedgerCard(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Open details", onClick = onOpen)
                .padding(start = Spacing.md, end = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(view.series.colorArgb)
            Text(view.name, style = TrackerType.title, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
            Text(estimateLabel("est.", view), style = TrackerType.numericSmall, color = c.muted)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // The figures are on the detail screen only, so the overview stays a glance.
            GroupChart(view, state, vm, cursor, onCursor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OthersSection(state: LevelsState, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("Not in use now")
        Text("Other phases, paused items and earlier doses. Tap to show a chart.", style = TrackerType.caption, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.others.forEach { g -> QuickChip(g.name, g.name in state.opened) { onToggle(g.name) } }
        }
    }
}

/** One compound in detail: larger chart, figures and the latest doses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelDetailScreen(group: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "level-$group") { LevelsViewModel(it, focus = group) }
    val state by vm.state.collectAsStateWithLifecycle()
    val paywall by vm.paywall.collectAsStateWithLifecycle()
    val c = Tracker.colors
    val view = state.views.firstOrNull()
    var cursor by remember { mutableStateOf<ChartCursor?>(null) }
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text(group) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { BackToNowButton(visible = !state.atDefault || cursor != null) { vm.resetView(); cursor = null } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, titleContentColor = c.ink, navigationIconContentColor = c.ink, actionIconContentColor = c.ink),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            state.access.trialEndsAt?.let { endsAt -> item(key = "trial") { TrialBanner(endsAt) } }
            if (state.access.locked) item(key = "locked") { LockedLevels { vm.showPaywall(Feature.LEVELS_RANGE) } }
            if (!state.access.locked) item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Segmented(LevelRange.entries, state.window.range, { it.label }) { vm.setRange(it); cursor = null }
                    RangeLimitNote(state.access.maxDays)
                    // Without the modes, Logged and Plan only open the paywall (the view model decides).
                    Segmented(listOf(LevelMode.COMBINED, LevelMode.RECORDED, LevelMode.PLANNED), state.window.mode, ::modeLabel) { vm.setMode(it) }
                }
            }
            if (!state.loading && view == null) item(key = "empty") {
                EmptyState("No level data", "$group has no doses or plan items with level data.")
            }
            if (view != null && !state.access.locked) item(key = "chart") {
                LedgerCard {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ColorDot(view.series.colorArgb)
                            Text(estimateLabel("Estimated", view), style = TrackerType.numericSmall, color = c.muted)
                        }
                        GroupChart(view, state, vm, cursor, { cursor = it }, height = 320.dp)
                        view.metrics?.let { Metrics(it, view.series.unitLabel) }
                    }
                }
            }
            if (view != null) item(key = "adjust") {
                AdjustLevel(view.adjustPercent) { vm.setAdjustment(group, it) }
            }
            if (state.doses.isNotEmpty()) item(key = "doses") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionLabel("Latest doses")
                    LedgerCard {
                        state.doses.forEachIndexed { i, d ->
                            if (i > 0) RowDivider()
                            FitRow(
                                start = {
                                    Column {
                                        Text(d.compound, style = TrackerType.bodySmall, color = c.ink)
                                        Text(d.whenLabel, style = TrackerType.numericSmall, color = c.muted)
                                    }
                                },
                                end = { Text(d.amount, style = TrackerType.numericSmall.copy(fontSize = TrackerType.bodySmall.fontSize), color = c.ink) },
                                modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = Spacing.lg),
                            )
                        }
                    }
                }
            }
            item(key = "note") { Text("$ESTIMATE_NOTE $GESTURE_NOTE", style = TrackerType.caption, color = c.muted) }
        }
    }
    paywall?.let { PaywallSheet(it, vm::dismissPaywall) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metrics(m: LevelMetrics, unit: String) {
    val zone = ZoneId.systemDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Metric("NOW", "${levelText(m.current)} $unit")
        m.steadyState?.let {
            Metric("STEADY RANGE", "${formatNumber(it.trough, 0)}–${formatNumber(it.peak, 0)} $unit")
            Metric("STEADY AVG", "${formatNumber(it.average, 0)} $unit")
        }
        Metric("90% OF STEADY", Formats.halfLife(m.timeTo90.toMinutes() / 60.0))
        m.clearsAt?.let { Metric("BELOW 10%", it.atZone(zone).format(Formats.dayShort)) }
    }
}

/** "est. ng/dL", or "est. ng/dL · adjusted +15%" when the user scaled the estimate. */
private fun estimateLabel(prefix: String, view: GroupView): String =
    "$prefix ${view.series.unitLabel}" + (view.adjustLabel?.let { " · adjusted $it" } ?: "")

/**
 * The user's scaling of this group's estimate: "Adjust level" (docs/MODELS.md). The slider shows its
 * value while dragging and saves on release, so the curves are recomputed once.
 */
@Composable
private fun AdjustLevel(percent: Int, onChange: (Int) -> Unit) {
    val c = Tracker.colors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: percent
    fun set(value: Int) = onChange(value.coerceIn(LevelAdjustments.MIN, LevelAdjustments.MAX))
    LedgerCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Adjust level", style = TrackerType.label, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
                Text(
                    LevelAdjustments.label(shown) ?: "Off",
                    style = TrackerType.numericSmall.copy(fontSize = TrackerType.bodySmall.fontSize), color = c.ink,
                )
            }
            Text(
                "Scales this estimate by a percentage, for example to line it up with your own lab results. " +
                    "It changes the curve and its figures only, not your doses, plan or lab results.",
                style = TrackerType.caption, color = c.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { set(percent - ADJUST_STEP) }, enabled = percent > LevelAdjustments.MIN, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Lower by $ADJUST_STEP%")
                }
                Slider(
                    value = dragging ?: percent.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { set(it.roundToInt()) }; dragging = null },
                    valueRange = LevelAdjustments.MIN.toFloat()..LevelAdjustments.MAX.toFloat(),
                    steps = LevelAdjustments.MAX - LevelAdjustments.MIN - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Adjust level" },
                )
                IconButton(onClick = { set(percent + ADJUST_STEP) }, enabled = percent < LevelAdjustments.MAX, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Raise by $ADJUST_STEP%")
                }
            }
            if (percent != 0) AccentTextButton("Reset to estimate", { set(0) })
        }
    }
}

/** The minus and plus buttons move the adjustment by this many percent. */
private const val ADJUST_STEP = 5

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

/**
 * A group's chart with the reading cursor. While scrubbing, ticks mark days and logs passed, and the logs
 * near the cursor are listed under the chart.
 */
@Composable
private fun GroupChart(
    view: GroupView,
    state: LevelsState,
    vm: LevelsViewModel,
    cursor: ChartCursor?,
    onCursor: (ChartCursor) -> Unit,
    height: Dp = 220.dp,
) {
    val zone = remember { ZoneId.systemDefault() }
    val haptics = rememberScrubHaptics()
    // Logged doses and entries, plus the planned doses drawn on this curve.
    val marks = remember(state.timeline, view.series) {
        (state.timeline.marks(view.name).asList() + view.series.events.map { it.atMs }).distinct().sorted().toLongArray()
    }
    var scrubbing by remember { mutableStateOf(false) }
    val mine = cursor?.takeIf { it.group == view.name }
    val lastAt = remember { longArrayOf(Long.MIN_VALUE) }
    val callbacks = ChartCallbacks(
        onPan = vm::pan,
        onZoom = vm::zoom,
        onPoint = { f ->
            val span = state.toMs - state.fromMs
            val at = state.fromMs + (f * span).toLong()
            // Last position seen by the gesture, not the composed cursor: several moves can arrive before a recomposition.
            val previous = lastAt[0].takeIf { it != Long.MIN_VALUE }
            lastAt[0] = at
            if (scrubbing && previous != null) {
                when {
                    state.timeline.crosses(marks, previous, at) -> haptics.mark()
                    crossesBoundary(previous, at, span, zone) -> haptics.tick()
                }
            }
            onCursor(ChartCursor(view.name, at))
        },
        onScrubbing = { scrubbing = it; lastAt[0] = Long.MIN_VALUE },
    )
    LevelChart(
        view.series, state.fromMs, state.toMs, state.nowMs, state.bands, mine?.atMs, callbacks,
        measured = view.measured, height = height, adjustLabel = view.adjustLabel,
    )
    if (view.measured.any { it.atMs in state.fromMs..state.toMs }) LabLegend()
    if (view.hiddenLabs) AccentTextButton("Show lab results", { vm.showPaywall(Feature.LEVELS_LAB_OVERLAY) })
    if (mine != null) {
        // Only while the cursor is in view: after a pan it can sit off the chart.
        val reading = view.series.series.takeIf { it.values.isNotEmpty() && mine.atMs in state.fromMs..state.toMs }?.let { s ->
            val i = nearestIndex(s.times, mine.atMs)
            "${chartTime(s.times[i], zone)} · est. ${levelText(s.values[i])} ${view.series.unitLabel}"
        }
        ScrubPanel(state.timeline, mine, zone, reading = reading, labUnits = state.labUnits, commonNames = state.commonNames)
    }
}

/**
 * "Back to now", shown only after panning, zooming or reading a value, so it never sits there doing nothing. Its
 * place stays reserved, so the buttons beside it never move.
 */
@Composable
private fun BackToNowButton(visible: Boolean, onClick: () -> Unit) {
    val motion = Motions.current
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motions.spec(motion, 200)) + scaleIn(Motions.spec(motion, 200), initialScale = 0.8f),
            exit = fadeOut(Motions.spec(motion, 150)) + scaleOut(Motions.spec(motion, 150), targetScale = 0.8f),
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(BackToNowIcon, contentDescription = "Back to now", tint = Tracker.colors.ink)
            }
        }
    }
}

/** "Back to now": the location glyph (Today's calendar icon opens another day there). */
private val BackToNowIcon get() = Icons.Outlined.MyLocation

/** What the open diamonds on a curve are. */
@Composable
private fun LabLegend() {
    val c = Tracker.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Canvas(Modifier.size(12.dp)) { diamond(center, 5.dp.toPx(), c.ink, c.bg) }
        Text("Lab result", style = TrackerType.caption, color = c.muted)
    }
}
