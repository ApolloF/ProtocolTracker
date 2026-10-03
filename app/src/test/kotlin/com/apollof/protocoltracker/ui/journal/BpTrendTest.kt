package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.ScreenshotApp
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZoneId
import kotlin.test.assertEquals

/** The Blood pressure card shows the 7-day-average chart under the Blood pressure chip, with 2 or more weeks only. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = ScreenshotApp::class)
class BpTrendTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
    private fun charts() = compose.onAllNodesWithContentDescription("Blood pressure chart", substring = true).fetchSemanticsNodes()

    /** Readings [daysAgo] before the pinned clock (26 Sep 2026 10:00). */
    private fun readings(vararg daysAgo: Long): Unit = runBlocking {
        daysAgo.forEachIndexed { i, d ->
            val at = ScreenshotApp.NOW.minus(Duration.ofDays(d))
            container.repository.saveJournal(JournalEntry.BloodPressure("bp$i", at, 120 + (d % 9).toInt(), 78 + (d % 5).toInt(), createdAt = at))
        }
    }

    /** Opens the Journal and waits for the Blood pressure card. */
    private fun show() {
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(TIMEOUT_MS) { count("BLOOD PRESSURE") > 0 }
    }

    /** Selects the Blood pressure chip and waits until the Journal state has applied it. */
    private fun selectChip() {
        compose.onNode(hasText("Blood pressure") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Blood pressure") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun twelveWeeksShowTheChartUnderTheChipOnly() {
        readings(*(0L..11L).flatMap { w -> listOf(7 * w + 1, 7 * w + 3) }.toLongArray())
        show()
        compose.waitUntil(TIMEOUT_MS) { count("7-day average (2)") > 0 }
        assertEquals(0, charts().size, "no chart on All")
        assertEquals(0, count(BP_TREND_CAPTION))

        selectChip()
        compose.waitUntil(TIMEOUT_MS) { charts().isNotEmpty() }
        assertEquals(1, count(BP_TREND_CAPTION))
        // The newest point is the card's 7-day average: days 1 and 3 give 121/79 and 123/81.
        assertEquals(1, count("122/80"))
        assertEquals(1, count("7-day average (2)"))
        val zone = ZoneId.systemDefault()
        val from = ScreenshotApp.NOW.minus(Duration.ofDays(77)).atZone(zone).format(Formats.dayMonth)
        val to = ScreenshotApp.NOW.atZone(zone).format(Formats.date)
        assertEquals(
            "Blood pressure chart, 7-day averages, 12 points from $from to $to, latest 122/80 mmHg",
            charts().single().config[SemanticsProperties.ContentDescription].single(),
        )
    }

    @Test
    fun oneWeekAddsNothing() {
        readings(1, 3, 5)
        show()
        selectChip()
        assertEquals(1, count("7-day average (3)"))
        assertEquals(0, charts().size)
        assertEquals(0, count(BP_TREND_CAPTION))
    }

    @Test
    fun readingsOlderThan26WeeksAddNothing() {
        readings(190, 200, 210)
        show()
        selectChip()
        assertEquals(1, count("Latest", substring = true))
        assertEquals(0, charts().size)
        assertEquals(0, count(BP_TREND_CAPTION))
    }

    companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
