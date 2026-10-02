package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

/** OTHE-5: with the lastTaken hook (dev), an extra dose starts at the last amount and says when it was taken. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ExtraDoseLastTakenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anExtraDoseStartsAtTheLastAmount() {
        val ai = Presets.byId("preset:anastrozole")!!
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val takenAt = now.minus(Duration.ofDays(1))
        val last = DoseLog(
            "l", null, ai.id, null, null, takenAt, Amount(0.25, DoseUnit.MG), status = LogStatus.TAKEN,
            snapshot = DoseSnapshot(ai.displayName, ai.group, ai.category, ai.baseUnit, ai.pk, ai.defaultFormulation), createdAt = takenAt,
        )
        var saved: Amount? = null
        compose.setContent {
            ProtocolTrackerTheme {
                LogDoseSheet(
                    LogTarget.Unscheduled(ai), listOf(ai), now, zone, onDismiss = {}, onSaveScheduled = { _, _, _, _, _ -> }, onSkip = { _, _ -> },
                    onSaveUnscheduled = { _, amount, _, _, _ -> saved = amount }, lastTaken = { if (it == ai.id) last else null },
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Last taken:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val line = compose.onAllNodesWithText("Last taken:", substring = true).fetchSemanticsNodes().single()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        kotlin.test.assertTrue(line.startsWith("Last taken: 0.25 mg") && line.endsWith("Yesterday ${Formats.time(takenAt, zone)}"), line)
        compose.onNodeWithText("Log 0.25 mg", substring = true).performScrollTo().performClick()
        compose.waitUntil(5_000) { saved != null }
        assertEquals(Amount(0.25, DoseUnit.MG), saved)
    }
}
