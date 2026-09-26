package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.theme.Tracker

/** Colour swatches for compounds and phases; the selected one gets a ring and a check mark. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchPicker(palette: List<Long>, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        palette.forEachIndexed { i, argb ->
            val isSelected = argb == selected
            val swatch = c.series(argb)
            Box(
                Modifier.size(48.dp)
                    .selectable(selected = isSelected, onClick = { onSelect(argb) })
                    .semantics { contentDescription = "Colour ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp)
                        .then(if (isSelected) Modifier.border(2.dp, c.ink, CircleShape) else Modifier)
                        .background(swatch, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(
                        Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}
