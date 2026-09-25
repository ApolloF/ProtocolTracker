package com.apollof.protocoltracker.ui.plan

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onOpenSettings: () -> Unit, onEditItem: (itemId: String?, phaseId: String?) -> Unit, onOpenCompounds: () -> Unit) {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Phase?>(null) }
    var deleting by remember { mutableStateOf<Phase?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan") },
                actions = {
                    IconButton(onClick = onOpenCompounds) { Icon(Icons.Outlined.Science, contentDescription = "Compounds") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = vm.newPhase(state.phases.map { it.phase }) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Phase") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "always") {
                GroupCard(
                    title = "Always", subtitle = "Runs in every phase", color = null, status = null,
                    items = state.always, onItem = { onEditItem(it, null) }, onAdd = { onEditItem(null, null) },
                )
            }
            if (!state.loading && state.phases.isEmpty()) item(key = "empty") {
                EmptyState(
                    title = "No phases",
                    body = "Phases group what you take over a date range, e.g. Cruise, Blast, PCT. The phase covering today is active automatically.",
                    actionLabel = "Add phase", onAction = { editing = vm.newPhase(emptyList()) },
                )
            }
            items(state.phases, key = { it.phase.id }) { card ->
                GroupCard(
                    title = card.phase.name, subtitle = card.range, color = card.phase.colorArgb, status = card.status,
                    items = card.items, onItem = { onEditItem(it, card.phase.id) }, onAdd = { onEditItem(null, card.phase.id) },
                    onEdit = { editing = card.phase }, onDelete = { deleting = card.phase },
                )
            }
        }
    }

    editing?.let { phase -> PhaseDialog(phase, onDismiss = { editing = null }, onSave = { vm.savePhase(it); editing = null }) }
    deleting?.let { phase ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${phase.name}?") },
            text = { Text("Its plan items are removed. Logged doses stay in History.") },
            confirmButton = { TextButton(onClick = { vm.deletePhase(phase.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GroupCard(
    title: String,
    subtitle: String,
    color: Long?,
    status: PhaseStatus?,
    items: List<ItemRow>,
    onItem: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (color != null) { ColorDot(color, size = 14.dp); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (status != null) SuggestionChip(
                    onClick = {}, enabled = false,
                    label = { Text(status.label, color = if (status == PhaseStatus.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                if (onEdit != null) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Phase options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit phase") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete phase") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete?.invoke() })
                    }
                }
            }
            if (items.isNotEmpty()) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            items.forEach { row -> ItemRowView(row) { onItem(row.item.id) } }
            TextButton(onClick = onAdd, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add compound")
            }
        }
    }
}

@Composable
private fun ItemRowView(row: ItemRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit", onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(row.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.name + if (!row.item.enabled) " (paused)" else "", style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (row.item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${row.dose} · ${row.schedule}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        row.weekly?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseDialog(initial: Phase, onDismiss: () -> Unit, onSave: (Phase) -> Unit) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompoundColors.palette.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clickable { color = c }.semantics { contentDescription = "Colour ${i + 1}"; selected = c == color },
                            contentAlignment = Alignment.Center,
                        ) {
                            ColorDot(c, size = if (c == color) 32.dp else 24.dp)
                            if (c == color) ColorDot(0xFFFFFFFF, size = 10.dp)
                        }
                    }
                }
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
