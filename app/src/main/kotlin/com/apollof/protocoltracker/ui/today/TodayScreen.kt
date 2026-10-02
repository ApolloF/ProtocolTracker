package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Sick
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.HAIR_SHEDDING_LABELS
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.SiteRotation
import com.apollof.protocoltracker.domain.model.SymptomCatalog
import com.apollof.protocoltracker.domain.model.latestTaken
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.tabScreenTop
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.AccentTextButton
import com.apollof.protocoltracker.ui.components.CheckState
import com.apollof.protocoltracker.ui.components.CycleCard
import com.apollof.protocoltracker.ui.components.DatePickDialog
import com.apollof.protocoltracker.ui.components.DoseRow
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.GroupCard
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.ScreenHeader
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.SettingsButton
import com.apollof.protocoltracker.ui.components.timingIcon
import com.apollof.protocoltracker.ui.devOr
import com.apollof.protocoltracker.ui.health.BloodworkSheet
import com.apollof.protocoltracker.ui.health.SymptomSheet
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.SectionLabelStyle
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import kotlinx.coroutines.launch

private sealed interface Sheet {
    data class Dose(val target: LogTarget) : Sheet
    data object BloodPressure : Sheet
    data object Note : Sheet
    data object Symptoms : Sheet
    data object Bloodwork : Sheet
}

