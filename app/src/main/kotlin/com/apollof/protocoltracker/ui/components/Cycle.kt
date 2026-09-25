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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.schedule.DayStatus
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import java.time.format.TextStyle
import java.util.Locale

/**
 * Cycle name, week/day and progress, with the week strip shown according to the setting:
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
) {
    val c = Tracker.colors
    LedgerCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Text(subtitle.uppercase(), style = NumericStyle.copy(fontSize = 12.sp, letterSpacing = 0.5.sp), color = c.muted)
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
                        Text("Week", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (progress != null) ThinProgress(progress)
            when {
                week.isEmpty() -> Unit
                mode == WeekBarMode.FULL || (mode == WeekBarMode.COLLAPSIBLE && expanded) -> WeekStripFull(week)
                mode == WeekBarMode.COMPACT -> WeekStripCompact(week)
                else -> Unit
            }
        }
    }
}

private fun dayName(status: DayStatus, style: TextStyle): String = status.date.dayOfWeek.getDisplayName(style, Locale.getDefault())

@Composable
fun WeekStripFull(week: List<DayStatus>, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (day in week) {
            val shape = RoundedCornerShape(10.dp)
            val box = when {
                day.isToday -> Modifier.background(c.accentSoft, shape).border(2.dp, c.accent, shape)
                day.isFuture -> Modifier.border(1.dp, c.line, shape)
                else -> Modifier.background(c.surface2, shape)
            }
            val statusColor = when {
                day.isToday -> c.accentText
                day.missed > 0 -> c.warn
                else -> c.muted
            }
            Column(
                Modifier
                    .weight(1f)
                    .then(box)
                    .padding(vertical = 10.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${dayName(day, TextStyle.FULL)} ${day.date.dayOfMonth}: ${day.summary}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    dayName(day, TextStyle.SHORT).uppercase().take(3), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (day.isToday) c.accentText else c.muted,
                )
                Text("${day.date.dayOfMonth}", style = NumericStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), color = if (day.isToday) c.accentText else c.ink)
                DayStatusText(day, statusColor)
            }
        }
    }
}

/** Short status for a narrow cell: a tick icon plus "all", "1 miss", "3/7" or "5 due". */
@Composable
private fun DayStatusText(day: DayStatus, color: androidx.compose.ui.graphics.Color) {
    val weight = if (day.isToday || day.missed > 0) FontWeight.SemiBold else FontWeight.Normal
    when {
        day.scheduled == 0 -> Text("–", fontSize = 10.sp, color = color)
        day.isToday -> Text("${day.taken + day.skipped}/${day.scheduled}", fontSize = 10.sp, fontWeight = weight, color = color, maxLines = 1)
        day.isFuture -> Text("${day.scheduled} due", fontSize = 10.sp, color = color, maxLines = 1)
        day.missed > 0 -> Text("${day.missed} miss", fontSize = 10.sp, fontWeight = weight, color = color, maxLines = 1)
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
            Text("all", fontSize = 10.sp, color = color, maxLines = 1)
        }
    }
}

/** One thin row: day letter and a tick, "!", count or dot. */
@Composable
fun WeekStripCompact(week: List<DayStatus>, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (day in week) {
            val shape = RoundedCornerShape(8.dp)
            val glyph = when {
                day.isToday -> "${day.taken + day.skipped}/${day.scheduled}"
                day.scheduled == 0 -> "–"
                day.isFuture -> "·"
                day.missed > 0 -> "!"
                else -> null
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (day.isToday) Modifier.background(c.accentSoft, shape).border(1.5.dp, c.accent, shape) else Modifier)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${dayName(day, TextStyle.FULL)} ${day.date.dayOfMonth}: ${day.summary}"
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayName(day, TextStyle.NARROW), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (day.isToday) c.accentText else c.muted)
                Spacer(Modifier.width(4.dp))
                val glyphColor = when {
                    day.missed > 0 && !day.isToday -> c.warn
                    day.isToday -> c.accentText
                    else -> c.muted
                }
                if (glyph == null) Icon(Icons.Outlined.Check, contentDescription = null, tint = glyphColor, modifier = Modifier.size(12.dp))
                else Text(glyph, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = glyphColor)
            }
        }
    }
}

/** Plan-card grid cell: small label over a mono value. */
@Composable
fun FigureCell(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = c.accentText)
        Text(value, style = NumericStyle.copy(fontSize = 15.sp), color = c.ink)
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
