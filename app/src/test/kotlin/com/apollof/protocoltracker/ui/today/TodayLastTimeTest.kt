package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
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
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A dose due today says when the one before it was missed or skipped, and when the compound was last taken. */
@RunWith(AndroidJUnit4::class)
class TodayLastTimeTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val today = LocalDate.now()

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
        // Weekly, any time: two weeks ago, a week ago and today.
        container.repository.saveItem(
            PlanItem(
                "weekly", null, "preset:test-cyp", Amount(100.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 200.0),
                Schedule.EveryNDays(7, today.minusDays(14), listOf(Timing.Slot(DaySlot.ANY_TIME)), fromLastDose = false),
            ),
        )
    }

    private fun occurrenceOn(date: LocalDate): Occurrence = runBlocking {
        val zone = container.zone()
        val protocol = container.repository.protocolNow()
        occurrences(
            protocol.phases, protocol.items, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant(), zone,
            container.repository.anchorsNow(),
        ).single()
    }

    private fun showToday() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Mark Test C taken").assertExists() }.isSuccess }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aMissedDoseLastWeekIsNamedWithTheLastTakenOne() {
        val twoWeeksAgo = occurrenceOn(today.minusDays(14))
        runBlocking { container.repository.logOccurrence(twoWeeksAgo, LogStatus.TAKEN) }
        showToday()
        val zone = container.zone()
        waitForText("Missed last time")
        compose.onNodeWithText(
            "Missed last time (${today.minusDays(7).format(Formats.dayShort)}) · last taken " +
                "${today.minusDays(14).format(Formats.dayShort)}, ${Formats.time(twoWeeksAgo.at, zone)}",
        ).assertExists()
    }

    @Test
    fun aSkippedDoseLastWeekSaysSkipped() {
        runBlocking {
            container.repository.logOccurrence(occurrenceOn(today.minusDays(14)), LogStatus.TAKEN)
            container.repository.logOccurrence(occurrenceOn(today.minusDays(7)), LogStatus.SKIPPED)
        }
        showToday()
        waitForText("Skipped last time")
    }

    @Test
    fun noNoteForADoseBeforeTheFirstLog() {
        // Nothing logged yet: last week's dose predates the history in the app, so it is not called missed.
        showToday()
        compose.waitForIdle()
        assert(compose.onAllNodesWithText("last time", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun noNoteWhenTheLastDoseWasTaken() {
        runBlocking {
            container.repository.logOccurrence(occurrenceOn(today.minusDays(14)), LogStatus.TAKEN)
            container.repository.logOccurrence(occurrenceOn(today.minusDays(7)), LogStatus.TAKEN)
        }
        showToday()
        compose.waitForIdle()
        assert(compose.onAllNodesWithText("last time", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
