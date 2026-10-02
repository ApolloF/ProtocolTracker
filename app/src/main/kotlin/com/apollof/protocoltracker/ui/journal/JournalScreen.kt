package com.apollof.protocoltracker.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Sick
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.container
import com.apollof.protocoltracker.data.Motion
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.MarkerFlag
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.MarkerTrend
import com.apollof.protocoltracker.domain.model.UnlistedTrend
import com.apollof.protocoltracker.domain.model.flag
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.units.DoseAdjust
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.components.tokensTogether
import com.apollof.protocoltracker.ui.tabScreenTop
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.devOr
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.ScreenHeader
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.SettingsButton
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.health.BloodworkSheet
import com.apollof.protocoltracker.ui.health.MarkerSheet
import com.apollof.protocoltracker.ui.health.ResultRow
import com.apollof.protocoltracker.ui.health.refText
import com.apollof.protocoltracker.ui.health.valueText
import com.apollof.protocoltracker.ui.health.SymptomSheet
import com.apollof.protocoltracker.ui.theme.Motions
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import com.apollof.protocoltracker.ui.today.BloodPressureSheet
import com.apollof.protocoltracker.ui.today.JournalLine
import com.apollof.protocoltracker.ui.today.NoteSheet
import java.time.ZoneId
import kotlinx.coroutines.launch

private sealed interface Editing {
    data class Dose(val log: DoseLog) : Editing
    data class Bp(val entry: JournalEntry.BloodPressure?) : Editing
    data class Note(val entry: JournalEntry.Note?) : Editing
    data class Symptoms(val entry: JournalEntry.Symptoms?) : Editing
    data class Bloodwork(val entry: JournalEntry.Bloodwork?) : Editing
}

