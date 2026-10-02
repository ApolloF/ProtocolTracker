package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

/** POL-10 (dev): an edit sheet's "Delete entry" removes the entry and Undo brings it back unchanged. */
@RunWith(AndroidJUnit4::class)
class JournalDeleteEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun stored() = runBlocking { container.repository.journalNow() }

    @Test
    fun deleteEntryThenUndo() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val at = Instant.now().minusSeconds(3_600).truncatedTo(ChronoUnit.MILLIS) // stored in milliseconds
        val note = JournalEntry.Note("n1", at, "Slept badly", at)
        runBlocking { container.repository.saveJournal(note) }
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(TIMEOUT_MS) { shown("Slept badly") }

        compose.onNodeWithText("Slept badly").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { shown("Delete entry") }
        compose.onNodeWithText("Delete entry").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { stored().isEmpty() && shown("Undo") }

        compose.onNodeWithText("Undo").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { stored().isNotEmpty() }
        assertEquals(listOf<JournalEntry>(note), stored())
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
