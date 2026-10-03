package com.apollof.protocoltracker.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings › Times of day › Day starts at: 4:00 by default, and a chip saves its hour. */
@RunWith(AndroidJUnit4::class)
class DayStartSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    private fun selected(label: String) =
        compose.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theDayStartIsChosenWithAChip() {
        val default = LocalTime.of(4, 0)
        assertEquals(default, runBlocking { container.settings.current() }.slotTimes.dayStart)

        compose.setContent { ProtocolTrackerTheme { SettingsPageScreen(SettingsPage.TIMES, onBack = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Day starts at", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { selected(default.format(Formats.time)) }

        val two = LocalTime.of(2, 0).format(Formats.time)
        compose.onNodeWithText(two).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { selected(two) }
        assertEquals(LocalTime.of(2, 0), runBlocking { container.settings.current() }.slotTimes.dayStart)
    }
}
