package com.apollof.protocoltracker.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.TimeFormat
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UnitsSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @After
    fun reset() {
        DisplayFormat.current = DisplayFormat()
    }

    private fun choose(label: String) {
        compose.onNodeWithText(label).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        // Selected once the saved setting has come back from the store.
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun choicesAreSavedAndApplyToFormatting() {
        compose.setContent { ProtocolTrackerTheme { SettingsPageScreen(SettingsPage.UNITS, onBack = {}) } }
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("12-hour").assertExists() }.isSuccess }
        choose("12-hour")
        choose("SI")
        choose("Syringe units")
        val s = runBlocking { container.settings.current() }
        assertEquals(TimeFormat.H12, s.timeFormat)
        assertEquals(LabUnits.SI, s.labUnits)
        assertTrue(s.syringeUnits)
        assertFalse(DisplayFormat.current.use24Hour)
        assertTrue(DisplayFormat.current.syringeUnits)
    }
}