/** [onImportBloodwork] opens the bloodwork import from a new draw's sheet (dev). */
@Composable
fun JournalScreen(onOpenSettings: () -> Unit, onImportBloodwork: (() -> Unit)? = null) {
    val vm = appViewModel { JournalViewModel(it) }
    val focus = LocalContext.current.container.journalFocus
    val focusRequest by focus.pending.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val markerSheet by vm.markerSheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var adherenceOpen by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val c = Tracker.colors
    val motion = Motions.current

    // A snackbar with an action stays until dismissed by default; dev lets Undo time out so a late tap cannot undo.
    val undoDuration = devOr(dev = SnackbarDuration.Long, stable = SnackbarDuration.Indefinite)
    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            scope.launch { if (snackbar.showSnackbar(msg.text, actionLabel = "Undo", duration = undoDuration) == SnackbarResult.ActionPerformed) msg.undo?.invoke() }
        }
    }
    // A saved bloodwork import (dev): its draws under the Bloodwork chip, with Undo.
    LaunchedEffect(focusRequest) {
        val msg = focus.take() ?: return@LaunchedEffect
        vm.setFilter(JournalFilter.BLOODWORK)
        scope.launch { if (snackbar.showSnackbar(msg.text, actionLabel = "Undo", duration = undoDuration) == SnackbarResult.ActionPerformed) msg.undo?.invoke() }
    }

    Scaffold(containerColor = c.bg, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).tabScreenTop(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                ScreenHeader("Journal") {
                    if (BuildConfig.DEV_FEATURES) {
                        // Four kinds of entry: one Add button with a menu keeps the header from overflowing.
                        Box {
                            IconButton(onClick = { addMenu = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.Add, contentDescription = "Add entry", tint = c.ink)
                            }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }, containerColor = c.surface) {
                                DropdownMenuItem(text = { Text("Blood pressure") }, leadingIcon = { Icon(Icons.Outlined.MonitorHeart, null) },
                                    onClick = { addMenu = false; editing = Editing.Bp(null) })
                                DropdownMenuItem(text = { Text("Note") }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                                    onClick = { addMenu = false; editing = Editing.Note(null) })
                                DropdownMenuItem(text = { Text("Symptoms") }, leadingIcon = { Icon(Icons.Outlined.Sick, null) },
                                    onClick = { addMenu = false; editing = Editing.Symptoms(null) })
                                DropdownMenuItem(text = { Text("Bloodwork") }, leadingIcon = { Icon(Icons.Outlined.Bloodtype, null) },
                                    onClick = { addMenu = false; editing = Editing.Bloodwork(null) })
                            }
                        }
                    } else {
                        IconButton(onClick = { editing = Editing.Bp(null) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.MonitorHeart, contentDescription = "Add blood pressure", tint = c.ink)
                        }
                        IconButton(onClick = { editing = Editing.Note(null) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.EditNote, contentDescription = "Add note", tint = c.ink)
                        }
                    }
                    SettingsButton(onOpenSettings)
                }
            }

            if (!state.loading && state.empty) item(key = "empty") {
                EmptyState(
                    "Nothing logged yet",
                    devOr(
                        dev = "Doses you check on Today, blood pressure, notes, symptoms and bloodwork appear here by day.",
                        stable = "Doses you check on Today, blood pressure readings and notes appear here by day.",
                    ),
                )
            }

            if (!state.empty) item(key = "filters") {
                val chips = rememberLazyListState()
                val selectedChip = if (state.compound != null) {
                    state.compounds.indexOfFirst { it.id == state.compound }.let { if (it < 0) -1 else JournalFilter.available.size + it }
                } else {
                    JournalFilter.available.indexOf(state.filter)
                }
                // Dev: a filter chosen elsewhere (Bloodwork after an import) is scrolled into view.
                if (BuildConfig.DEV_FEATURES) LaunchedEffect(selectedChip) { chips.reveal(selectedChip, motion) }
                LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(JournalFilter.available, key = { it.name }) { f ->
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
                                if (BuildConfig.DEV_FEATURES) {
                                    // Two lines, so the reading and its day never wrap apart at a dangling "·".
                                    Text("Latest ${bp.latest} mmHg", style = NumericStyle, color = c.ink)
                                    Text(bp.latestWhen, style = TrackerType.caption, color = c.muted)
                                } else {
                                    Text("Latest ${bp.latest} mmHg · ${bp.latestWhen}", style = NumericStyle, color = c.ink)
                                }
                            }
                            bp.average7?.let {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(it, style = TrackerType.figureLarge, color = c.ink)
                                    Text("7-day average (${bp.readings7})", style = TrackerType.caption, color = c.muted)
                                }
                            }
                        }
                        // Dev: filled only under the Blood pressure chip; stable's card is unchanged.
                        if (state.bpWeeks.size >= 2) {
                            BpTrendBlock(state.bpWeeks, vm.zone(), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
                        }
                    }
                }
            }

            // Dev: the Symptoms chip opens with the mood chart (two or more days with a rating).
            if (state.mood.isNotEmpty() && state.filter == JournalFilter.SYMPTOMS) item(key = "mood") {
                LedgerCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionLabel("Mood")
                        MoodTrendBlock(state.mood, vm.zone())
                    }
                }
            }

            val hasBloodwork = state.bloodwork.isNotEmpty() || state.unlisted.isNotEmpty()
            // Dev: All shows one row that opens the Bloodwork chip; the card with every marker lives under that chip.
            if (hasBloodwork && state.compound == null) when (state.filter) {
                JournalFilter.BLOODWORK -> item(key = "bloodwork") {
                    BloodworkCard(state.bloodwork, state.unlisted, state.labUnits, state.lastDraw, onOpen = vm::showMarker)
                }
                JournalFilter.ALL -> item(key = "bloodwork") {
                    BloodworkSummary(state.bloodwork, state.unlisted, state.lastDraw) { vm.setFilter(JournalFilter.BLOODWORK) }
                }
                else -> {}
            }

            // Dev: nothing logged yet means no adherence card under the empty state.
            if (state.adherence.isNotEmpty() && devOr(dev = !state.empty, stable = true) &&
                (state.filter == JournalFilter.ALL || state.filter == JournalFilter.DOSES)
            ) item(key = "adherence") {
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
                            Text(a.name, color = c.ink, style = TrackerType.bodySmall, modifier = Modifier.weight(1f))
                            Text(a.week, style = NumericStyle, color = c.body2, modifier = Modifier.padding(end = 12.dp))
                            Text(a.month, style = NumericStyle, color = c.body2)
                        }
                    }
                }
            }

            state.days.forEach { day ->
                item(key = "d-${day.date}") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel(day.header)
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
                                                is JournalEntry.Symptoms -> Editing.Symptoms(e)
                                                is JournalEntry.Bloodwork -> Editing.Bloodwork(e)
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
        }, onDelete = e.entry?.let { entry -> { vm.deleteEntry(entry); editing = null } })
        is Editing.Note -> NoteSheet(vm.now(), vm.zone(), onDismiss = { editing = null }, existing = e.entry, onSave = { text, at ->
            vm.newNote(text, at, e.entry); editing = null
        }, onDelete = e.entry?.let { entry -> { vm.deleteEntry(entry); editing = null } })
        is Editing.Symptoms -> SymptomSheet(vm.now(), vm.zone(), onDismiss = { editing = null }, existing = e.entry, onSave = {
            vm.saveSymptoms(it, e.entry); editing = null
        }, onDelete = e.entry?.let { entry -> { vm.deleteEntry(entry); editing = null } })
        is Editing.Bloodwork -> BloodworkSheet(vm.now(), vm.zone(), state.labUnits, onDismiss = { editing = null }, existing = e.entry, onSave = {
            vm.saveBloodwork(it, e.entry); editing = null
        }, onImport = onImportBloodwork?.let { open -> { editing = null; open() } }, measured = state.measured, onDelete = e.entry?.let { entry -> { vm.deleteEntry(entry); editing = null } })
        null -> Unit
    }
    markerSheet?.takeIf { it.results.isNotEmpty() }?.let { MarkerSheet(it, state.labUnits, onDismiss = { vm.showMarker(null) }) }
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
            Text(row.log.snapshot.displayName, style = TrackerType.title, color = c.ink)
            Text(row.detail, style = NumericStyle, color = if (taken) c.body2 else c.muted)
            if (row.log.note.isNotBlank()) Text(row.log.note, style = TrackerType.bodySmall, color = c.muted, maxLines = 2)
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
                // Dev: an unchanged amount field keeps the stored amount (or the plan it shows), never a rounded copy.
                val saved = if (BuildConfig.DEV_FEATURES) DoseAdjust.fromField(value!!, log.amount.unit, log.plannedAmount, log.amount) else Amount(value!!, log.amount.unit)
                onSave(log.copy(takenAt = date.atTime(time).atZone(zone).toInstant(), amount = saved, status = status, note = note.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Latest result per marker (dev builds): value, reference range and in or out of range as text; a row opens the
 * marker sheet ([onOpen] with the key). Unlisted tests follow: one flagged Low or High shows as a row, the rest behind
 * "Other tests (N)". The label says how long ago the last draw was ([lastDraw], e.g. "3 days ago").
 */
@Composable
private fun BloodworkCard(trends: List<MarkerTrend>, unlisted: List<UnlistedTrend>, units: LabUnits, lastDraw: String?, onOpen: (String) -> Unit) {
    val c = Tracker.colors
    val zone = ZoneId.systemDefault()
    var othersOpen by rememberSaveable { mutableStateOf(false) }
    val (flagged, others) = unlisted.partition { it.result.flag().let { f -> f == MarkerFlag.LOW || f == MarkerFlag.HIGH } }
    fun meta(at: java.time.Instant, result: MarkerResult) = listOfNotNull(at.atZone(zone).format(Formats.date), result.refText(units)).joinToString(" · ")
    LedgerCard {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            SectionLabel(lastDraw?.let { "Bloodwork · last draw $it" } ?: "Bloodwork · latest results")
        }
        trends.forEachIndexed { i, t ->
            if (i > 0) RowDivider()
            ResultRow(t.marker.name, meta(t.at, t.result), t.marker.formatResult(t.result, units), t.result.flag(), inset = 16.dp) { onOpen(t.marker.key) }
        }
        flagged.forEachIndexed { i, u ->
            if (i > 0 || trends.isNotEmpty()) RowDivider()
            UnlistedRow(u, meta(u.at, u.result), units, onOpen)
        }
        if (others.isNotEmpty()) {
            if (trends.isNotEmpty() || flagged.isNotEmpty()) RowDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { othersOpen = !othersOpen }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Other tests (${others.size})", style = TrackerType.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
                Icon(if (othersOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (othersOpen) "Hide" else "Show", tint = c.ink)
            }
            if (othersOpen) others.forEach { u ->
                RowDivider()
                UnlistedRow(u, meta(u.at, u.result), units, onOpen)
            }
        }
    }
}

/** One row for the Bloodwork card on All: "Bloodwork · last draw 3 days ago · 2 out of range". */
@Composable
private fun BloodworkSummary(trends: List<MarkerTrend>, unlisted: List<UnlistedTrend>, lastDraw: String?, onOpen: () -> Unit) {
    val c = Tracker.colors
    val outOfRange = (trends.map { it.result } + unlisted.map { it.result }).count { it.flag().let { f -> f == MarkerFlag.LOW || f == MarkerFlag.HIGH } }
    val label = listOfNotNull(
        lastDraw?.let { "Bloodwork · last draw $it" } ?: "Bloodwork",
        "$outOfRange out of range".takeIf { outOfRange > 0 },
    ).joinToString(" · ")
    LedgerCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Show bloodwork", onClick = onOpen).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Each part stays whole ("1 out of range"), so the label only breaks at a " · ".
            SectionLabel(tokensTogether(label), color = c.ink, modifier = Modifier.weight(1f).clearAndSetSemantics { text = AnnotatedString(label.uppercase()); heading() })
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.muted)
        }
    }
}

@Composable
private fun UnlistedRow(u: UnlistedTrend, meta: String, units: LabUnits, onOpen: (String) -> Unit) =
    ResultRow(u.name, meta, u.result.valueText(units), u.result.flag(), inset = 16.dp) { onOpen(u.key) }

/** Scrolls the least distance that shows item [index] whole, following the Motion setting. */
private suspend fun LazyListState.reveal(index: Int, motion: Motion) {
    if (index < 0) return
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        if (motion == Motion.OFF) scrollToItem(index) else animateScrollToItem(index)
        return
    }
    val delta = when {
        item.offset < info.viewportStartOffset -> item.offset - info.viewportStartOffset
        item.offset + item.size > info.viewportEndOffset -> item.offset + item.size - info.viewportEndOffset
        else -> return
    }.toFloat()
    if (motion == Motion.OFF) scrollBy(delta) else animateScrollBy(delta, Motions.spec(motion, 250))
}
