package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.data.Motion
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.schedule.DayMark
import com.apollof.protocoltracker.domain.schedule.DayStatus
import com.apollof.protocoltracker.domain.schedule.weekStartOf
import com.apollof.protocoltracker.ui.theme.Motions
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Cycle name, week/day and progress, with the week strip shown according to the setting. Tapping a day opens it
 * (check off or backfill its doses). Strip modes:
 * hidden, behind a "Week" toggle, as one compact row, or in full.
 */
@Composable
fun CycleCard(
    title: String,
    subtitle: String,
    progress: Float?,
    mode: WeekBarMode,
    week: List<DayStatus>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Draws the week strip (compact = the one-row mode); see [WeekPager]. */
    strip: @Composable (compact: Boolean) -> Unit,
) {
    val c = Tracker.colors
    LedgerCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = TrackerType.title, color = c.ink)
                    Text(subtitle.uppercase(), style = TrackerType.numericSmall.copy(letterSpacing = 0.5.sp), color = c.muted)
                }
                if (mode == WeekBarMode.COLLAPSIBLE && week.isNotEmpty()) {
                    Row(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, c.line, RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button, onClick = onToggle)
                            .padding(start = 14.dp, end = 10.dp)
                            .semantics { stateDescription = if (expanded) "Week shown" else "Week hidden" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Week", style = TrackerType.label, color = c.ink)
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (progress != null) ThinProgress(progress)
            when {
                week.isEmpty() -> Unit
                mode == WeekBarMode.FULL || (mode == WeekBarMode.COLLAPSIBLE && expanded) -> strip(false)
                mode == WeekBarMode.COMPACT -> strip(true)
                else -> Unit
            }
        }
    }
}

private fun dayName(status: DayStatus, style: TextStyle): String = status.date.dayOfWeek.getDisplayName(style, Locale.getDefault())

/**
 * The week strip as pages, one week each, from [weeks] (their Mondays). It swipes back to earlier weeks; picking a
 * day elsewhere (date picker, Back to today) turns to its week. [selected] is the day shown below the strip.
 */
@Composable
fun WeekPager(
    weeks: List<LocalDate>,
    days: Map<LocalDate, List<DayStatus>>,
    today: LocalDate,
    selected: LocalDate,
    compact: Boolean,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weeks.isEmpty()) return
    val target = weekStartOf(selected)
    val pager = rememberPagerState(initialPage = weeks.indexOf(target).coerceAtLeast(0)) { weeks.size }
    val motion = Motions.current
    val scope = rememberCoroutineScope()
    fun turnTo(page: Int) {
        if (page !in weeks.indices) return
        scope.launch { if (motion == Motion.OFF) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    // The selected day moved to another week: show that week. Swiping alone never changes the selected day.
    LaunchedEffect(target, weeks) {
        val page = weeks.indexOf(target)
        if (page >= 0 && page != pager.currentPage) {
            if (motion == Motion.OFF) pager.scrollToPage(page) else pager.animateScrollToPage(page)
        }
    }
    HorizontalPager(
        state = pager,
        key = { weeks[it] },
        pageSpacing = 12.dp,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Previous week") { turnTo(pager.currentPage - 1); pager.currentPage > 0 },
                CustomAccessibilityAction("Next week") { turnTo(pager.currentPage + 1); pager.currentPage < weeks.lastIndex },
            )
        },
    ) { page ->
        val week = days[weeks[page]] ?: return@HorizontalPager
        if (compact) WeekStripCompact(week, selected = selected, onDay = onDay) else WeekStripFull(week, selected = selected, onDay = onDay)
    }
}

/** A selected day other than today gets an ink frame; today keeps its accent either way. */
@Composable
private fun cellFrame(day: DayStatus, selected: LocalDate?, shape: RoundedCornerShape, width: androidx.compose.ui.unit.Dp): Modifier {
    val c = Tracker.colors
    val isSelected = day.date == selected && !day.isToday
    return when {
        day.isToday -> Modifier.background(c.accentSoft, shape).border(width, c.accent, shape)
        isSelected -> Modifier.background(c.surface, shape).border(width, c.ink, shape)
        else -> Modifier
    }
}

/** Status colour of a cell's text; the text itself says the status. */
@Composable
private fun markColor(day: DayStatus): androidx.compose.ui.graphics.Color {
    val c = Tracker.colors
    return when (day.mark) {
        DayMark.TODAY -> c.accentText
        DayMark.MISSED -> c.warn
        else -> c.muted
    }
}

private fun cellDescription(day: DayStatus) = "${dayName(day, TextStyle.FULL)} ${day.date.dayOfMonth}: ${day.summary}"

