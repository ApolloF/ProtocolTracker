package com.apollof.protocoltracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.reminders.Notifications
import com.apollof.protocoltracker.ui.AppNav
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = Settings())
            ProtocolTrackerTheme(settings.theme) {
                NotificationPermissionOnce(settings.doseReminders || settings.dailySummary)
                AppNav()
            }
        }
    }
}

/** Asks for notification permission once per launch when reminders are on; Android stops re-asking after two denials. */
@androidx.compose.runtime.Composable
private fun NotificationPermissionOnce(remindersOn: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(remindersOn) {
        if (remindersOn && !asked && !Notifications.canPost(context)) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
