package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit, title: String = "Time") {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // DatePicker works in UTC milliseconds at midnight.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

/** Read-only field that opens a date picker; optional dates can be cleared. */
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, optional: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.format(Formats.date) ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { if (optional) Text("None") },
        trailingIcon = {
            Row {
                if (optional && value != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Clear $label") }
                IconButton(onClick = { open = true }) { Icon(Icons.Outlined.CalendarMonth, "Choose $label") }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
    if (open) DatePickDialog(value ?: LocalDate.now(), onDismiss = { open = false }, onConfirm = { onChange(it); open = false })
}

@Composable
fun TimeField(label: String, value: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value.format(Formats.time), onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Schedule, "Choose $label") } },
        modifier = modifier,
    )
    if (open) TimePickDialog(value, onDismiss = { open = false }, onConfirm = { onChange(it); open = false }, title = label)
}

/** Numeric text input that keeps the raw text while typing and reports parsed values. */
@Composable
fun NumberField(
    label: String,
    text: String,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
) {
    OutlinedTextField(
        value = text,
        onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d{0,7}([.,]\d{0,4})?"""))) onText(v) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier,
    )
}

fun String.toDecimal(): Double? = replace(',', '.').toDoubleOrNull()

@Composable
fun FieldRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}
