package com.apollof.protocoltracker.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavTest {
    @get:Rule
    val compose = createComposeRule()

    private fun waitFor(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun tabsAndSettingsPagesAreReachable() {
        compose.setContent { ProtocolTrackerTheme { AppNav() } }
        waitFor("Today")
        compose.onAllNodesWithText("Levels")[0].performClick()
        waitFor("Estimated".uppercase())
        compose.onAllNodesWithText("Journal")[0].performClick()
        // The four kinds of entry sit behind one Add button.
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Add entry").assertExists() }.isSuccess }

        // Settings sits in the same place on every tab.
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Appearance")
        // There is no Experimental page (AUD-12); the last row is About.
        compose.onNodeWithText("About").performScrollTo().performClick()
        waitFor("Data stays on this device. The app has no network access and no account.")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Appearance")
        assertTrue(compose.onAllNodesWithText("Experimental").fetchSemanticsNodes().isEmpty())
    }

    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tabCount() = compose.onAllNodes(tab).fetchSemanticsNodes().size

    @Test
    fun theBarComesAndGoesWithTheTabScreens() {
        compose.setContent { ProtocolTrackerTheme { AppNav() } }
        waitFor("Today")
        compose.waitUntil(15_000) { tabCount() == 4 }

        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Appearance")
        compose.waitUntil(15_000) { tabCount() == 0 }

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(15_000) { tabCount() == 4 }
        compose.onAllNodes(tab)[0].assertIsSelected()
    }
}
