package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.ui.UiMessage
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

/** Journal's Undo snackbar times out (it once stayed for good after an import). */
@RunWith(AndroidJUnit4::class)
class JournalUndoTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    private fun undoShown() = compose.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun undoTimesOut() {
        container.journalFocus.request(UiMessage("2 blood draws saved") {})
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(15_000) { undoShown() }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(15_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse(undoShown())
    }
}
