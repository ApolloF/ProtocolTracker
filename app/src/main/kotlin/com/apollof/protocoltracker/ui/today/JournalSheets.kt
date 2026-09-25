package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.bloodPressureProblems
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.TimePickDialog
import com.apollof.protocoltracker.ui.theme.Tracker
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Time row shared by the journal sheets: now, or a picked time on the same day. */
@Composable
private fun TimeChoice(now: Instant, zone: ZoneId, time: Instant?, onTime: (Instant?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Time")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("Now · ${Formats.time(now, zone)}", time == null, Modifier.weight(1f)) { onTime(null) }
            QuickChip(time?.let { Formats.time(it, zone) } ?: "Earlier…", time != null, Modifier.weight(1f)) { picking = true }
        }
    }
    if (picking) {
        val base = (time ?: now).atZone(zone)
        TimePickDialog(LocalTime.of(base.hour, base.minute), onDismiss = { picking = false }, onConfirm = {
            onTime(base.toLocalDate().atTime(it).atZone(zone).toInstant()); picking = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodPressureSheet(
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (systolic: Int, diastolic: Int, pulse: Int?, at: Instant, note: String) -> Unit,
    existing: JournalEntry.BloodPressure? = null,
) {
    var systolic by remember { mutableStateOf(existing?.systolic?.toString() ?: "") }
    var diastolic by remember { mutableStateOf(existing?.diastolic?.toString() ?: "") }
    var pulse by remember { mutableStateOf(existing?.pulse?.toString() ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var time by remember { mutableStateOf(existing?.at) }
    var tried by remember { mutableStateOf(false) }
    val problems = bloodPressureProblems(systolic.toIntOrNull(), diastolic.toIntOrNull(), pulse.takeIf { it.isNotBlank() }?.toIntOrNull())
    val c = Tracker.colors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Blood pressure", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IntField("Systolic", systolic, { systolic = it }, "mmHg", Modifier.weight(1f))
                IntField("Diastolic", diastolic, { diastolic = it }, "mmHg", Modifier.weight(1f))
            }
            IntField("Pulse (optional)", pulse, { pulse = it }, "bpm", Modifier.fillMaxWidth())
            TimeChoice(now, zone, time) { time = it }
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            if (tried && problems.isNotEmpty()) Text(problems.first(), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton("Save", {
                    tried = true
                    if (problems.isEmpty()) onSave(systolic.toInt(), diastolic.toInt(), pulse.toIntOrNull(), time ?: now, note)
                }, Modifier.weight(2f), Icons.Outlined.Check)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (text: String, at: Instant) -> Unit,
    existing: JournalEntry.Note? = null,
) {
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var time by remember { mutableStateOf(existing?.at) }
    val c = Tracker.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Note", style = MaterialTheme.typography.titleLarge, color = c.ink)
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= JournalEntry.MAX_NOTE_LENGTH) text = it },
                label = { Text("What happened") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            TimeChoice(now, zone, time) { time = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton("Save", { if (text.isNotBlank()) onSave(text, time ?: now) }, Modifier.weight(2f), Icons.Outlined.Check, enabled = text.isNotBlank())
            }
        }
    }
}

@Composable
private fun IntField(label: String, value: String, onValue: (String) -> Unit, suffix: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 3 && v.all(Char::isDigit)) onValue(v) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
