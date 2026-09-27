package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.ScreenshotApp
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertEquals

/** Dev, gestures in their own class: tapping the newest point of the BP chart reads the card's 7-day average. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = ScreenshotApp::class)
class BpTrendTapTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun devOnly() = assumeTrue(BuildConfig.DEV_FEATURES)

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun tappingTheNewestPointReadsTheCardsAverage() {
        runBlocking {
            // Four weeks, two readings each; the last 7 days hold 126/82 and 130/84.
            listOf(1L to (126 to 82), 3L to (130 to 84), 8L to (124 to 80), 10L to (128 to 82), 15L to (122 to 78), 22L to (120 to 76))
                .forEachIndexed { i, (days, bp) ->
                    val at = ScreenshotApp.NOW.minus(Duration.ofDays(days))
                    container.repository.saveJournal(JournalEntry.BloodPressure("bp$i", at, bp.first, bp.second, createdAt = at))
                }
        }
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(TIMEOUT_MS) { count("BLOOD PRESSURE") > 0 }
        compose.onNode(hasText("Blood pressure") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Blood pressure") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription("Blood pressure chart", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, count(BP_TREND_CAPTION))

        // The newest week ends now, at the right end of the plot (8 dp inset).
        compose.onNodeWithContentDescription("Blood pressure chart", substring = true)
            .performTouchInput { click(Offset(width - 8.dp.toPx(), centerY)) }
        compose.waitUntil(TIMEOUT_MS) { count("Last 7 days · 128/83 mmHg · 2 readings") > 0 }
        assertEquals(0, count(BP_TREND_CAPTION))
        assertEquals(1, count("128/83"), "the card's 7-day average")
        assertEquals(1, count("7-day average (2)"))
    }

    companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
