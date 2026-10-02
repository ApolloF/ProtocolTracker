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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SIM-4, SIM-5, SIM-16 (dev): the reading sits under the chart ("… · est. … ng/dL"), the panel lists the last dose by
 * its short name and journal entries only, a draw names T and E2, and a legend explains the lab diamonds.
 */
@RunWith(AndroidJUnit4::class)
class LevelsReadingTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        container.settings.update { it.copy(experimentalScrub = true) }
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(
            PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = LocalDate.now().minusDays(10)),
        )
        val testC = container.repository.protocolNow().compounds.getValue("preset:test-cyp")
        container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minusSeconds(86_400))
        val drawn = Instant.now().minusSeconds(3_600)
        container.repository.saveJournal(
            JournalEntry.Bloodwork("b", drawn, listOf(MarkerResult("total_testosterone", 1100.0), MarkerResult("estradiol", 45.0)), createdAt = drawn),
        )
    }

    private fun clickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun texts(part: String) = compose.onAllNodesWithText(part, substring = true).fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }

    private fun readOnTheChart() {
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        val chart = SemanticsMatcher("testosterone chart") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("Testosterone estimated level chart") }
        }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Testosterone") and clickLabel("Open details")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(chart)
        compose.waitUntil(TIMEOUT_MS) {
            val shown = texts("Last dose:").isNotEmpty()
            if (!shown) compose.onNode(chart).performTouchInput { click(center) }
            shown
        }
    }

    @Test
    fun stableKeepsItsPanel() {
        assumeTrue(!BuildConfig.DEV_FEATURES)
        readOnTheChart()
        assertEquals(0, texts(" · est. ").size)
        assertTrue(texts("Last dose:").single().startsWith("Last dose: Test C (testosterone cypionate) 125 mg"))
        assertTrue(texts("LOGGED NEAR ").isNotEmpty() || texts("NEAREST LOG").isNotEmpty())
        assertEquals(0, texts("Lab result").size)
    }

    @Test
    fun theReadingSitsUnderTheChart() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        val chart = SemanticsMatcher("testosterone chart") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("Testosterone estimated level chart") }
        }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Testosterone") and clickLabel("Open details")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(chart)
        assertEquals(1, texts("Lab result").size, "the legend names the diamonds")

        compose.waitUntil(TIMEOUT_MS) {
            val shown = texts("Last dose:").isNotEmpty()
            if (!shown) compose.onNode(chart).performTouchInput { click(center) }
            shown
        }
        assertTrue(texts(" · est. ").single().endsWith(" ng/dL"))
        assertTrue(texts("Last dose:").single().startsWith("Last dose: Test C 125 mg"))
        assertEquals(listOf("Bloodwork · T 1100 ng/dL · E2 45 pg/mL"), texts("Bloodwork ·"))
        // The planned doses near the cursor are on the curve, not in the panel.
        assertEquals(0, texts("(testosterone cypionate)").size)
    }

    private companion object {
        const val TIMEOUT_MS = 120_000L
    }
}
