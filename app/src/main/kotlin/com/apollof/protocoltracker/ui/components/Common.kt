package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Compound or phase colour dot, adjusted for the current theme. */
@Composable
fun ColorDot(argb: Long, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).background(Tracker.colors.series(argb), CircleShape))
}

/**
 * Header of a main tab: a small mono line above (date, cycle), the screen title, and actions on the right.
 * Every tab ends its actions with [SettingsButton] so Settings is always in the same place.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Tracker.colors
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), style = NumericStyle.copy(fontSize = TrackerType.caption.fontSize), color = c.muted)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Tracker.colors.ink)
    }
}

/** Empty state: what is missing and the action that fixes it. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val c = Tracker.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = TrackerType.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        Text(body, style = TrackerType.bodySmall, color = c.muted, textAlign = TextAlign.Center)
        if (actionLabel != null) PrimaryButton(actionLabel, onAction, Modifier.padding(top = 8.dp))
    }
}

/** Confirmation before a significant action; [confirm] names the action ("Delete"). */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) Tracker.colors.danger else Tracker.colors.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Tracker.colors.surface,
    )
}

object Formats {
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    val dayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
    val date: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

    fun time(at: Instant, zone: ZoneId): String = at.atZone(zone).format(time)

    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayShort)
    }

    /** Hours shown as days when ≥ 48 h, e.g. "7.9 d" or "9.4 h". */
    fun halfLife(hours: Double): String = if (hours >= 48) "%.1f d".format(hours / 24) else "%.1f h".format(hours)
}
