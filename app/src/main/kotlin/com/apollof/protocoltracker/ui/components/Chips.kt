package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/**
 * The app's one selectable pill (filters, quick amounts, times, phases). Selection is shown by border,
 * fill and weight, and announced, never by colour alone.
 */
@Composable
fun QuickChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, mono: Boolean = false, onClick: () -> Unit) {
    val c = Tracker.colors
    val shape = RoundedCornerShape(Radii.medium)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(c.accentSoft).border(BorderStroke(1.5.dp, c.accent), shape)
                else Modifier.background(c.surface).border(BorderStroke(1.dp, c.line), shape),
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val base = if (mono) NumericStyle.copy(fontSize = TrackerType.bodySmall.fontSize) else TrackerType.bodySmall
        Text(
            label, style = base.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1,
            color = when {
                !enabled -> c.muted.copy(alpha = 0.5f)
                selected -> c.accentText
                else -> c.ink
            },
        )
    }
}
