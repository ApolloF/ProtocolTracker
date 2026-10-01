package com.apollof.protocoltracker.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.Rule
import kotlin.test.assertEquals
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
        // Dev builds group the four kinds of entry behind one Add button.
        val add = if (BuildConfig.DEV_FEATURES) "Add entry" else "Add note"
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription(add).assertExists() }.isSuccess }

        // Settings sits in the same place on every tab.
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Appearance")
        // Dev has no Experimental page (AUD-12); its last row is About.
        val page = if (BuildConfig.DEV_FEATURES) "About" else "Experimental"
        compose.onNodeWithText(page).performScrollTo().performClick()
        waitFor(if (BuildConfig.DEV_FEATURES) "Data stays on this device. The app has no network access and no account." else "Compare mode in Levels")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Appearance")
        assertEquals(!BuildConfig.DEV_FEATURES, compose.onAllNodesWithText("Experimental").fetchSemanticsNodes().isNotEmpty())
    }
}
