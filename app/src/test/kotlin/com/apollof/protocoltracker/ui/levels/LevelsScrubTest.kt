package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
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
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Experimental scrubbing on Levels; its own class so it runs in a fresh JVM (see forkEvery in app/build.gradle.kts). */
@RunWith(AndroidJUnit4::class)
class LevelsScrubTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(
            PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = LocalDate.now().minusDays(10)),
        )
        container.settings.update { it.copy(experimentalScrub = true, labUnits = LabUnits.SI) }
        val testC = container.repository.protocolNow().compounds.getValue("preset:test-cyp")
        container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minusSeconds(86_400))
    }

    private fun clickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    @Test
    fun scrubModeShowsLogsNearTheCursorAndSiUnits() {
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
        /** Cold JVM plus level calculations on a slow CI runner. */
        const val TIMEOUT_MS = 120_000L
    }
}
