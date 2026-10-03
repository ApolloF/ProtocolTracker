package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.schedule.weekStartOf
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Dev Today: the week strip turns by week and a tapped day shows below it, in place of today. */
@RunWith(AndroidJUnit4::class)
class TodayWeekPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val today = LocalDate.now()

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
        container.settings.update { it.copy(weekBar = WeekBarMode.FULL) }
        // Daily, any time, from three weeks ago: every earlier day has one missed dose.
        container.repository.saveItem(
            PlanItem(
                "daily", null, "preset:test-cyp", Amount(100.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 200.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = today.minusDays(21),
            ),
        )
    }

    private fun show() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodes(dayCell(today)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hasText(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Logs the plan's first dose, so the history starts three weeks ago and later unlogged days count as missed. */
    private fun startHistory() = runBlocking {
        val zone = container.zone()
        val first = today.minusDays(21)
        val protocol = container.repository.protocolNow()
        val occ = occurrences(
            protocol.phases, protocol.items, first.atStartOfDay(zone).toInstant(), first.plusDays(1).atStartOfDay(zone).toInstant(), zone,
            container.repository.anchorsNow(),
        ).single()
        container.repository.logOccurrence(occ, LogStatus.TAKEN)
    }

    @Test
    fun theStripTurnsBackAWeekAndAPastDayIsCheckedAtItsPlannedTime() {
        startHistory()
        show()
        val lastWeek = weekStartOf(today).minusDays(1)
        assertTrue(compose.onAllNodes(dayCell(lastWeek)).fetchSemanticsNodes().isEmpty(), "only this week at first")
        compose.turnWeek("Previous week")
        compose.waitUntil(15_000) { compose.onAllNodes(dayCell(lastWeek)).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(dayCell(lastWeek)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { hasText("Back to today") && hasText("1 MISSED") }
        compose.onNodeWithText("Missed · 100 mg · 0.5 mL").assertExists()

        compose.onNodeWithContentDescription("Mark Test C taken").performScrollTo().performClick()
        compose.waitUntil(15_000) { hasText("1 TAKEN") }
        val log = runBlocking { container.repository.allLogs.first().single { it.occurrenceKey?.contains("@$lastWeek/") == true } }
        assertEquals(LogStatus.TAKEN, log.status)
        assertEquals(log.scheduledAt, log.takenAt)
        assertEquals(lastWeek, log.takenAt.atZone(container.zone()).toLocalDate())
        assertNull(log.site)

        compose.onNodeWithText("Back to today").performClick()
        compose.waitUntil(15_000) { !hasText("Back to today") }
    }

    @Test
    fun aDayBeforeTheFirstLogIsNotLoggedRatherThanMissed() {
        show()
        val lastWeek = weekStartOf(today).minusDays(1)
        compose.turnWeek("Previous week")
        compose.waitUntil(15_000) { compose.onAllNodes(dayCell(lastWeek)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(dayCell(lastWeek)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { hasText("Back to today") && hasText("1 NOT LOGGED") }
        assertTrue(compose.onAllNodesWithText("Missed", substring = true).fetchSemanticsNodes().isEmpty())
        // It can still be checked off, at its planned time.
        compose.onNodeWithContentDescription("Mark Test C taken").performScrollTo().performClick()
        compose.waitUntil(15_000) { hasText("1 TAKEN") }
    }

    @Test
    fun aLaterDayListsItsPlanWithoutChecks() {
        show()
        val tomorrow = today.plusDays(1)
        if (compose.onAllNodes(dayCell(tomorrow)).fetchSemanticsNodes().isEmpty()) compose.turnWeek("Next week")
        compose.waitUntil(15_000) { compose.onAllNodes(dayCell(tomorrow)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(dayCell(tomorrow)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { hasText("Back to today") && hasText("1 PLANNED") }
        assertTrue(compose.onAllNodesWithContentDescription("Mark Test C taken").fetchSemanticsNodes().isEmpty())
    }
}
