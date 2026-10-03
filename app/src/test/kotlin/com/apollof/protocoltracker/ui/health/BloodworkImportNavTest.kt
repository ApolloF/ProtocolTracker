package com.apollof.protocoltracker.ui.health

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.AppNav
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Today › Log › Bloodwork › Import results › paste › Save lands in Journal under the Bloodwork chip, with Undo. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class BloodworkImportNavTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT_MS) { count(text) > 0 }
    private fun bloodwork() = runBlocking { app.container.repository.journal.first() }.filterIsInstance<JournalEntry.Bloodwork>()

    @Test
    fun importFromTheLogMenuLandsInJournalWithUndo() {
        app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("answer", ANSWER))
        compose.setContent { ProtocolTrackerTheme { AppNav() } }
        val logButton = hasClickAction() and hasAnyDescendant(hasText("Log"))
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(logButton, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(logButton, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Bloodwork")
        compose.onNode(hasText("Bloodwork") and hasClickAction()).performClick()
        waitFor("Import results")
        compose.onNodeWithText("Import results").performClick()

        waitFor("Paste answer")
        compose.onNodeWithText("Paste answer").performScrollTo().performClick()
        waitFor("Save 2 results")
        compose.onNodeWithText("Save 2 results").performScrollTo().performClick()

        waitFor("Bloodwork saved")
        compose.onNode(hasText("Bloodwork") and isSelectable()).assert(isSelected())
        assertEquals(1, bloodwork().size)
        compose.onNodeWithText("Undo").performClick()
        waitFor("Nothing logged yet")
        assertTrue(bloodwork().isEmpty(), "Undo deletes the saved draw")
        assertEquals(0, count("Paste answer"), "the import left the back stack")
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L

        val ANSWER = """
            protocoltracker-bloodwork-1
            lab: Saltro
            date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
            total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
            hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
            end
        """.trimIndent()
    }
}
