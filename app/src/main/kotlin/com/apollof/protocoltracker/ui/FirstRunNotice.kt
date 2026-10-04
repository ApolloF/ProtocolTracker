package com.apollof.protocoltracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/** What the app is and is not, shown once on first start and in Settings › About (review 2026-10, F6). */
internal val NOTICE_LINES = listOf(
    "ProtocolTracker is a personal log. It does not recommend, prescribe or adjust doses, and it does not diagnose or interpret results.",
    "Level curves are model estimates, not measurements.",
    "It is not a medical device and not medical advice. Talk to a doctor about medicines and lab results.",
)

/** The privacy summary under the notice; the full text is PRIVACY.md in the repository. */
internal const val PRIVACY_LINE =
    "Your data stays on this device: the app has no network access and no account. Backups and reports you export are unencrypted files; keep them somewhere private."

/** The first-run notice: read once, then [onAcknowledge] stores that it was seen and the app opens. */
@Composable
fun FirstRunNotice(onAcknowledge: () -> Unit) {
    val c = Tracker.colors
    Column(
        Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text("Before you start", style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.semantics { heading() })
            NOTICE_LINES.forEach { Text(it, style = TrackerType.body, color = c.body2) }
            Text(PRIVACY_LINE, style = TrackerType.bodySmall, color = c.muted)
            Text("You can read this again in Settings › About.", style = TrackerType.bodySmall, color = c.muted)
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton("I understand", onAcknowledge, Modifier.fillMaxWidth())
        }
    }
}
