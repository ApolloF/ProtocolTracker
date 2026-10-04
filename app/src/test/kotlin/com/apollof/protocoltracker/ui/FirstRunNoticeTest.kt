package com.apollof.protocoltracker.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Review 2026-10, F6: the first-run notice says what the app is not, once, and the same words are in Settings › About. */
@RunWith(AndroidJUnit4::class)
class FirstRunNoticeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theNoticeStatesTheBoundaryAndIsAcknowledged() {
        var acknowledged = 0
        compose.setContent { ProtocolTrackerTheme { FirstRunNotice { acknowledged++ } } }
        assertTrue(NOTICE_LINES.any { "not a medical device" in it })
        assertTrue(NOTICE_LINES.any { "does not recommend, prescribe or adjust doses" in it })
        NOTICE_LINES.forEach { assertEquals(1, count(it), it) }
        assertEquals(1, count(ApplicationProvider.getApplicationContext<Context>().getString(R.string.privacy_line)))
        compose.onNodeWithText("I understand").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, acknowledged)
    }
}
