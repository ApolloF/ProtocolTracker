package com.apollof.protocoltracker.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.data.CheckTime
import com.apollof.protocoltracker.data.Palette
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.reminders.Notifications
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ConfirmDialog
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import com.apollof.protocoltracker.ui.theme.dynamicTrackerColors
import com.apollof.protocoltracker.ui.theme.trackerColors
import java.time.LocalDate

/** Settings sub-pages, in the order the overview lists them. */
enum class SettingsPage(val title: String, val icon: ImageVector) {
    APPEARANCE("Appearance", Icons.Outlined.Palette),
    TODAY("Today", Icons.Outlined.Today),
    TIMES("Times of day", Icons.Outlined.Schedule),
    REMINDERS("Reminders", Icons.Outlined.NotificationsNone),
    DATA("Export and data", Icons.Outlined.SaveAlt),
    EXPERIMENTAL("Experimental", Icons.Outlined.Science),
    ABOUT("About", Icons.Outlined.Info),
}

/** One-line summary of a page's current values on the overview. */
private fun summary(page: SettingsPage, s: Settings): String = when (page) {
    SettingsPage.APPEARANCE -> "${s.theme.label} · ${s.palette.label}" + if (s.pureBlack) " · pure black" else ""
    SettingsPage.TODAY -> "Week bar ${s.weekBar.label.lowercase()} · check records ${if (s.checkTime == CheckTime.SCHEDULED) "scheduled time" else "current time"}"
    SettingsPage.TIMES -> "Morning ${s.slotTimes.timeOf(DaySlot.MORNING)} · Evening ${s.slotTimes.timeOf(DaySlot.EVENING)}"
    SettingsPage.REMINDERS -> if (s.doseReminders) "Dose reminders on" else "Dose reminders off"
    SettingsPage.DATA -> "Reports, backup, restore, import"
    SettingsPage.EXPERIMENTAL -> if (s.experimentalCompare) "Compare mode on" else "Features still being tested"
    SettingsPage.ABOUT -> "Version ${BuildConfig.VERSION_NAME}"
}

private val ThemeMode.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)

/** Settings overview: one row per page with its current values. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPage: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { SettingsViewModel(it, context.contentResolver) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val c = Tracker.colors
    SettingsScaffold("Settings", onBack) {
        LedgerCard {
            SettingsPage.entries.forEachIndexed { i, page ->
                if (i > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onOpenPage(page) }
                        .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(page.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text(page.title, style = TrackerType.title, color = c.ink)
                        Text(summary(page, settings), style = TrackerType.caption, color = c.muted, maxLines = 1)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                }
            }
        }
    }
}

/** A settings sub-page. */
@Composable
fun SettingsPageScreen(page: SettingsPage, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { SettingsViewModel(it, context.contentResolver) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    SettingsScaffold(page.title, onBack, snackbar) {
        when (page) {
            SettingsPage.APPEARANCE -> AppearancePage(settings, vm)
            SettingsPage.TODAY -> TodayPage(settings, vm)
            SettingsPage.TIMES -> TimesPage(settings, vm)
            SettingsPage.REMINDERS -> RemindersPage(settings, vm)
            SettingsPage.DATA -> DataPage(vm)
            SettingsPage.EXPERIMENTAL -> ExperimentalPage(settings, vm)
            SettingsPage.ABOUT -> AboutPage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, snackbar: SnackbarHostState? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Tracker.colors
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, titleContentColor = c.ink, navigationIconContentColor = c.ink),
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen).padding(top = Spacing.sm, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
            content = content,
        )
    }
}

/** A labelled group of settings. */
@Composable
private fun Group(label: String?, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Tracker.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (label != null) SectionLabel(label)
        if (note != null) Text(note, style = TrackerType.caption, color = c.muted)
        content()
    }
}

