package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
import com.apollof.protocoltracker.domain.pk.LevelAdjustments
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals

/** The Adjust level card saves a per-group percentage, marks the estimate and resets it; the plan stays as it was. */
@RunWith(AndroidJUnit4::class)
class LevelAdjustTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seedPlan() = runBlocking {
        container.repository.seedPresets()
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(
            PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = LocalDate.now().minusDays(10)),
        )
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun adjustments() = runBlocking { container.settings.settings.first().levelAdjustments }

    /**
     * Waits until the card shows [percent]. The card reads the value back from the settings store, so this also waits
     * for the write; the next tap starts from the shown value. Polling the store instead stalled the screen under
     * Robolectric (the write and the update need the main looper, which only a UI query runs), so the store is read
     * once, at the end.
     */
    private fun awaitShown(percent: Int) = runCatching { waitFor(LevelAdjustments.label(percent) ?: "Off") }.onFailure {
        val texts = compose.onRoot().printToString(maxDepth = 40).lines().filter { "Text =" in it }.joinToString(" | ")
        throw AssertionError("expected $percent; shown: $texts", it)
    }

    private fun tapDescribed(description: String) {
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun raiseLowerAndReset() {
        compose.setContent { ProtocolTrackerTheme { LevelDetailScreen("Testosterone", onBack = {}) } }
        // The card sits below the chart, outside the first screen: scroll it in once the chart has loaded.
        waitFor("Estimated", substring = true)
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Adjust level"))
        waitFor("Off")

        tapDescribed("Raise by 5%")
        awaitShown(5)
        tapDescribed("Raise by 5%")
        awaitShown(10)
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("adjusted +10%", substring = true))

        tapDescribed("Lower by 5%")
        awaitShown(5)

        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Reset to estimate"))
        compose.onNodeWithText("Reset to estimate").performSemanticsAction(SemanticsActions.OnClick)
        awaitShown(0)
        assertEquals(LevelAdjustments.NONE, adjustments())

        val item = runBlocking { container.repository.protocolNow().items.single() }
        assertEquals(Amount(250.0, DoseUnit.MG), item.dose)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
