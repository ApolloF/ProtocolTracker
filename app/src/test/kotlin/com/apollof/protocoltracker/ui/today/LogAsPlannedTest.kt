package com.apollof.protocoltracker.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import com.apollof.protocoltracker.waitForData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 250 mg/week in daily doses is 35.714285… mg, which the dose field shows as 35.7143. Logging it unchanged from the
 * sheet, and editing only the note in Journal, store the plan's own amount, so nothing reads as adjusted.
 */
@RunWith(AndroidJUnit4::class)
class LogAsPlannedTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
        container.repository.saveItem(
            PlanItem(
                "test-item", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 250.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = LocalDate.now(),
            ),
        )
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
    private fun waitFor(text: String) = compose.waitUntil(15_000) { count(text) > 0 }
    private fun log() = runBlocking { container.repository.allLogsNow().single() }

    @Test
    fun anUnchangedDoseSavesAsPlannedAndANoteEditKeepsIt() {
        var journal by mutableStateOf(false)
        compose.setContent {
            ProtocolTrackerTheme { if (journal) JournalScreen(onOpenSettings = {}) else TodayScreen(onOpenSettings = {}, onOpenPlan = {}) }
        }
        waitFor("35.71 mg · 0.14 mL")
        compose.onNodeWithText("35.71 mg · 0.14 mL", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Plan: 35.71 mg · 0.14 mL")

        // One rounding for the plan and the button, and no "+0 mg vs plan".
        assertEquals(0, count("vs plan"))
        compose.onNodeWithText("Log 35.71 mg").assertExists()
        compose.onNodeWithText("Choose site").performScrollTo().performClick()
        compose.onNode(hasContentDescription(InjectionSites.longLabel("vg_l"))).performScrollTo().performClick()
        compose.onNodeWithText("Log 35.71 mg").performScrollTo().performClick()
        compose.waitForData(15_000) { container.repository.allLogsNow().isNotEmpty() }

        val logged = log()
        assertEquals(logged.plannedAmount, logged.amount)
        assertFalse(logged.adjusted)
        assertEquals("vg_l", logged.site)
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Undo Test C").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, count("(plan"))

        journal = true
        waitFor("35.71 mg · 0.14 mL")
        assertEquals(0, count("(plan"))
        compose.onNode(hasClickAction() and hasText("35.71 mg", substring = true)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Delete entry")
        compose.onNode(hasSetTextAction() and hasText("Note", substring = true)).performTextReplacement("Left side")
        compose.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Left side")
        assertEquals(logged.amount, log().amount)
        assertEquals(0, count("(plan"))
    }

    @Test
    fun aChangedDoseStillSavesAsTyped() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        waitFor("35.71 mg · 0.14 mL")
        compose.onNodeWithText("35.71 mg · 0.14 mL", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Plan: 35.71 mg · 0.14 mL")
        compose.onNode(hasSetTextAction() and hasText("Dose", substring = true)).performTextReplacement("")
        compose.onNode(hasSetTextAction() and hasText("Dose", substring = true)).performTextInput("40")
        waitFor("+4.286 mg")
        compose.onNodeWithText("Log 40 mg").performScrollTo().performClick()
        compose.waitForData(15_000) { container.repository.allLogsNow().isNotEmpty() }
        assertEquals(Amount(40.0, DoseUnit.MG), log().amount)
    }
}
