package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.assertEquals

/**
 * Today's rows and the injection site: a pending injectable shows its compound's suggestion, and a one-tap check, Log
 * all or a missed-dose check records exactly the site its row showed (dev). The Day sheet and stable show and record none.
 */
@RunWith(AndroidJUnit4::class)
// Tall enough that the Missed card and every group are composed.
@Config(qualifiers = "w400dp-h1400dp")
class TodaySiteTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val dev = BuildConfig.DEV_FEATURES

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
    }

    /** 700 mg per week of Test C, daily at any time from [start]: 100 mg (0.5 mL) a day. */
    private fun planTestC(id: String = "test-item", start: LocalDate = LocalDate.now(), end: LocalDate? = null, sortOrder: Int = 0) = runBlocking {
        container.repository.saveItem(
            PlanItem(
                id, null, "preset:test-cyp", Amount(700.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = start, endDate = end, sortOrder = sortOrder,
            ),
        )
    }

    /** An extra dose of the preset [compound] at [site], [daysAgo] days back. */
    private fun extra(site: String, daysAgo: Long, compound: String = "test-cyp") = runBlocking {
        val c = container.repository.compounds.first().first { it.id == "preset:$compound" }
        val amount = Amount(if (compound == "hcg") 500.0 else 100.0, if (compound == "hcg") DoseUnit.IU else DoseUnit.MG)
        container.repository.logUnscheduled(c, amount, c.defaultFormulation, Instant.now().minus(Duration.ofDays(daysAgo)), site = SiteWrite.Set(site))
    }

    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(15_000) { count(text, substring) > 0 }

    /** Every dose line on screen ("100 mg · 0.5 mL · pin 1/7 · R delt"); weekly injections add a pin number that depends on the weekday. */
    private fun doseLines(): List<String> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
        .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        .filter { " mg" in it || " IU" in it }
    private fun pendingTestC() = doseLines().filter { "0.5 mL" in it }
    private fun planLogs(): List<DoseLog> = runBlocking { container.repository.allLogsNow().filter { it.planItemId != null } }

    private fun showToday() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Mark Test C taken").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aCheckRecordsTheSuggestionItsRowShows() {
        assumeTrue(dev)
        planTestC()
        extra("delt_l", daysAgo = 1)
        showToday()
        compose.waitUntil(15_000) { pendingTestC().any { it.endsWith(" · R delt") } }

        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        waitFor("Test C taken · R delt")
        assertEquals("delt_r", planLogs().single().site)
        // The taken row shows the recorded site.
        compose.waitUntil(15_000) { doseLines().any { "taken" in it && it.endsWith(" · R delt") } }
    }

    /** Test C due yesterday (missed) and today; the last site was L delt, two days ago. */
    private fun missedAndToday() {
        planTestC(start = LocalDate.now().minusDays(1))
        extra("delt_l", daysAgo = 2)
        showToday()
        compose.onNodeWithText("MISSED").assertExists()
    }

    @Test
    fun checkingAMissedRowMovesTodaysRowOn() {
        assumeTrue(dev)
        missedAndToday()
        // Both cards suggest R delt: each row records what it shows, and the other moves on.
        compose.waitUntil(15_000) { pendingTestC().count { it.endsWith(" · R delt") } == 2 }
        compose.onAllNodesWithContentDescription("Mark Test C taken")[0].performClick()
        waitFor("Test C logged · R delt")
        assertEquals("delt_r", planLogs().single().site)
        compose.waitUntil(15_000) { pendingTestC().singleOrNull()?.endsWith(" · L delt") == true }
    }

    @Test
    fun aDoseLoggedWithoutASiteRemovesTheSuggestion() {
        assumeTrue(dev)
        missedAndToday()
        compose.waitUntil(15_000) { pendingTestC().count { it.endsWith(" · R delt") } == 2 }
        // As from a notification: yesterday's dose logged with no site ends the chain.
        runBlocking {
            val zone = container.zone()
            val protocol = container.repository.protocolNow()
            val yesterday = LocalDate.now().minusDays(1)
            val key = occurrences(
                protocol.phases, protocol.items, yesterday.atStartOfDay(zone).toInstant(), LocalDate.now().atStartOfDay(zone).toInstant(),
                zone, container.repository.anchorsNow(),
            ).single().key
            container.doseActions.takeKeys(listOf(key))
        }
        compose.waitUntil(15_000) { doseLines().none { "delt" in it } }
        assertEquals(1, pendingTestC().size)
        assertEquals(null, planLogs().single().site)
    }

    @Test
    fun logAllRecordsEachShownSiteNeverOneTwicePerCompound() {
        assumeTrue(dev)
        planTestC("a", sortOrder = 0)
        planTestC("b", sortOrder = 1)
        runBlocking {
            container.repository.saveItem(
                PlanItem("h", null, "preset:hcg", Amount(500.0, DoseUnit.IU), schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = LocalDate.now()),
            )
        }
        extra("delt_l", daysAgo = 1)
        extra("abdomen_l", daysAgo = 1, compound = "hcg")
        showToday()
        // Two Test C rows share one card: only the first shows the suggestion. hCG has its own.
        compose.waitUntil(15_000) { doseLines().any { it.endsWith(" · R delt") } && doseLines().any { it.endsWith(" · R abdomen") } }
        assertEquals(1, pendingTestC().count { "delt" !in it })

        compose.onNodeWithText("Log all 3").performClick()
        waitFor("3 doses taken")
        val sites = planLogs().associate { it.planItemId to it.site }
        assertEquals<Map<String?, String?>>(mapOf("a" to "delt_r", "b" to null, "h" to "abdomen_r"), sites)
    }

    /** Both flavors: backfilling a past day in the Day sheet shows and records no site. */
    @Test
    fun theDaySheetShowsAndRecordsNoSite() {
        val day = LocalDate.now().minusDays(3)
        runBlocking { container.settings.update { it.copy(weekBar = WeekBarMode.FULL) } }
        planTestC(start = day, end = day)
        extra("delt_l", daysAgo = 4)
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.openPastDay(day)
        waitFor("1 MISSED")
        assertEquals(listOf("Missed · 100 mg · 0.5 mL"), doseLines())

        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        waitFor("1 TAKEN")
        assertEquals(null, planLogs().single().site)
    }

    @Test
    fun stableShowsNoSiteAndACheckRecordsNone() {
        assumeFalse(dev)
        planTestC()
        extra("delt_l", daysAgo = 1)
        showToday()
        assertEquals(1, pendingTestC().size)
        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        waitFor("Test C taken")
        assertEquals(null, planLogs().single().site)
        assertEquals(0, count("delt", substring = true))
    }
}