@Composable
fun TodayScreen(onOpenSettings: () -> Unit, onOpenPlan: () -> Unit, onImportBloodwork: (() -> Unit)? = null) {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val lastDraw by vm.lastDraw.collectAsStateWithLifecycle()
    val siteLogs by vm.siteLogs.collectAsStateWithLifecycle()
    val measuredMarkers by vm.measuredMarkers.collectAsStateWithLifecycle()
    var pickingDay by remember { mutableStateOf(false) }
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

    var logMenu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.loading) LogButton(onClick = { logMenu = true })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).tabScreenTop(),
            // Bottom padding keeps the last card clear of the Log button.
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.section, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            item(key = "header") {
                ScreenHeader("Today", eyebrow = state.dateLabel) {
                    IconButton(onClick = { pickingDay = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Open another day", tint = c.ink)
                    }
                    SettingsButton(onOpenSettings)
                }
            }

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
                    CycleCard(title, state.cycleSubtitle, state.progress, state.weekBar, state.week, weekOpen, onToggle = { weekOpen = !weekOpen }, onDay = vm::openDay)
                }
            }

            // Dev: a day without doses says so and names the next one (nextDue is null otherwise).
            state.nextDue?.takeIf { state.caughtUp.isEmpty() }?.let { next ->
                item(key = "next-due") {
                    LedgerCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Nothing due today", style = TrackerType.title, color = c.ink)
                            Text("Next: $next", style = TrackerType.bodySmall, color = c.body2)
                        }
                    }
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
                            item.commonName, item.name, item.detail, rowTag(item.category), CheckState.PENDING,
                            onCheck = { vm.logMissedAsTaken(item) },
                            onOpen = { vm.targetFor(item, "missed")?.let { sheet = Sheet.Dose(it) } },
                            holdTokens = BuildConfig.DEV_FEATURES,
                        )
                    }
                    state.caughtUp.forEach { item ->
                        RowDivider()
                        DoseRow(
                            item.commonName, item.name, item.detail, rowTag(item.category), item.state,
                            onCheck = { vm.check(item) },
                            onOpen = { vm.targetFor(item, "logged late")?.let { sheet = Sheet.Dose(it) } },
                            holdTokens = BuildConfig.DEV_FEATURES,
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
                                item.commonName, item.name, item.detail, rowTag(item.category), item.state,
                                onCheck = { vm.check(item) },
                                onOpen = { vm.targetFor(item, group.label.lowercase())?.let { sheet = Sheet.Dose(it) } },
                                holdTokens = BuildConfig.DEV_FEATURES,
                            )
                        }
                    }
                }
            }

            if (state.extras.isNotEmpty()) item(key = "extras") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Also logged today")
                    state.extras.forEach { item -> ExtraRow(item) { log -> sheet = Sheet.Dose(LogTarget.Edit(log)) } }
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
            onSaveScheduled = { t, amount, at, note, site -> vm.saveScheduled(t, amount, at, note, site); sheet = null },
            onSkip = { t, note -> vm.skipScheduled(t, note); sheet = null },
            onSaveUnscheduled = { compound, amount, at, note, site -> vm.logUnscheduled(compound, amount, at, note, site); sheet = null },
            sites = if (BuildConfig.DEV_FEATURES) { id, editing -> SiteRotation.forDose(siteLogs.orEmpty(), id, editing) } else null,
            lastTaken = if (BuildConfig.DEV_FEATURES) { id -> siteLogs.orEmpty().latestTaken(id) } else null,
            planCompounds = devOr(dev = state.planCompoundIds, stable = emptySet()),
            onSaveEdit = { log -> (s.target as? LogTarget.Edit)?.let { vm.saveEdit(log, it.log) }; sheet = null },
            onDelete = { log -> vm.deleteLog(log); sheet = null },
        )
        Sheet.BloodPressure -> BloodPressureSheet(vm.now(), vm.zone(), onDismiss = { sheet = null }, onSave = { sys, dia, pulse, at, note ->
            vm.saveBloodPressure(sys, dia, pulse, at, note); sheet = null
        })
        Sheet.Note -> NoteSheet(vm.now(), vm.zone(), onDismiss = { sheet = null }, onSave = { text, at -> vm.saveNote(text, at); sheet = null })
        Sheet.Symptoms -> SymptomSheet(vm.now(), vm.zone(), onDismiss = { sheet = null }, onSave = { vm.saveSymptoms(it); sheet = null })
        Sheet.Bloodwork -> BloodworkSheet(
            vm.now(), vm.zone(), state.labUnits, onDismiss = { sheet = null }, onSave = { vm.saveBloodwork(it); sheet = null },
            onImport = onImportBloodwork?.let { open -> { sheet = null; open() } }, measured = measuredMarkers,
        )
        null -> Unit
    }
    if (logMenu) LogMenuSheet(
        onDismiss = { logMenu = false },
        onDose = { logMenu = false; sheet = Sheet.Dose(LogTarget.Unscheduled(null)) },
        onBloodPressure = { logMenu = false; sheet = Sheet.BloodPressure },
        onNote = { logMenu = false; sheet = Sheet.Note },
        onSymptoms = { logMenu = false; sheet = Sheet.Symptoms },
        onBloodwork = { logMenu = false; sheet = Sheet.Bloodwork },
        bloodworkSubtitle = lastDraw ?: "Lab results of a blood draw",
    )
    // Hidden while a log sheet it opened is shown; it comes back after saving, on the same day.
    day?.let { d ->
        if (sheet == null) DaySheet(
            d, onDismiss = vm::closeDay, onShift = vm::shiftDay,
            onCheck = { vm.checkOnDay(it, d) },
            onOpen = { item -> vm.dayTarget(item, d)?.let { sheet = Sheet.Dose(it) } },
            onLogGroup = { vm.logDayGroup(it, d) },
            onOpenExtra = { log -> sheet = Sheet.Dose(LogTarget.Edit(log)) },
        )
    }
    if (pickingDay) DatePickDialog(vm.today(), onDismiss = { pickingDay = false }, onConfirm = { pickingDay = false; vm.openDay(it) })
}

/** The one floating action on Today: log something that is not a planned dose. */
@Composable
private fun LogButton(onClick: () -> Unit) {
    val c = Tracker.colors
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        text = { Text("Log", style = TrackerType.label) },
        containerColor = c.accent, contentColor = c.onAccent,
        shape = RoundedCornerShape(16.dp),
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    )
}

