package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
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

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
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

    private fun clickLabel(label: String) =
        SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun waitFor(text: String) = runCatching {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }.onFailure { throw AssertionError(compose.onRoot().printToString(maxDepth = 40).lines().filter { "Text =" in it }.joinToString(" | "), it) }

    @Test
    fun jumpBarScrollsToChartsAndTitleOpensDetail() {
        var opened: String? = null
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = { opened = it }) } }
        waitFor("Anastrozole")

        // Jump to Anastrozole: its chart title becomes visible.
        compose.onNode(hasText("Anastrozole") and clickLabel("Go to chart")).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Anastrozole") and clickLabel("Open details")).assertIsDisplayed()

        // Paused compound: a chip that opens its chart.
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Oxandrolone"))
        compose.onNodeWithText("Oxandrolone").assertIsNotSelected().performClick()
        // Its chart now exists.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Oxandrolone") and clickLabel("Open details")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasText("Testosterone") and clickLabel("Go to chart")).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Testosterone") and clickLabel("Open details")).performClick()
        compose.waitUntil(5_000) { opened != null }
        assertEquals("Testosterone", opened)
    }
}
