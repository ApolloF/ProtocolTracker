package com.apollof.protocoltracker.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import kotlin.test.assertTrue

/** DEV-2: with the 12-hour clock chosen, the time dial shows AM and PM. */
@RunWith(AndroidJUnit4::class)
class TimePickerClockTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun reset() {
        DisplayFormat.current = DisplayFormat()
    }

    @Test
    fun theDialFollowsTheClockSetting() {
        DisplayFormat.current = DisplayFormat(use24Hour = false)
        compose.setContent { ProtocolTrackerTheme { TimePickDialog(LocalTime.of(23, 0), onDismiss = {}, onConfirm = {}) } }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("PM").fetchSemanticsNodes().isNotEmpty())
    }
}