/** What the Log button can record. Planned doses are checked in their rows instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogMenuSheet(
    onDismiss: () -> Unit,
    onDose: () -> Unit,
    onBloodPressure: () -> Unit,
    onNote: () -> Unit,
    onSymptoms: () -> Unit,
    onBloodwork: () -> Unit,
    bloodworkSubtitle: String,
) {
    val c = Tracker.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Text(
                "LOG", style = SectionLabelStyle, color = c.muted,
                modifier = Modifier.padding(horizontal = Spacing.section, vertical = Spacing.sm).semantics { heading() },
            )
            LogMenuRow(Icons.Outlined.Vaccines, "Extra dose", "A dose that is not in today's plan", onDose)
            LogMenuRow(Icons.Outlined.MonitorHeart, "Blood pressure", "Systolic, diastolic and pulse", onBloodPressure)
            LogMenuRow(Icons.Outlined.EditNote, "Note", devOr(dev = "Anything else, in your own words", stable = "Side effects, how you feel, anything else"), onNote)
            if (BuildConfig.DEV_FEATURES) {
                LogMenuRow(Icons.Outlined.Sick, "Symptoms", "Symptoms, mood and hair shedding", onSymptoms)
                LogMenuRow(Icons.Outlined.Bloodtype, "Bloodwork", bloodworkSubtitle, onBloodwork)
            }
        }
    }
}

@Composable
private fun LogMenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.section, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accentText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Spacing.lg))
        Column {
            Text(title, style = TrackerType.title, color = c.ink)
            Text(subtitle, style = TrackerType.caption, color = c.muted)
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

/** One journal entry (blood pressure, note, symptoms or bloodwork); tap calls [onClick], long-press deletes (with undo). */
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
                // Dev: a long note shows its start; the whole text opens with a tap.
                Text(
                    entry.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f),
                    maxLines = devOr(dev = NOTE_LINES, stable = Int.MAX_VALUE), overflow = TextOverflow.Ellipsis,
                )
            }
            is JournalEntry.Symptoms -> {
                Icon(Icons.Outlined.Sick, contentDescription = "Symptoms", tint = c.ink, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        entry.symptoms.joinToString(", ") { devOr(dev = SymptomCatalog.readableLabel(it), stable = SymptomCatalog.label(it)) }.ifEmpty { "Symptoms" },
                        style = MaterialTheme.typography.bodyMedium, color = c.ink,
                    )
                    val details = listOfNotNull(
                        // Dev: the names alone; group counts read as a hormone verdict.
                        SymptomCatalog.summary(entry.symptoms).ifEmpty { null }.takeIf { !BuildConfig.DEV_FEATURES },
                        entry.mood?.let { "mood $it/10" },
                        entry.hairShedding?.let { "hair ${HAIR_SHEDDING_LABELS[it - 1].lowercase()}" },
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details, style = TrackerType.numericSmall, color = c.muted)
                    if (entry.note.isNotBlank()) Text(entry.note, style = TrackerType.bodySmall, color = c.muted, maxLines = 2)
                }
            }
            is JournalEntry.Bloodwork -> {
                Icon(Icons.Outlined.Bloodtype, contentDescription = "Bloodwork", tint = c.ink, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(listOfNotNull("Bloodwork", entry.lab.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    Text(bloodworkSummary(entry), style = TrackerType.numericSmall, color = c.muted)
                }
            }
        }
        Text(time, style = NumericStyle, color = c.muted)
    }
}

/**
 * "3 results · 1 out of range" or "3 results · all in range". "All in range" only when no result is out of range or
 * unclear (E2 `<40` against 20–150); an unclear draw shows the count alone. "1 result" in dev, "1 results" in stable.
 */
internal fun bloodworkSummary(entry: JournalEntry.Bloodwork): String {
    val n = entry.results.size
    val count = devOr(dev = if (n == 1) "1 result" else "$n results", stable = "$n results")
    return count + when {
        entry.outOfRange > 0 -> " · ${entry.outOfRange} out of range"
        entry.unclear > 0 -> ""
        else -> " · all in range"
    }
}

/** Dev: Today and Day sheet rows carry no category tag (the dose line already says it); Plan keeps it. */
internal fun rowTag(category: CompoundCategory?): CompoundCategory? = devOr(dev = null, stable = category)

/** Lines of a note shown in a Journal or Today row (dev). */
internal const val NOTE_LINES = 4

/** A dose logged outside the plan: name and amount; dev opens it in the dose sheet's edit mode ([onOpen]). */
@Composable
internal fun ExtraRow(item: DoseItem, onOpen: (DoseLog) -> Unit) {
    val c = Tracker.colors
    val log = item.entry.log
    val open = if (BuildConfig.DEV_FEATURES && log != null) Modifier.clickable(onClickLabel = "Edit") { onOpen(log) } else Modifier
    Row(
        Modifier.fillMaxWidth().heightIn(min = devOr(dev = 48.dp, stable = 44.dp)).then(open).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(log?.snapshot?.displayName ?: item.name, color = c.ink, modifier = Modifier.weight(1f))
        Text(item.detail, style = NumericStyle, color = c.muted)
    }
}