@Composable
private fun AppearancePage(settings: Settings, vm: SettingsViewModel) {
    Group("Theme") {
        Segmented(ThemeMode.entries, settings.theme, { it.label }) { m -> vm.update { it.copy(theme = m) } }
    }
    Group("Colour scheme") {
        val palettes = Palette.entries.filter { it != Palette.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        palettes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { p ->
                    PaletteOption(p, p == settings.palette, Modifier.weight(1f)) { vm.update { it.copy(palette = p) } }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Group(null) {
        LedgerCard {
            ToggleRow("Pure black", "Dark theme on a black background. Saves power on OLED screens.", settings.pureBlack) { on ->
                vm.update { it.copy(pureBlack = on) }
            }
        }
    }
}

/** One scheme: a preview of its background, surface and accent, its name, and a check when selected. */
@Composable
private fun PaletteOption(palette: Palette, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Tracker.colors
    val preview = if (palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        dynamicTrackerColors(if (c.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), c.dark)
    } else {
        trackerColors(palette, c.dark)
    }
    val shape = RoundedCornerShape(Radii.large)
    Row(
        modifier.heightIn(min = 64.dp).clip(shape).background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(preview.bg).border(1.dp, c.line, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(preview.accent))
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            palette.label, style = TrackerType.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = c.ink, modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = c.accentText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TodayPage(settings: Settings, vm: SettingsViewModel) {
    Group("Week bar", "How the week strip on Today is shown.") {
        Segmented(WeekBarMode.entries, settings.weekBar, { it.label }) { m -> vm.update { it.copy(weekBar = m) } }
    }
    Group("Checking an exact-time dose records", "Part-of-day doses always record the current time when checked today.") {
        Segmented(CheckTime.entries, settings.checkTime, { if (it == CheckTime.SCHEDULED) "Scheduled time" else "Current time" }) { t ->
            vm.update { it.copy(checkTime = t) }
        }
    }
}

@Composable
private fun TimesPage(settings: Settings, vm: SettingsViewModel) {
    Group(
        "Parts of the day",
        "Clock times for parts of the day. They set reminders and place doses on level curves; logged doses keep their part of the day when these change.",
    ) {
        DaySlot.entries.filter { it != DaySlot.ANY_TIME }.chunked(2).forEach { pair ->
            FieldRow {
                pair.forEach { slot ->
                    TimeField(slot.label, settings.slotTimes.timeOf(slot), { t ->
                        vm.update { it.copy(slotTimes = it.slotTimes.copy(times = it.slotTimes.times + (slot to t))) }
                    }, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Group("Any time", "Unlogged any-time doses are reminded at this time.") {
        TimeField("Any-time reminder", settings.slotTimes.anyTimeReminder, { t ->
            vm.update { it.copy(slotTimes = it.slotTimes.copy(anyTimeReminder = t)) }
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RemindersPage(settings: Settings, vm: SettingsViewModel) {
    val context = LocalContext.current
    // Bumped on resume so permission rows reflect changes made in system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val tick = resumeTick
    val notificationsOk = remember(tick) { Notifications.canPost(context) }
    val exactOk = remember(tick) { vm.canScheduleExact() }

    if (!notificationsOk || (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) Group("Permissions") {
        LedgerCard {
            if (!notificationsOk) ActionRow("Notifications are off", "Reminders cannot be shown.", "Allow") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
            }
            if (!notificationsOk && !exactOk) RowDivider()
            if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActionRow("Exact alarms not allowed", "Reminders may arrive up to 10 minutes late.", "Allow") {
                context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }
        }
    }
    Group(null) {
        LedgerCard {
            ToggleRow("Dose reminders", "Notification at each scheduled time with Taken, Snooze and Skip.", settings.doseReminders) { on ->
                vm.update { it.copy(doseReminders = on) }
            }
            RowDivider()
            ToggleRow("Daily summary", "One notification listing the day's doses.", settings.dailySummary) { on ->
                vm.update { it.copy(dailySummary = on) }
            }
        }
        if (settings.dailySummary) {
            TimeField("Summary time", settings.dailySummaryTime, { t -> vm.update { it.copy(dailySummaryTime = t) } }, Modifier.fillMaxWidth())
        }
    }
    Group("Snooze length") {
        Segmented(listOf(10, 15, 30, 60), settings.snoozeMinutes, { "$it min" }) { m -> vm.update { it.copy(snoozeMinutes = m) } }
    }
}

@Composable
private fun DataPage(vm: SettingsViewModel) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    var reportRange by remember { mutableStateOf(ReportRange.ALL) }
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        uri?.let { vm.exportReport(it, reportRange, markdown = false) }
    }
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let { vm.exportReport(it, reportRange, markdown = true) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readLegacy) }
    val files = arrayOf("application/json", "application/octet-stream", "text/plain")

    Group("Reports", "Reports list the plan, every logged dose (with planned amounts), missed doses, blood pressure and notes by date.") {
        Segmented(ReportRange.entries, reportRange, { it.label }) { reportRange = it }
        LedgerCard {
            LinkRow("Save readable report (HTML)") { htmlLauncher.launch("protocoltracker-report-${LocalDate.now()}.html") }
            RowDivider()
            LinkRow("Save report for AI (Markdown)") { markdownLauncher.launch("protocoltracker-report-${LocalDate.now()}.md") }
        }
    }
    Group("Backup", "Data is stored only on this device. Save a backup file regularly, for example to Drive or Files.") {
        LedgerCard {
            LinkRow("Save backup") { exportLauncher.launch("protocoltracker-${LocalDate.now()}.json") }
            RowDivider()
            LinkRow("Restore backup") { restoreLauncher.launch(files) }
            RowDivider()
            LinkRow("Import CycleTracker export") { importLauncher.launch(files) }
        }
    }

    when (val p = pending) {
        is PendingData.Restore -> ConfirmDialog(
            title = "Replace all data?",
            text = "The backup from ${p.backup.exportedAt.toString().take(10)} has ${p.backup.phases.size} phases, ${p.backup.items.size} plan items, " +
                "${p.backup.logs.size} logged doses and ${p.backup.journal.size} journal entries. Current data on this device is replaced.",
            confirm = "Replace", onConfirm = vm::confirm, onDismiss = vm::dismiss,
        )
        is PendingData.Import -> ConfirmDialog(
            title = "Import CycleTracker data?",
            text = buildString {
                append("${p.result.phases.size} phases, ${p.result.items.size} plan items, ${p.result.logs.size} logged doses, ${p.result.compounds.size} new compounds. ")
                append("Importing the same file again updates these entries instead of duplicating them.")
                if (p.result.warnings.isNotEmpty()) append("\n\n" + p.result.warnings.joinToString("\n") { "• $it" })
            },
            confirm = "Import", onConfirm = vm::confirm, onDismiss = vm::dismiss, destructive = false,
        )
        null -> Unit
    }
}

@Composable
private fun ExperimentalPage(settings: Settings, vm: SettingsViewModel) {
    Group(null, "Experimental features are not finished. They can change or be removed in a later version.") {
        LedgerCard {
            ToggleRow(
                "Compare mode in Levels",
                "Shows several compounds on one chart as a percentage, to compare their trends. Estimates only.",
                settings.experimentalCompare,
            ) { on -> vm.update { it.copy(experimentalCompare = on) } }
        }
    }
}

@Composable
private fun AboutPage() {
    val c = Tracker.colors
    Group("ProtocolTracker ${BuildConfig.VERSION_NAME}") {
        Text(
            "Level curves are estimates based on the Steroid Plotter data sheet and published labels. They are not measurements and not medical advice.",
            style = TrackerType.bodySmall, color = c.body2,
        )
        Text("Data stays on this device. The app has no network access and no account.", style = TrackerType.bodySmall, color = c.body2)
        Text("Fonts: IBM Plex Sans and IBM Plex Mono (SIL Open Font License).", style = TrackerType.caption, color = c.muted)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TrackerType.title, color = c.ink)
            Text(subtitle, style = TrackerType.caption, color = c.muted)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Spacing.lg, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TrackerType.title, color = c.danger)
            Text(subtitle, style = TrackerType.caption, color = c.muted)
        }
        TextButton(onClick = onClick) { Text(action, style = TrackerType.label, color = c.accentText) }
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(start = Spacing.lg, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TrackerType.body, color = c.accentText, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.muted)
    }
}
