package com.apollof.protocoltracker.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.SectionHeader
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.TimePickDialog
import com.apollof.protocoltracker.ui.components.UnitSelector
import com.apollof.protocoltracker.ui.components.unitsFor
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditorScreen(itemId: String?, phaseId: String?, onDone: () -> Unit, onNewCompound: () -> Unit) {
    val vm = appViewModel(key = "item-$itemId-$phaseId") { ItemEditorViewModel(it, itemId, phaseId) }
    val d by vm.draft.collectAsStateWithLifecycle()
    val compounds by vm.compounds.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val preview = remember(d, compounds, loaded) { vm.preview(d, compounds) }
    val compound = compounds.firstOrNull { it.id == d.compoundId }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (d.isNew) "Add compound" else "Edit compound") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (!d.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove from plan") }
                    TextButton(onClick = { vm.save(onDone) }, enabled = preview.errors.isEmpty()) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Compound
            var menu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                OutlinedTextField(
                    value = compound?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Compound") },
                    leadingIcon = compound?.let { { ColorDot(it.colorArgb) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    compounds.filter { !it.archived }.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, leadingIcon = { ColorDot(c.colorArgb) }, onClick = { vm.selectCompound(c); menu = false })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("New compound…") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { menu = false; onNewCompound() })
                }
            }

            // Dose
            if (compound != null) {
                SectionHeader("Dose")
                val showVolume = compound.category in setOf(CompoundCategory.INJECTABLE, CompoundCategory.HCG) || d.doseUnit == DoseUnit.ML
                val showTablet = compound.baseUnit == BaseUnit.MG &&
                    (compound.category !in setOf(CompoundCategory.INJECTABLE, CompoundCategory.HCG) || d.doseUnit == DoseUnit.TABLET)
                val units = buildList {
                    addAll(unitsFor(compound.baseUnit, Formulation()))
                    if (showVolume) add(DoseUnit.ML)
                    if (showTablet) add(DoseUnit.TABLET)
                }
                NumberField("Dose", d.doseText, { t -> vm.edit { it.copy(doseText = t) } }, Modifier.fillMaxWidth(), suffix = d.doseUnit.label)
                UnitSelector(units, d.doseUnit) { u -> vm.edit { it.copy(doseUnit = u) } }
                FieldRow {
                    if (showVolume) NumberField(
                        "Concentration", d.perMlText, { t -> vm.edit { it.copy(perMlText = t) } }, Modifier.weight(1f),
                        suffix = "${compound.baseUnit.label}/mL",
                    )
                    if (showTablet) NumberField(
                        "Tablet strength", d.perTabletText, { t -> vm.edit { it.copy(perTabletText = t) } }, Modifier.weight(1f),
                        suffix = compound.baseUnit.label,
                    )
                }
            }

            // Schedule
            SectionHeader("Schedule")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleKind.entries.forEach { kind ->
                    FilterChip(selected = d.kind == kind, onClick = { vm.edit { it.copy(kind = kind) } }, label = { Text(kind.label) })
                }
            }
            val locale = LocalConfiguration.current.locales[0]
            when (d.kind) {
                ScheduleKind.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in d.weekdays,
                            onClick = { vm.edit { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day) } },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        )
                    }
                }
                ScheduleKind.EVERY_N_DAYS -> FieldRow {
                    NumberField("Every", d.everyNText, { t -> vm.edit { it.copy(everyNText = t.filter(Char::isDigit)) } }, Modifier.weight(1f), suffix = "days")
                    DateField("First day", d.anchorDate, { date -> if (date != null) vm.edit { it.copy(anchorDate = date) } }, Modifier.weight(1.4f))
                }
                ScheduleKind.EVERY_HOURS -> {
                    NumberField("Interval", d.hoursText, { t -> vm.edit { it.copy(hoursText = t) } }, Modifier.fillMaxWidth(), suffix = "hours")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3.5 days" to "84", "5 days" to "120", "10 days" to "240", "12 h" to "12").forEach { (label, hours) ->
                            FilterChip(selected = d.hoursText == hours, onClick = { vm.edit { it.copy(hoursText = hours) } }, label = { Text(label) })
                        }
                    }
                    FieldRow {
                        DateField("First dose", d.anchorDate, { date -> if (date != null) vm.edit { it.copy(anchorDate = date) } }, Modifier.weight(1.4f))
                        TimeField("Time", d.anchorTime, { t -> vm.edit { it.copy(anchorTime = t) } }, Modifier.weight(1f))
                    }
                }
                else -> Unit
            }
            if (d.kind in setOf(ScheduleKind.DAILY, ScheduleKind.WEEKDAYS, ScheduleKind.EVERY_N_DAYS)) TimesEditor(d.times) { times -> vm.edit { it.copy(times = times) } }

            // Bounds
            SectionHeader("Limits")
            FieldRow {
                DateField("Starts", d.startDate, { date -> vm.edit { it.copy(startDate = date) } }, Modifier.weight(1f), optional = true)
                DateField("Ends", d.endDate, { date -> vm.edit { it.copy(endDate = date) } }, Modifier.weight(1f), optional = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Text("Paused items stay in the plan without reminders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = d.enabled, onCheckedChange = { on -> vm.edit { it.copy(enabled = on) } })
            }
            OutlinedTextField(d.notes, { t -> vm.edit { it.copy(notes = t) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            PreviewCard(preview)
            Button(onClick = { vm.save(onDone) }, enabled = preview.errors.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove from plan?") },
        text = { Text("Logged doses stay in History.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesEditor(times: List<LocalTime>, onChange: (List<LocalTime>) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        times.sorted().forEachIndexed { i, t ->
            InputChip(
                selected = false, onClick = { editing = i }, label = { Text(t.format(Formats.time)) },
                trailingIcon = if (times.size > 1) {
                    { IconButton(onClick = { onChange(times.sorted().filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.Delete, "Remove ${t.format(Formats.time)}") } }
                } else null,
            )
        }
        TextButton(onClick = { editing = -1 }) { Icon(Icons.Filled.Add, null); Text("Time") }
    }
    editing?.let { index ->
        val sorted = times.sorted()
        TimePickDialog(
            initial = sorted.getOrNull(index) ?: LocalTime.of(21, 0),
            onDismiss = { editing = null },
            onConfirm = { t ->
                val next = if (index >= 0) sorted.toMutableList().also { it[index] = t } else sorted + t
                onChange(next.distinct().sorted()); editing = null
            },
        )
    }
}

@Composable
private fun PreviewCard(preview: ItemPreview) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (preview.errors.isNotEmpty()) {
                preview.errors.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                return@Column
            }
            preview.summary?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            preview.weekly?.let { Text("≈ $it", style = MaterialTheme.typography.bodyMedium) }
            if (preview.next.isNotEmpty()) {
                Text("Next doses", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                preview.next.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
