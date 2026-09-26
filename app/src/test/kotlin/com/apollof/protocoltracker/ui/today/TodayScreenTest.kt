package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seedPlan() = runBlocking {
        container.repository.seedPresets()
        // 700 mg per week, daily at any time: one 100 mg (0.5 mL) dose due today whatever the clock says.
        container.repository.saveItem(
            PlanItem(
                "test-item", null, "preset:test-cyp", Amount(700.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = LocalDate.now(),
            ),
        )
    }

    private fun showToday() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Mark Test C taken").assertExists() }.isSuccess }
    }

    @Test
    fun checkLogsThePlannedDoseAndUndoRemovesIt() {
        showToday()
        compose.onNodeWithText("100 mg · 0.5 mL", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ANY TIME").assertExists()

        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        compose.waitUntil(15_000) { runBlocking { container.repository.allLogs.first().isNotEmpty() } }
        val log = runBlocking { container.repository.allLogs.first().single() }
        assertEquals(LogStatus.TAKEN, log.status)
        assertEquals(Amount(100.0, DoseUnit.MG), log.amount)
        assertEquals("test-item", log.planItemId)

        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Undo").assertExists() }.isSuccess }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { runBlocking { container.repository.allLogs.first().isEmpty() } }
        assertTrue(runBlocking { container.repository.allLogs.first().isEmpty() })
    }

    @Test
    fun rowOpensSheetWhereTheDoseCanBeAdjustedOnce() {
        showToday()
        compose.onNodeWithText("100 mg · 0.5 mL", substring = true).performClick()
        runCatching { compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Plan: 100 mg · 0.5 mL").assertExists() }.isSuccess } }
            .onFailure { throw AssertionError(dump(), it) }

        compose.onNodeWithText("+20").performClick()
        compose.onNodeWithText("vs plan. Only this dose changes.", substring = true).assertExists()
        compose.onNodeWithText("Log 120 mg").performScrollTo().performClick()

        compose.waitUntil(15_000) { runBlocking { container.repository.allLogs.first().isNotEmpty() } }
        val log = runBlocking { container.repository.allLogs.first().single() }
        assertEquals(Amount(120.0, DoseUnit.MG), log.amount)
        assertEquals(Amount(100.0, DoseUnit.MG), log.plannedAmount)
        // The plan itself is untouched.
        assertEquals(Amount(700.0, DoseUnit.MG), runBlocking { container.repository.items.first().single().dose })
        // The row now shows as checked, with the adjusted amount.
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Undo Test C").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Mark Test C taken").assertDoesNotExist()
        compose.onNodeWithText("120 mg (plan 100 mg)", substring = true).assertExists()
    }

    /** Every 3 days, first dose yesterday: yesterday's dose is missed and nothing is due today. */
    private fun planMissedDose() = runBlocking {
        container.repository.saveItem(
            PlanItem(
                "test-item", null, "preset:test-cyp", Amount(100.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 200.0),
                Schedule.EveryNDays(3, LocalDate.now().minusDays(1), listOf(Timing.Slot(DaySlot.ANY_TIME))),
            ),
        )
    }

    @Test
    fun catchingUpAMissedDoseChecksItAndRestartsTheInterval() {
        planMissedDose()
        showToday()
        compose.onNodeWithText("MISSED").assertExists()

        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Undo Test C").assertExists() }.isSuccess }
        compose.onNodeWithText("LOGGED LATE").assertExists()

        val log = runBlocking { container.repository.allLogs.first().single() }
        assertEquals(LocalDate.now(), log.takenAt.atZone(container.zone()).toLocalDate())
        // The next dose is counted from today, not from yesterday.
        val zone = container.zone()
        val next = runBlocking {
            val protocol = container.repository.protocolNow()
            occurrences(
                protocol.phases, protocol.items, LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant(),
                LocalDate.now().plusDays(10).atStartOfDay(zone).toInstant(), zone, container.repository.anchorsNow(),
            ).first().localDate
        }
        assertEquals(LocalDate.now().plusDays(3), next)
    }

    @Test
    fun missedDoseLoggedFromTheSheetStaysVisibleAsChecked() {
        planMissedDose()
        showToday()
        compose.onNodeWithText("100 mg · 0.5 mL", substring = true).performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Log 100 mg").assertExists() }.isSuccess }
        compose.onNodeWithText("Log 100 mg").performScrollTo().performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Undo Test C").assertExists() }.isSuccess }
        compose.onNodeWithText("LOGGED LATE").assertExists()
    }

    @Test
    fun logButtonOffersExtraDoseBloodPressureAndNote() {
        showToday()
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Extra dose").assertExists() }.isSuccess }
        compose.onNodeWithText("Blood pressure").assertExists()
        compose.onNodeWithText("Note").assertExists()
        compose.onNodeWithText("Extra dose").performClick()
        // The compound picker opens for an unscheduled dose.
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Test C", substring = true).assertExists() }.isSuccess }
    }

    @Test
    fun tappingADayOpensItAndCheckingBackfillsAtThePlannedTime() {
        val day = LocalDate.now().minusDays(3)
        runBlocking {
            container.settings.update { it.copy(weekBar = WeekBarMode.FULL) }
            // One dose only, three days ago: older than the missed-dose lookback, so it appears nowhere on Today.
            container.repository.saveItem(
                PlanItem(
                    "test-item", null, "preset:test-cyp", Amount(100.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 200.0),
                    Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = day, endDate = day,
                ),
            )
        }
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        val today = LocalDate.now()
        val todayCell = SemanticsMatcher("today's cell") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == "Open day" &&
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any {
                    it.startsWith("${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${today.dayOfMonth}:")
                }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(todayCell).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(todayCell).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Previous day").assertExists() }.isSuccess }
        repeat(3) { compose.onNodeWithContentDescription("Previous day").performClick(); compose.waitForIdle() }
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("0 OF 1 DONE").assertExists() }.isSuccess }

        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("1 OF 1 DONE").assertExists() }.isSuccess }
        val log = runBlocking { container.repository.allLogs.first().single() }
        assertEquals(LogStatus.TAKEN, log.status)
        assertEquals(day, log.takenAt.atZone(container.zone()).toLocalDate())
        assertEquals(log.scheduledAt, log.takenAt)
    }

    @Test
    fun devLogMenuOffersSymptomsAndBloodwork() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        showToday()
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Symptoms").assertExists() }.isSuccess }
        compose.onNodeWithText("Bloodwork").assertExists()
        compose.onNodeWithText("Symptoms").performClick()
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Night sweats").assertExists() }.isSuccess }
        compose.onNodeWithText("Night sweats").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(15_000) { runBlocking { container.repository.journalNow().isNotEmpty() } }
        val entry = runBlocking { container.repository.journalNow().single() }
        assertEquals(listOf("night_sweats"), (entry as JournalEntry.Symptoms).symptoms)
    }

    private fun dump(): String = buildString {
        val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot())
        for (i in 0 until roots.fetchSemanticsNodes().size) append(roots[i].printToString(maxDepth = 30)).append(NL + "====" + NL)
    }

    private companion object {
        val NL: String = System.lineSeparator()
    }
}
