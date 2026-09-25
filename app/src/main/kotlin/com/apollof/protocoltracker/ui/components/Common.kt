package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ColorDot(argb: Long, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).background(Color(argb), CircleShape))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = color,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Empty state: what is missing and the action that fixes it. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel) }
    }
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
