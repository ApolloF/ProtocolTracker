package com.apollof.protocoltracker.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.schedule.AgendaStatus
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.SectionHeader
import com.apollof.protocoltracker.ui.components.TimePickDialog
import com.apollof.protocoltracker.ui.theme.NumericStyle
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpenSettings: () -> Unit, onOpenPlan: () -> Unit) {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var timeFor by remember { mutableStateOf<DoseRow?>(null) }
    var showLogSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch {
                val result = snackbar.showSnackbar(msg.text, actionLabel = if (msg.undo != null) "Undo" else null, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) msg.undo?.invoke()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today")
                        if (state.dateLabel.isNotEmpty()) Text(state.dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") } },
            )
        },
        floatingActionButton = {
            if (state.hasPlan || state.compounds.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { showLogSheet = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Log dose") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loading && !state.hasPlan && state.done.isEmpty()) {
            EmptyState(
                title = "No plan yet",
                body = "Add a phase and the compounds you take. Scheduled doses then appear here to check off.",
                actionLabel = "Create plan", onAction = onOpenPlan, modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.phaseLabel?.let { label -> item(key = "phase") { PhaseBanner(label, state.phaseProgress) } }

            if (state.pendingCount > 1) item(key = "checkall") {
                FilledTonalButton(onClick = vm::checkAll, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Check all ${state.pendingCount} due")
                }
            }

            fun section(title: String, rows: List<DoseRow>, color: @Composable () -> Color, icon: (@Composable () -> Unit)? = null) {
                if (rows.isEmpty()) return
                item(key = "h-$title") { SectionHeader(title, color = color(), trailing = icon) }
                items(rows, key = { "$title-${it.entry.id}" }) { row ->
                    DoseRowCard(
                        row = row,
                        expanded = expandedId == row.entry.id,
                        onToggleExpand = { expandedId = if (expandedId == row.entry.id) null else row.entry.id },
                        onCheck = { if (row.entry.log != null) vm.uncheck(row) else vm.check(row) },
                        onTakenNow = { vm.checkNow(row); expandedId = null },
                        onPickTime = { timeFor = row },
                        onSkip = { vm.skip(row); expandedId = null },
                    )
                }
            }
            section("Overdue", state.overdue, { MaterialTheme.colorScheme.error }) {
                Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
            section("Due now", state.due, { MaterialTheme.colorScheme.primary })
            section("Later today", state.upcoming, { MaterialTheme.colorScheme.onSurfaceVariant })

            if (state.asNeeded.isNotEmpty()) {
                item(key = "h-prn") { SectionHeader("As needed") }
                item(key = "prn") { AsNeededChips(state.asNeeded, vm::logAsNeeded) }
            }
            section("Done", state.done, { MaterialTheme.colorScheme.onSurfaceVariant })

            if (state.nextDays.isNotEmpty()) {
                item(key = "h-next") { SectionHeader("Next ${TodayViewModel.PREVIEW_DAYS} days") }
                items(state.nextDays, key = { "d-${it.label}" }) { day -> DayPreviewCard(day) }
            }
        }
    }

    timeFor?.let { row ->
        val occ = row.entry.occurrence
        if (occ != null) TimePickDialog(
            initial = occ.at.atZone(ZoneId.systemDefault()).toLocalTime(),
            onDismiss = { timeFor = null },
            onConfirm = { time ->
                vm.checkAt(row, occ.localDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant())
                timeFor = null; expandedId = null
            },
        )
    }
    if (showLogSheet) LogDoseSheet(compounds = state.compounds, onDismiss = { showLogSheet = false }, onSave = { compound, amount, at, note ->
        vm.logExtra(compound, amount, at, note); showLogSheet = false
    })
}

@Composable
private fun PhaseBanner(label: String, progress: Float?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (progress != null) LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoseRowCard(
    row: DoseRow,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCheck: () -> Unit,
    onTakenNow: () -> Unit,
    onPickTime: () -> Unit,
    onSkip: () -> Unit,
) {
    val status = row.entry.status
    val done = status == AgendaStatus.TAKEN || status == AgendaStatus.SKIPPED
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (done) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (status == AgendaStatus.OVERDUE) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !done && row.entry.occurrence != null, onClickLabel = "More options", onClick = onToggleExpand)
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckCircle(
                    color = Color(row.color), status = status,
                    label = row.name,
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onCheck() },
                )
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(row.dose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(row.time, style = NumericStyle, color = if (status == AgendaStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded && !done) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 56.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onTakenNow) { Text("Taken now") }
                    OutlinedButton(onClick = onPickTime) { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Other time") }
                    TextButton(onClick = onSkip) { Text("Skip") }
                }
            }
        }
    }
}

/** 48 dp touch target; outline = pending, filled = taken, grey dash = skipped. */
@Composable
private fun CheckCircle(color: Color, status: AgendaStatus, label: String, onClick: () -> Unit) {
    val taken = status == AgendaStatus.TAKEN
    val skipped = status == AgendaStatus.SKIPPED
    val fill by animateColorAsState(
        when {
            taken -> color
            skipped -> MaterialTheme.colorScheme.outlineVariant
            else -> Color.Transparent
        }, label = "check",
    )
    val description = when {
        taken -> "$label taken. Tap to undo"
        skipped -> "$label skipped. Tap to undo"
        else -> "Mark $label taken"
    }
    Box(
        Modifier.size(48.dp)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = description; stateDescription = if (taken) "Taken" else if (skipped) "Skipped" else "Not taken" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(30.dp).background(fill, CircleShape).border(2.5.dp, if (taken || skipped) fill else color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (taken) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            if (skipped) Icon(Icons.Filled.Remove, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AsNeededChips(items: List<AsNeededItem>, onLog: (AsNeededItem) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            AssistChip(
                onClick = { onLog(item) },
                label = { Text("${item.compound.name} · ${item.dose}") },
                leadingIcon = { ColorDot(item.compound.colorArgb, size = 10.dp) },
            )
        }
    }
}

@Composable
private fun DayPreviewCard(day: DayPreview) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(day.label, style = MaterialTheme.typography.labelLarge)
            day.rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.time, style = NumericStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                    ColorDot(r.color, size = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${r.name} · ${r.dose}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
