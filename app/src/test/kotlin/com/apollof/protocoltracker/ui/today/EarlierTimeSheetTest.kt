package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

/** AUD-4: "Earlier…" at 00:30 with 23:30 picked means yesterday. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class EarlierTimeSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun reset() {
        DisplayFormat.current = DisplayFormat()
    }

    private val zone = ZoneId.of("UTC")
    private val now: Instant = LocalDateTime.of(2026, 10, 2, 0, 30).atZone(zone).toInstant()

    @Test
    fun aNoteAt2330PickedJustAfterMidnightIsYesterday() {
        // A 24-hour dial, so hour 23 is one tap.
        DisplayFormat.current = DisplayFormat(use24Hour = true)
        var saved: Instant? = null
        compose.setContent { ProtocolTrackerTheme { NoteSheet(now, zone, onDismiss = {}, onSave = { _, at -> saved = at }) } }
        compose.onNodeWithText("What happened").performTextInput("Slept badly")
        compose.onNodeWithText("Earlier…").performScrollTo().performClick()
        // The dial's numbers carry descriptions ("23 o'clock"), not text.
        val hour23 = hasContentDescription("23", substring = true)
        compose.waitUntil(5_000) { compose.onAllNodes(hour23, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hour23, useUnmergedTree = true)[0].performClick()
        compose.onNodeWithText("OK").performClick()
        val yesterday = LocalDateTime.of(2026, 10, 1, 23, 30).atZone(zone).toInstant()
        val label = "Yesterday ${Formats.time(yesterday, zone)}"
        compose.onNodeWithText(label).assertExists()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(yesterday, saved)
    }
}
