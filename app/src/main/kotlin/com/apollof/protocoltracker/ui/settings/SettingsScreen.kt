package com.apollof.protocoltracker.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.data.CheckTime
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.reminders.Notifications
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.SectionHeader
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel { SettingsViewModel(it, context.contentResolver) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Bumped on resume so permission rows reflect changes made in system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readLegacy) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Appearance")
            Segmented(ThemeMode.entries, settings.theme, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { m -> vm.update { it.copy(theme = m) } }

            SectionHeader("Checking a dose records")
            Segmented(CheckTime.entries, settings.checkTime, { if (it == CheckTime.SCHEDULED) "Scheduled time" else "Current time" }) { t -> vm.update { it.copy(checkTime = t) } }

            SectionHeader("Reminders")
            ToggleRow("Dose reminders", "Notification at each scheduled time with Taken, Snooze and Skip.", settings.doseReminders) { on -> vm.update { it.copy(doseReminders = on) } }
            Text("Snooze length", style = MaterialTheme.typography.bodyLarge)
            Segmented(listOf(10, 15, 30, 60), settings.snoozeMinutes, { "$it min" }) { m -> vm.update { it.copy(snoozeMinutes = m) } }
            ToggleRow("Daily summary", "One notification listing the day's doses.", settings.dailySummary) { on -> vm.update { it.copy(dailySummary = on) } }
            if (settings.dailySummary) TimeField("Summary time", settings.dailySummaryTime, { t -> vm.update { it.copy(dailySummaryTime = t) } }, Modifier.fillMaxWidth())

            val tick = resumeTick
            val notificationsOk = remember(tick) { Notifications.canPost(context) }
            val exactOk = remember(tick) { vm.canScheduleExact() }
            if (!notificationsOk) ActionRow("Notifications are off", "Reminders cannot be shown.", "Allow") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
            }
            if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActionRow("Exact alarms not allowed", "Reminders may arrive up to 10 minutes late.", "Allow") {
                context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }

            SectionHeader("Data")
            Text("Data is stored only on this device. Save a backup file regularly, e.g. to Drive or Files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkRow("Save backup") { exportLauncher.launch("protocoltracker-${LocalDate.now()}.json") }
            LinkRow("Restore backup") { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            LinkRow("Import CycleTracker export") { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }

            SectionHeader("About")
            Text(
                "ProtocolTracker ${BuildConfig.VERSION_NAME}. Level curves are model estimates from published or commonly cited half-lives. " +
                    "They are not measurements and not medical advice.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (val p = pending) {
        is PendingData.Restore -> ConfirmDialog(
            title = "Replace all data?",
            body = "The backup from ${p.backup.exportedAt.toString().take(10)} has ${p.backup.phases.size} phases, ${p.backup.items.size} plan items and " +
                "${p.backup.logs.size} logged doses. Current data on this device is replaced.",
            confirm = "Replace", onConfirm = vm::confirm, onDismiss = vm::dismiss,
        )
        is PendingData.Import -> ConfirmDialog(
            title = "Import CycleTracker data?",
            body = buildString {
                append("${p.result.phases.size} phases, ${p.result.items.size} plan items, ${p.result.logs.size} logged doses, ${p.result.compounds.size} new compounds. ")
                append("Importing the same file again updates these entries instead of duplicating them.")
                if (p.result.warnings.isNotEmpty()) append("\n\n" + p.result.warnings.joinToString("\n") { "• $it" })
            },
            confirm = "Import", onConfirm = vm::confirm, onDismiss = vm::dismiss,
        )
        null -> Unit
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Text(
        title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
