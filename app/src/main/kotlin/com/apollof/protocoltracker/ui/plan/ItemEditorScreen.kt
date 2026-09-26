package com.apollof.protocoltracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.CategoryTag
import com.apollof.protocoltracker.ui.components.CompoundName
import com.apollof.protocoltracker.ui.components.CompoundPicker
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.FigureCell
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.TimePickDialog
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.today.QuickChip
import java.time.DayOfWeek
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditorScreen(itemId: String?, phaseId: String?, onDone: () -> Unit, onNewCompound: () -> Unit) {
    val vm = appViewModel(key = "item-$itemId-$phaseId") { ItemEditorViewModel(it, itemId, phaseId) }
    val d by vm.draft.collectAsStateWithLifecycle()
    val compounds by vm.compounds.collectAsStateWithLifecycle()
    val phases by vm.phases.collectAsStateWithLifecycle()
    val slotTimes by vm.slotTimes.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val preview = remember(d, compounds, loaded, slotTimes) { vm.preview(d, compounds, slotTimes) }
    val compound = compounds.firstOrNull { it.id == d.compoundId }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(itemId == null && d.compoundId == null) }
    var addingTime by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    val c = Tracker.colors
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (d.isNew) "Add to plan" else "Edit plan item") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (!d.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove from plan") }
                    PrimaryButton("Save", { tried = true; if (preview.errors.isEmpty()) vm.save(onDone) }, Modifier.padding(end = 8.dp), Icons.Outlined.Check)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Field("Compound") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(10.dp)).clickable(onClickLabel = "Choose compound") { picking = true }
                        .padding(start = 14.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (compound == null) Text("Choose compound", color = c.muted, modifier = Modifier.weight(1f))
                    else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CompoundName(compound.commonName, compound.name)
                        Text(compound.supportKind?.label ?: compound.category.label, fontSize = 12.sp, color = c.muted)
                    }
                    if (compound != null) { CategoryTag(compound.category); Spacer(Modifier.width(8.dp)) }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.ink)
                }
            }

            if (compound != null) {
                val injected = compound.route == Route.INJECTION
                Field("Amount") {
                    Segmented(listOf(DoseBasis.PER_WEEK, DoseBasis.PER_DOSE), d.basis, { if (it == DoseBasis.PER_WEEK) "Per week" else "Per dose" }) { b ->
                        vm.edit { it.copy(basis = b, doseUnit = if (b == DoseBasis.PER_WEEK && it.doseUnit in setOf(DoseUnit.ML, DoseUnit.TABLET)) DoseUnit.MG else it.doseUnit) }
                    }
                    FieldRow {
                        NumberField(
                            if (d.basis == DoseBasis.PER_WEEK) "Weekly dose" else "Dose", d.doseText, { t -> vm.edit { it.copy(doseText = t) } },
                            Modifier.weight(1f), suffix = d.doseUnit.label,
                        )
                        if (injected) NumberField("Strength", d.perMlText, { t -> vm.edit { it.copy(perMlText = t) } }, Modifier.weight(1f), suffix = "${compound.baseUnit.label}/mL")
                        else if (compound.baseUnit.name == "MG") NumberField("Tablet", d.perTabletText, { t -> vm.edit { it.copy(perTabletText = t) } }, Modifier.weight(1f), suffix = compound.baseUnit.label)
                    }
                    val units = buildList {
                        if (compound.baseUnit.name == "MG") { add(DoseUnit.MG); add(DoseUnit.MCG) } else add(DoseUnit.IU)
                        if (d.basis == DoseBasis.PER_DOSE && injected) add(DoseUnit.ML)
                        if (d.basis == DoseBasis.PER_DOSE && !injected && compound.baseUnit.name == "MG") add(DoseUnit.TABLET)
                    }
                    if (units.size > 1) Segmented(units, d.doseUnit, { it.label }) { u -> vm.edit { it.copy(doseUnit = u) } }
                }

                Field("Schedule") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleKind.entries.forEach { kind ->
                            QuickChip(kind.label, d.kind == kind) { vm.edit { it.copy(kind = kind) } }
                        }
                    }
                    when (d.kind) {
                        ScheduleKind.WEEKDAYS -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                val on = day in d.weekdays
                                QuickChip(day.getDisplayName(TextStyle.NARROW, locale), on, Modifier.weight(1f).semantics {
                                    contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                                }) { vm.edit { it.copy(weekdays = if (on) it.weekdays - day else it.weekdays + day) } }
                            }
                        }
                        ScheduleKind.EVERY_N_DAYS -> {
                            FieldRow {
                                NumberField("Every", d.everyNText, { t -> vm.edit { it.copy(everyNText = t.filter(Char::isDigit)) } }, Modifier.weight(1f), suffix = "days")
                                DateField("First day", d.anchorDate, { date -> if (date != null) vm.edit { it.copy(anchorDate = date) } }, Modifier.weight(1.4f))
                            }
                            if ((d.everyNText.toIntOrNull() ?: 0) > 1) {
                                FromLastDoseRow(d.fromLastDose, unit = "day") { on -> vm.edit { it.copy(fromLastDose = on) } }
                            }
                        }
                        ScheduleKind.EVERY_HOURS -> {
                            NumberField("Interval", d.hoursText, { t -> vm.edit { it.copy(hoursText = t) } }, Modifier.fillMaxWidth(), suffix = "hours")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("3.5 days" to "84", "5 days" to "120", "10 days" to "240").forEach { (label, hours) ->
                                    QuickChip(label, d.hoursText == hours) { vm.edit { it.copy(hoursText = hours) } }
                                }
                            }
                            FieldRow {
                                DateField("First dose", d.anchorDate, { date -> if (date != null) vm.edit { it.copy(anchorDate = date) } }, Modifier.weight(1.4f))
                                TimeField("Time", d.anchorTime, { t -> vm.edit { it.copy(anchorTime = t) } }, Modifier.weight(1f))
                            }
                            FromLastDoseRow(d.fromLastDose, unit = "time") { on -> vm.edit { it.copy(fromLastDose = on) } }
                        }
                        else -> Unit
                    }
                }

                preview.figures?.let { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.band).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FigureCell(f.perDoseLabel, f.perDose, Modifier.weight(1f))
                        f.detail?.let { detail -> FigureCell(f.detailLabel ?: "", detail, Modifier.weight(1f)) }
                        FigureCell(if (d.basis == DoseBasis.PER_WEEK) (if (injected) "PINS" else "DOSES") else "TOTAL", f.dosesPerWeek ?: "${f.total} ${f.totalLabel}".removeSuffix(" per dose"), Modifier.weight(1f))
                    }
                }

                if (d.kind in setOf(ScheduleKind.DAILY, ScheduleKind.WEEKDAYS, ScheduleKind.EVERY_N_DAYS)) Field("When") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DaySlot.entries.forEach { slot ->
                            val on = slot in d.slots
                            QuickChip(slot.label, on) { vm.edit { it.copy(slots = if (on) it.slots - slot else it.slots + slot) } }
                        }
                        d.times.sorted().forEach { t ->
                            QuickChip("${t.format(Formats.time)}  ✕", true, Modifier.semantics { contentDescription = "Remove ${t.format(Formats.time)}" }) {
                                vm.edit { it.copy(times = it.times - t) }
                            }
                        }
                        QuickChip("Set time…", false) { addingTime = true }
                    }
                    val anyTime = DaySlot.ANY_TIME in d.slots
                    Text(
                        if (anyTime) "Any time: log whenever you take it that day." else "Parts of the day use the clock times in Settings.",
                        fontSize = 12.sp, color = c.muted,
                    )
                }

                Field("Reminder") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                            .border(1.dp, c.line, RoundedCornerShape(10.dp)).padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Remind me", fontWeight = FontWeight.SemiBold, color = c.ink)
                            Text(
                                "Any-time doses remind at ${slotTimes.anyTimeReminder.format(Formats.time)} if not logged", fontSize = 12.sp, color = c.muted,
                            )
                        }
                        Switch(checked = d.remind, onCheckedChange = { on -> vm.edit { it.copy(remind = on) } })
                    }
                }

                Field("Phase") {
                    var open by remember { mutableStateOf(false) }
                    val current = phases.firstOrNull { it.id == d.phaseId }
                    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                        OutlinedTextField(
                            value = current?.name?.ifBlank { "Unnamed phase" } ?: "All phases", onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("All phases") }, onClick = { vm.edit { it.copy(phaseId = null) }; open = false })
                            phases.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name.ifBlank { "Unnamed phase" }) }, onClick = { vm.edit { it.copy(phaseId = p.id) }; open = false })
                            }
                        }
                    }
                    FieldRow {
                        DateField("Starts", d.startDate, { date -> vm.edit { it.copy(startDate = date) } }, Modifier.weight(1f), optional = true)
                        DateField("Ends", d.endDate, { date -> vm.edit { it.copy(endDate = date) } }, Modifier.weight(1f), optional = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Active", color = c.ink)
                            Text("Paused items stay in the plan without doses or reminders.", fontSize = 12.sp, color = c.muted)
                        }
                        Switch(checked = d.enabled, onCheckedChange = { on -> vm.edit { it.copy(enabled = on) } })
                    }
                    OutlinedTextField(d.notes, { t -> vm.edit { it.copy(notes = t) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }

                if (preview.next.isNotEmpty()) Field("Next doses") {
                    preview.next.forEach { Text(it, style = NumericStyle, color = c.body2) }
                }
            }

            if (tried && preview.errors.isNotEmpty()) {
                preview.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            PrimaryButton("Save", { tried = true; if (preview.errors.isEmpty()) vm.save(onDone) }, Modifier.fillMaxWidth(), Icons.Outlined.Check)
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
            CompoundPicker(compounds, onPick = { vm.selectCompound(it); picking = false }, footer = {
                SecondaryButton("New compound…", { picking = false; onNewCompound() }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
            })
        }
    }
    if (addingTime) TimePickDialog(java.time.LocalTime.of(9, 0), onDismiss = { addingTime = false }, onConfirm = { t ->
        vm.edit { it.copy(times = (it.times + t).distinct()) }; addingTime = false
    })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove from plan?") },
        text = { Text("Logged doses stay in the journal.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        content()
    }
}

/** Switch for interval schedules: count the next dose from the last logged one instead of the fixed plan grid. */
@Composable
private fun FromLastDoseRow(checked: Boolean, unit: String, onChange: (Boolean) -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text("Count from last dose", fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(
                if (checked) "A late or early dose moves the next one. Counted from the $unit you log it."
                else "Doses follow the fixed schedule, even after a late dose.",
                fontSize = 12.sp, color = c.muted,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
