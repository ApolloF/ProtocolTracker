package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** SIM-14: Journal's "+" opens Today's Log menu, and a new entry gets the same snackbar with Undo. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class JournalLogMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)

    private fun openMenu() {
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(TIMEOUT_MS) { shown("Nothing logged yet") }
        compose.onNodeWithContentDescription("Add entry").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { shown("Extra dose") }
    }

    @Test
    fun theLogMenuAddsANoteWithUndo() {
        openMenu()
        listOf("Blood pressure", "Note", "Symptoms", "Bloodwork").forEach { assertEquals(true, shown(it), it) }

        click("Note")
        compose.waitUntil(TIMEOUT_MS) { shown("What happened") }
        compose.onNode(hasSetTextAction() and hasText("What happened")).performTextReplacement("Headache")
        click("Save")
        compose.waitUntil(TIMEOUT_MS) { shown("Note saved") && shown("Headache") }
        click("Undo")
        compose.waitUntil(TIMEOUT_MS) { runBlocking { container.repository.journalNow() }.isEmpty() }
    }

    @Test
    fun theLogMenuOpensTheExtraDosePicker() {
        runBlocking { container.repository.seedPresets() }
        openMenu()
        click("Extra dose")
        compose.waitUntil(TIMEOUT_MS) { shown("Choose compound") }
        compose.onNode(hasSetTextAction() and hasText("Search")).performTextReplacement("cypionate")
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Test C", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
