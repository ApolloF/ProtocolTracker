package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** A pasted note of many lines once grew the sheet until Save and Cancel left the screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class NoteSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aLongNoteKeepsSaveOnScreen() {
        val now = Instant.parse("2026-10-01T20:00:00Z")
        val text = (1..60).joinToString("\n") { "Line $it of a long pasted note" }
        compose.setContent {
            ProtocolTrackerTheme { NoteSheet(now, ZoneId.of("UTC"), onDismiss = {}, onSave = { _, _ -> }, existing = JournalEntry.Note("n", now, text, now)) }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
