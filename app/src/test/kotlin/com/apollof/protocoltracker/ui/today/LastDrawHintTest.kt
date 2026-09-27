package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.ui.ScreenshotApp
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

/** Dev: the Log menu's Bloodwork row and the Journal Bloodwork card say how long ago the last draw was (clock 26 Sep 2026 10:00). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = ScreenshotApp::class)
class LastDrawHintTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun devOnly() = assumeTrue(BuildConfig.DEV_FEATURES)

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun seedDraw(id: String, day: Int): Unit = runBlocking {
        val at = LocalDateTime.of(2026, 9, day, 8, 0).atZone(ZoneId.systemDefault()).toInstant()
        container.repository.saveJournal(JournalEntry.Bloodwork(id, at, listOf(MarkerResult("hematocrit", 49.0)), createdAt = at))
    }

    /** Opens Today's Log menu and returns the Bloodwork row's subtitle once the menu is shown. */
    private fun logMenuSubtitle(expected: String): Int {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        val logButton = hasClickAction() and hasAnyDescendant(hasText("Log"))
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(logButton, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(logButton, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { count("Systolic, diastolic and pulse") > 0 }
        compose.waitUntil(TIMEOUT_MS) { count(expected) > 0 }
        return count(expected)
    }

    @Test
    fun drawThreeDaysAgo() {
        seedDraw("b", 23)
        assertEquals(1, logMenuSubtitle("Last draw 3 days ago"))
        assertEquals(0, count("Lab results of a blood draw"))
    }

    @Test
    fun noDraw() {
        assertEquals(1, logMenuSubtitle("Lab results of a blood draw"))
        assertEquals(0, compose.onAllNodesWithText("Last draw", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun onlyAFutureDraw() {
        seedDraw("b", 30)
        assertEquals(1, logMenuSubtitle("Lab results of a blood draw"))
        assertEquals(0, compose.onAllNodesWithText("Last draw", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun journalCardLabel() {
        seedDraw("a", 2)
        seedDraw("b", 23)
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(TIMEOUT_MS) { count("Bloodwork · last draw 3 days ago".uppercase()) > 0 }
        assertEquals(0, count("Bloodwork · latest results".uppercase()))
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
