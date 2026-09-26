package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.apollof.protocoltracker.domain.pk.LabUnits
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
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LevelsScreenTest {
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
        // Paused: listed under "Not in use now", no chart until opened.
        container.repository.saveItem(PlanItem("v", null, "preset:oxandrolone", Amount(20.0, DoseUnit.MG), schedule = daily, startDate = start, enabled = false))
    }

    /** Invokes the click action directly: touch injection can miss nodes that are moving while the list settles. */
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private fun clickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun waitFor(text: String) = runCatching {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }.onFailure { throw AssertionError(compose.onRoot().printToString(maxDepth = 40).lines().filter { "Text =" in it }.joinToString(" | "), it) }

    @Test
    fun jumpBarScrollsToChartsAndTitleOpensDetail() {
        var opened: String? = null
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = { opened = it }) } }
        // The first chart is drawn, so the list is ready to scroll.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Testosterone") and clickLabel("Open details")).fetchSemanticsNodes().isNotEmpty() }

        // Jump to Anastrozole: its chart title comes into the top part of the screen, just under the jump bar.
        compose.waitUntil(TIMEOUT_MS) {
            val shown = compose.onAllNodes(hasText("Anastrozole") and clickLabel("Open details")).fetchSemanticsNodes()
                .any { it.boundsInRoot.top > 0f && it.boundsInRoot.top < 400f }
            if (!shown) compose.onNode(hasText("Anastrozole") and clickLabel("Go to chart")).tap()
            shown
        }

        // Paused compound: a chip that opens its chart.
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Oxandrolone"))
        compose.onNodeWithText("Oxandrolone").assertIsNotSelected().tap()
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText("Oxandrolone") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNode(hasText("Testosterone") and clickLabel("Go to chart")).tap()
        compose.waitForIdle()
        compose.onNode(hasText("Testosterone") and clickLabel("Open details")).tap()
        compose.waitUntil(TIMEOUT_MS) { opened != null }
        assertEquals("Testosterone", opened)
    }

    @Test
    fun scrubModeShowsLogsNearTheCursorAndSiUnits() {
        runBlocking {
            container.settings.update { it.copy(experimentalScrub = true, labUnits = LabUnits.SI) }
            val testC = container.repository.protocolNow().compounds.getValue("preset:test-cyp")
            container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, java.time.Instant.now().minusSeconds(86_400))
        }
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        val chart = SemanticsMatcher("testosterone chart") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("Testosterone estimated level chart") }
        }
        // The chart card is drawn (same readiness check as the jump bar test), then brought fully into view.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Testosterone") and clickLabel("Open details")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(chart)
        // Testosterone is shown in nmol/L.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("est. nmol/L").fetchSemanticsNodes().isNotEmpty() }
        // A tap can miss while the list is still settling; tap again until the panel shows.
        compose.waitUntil(TIMEOUT_MS) {
            val shown = compose.onAllNodesWithText("Last dose:", substring = true).fetchSemanticsNodes().isNotEmpty()
            if (!shown) compose.onNode(chart).performTouchInput { click(center) }
            shown
        }
        compose.onNodeWithText("Last dose: Test C", substring = true).assertExists()
    }

    private companion object {
        /** Generous: Robolectric shares the CPU with level calculations still finishing from earlier tests. */
        const val TIMEOUT_MS = 60_000L
    }
}
