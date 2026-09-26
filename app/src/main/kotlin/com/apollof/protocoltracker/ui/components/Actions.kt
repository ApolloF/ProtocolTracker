package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

private val ButtonShape = RoundedCornerShape(Radii.medium)

/** Filled accent button, 48 dp high. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Tracker.colors
    Button(
        onClick = onClick, enabled = enabled, shape = ButtonShape, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = TrackerType.label)
    }
}

/** Outlined surface button, 48 dp high. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Tracker.colors
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = ButtonShape, modifier = modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = c.ink),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = TrackerType.label)
    }
}

/** Text button in the accent colour, e.g. "Log all" in a group header. */
@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, color = Tracker.colors.accentText, style = TrackerType.label)
    }
}
