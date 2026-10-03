package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Back to now" shows only when there is something to go back from. */
@RunWith(AndroidJUnit4::class)
class LevelsBackToNowTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        container.repository.saveItem(
            PlanItem(
                "t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING))), startDate = LocalDate.now().minusDays(10),
            ),
        )
    }

    private val chart = SemanticsMatcher("testosterone chart") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("Testosterone estimated level chart") }
    }

    private fun backToNowShown() = compose.onAllNodesWithContentDescription("Back to now").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theButtonShowsOnlyAwayFromNow() {
        compose.setContent { ProtocolTrackerTheme { LevelDetailScreen("Testosterone", onBack = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(chart).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertFalse(backToNowShown(), "nothing to go back to at the start")

        // Dev reads a value on a tap (the cursor); stable pans with a drag. Either way the button appears.
        compose.waitUntil(TIMEOUT_MS) {
            if (!backToNowShown()) compose.onNode(chart).performTouchInput { if (BuildConfig.DEV_FEATURES) click(center) else swipeLeft() }
            backToNowShown()
        }
        compose.onNodeWithContentDescription("Back to now").performClick()
        compose.waitUntil(TIMEOUT_MS) { !backToNowShown() }
        assertTrue(compose.onAllNodes(chart).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theDefaultWindowIsAtNow() {
        val default = LevelsState()
        assertTrue(default.atDefault)
        assertFalse(default.copy(window = default.window.copy(centerOffsetMs = 3_600_000)).atDefault)
        assertFalse(default.copy(window = default.window.copy(zoom = 2.0)).atDefault)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
