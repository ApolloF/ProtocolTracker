package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.AppNav
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

/** Experimental compare mode: switched on in Settings > Experimental, used on the Levels screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class LevelsCompareTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seedPlan() = runBlocking {
        container.repository.seedPresets()
        val start = LocalDate.now().minusDays(10)
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = start))
        container.repository.saveItem(PlanItem("a", null, "preset:anastrozole", Amount(0.5, DoseUnit.MG), schedule = daily, startDate = start))
    }

    /** Invokes the click action directly: touch injection can miss nodes that are moving while the list settles. */
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private fun waitFor(text: String) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun selected(text: String, value: Boolean) = hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, value)

    @Test
    fun compareModeShowsChosenCompoundsOnOneChart() {
        compose.setContent { ProtocolTrackerTheme { AppNav() } }
        waitFor("Today")

        // Switch the feature on in Settings > Experimental (settings persist between tests, so only if it is off).
        compose.onNodeWithContentDescription("Settings").tap()
        waitFor("Experimental")
        compose.onNodeWithText("Experimental").tap()
        waitFor("Compare mode in Levels")
        val toggle = compose.onNode(hasText("Compare mode in Levels"))
        if (toggle.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) != ToggleableState.On) toggle.tap()
        compose.waitUntil(TIMEOUT_MS) {
            compose.onNode(hasText("Compare mode in Levels")).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
        }
        compose.onNodeWithContentDescription("Back").tap()
        waitFor("Appearance")
        compose.onNodeWithContentDescription("Back").tap()

        compose.onAllNodesWithText("Levels")[0].tap()
        waitFor("Compare")
        compose.onNodeWithText("Compare").tap()
        waitFor("100% MEANS")
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("100% = steady state on plan", substring = true).fetchSemanticsNodes().size == 2 }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Shared dose"))
        compose.onNodeWithText("Shared dose").tap()
        waitFor("ANCHOR")
        // Testosterone is the default anchor; anastrozole is compared at its weekly dose.
        waitFor("At Testosterone weekly dose")

        // Back on the plan baseline, where no anchor chips show, leave anastrozole out.
        compose.onNode(hasText("Plan") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).tap()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("ANCHOR").fetchSemanticsNodes().isEmpty() }
        if (compose.onAllNodes(selected("Anastrozole", false)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(selected("Anastrozole", false)).tap() // left out by an earlier run
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(selected("Anastrozole", true)).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNode(selected("Anastrozole", true)).tap()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(selected("Anastrozole", false)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, compose.onAllNodesWithText("100% = steady state on plan", substring = true).fetchSemanticsNodes().size)
        // Restore for other tests.
        compose.onNode(selected("Anastrozole", false)).tap()
    }

    private companion object {
        /** Generous: Robolectric shares the CPU with level calculations still finishing from earlier tests. */
        const val TIMEOUT_MS = 60_000L
    }
}
