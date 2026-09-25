package com.apollof.protocoltracker.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.today.BloodPressureSheet
import com.apollof.protocoltracker.ui.today.JournalLine
import com.apollof.protocoltracker.ui.today.NoteSheet
import com.apollof.protocoltracker.ui.today.QuickChip
import kotlinx.coroutines.launch
import java.time.ZoneId

private sealed interface Editing {
    data class Dose(val log: DoseLog) : Editing
    data class Bp(val entry: JournalEntry.BloodPressure?) : Editing
    data class Note(val entry: JournalEntry.Note?) : Editing
}

@Composable
fun JournalScreen(onOpenSettings: () -> Unit) {
    val vm = appViewModel { JournalViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var adherenceOpen by rememberSaveable { mutableStateOf(false) }
    val c = Tracker.colors

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch { if (snackbar.showSnackbar(msg.text, actionLabel = "Undo") == SnackbarResult.ActionPerformed) msg.undo?.invoke() }
        }
    }

    Scaffold(containerColor = c.bg, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Journal", style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = { editing = Editing.Bp(null) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MonitorHeart, contentDescription = "Add blood pressure", tint = c.ink)
                    }
                    IconButton(onClick = { editing = Editing.Note(null) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.EditNote, contentDescription = "Add note", tint = c.ink)
                    }
                }
            }

            if (!state.loading && state.empty) item(key = "empty") {
                EmptyState("Nothing logged yet", "Doses you check on Today, blood pressure readings and notes appear here by day.")
            }

            if (!state.empty) item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(JournalFilter.entries.toList(), key = { it.name }) { f ->
                        QuickChip(f.label, state.filter == f && (state.compound == null || f != JournalFilter.DOSES)) { vm.setFilter(f); vm.setCompound(null) }
                    }
                    items(state.compounds, key = { it.id }) { cf ->
                        QuickChip(cf.name, state.compound == cf.id) { vm.setCompound(if (state.compound == cf.id) null else cf.id) }
                    }
                }
            }

            state.bloodPressure?.let { bp ->
                if (state.filter == JournalFilter.ALL || state.filter == JournalFilter.BLOOD_PRESSURE) item(key = "bp") {
                    LedgerCard {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SectionLabel("Blood pressure")
                                Text("Latest ${bp.latest} mmHg · ${bp.latestWhen}", style = NumericStyle, color = c.ink)
                            }
                            bp.average7?.let {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(it, style = NumericStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium), color = c.ink)
                                    Text("7-day average (${bp.readings7})", fontSize = 12.sp, color = c.muted)
                                }
                            }
                        }
                    }
                }
            }

            if (state.adherence.isNotEmpty() && state.filter != JournalFilter.BLOOD_PRESSURE && state.filter != JournalFilter.NOTES) item(key = "adherence") {
                LedgerCard {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { adherenceOpen = !adherenceOpen }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Adherence · 7 days / 30 days", color = c.ink, modifier = Modifier.weight(1f))
                        Icon(if (adherenceOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (adherenceOpen) "Hide" else "Show", tint = c.ink)
                    }
                    if (adherenceOpen) state.adherence.forEach { a ->
                        RowDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(a.week, style = NumericStyle, color = c.body2, modifier = Modifier.padding(end = 12.dp))
                            Text(a.month, style = NumericStyle, color = c.body2)
                        }
                    }
                }
            }

            state.days.forEach { day ->
                item(key = "d-${day.date}") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("${day.label} · ${day.date}")
                        LedgerCard {
                            day.rows.forEachIndexed { i, row ->
                                if (i > 0) RowDivider()
                                when (row) {
                                    is JournalRow.Dose -> DoseLine(row) { editing = Editing.Dose(row.log) }
                                    is JournalRow.Entry -> JournalLine(
                                        row.entry, row.time, onDelete = { vm.deleteEntry(row.entry) },
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        onClick = {
                                            editing = when (val e = row.entry) {
                                                is JournalEntry.BloodPressure -> Editing.Bp(e)
                                                is JournalEntry.Note -> Editing.Note(e)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val e = editing) {
        is Editing.Dose -> EditLogDialog(e.log, vm.zone(), onDismiss = { editing = null }, onSave = { vm.update(it); editing = null }, onDelete = { vm.deleteLog(e.log); editing = null })
        is Editing.Bp -> BloodPressureSheet(vm.now(), vm.zone(), onDismiss = { editing = null }, existing = e.entry, onSave = { sys, dia, pulse, at, note ->
            vm.newBloodPressure(sys, dia, pulse, at, note, e.entry); editing = null
        })
        is Editing.Note -> NoteSheet(vm.now(), vm.zone(), onDismiss = { editing = null }, existing = e.entry, onSave = { text, at ->
            vm.newNote(text, at, e.entry); editing = null
        })
        null -> Unit
    }
}

@Composable
private fun DoseLine(row: JournalRow.Dose, onClick: () -> Unit) {
    val c = Tracker.colors
    val taken = row.log.status == LogStatus.TAKEN
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Edit", onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.log.snapshot.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
            Text(row.detail, style = NumericStyle, color = if (taken) c.body2 else c.muted)
            if (row.log.note.isNotBlank()) Text(row.log.note, fontSize = 13.sp, color = c.muted, maxLines = 2)
        }
        Text(row.time, style = NumericStyle, color = c.muted)
    }
}

@Composable
private fun EditLogDialog(log: DoseLog, zone: ZoneId, onDismiss: () -> Unit, onSave: (DoseLog) -> Unit, onDelete: () -> Unit) {
    val local = log.takenAt.atZone(zone)
    var date by remember { mutableStateOf(local.toLocalDate()) }
    var time by remember { mutableStateOf(local.toLocalTime().withSecond(0).withNano(0)) }
    var amount by remember { mutableStateOf(formatNumber(log.amount.value, 4)) }
    var status by remember { mutableStateOf(log.status) }
    var note by remember { mutableStateOf(log.note) }
    val value = amount.toDecimal()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(log.snapshot.displayName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Segmented(LogStatus.entries, status, { if (it == LogStatus.TAKEN) "Taken" else "Skipped" }) { status = it }
                FieldRow {
                    DateField("Date", date, { if (it != null) date = it }, Modifier.weight(1.3f))
                    TimeField("Time", time, { time = it }, Modifier.weight(1f))
                }
                NumberField("Amount", amount, { amount = it }, Modifier.fillMaxWidth(), suffix = log.amount.unit.label)
                log.plannedAmount?.let { Text("Plan: ${formatNumber(it.value, 3)} ${it.unit.label}", style = NumericStyle, color = Tracker.colors.muted) }
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onDelete) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = {
                onSave(log.copy(takenAt = date.atTime(time).atZone(zone).toInstant(), amount = Amount(value!!, log.amount.unit), status = status, note = note.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
