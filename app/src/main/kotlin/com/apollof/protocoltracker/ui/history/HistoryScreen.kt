package com.apollof.protocoltracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.SectionHeader
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.theme.NumericStyle
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenSettings: () -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DoseLog?>(null) }

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch {
                if (snackbar.showSnackbar(msg.text, actionLabel = "Undo") == SnackbarResult.ActionPerformed) msg.undo?.invoke()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loading && state.days.isEmpty() && state.filter == null) {
            EmptyState("No doses logged", "Check doses on Today or use Log dose. They appear here by day.", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            if (state.adherence.isNotEmpty()) {
                item(key = "adh-h") { SectionHeader("Adherence · 7 days / 30 days") }
                item(key = "adh") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.adherence.forEach { a ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ColorDot(a.color, size = 10.dp); Spacer(Modifier.width(8.dp))
                                    Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(a.week, style = NumericStyle, modifier = Modifier.width(96.dp))
                                    Text(a.month, style = NumericStyle, modifier = Modifier.width(96.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (state.filters.size > 1) item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    item { FilterChip(selected = state.filter == null, onClick = { vm.setFilter(null) }, label = { Text("All") }) }
                    items(state.filters, key = { it.id }) { f ->
                        FilterChip(
                            selected = state.filter == f.id, onClick = { vm.setFilter(f.id) }, label = { Text(f.name) },
                            leadingIcon = { ColorDot(f.color, size = 8.dp) },
                        )
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "d-${day.date}") { SectionHeader(day.label) }
                items(day.rows, key = { it.log.id }) { row ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit") { editing = row.log }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val taken = row.log.status == LogStatus.TAKEN
                        Icon(
                            if (taken) Icons.Filled.CheckCircle else Icons.Outlined.RemoveCircleOutline,
                            contentDescription = if (taken) "Taken" else "Skipped",
                            tint = if (taken) androidx.compose.ui.graphics.Color(row.color) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (taken) row.dose else "Skipped", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (row.log.note.isNotBlank()) Text(row.log.note, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(row.time, style = NumericStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { log ->
        EditLogDialog(log, onDismiss = { editing = null }, onSave = { vm.update(it); editing = null }, onDelete = { vm.delete(log); editing = null })
    }
}

@Composable
private fun EditLogDialog(log: DoseLog, onDismiss: () -> Unit, onSave: (DoseLog) -> Unit, onDelete: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val local = log.takenAt.atZone(zone)
    var date by remember { mutableStateOf(local.toLocalDate()) }
    var time by remember { mutableStateOf(local.toLocalTime().withSecond(0).withNano(0)) }
    var amount by remember { mutableStateOf(formatNumber(log.amount.value, 4)) }
    var status by remember { mutableStateOf(log.status) }
    var note by remember { mutableStateOf(log.note) }
    val value = amount.toDecimal()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(log.snapshot.compoundName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(LogStatus.entries, status, { if (it == LogStatus.TAKEN) "Taken" else "Skipped" }) { status = it }
                FieldRow {
                    DateField("Date", date, { if (it != null) date = it }, Modifier.weight(1.3f))
                    TimeField("Time", time, { time = it }, Modifier.weight(1f))
                }
                NumberField("Amount", amount, { amount = it }, Modifier.fillMaxWidth(), suffix = log.amount.unit.label)
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onDelete) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = {
                onSave(log.copy(
                    takenAt = date.atTime(time).atZone(zone).toInstant(), amount = Amount(value!!, log.amount.unit), status = status, note = note.trim(),
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
