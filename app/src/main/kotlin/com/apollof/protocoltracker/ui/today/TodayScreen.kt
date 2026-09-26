package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.AccentTextButton
import com.apollof.protocoltracker.ui.components.BottomActions
import com.apollof.protocoltracker.ui.components.CheckState
import com.apollof.protocoltracker.ui.components.CycleCard
import com.apollof.protocoltracker.ui.components.DoseRow
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.GroupCard
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.timingIcon
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import kotlinx.coroutines.launch

private sealed interface Sheet {
    data class Dose(val target: LogTarget) : Sheet
    data object BloodPressure : Sheet
    data object Note : Sheet
}

@Composable
fun TodayScreen(onOpenSettings: () -> Unit, onOpenPlan: () -> Unit) {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var weekOpen by rememberSaveable { mutableStateOf(false) }
    val c = Tracker.colors

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch {
                val result = snackbar.showSnackbar(msg.text, actionLabel = if (msg.undo != null) "Undo" else null, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) msg.undo?.invoke()
            }
        }
    }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.loading) BottomActions(
                onDose = { sheet = Sheet.Dose(LogTarget.Unscheduled(null)) },
                onBloodPressure = { sheet = Sheet.BloodPressure },
                onNote = { sheet = Sheet.Note },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") { Header(state.dateLabel, onOpenSettings) }

            if (!state.loading && !state.hasPlan && state.extras.isEmpty() && state.journal.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No plan yet",
                        body = "Add the compounds you take in Plan. Scheduled doses then appear here to check off.",
                        actionLabel = "Open plan", onAction = onOpenPlan,
                    )
                }
            }

            state.cycleTitle?.let { title ->
                item(key = "cycle") {
                    CycleCard(title, state.cycleSubtitle, state.progress, state.weekBar, state.week, weekOpen, onToggle = { weekOpen = !weekOpen })
                }
            }

            if (state.missed.isNotEmpty() || state.caughtUp.isNotEmpty()) item(key = "missed") {
                // Late logs stay here checked, so catching up on a missed dose visibly checks it off.
                GroupCard(
                    title = if (state.missed.isNotEmpty()) "Missed" else "Logged late",
                    icon = if (state.missed.isNotEmpty()) Icons.Outlined.WarningAmber else Icons.Outlined.History,
                    count = if (state.missed.isNotEmpty()) "${state.missed.size}" else "${state.caughtUp.size}",
                ) {
                    state.missed.forEach { item ->
                        RowDivider()
                        DoseRow(
                            item.commonName, item.name, item.detail, item.category, CheckState.PENDING,
                            onCheck = { vm.logMissedAsTaken(item) },
                            onOpen = { vm.targetFor(item, "missed")?.let { sheet = Sheet.Dose(it) } },
                        )
                    }
                    state.caughtUp.forEach { item ->
                        RowDivider()
                        DoseRow(
                            item.commonName, item.name, item.detail, item.category, item.state,
                            onCheck = { vm.check(item) },
                            onOpen = { vm.targetFor(item, "logged late")?.let { sheet = Sheet.Dose(it) } },
                        )
                    }
                }
            }

            state.groups.forEach { group ->
                item(key = "g-${group.key}") {
                    GroupCard(
                        title = group.label,
                        icon = timingIcon(group.slot),
                        count = "${group.items.size}",
                        action = {
                            when {
                                group.pending > 1 -> AccentTextButton("Log all ${group.pending}", { vm.logGroup(group) })
                                group.pending == 0 -> DoneBadge()
                            }
                        },
                    ) {
                        group.items.forEach { item ->
                            RowDivider()
                            DoseRow(
                                item.commonName, item.name, item.detail, item.category, item.state,
                                onCheck = { vm.check(item) },
                                onOpen = { vm.targetFor(item, group.label.lowercase())?.let { sheet = Sheet.Dose(it) } },
                            )
                        }
                    }
                }
            }

            if (state.extras.isNotEmpty()) item(key = "extras") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Also logged today")
                    state.extras.forEach { item ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.entry.log?.snapshot?.displayName ?: item.name, color = c.ink, modifier = Modifier.weight(1f))
                            Text(item.detail, style = NumericStyle, color = c.muted)
                        }
                    }
                }
            }

            if (state.journal.isNotEmpty()) item(key = "journal") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Logged today")
                    state.journal.forEach { entry -> JournalLine(entry, Formats.time(entry.at, vm.zone()), onDelete = { vm.deleteJournal(entry) }) }
                }
            }
        }
    }

    when (val s = sheet) {
        is Sheet.Dose -> LogDoseSheet(
            target = s.target, compounds = state.compounds, now = vm.now(), zone = vm.zone(),
            onDismiss = { sheet = null },
            onSaveScheduled = { t, amount, at, note -> vm.saveScheduled(t, amount, at, note); sheet = null },
            onSkip = { t, note -> vm.skipScheduled(t, note); sheet = null },
            onSaveUnscheduled = { compound, amount, at, note -> vm.logUnscheduled(compound, amount, at, note); sheet = null },
        )
        Sheet.BloodPressure -> BloodPressureSheet(vm.now(), vm.zone(), onDismiss = { sheet = null }, onSave = { sys, dia, pulse, at, note ->
            vm.saveBloodPressure(sys, dia, pulse, at, note); sheet = null
        })
        Sheet.Note -> NoteSheet(vm.now(), vm.zone(), onDismiss = { sheet = null }, onSave = { text, at -> vm.saveNote(text, at); sheet = null })
        null -> Unit
    }
}

@Composable
private fun Header(date: String, onOpenSettings: () -> Unit) {
    val c = Tracker.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = NumericStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), color = c.muted)
            Text("Today", style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = "Settings", tint = c.ink)
        }
    }
}

@Composable
private fun DoneBadge() {
    val c = Tracker.colors
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = c.accentText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Done", color = c.accentText, style = MaterialTheme.typography.labelLarge)
    }
}

/** A blood pressure reading or note; long-press deletes (with undo). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournalLine(entry: JournalEntry, time: String, onDelete: () -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val c = Tracker.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .combinedClickable(onClick = onClick, onLongClickLabel = "Delete entry", onLongClick = onDelete)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (entry) {
            is JournalEntry.BloodPressure -> {
                Icon(Icons.Outlined.MonitorHeart, contentDescription = "Blood pressure", tint = c.ink, modifier = Modifier.size(20.dp))
                Text(
                    "${entry.systolic}/${entry.diastolic} mmHg" + (entry.pulse?.let { " · $it bpm" } ?: "") + entry.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    style = NumericStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = c.ink, modifier = Modifier.weight(1f),
                )
            }
            is JournalEntry.Note -> {
                Icon(Icons.Outlined.EditNote, contentDescription = "Note", tint = c.ink, modifier = Modifier.size(20.dp))
                Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
        Text(time, style = NumericStyle, color = c.muted)
    }
}