@Composable
fun WeekStripFull(week: List<DayStatus>, modifier: Modifier = Modifier, selected: LocalDate? = null, onDay: (LocalDate) -> Unit = {}) {
    val c = Tracker.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (day in week) {
            val shape = RoundedCornerShape(Radii.medium)
            val box = cellFrame(day, selected, shape, 2.dp).let { frame ->
                when {
                    day.isToday || (day.date == selected) -> frame
                    day.isFuture -> Modifier.border(1.dp, c.line, shape)
                    else -> Modifier.background(c.surface2, shape)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(box)
                    .clickable(role = Role.Button, onClickLabel = "Open day") { onDay(day.date) }
                    .padding(vertical = 10.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = cellDescription(day)
                        this.selected = day.date == selected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // A cell is a seventh of the width: its text shrinks with a large font instead of wrapping or clipping.
                Text(
                    dayName(day, TextStyle.SHORT).uppercase().take(3), style = TrackerType.overline,
                    color = if (day.isToday) c.accentText else c.muted, maxLines = 1, autoSize = fit(TrackerType.overline),
                )
                Text("${day.date.dayOfMonth}", style = TrackerType.figure, color = if (day.isToday) c.accentText else c.ink, maxLines = 1, autoSize = fit(TrackerType.figure))
                DayStatusText(day, markColor(day))
            }
        }
    }
}

/** Short status for a narrow cell: [DayStatus.cell], with a tick before "all" and a warning sign plus the count for missed doses. */
@Composable
private fun DayStatusText(day: DayStatus, color: androidx.compose.ui.graphics.Color) {
    val weight = if (day.mark == DayMark.TODAY || day.mark == DayMark.MISSED) FontWeight.SemiBold else FontWeight.Normal
    val icon = when (day.mark) {
        DayMark.ALL_TAKEN -> Icons.Outlined.Check
        DayMark.MISSED -> Icons.Outlined.WarningAmber
        else -> null
    }
    // "3 miss" did not fit a seventh of a narrow screen and showed as a bare orange "3".
    val text = if (day.mark == DayMark.MISSED) "${day.missed}" else day.cell
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Text(text, style = TrackerType.micro.copy(fontWeight = weight), color = color, maxLines = 1, autoSize = fit(TrackerType.micro))
    }
}

/**
 * One-line text for a week cell: [style]'s size while it fits, smaller when a large font would cut it, but never
 * drawn below [TrackerType.cellTextMin] whatever the font scale.
 */
@Composable
private fun fit(style: androidx.compose.ui.text.TextStyle): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = with(LocalDensity.current) { TrackerType.cellTextMin.toSp() }, maxFontSize = style.fontSize)

/** One thin row: day letter and a tick, "!", "–" (skipped), count or dot. */
@Composable
fun WeekStripCompact(week: List<DayStatus>, modifier: Modifier = Modifier, selected: LocalDate? = null, onDay: (LocalDate) -> Unit = {}) {
    val c = Tracker.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (day in week) {
            val shape = RoundedCornerShape(Radii.small)
            val glyph = when (day.mark) {
                DayMark.TODAY -> day.cell
                DayMark.NONE, DayMark.UNTRACKED -> "–"
                DayMark.FUTURE -> "·"
                DayMark.MISSED -> "!"
                DayMark.SKIPPED -> "−"
                DayMark.ALL_TAKEN -> null
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .then(cellFrame(day, selected, shape, 1.5.dp))
                    .clickable(role = Role.Button, onClickLabel = "Open day") { onDay(day.date) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = cellDescription(day)
                        this.selected = day.date == selected
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayName(day, TextStyle.NARROW), style = TrackerType.overline.copy(fontSize = TrackerType.caption.fontSize), color = if (day.isToday) c.accentText else c.muted)
                Spacer(Modifier.width(4.dp))
                val glyphColor = markColor(day)
                if (glyph == null) Icon(Icons.Outlined.Check, contentDescription = null, tint = glyphColor, modifier = Modifier.size(12.dp))
                else Text(glyph, style = TrackerType.overline.copy(fontWeight = FontWeight.Bold), color = glyphColor)
            }
        }
    }
}

/** Plan-card grid cell: small label over a mono value. */
@Composable
fun FigureCell(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = TrackerType.overline, color = c.accentText, maxLines = 1, autoSize = fit(TrackerType.overline))
        Text(value, style = TrackerType.figure, color = c.ink, maxLines = 1, autoSize = fit(TrackerType.figure))
    }
}

/** Twelve week segments for a cycle: done, current, upcoming; the text below states the week. */
@Composable
fun WeekSegments(total: Int, current: Int, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total.coerceIn(1, 52)) { i ->
            val color = when {
                i < current - 1 -> c.accent
                i == current - 1 -> c.accentMid
                else -> c.surface2
            }
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}
