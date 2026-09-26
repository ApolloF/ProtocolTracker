package com.apollof.protocoltracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.ColorSwatchPicker
import com.apollof.protocoltracker.ui.components.DateField

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhaseDialog(initial: Phase, onDismiss: () -> Unit, onSave: (Phase) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(initial.startDate) }
    var end by remember { mutableStateOf(initial.endDate) }
    var color by remember { mutableLongStateOf(initial.colorArgb) }
    var notes by remember { mutableStateOf(initial.notes) }
    val endError = end?.let { if (it < start) "End is before start" else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isEmpty()) "New phase" else "Edit phase") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("e.g. Cruise, Blast, PCT") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("Start", start, { if (it != null) start = it })
                DateField("End (optional)", end, { end = it }, optional = true)
                Text(
                    endError ?: "Without an end date the phase runs until the next one starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (endError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorSwatchPicker(CompoundColors.palette, color, { color = it })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && endError == null,
                onClick = { onSave(initial.copy(name = name.trim(), startDate = start, endDate = end, colorArgb = color, notes = notes.trim())) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
