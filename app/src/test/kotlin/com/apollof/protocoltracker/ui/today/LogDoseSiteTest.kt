package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SiteRotation
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import com.apollof.protocoltracker.waitForData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** The Site row of the Log dose sheet: the sheet with its site hook, and Today's wiring. */
@RunWith(AndroidJUnit4::class)
class LogDoseSiteTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private var saved: SiteWrite? = null

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
    }

    private fun compound(id: String): Compound = runBlocking { container.repository.compounds.first().first { it.id == "preset:$id" } }

    /** An extra Test C dose at [site], [daysAgo] days back. */
    private fun extra(site: String?, daysAgo: Long) = runBlocking {
        val testC = compound("test-cyp")
        container.repository.logUnscheduled(testC, Amount(100.0, DoseUnit.MG), testC.defaultFormulation, now.minus(Duration.ofDays(daysAgo)), site = SiteWrite.Set(site))
    }

    /** The extra-dose form for [compound], with the site hook reading the stored logs unless [hook] is false. */
    private fun showExtra(compound: Compound, hook: Boolean = true) {
        val logs = runBlocking { container.repository.allLogsNow() }
        compose.setContent {
            ProtocolTrackerTheme {
                LogDoseSheet(
                    target = LogTarget.Unscheduled(compound), compounds = listOf(compound), now = now, zone = zone,
                    onDismiss = {}, onSaveScheduled = { _, _, _, _, _ -> }, onSkip = { _, _ -> },
                    onSaveUnscheduled = { _, _, _, _, site -> saved = site },
                    sites = if (hook) { id, editing -> SiteRotation.forDose(logs, id, editing) } else null,
                )
            }
        }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("LOG EXTRA DOSE").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun logAndGetSite(): SiteWrite? {
        compose.onNode(hasSetTextAction() and hasText("Dose", substring = true)).performTextInput("100")
        compose.onNodeWithText("Log 100 mg").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MS) { saved != null }
        return saved
    }

    private fun site(key: String) = compose.onNode(hasContentDescription(InjectionSites.longLabel(key)))
    private fun isSelected(key: String) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        .matches(compose.onNodeWithContentDescription(InjectionSites.longLabel(key)).fetchSemanticsNode())
    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theSuggestionIsPreselectedAndLogged() {
        extra("delt_l", daysAgo = 1)
        showExtra(compound("test-cyp"))
        assertEquals(1, count("Last: L delt · Yesterday"))
        assertEquals(true, isSelected("delt_r"))
        assertEquals(false, isSelected("delt_l"))
        site("delt_r").assertHeightIsAtLeast(48.dp)
        // The expand chip names what it shows; the dose "+" button is "More".
        assertEquals(1, count("All sites"))
        assertEquals(SiteWrite.Set("delt_r"), logAndGetSite())
    }

    @Test
    fun anotherChipIsLogged() {
        extra("delt_l", daysAgo = 1)
        showExtra(compound("test-cyp"))
        site("delt_l").performScrollTo().performClick()
        assertEquals(false, isSelected("delt_r"))
        assertEquals(SiteWrite.Set("delt_l"), logAndGetSite())
    }

    @Test
    fun tappingTheSelectedChipClearsIt() {
        extra("delt_l", daysAgo = 1)
        showExtra(compound("test-cyp"))
        site("delt_r").performScrollTo().performClick()
        assertEquals(false, isSelected("delt_r"))
        assertEquals(SiteWrite.Set(null), logAndGetSite())
    }

    @Test
    fun untrackedOffersChooseSiteThenEverySite() {
        showExtra(compound("test-cyp"))
        assertEquals(0, compose.onAllNodesWithText("Last:", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription("Left deltoid").fetchSemanticsNodes().size)
        compose.onNodeWithText("Choose site").performScrollTo().performClick()
        InjectionSites.all.forEach { site(it.key).assertExists() }
        assertEquals(0, count("All sites"))
        site("vg_l").performScrollTo().performClick()
        assertEquals(SiteWrite.Set("vg_l"), logAndGetSite())
    }

    @Test
    fun anOralCompoundHasNoRow() {
        showExtra(compound("oxandrolone"))
        assertEquals(0, count("SITE"))
        assertEquals(0, count("Choose site"))
        assertEquals(SiteWrite.Keep, logAndGetSite())
    }

    @Test
    fun withoutTheHookThereIsNoRowAndTheSiteIsKept() {
        extra("delt_l", daysAgo = 1)
        showExtra(compound("test-cyp"), hook = false)
        assertEquals(0, count("SITE"))
        assertEquals(0, compose.onAllNodesWithContentDescription("Right deltoid").fetchSemanticsNodes().size)
        assertEquals(SiteWrite.Keep, logAndGetSite())
    }

    /** 700 mg per week, daily at any time: one 100 mg (0.5 mL) dose due today. */
    private fun planDaily() = runBlocking {
        container.repository.saveItem(
            PlanItem(
                "test-item", null, "preset:test-cyp", Amount(700.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = LocalDate.now(),
            ),
        )
    }

    private fun todayLog() = runBlocking { container.repository.allLogsNow().single { it.planItemId == "test-item" } }

    /**
     * Opens Today's Test C row: pending it reads "100 mg · 0.5 mL", taken "100 mg · taken 10:00". A semantics click, as
     * the snackbar of the last log can cover the row on this small screen.
     */
    private fun openTodayRow(taken: Boolean = false) {
        compose.onNodeWithText(if (taken) "100 mg · taken" else "100 mg · 0.5 mL", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { count("Plan: 100 mg · 0.5 mL") > 0 }
    }

    /** Saves the open taken dose with a note and returns its stored site once the note is in. */
    private fun saveWithNote(): String? {
        compose.onNode(hasSetTextAction() and hasText("Note", substring = true)).performScrollTo().performTextInput("again")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MS) { todayLog().note == "again" }
        return todayLog().site
    }

    @Test
    fun todaySheetStoresTheSuggestionAndReopensWithIt() {
        planDaily()
        extra("delt_l", daysAgo = 1)
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Mark Test C taken").fetchSemanticsNodes().isNotEmpty() }

        openTodayRow()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Right deltoid").fetchSemanticsNodes().isNotEmpty() && isSelected("delt_r") }
        compose.onNodeWithText("Log 100 mg").performScrollTo().performClick()
        compose.waitForData(TIMEOUT_MS) { container.repository.allLogsNow().any { it.planItemId == "test-item" } }
        assertEquals("delt_r", todayLog().site)

        // The taken dose reopens at its own site; "Last" is the dose before it.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Undo Test C").fetchSemanticsNodes().isNotEmpty() }
        openTodayRow(taken = true)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Right deltoid").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(true, isSelected("delt_r"))
        assertEquals(1, count("Last: L delt · Yesterday"))
        assertEquals("delt_r", saveWithNote())
    }

    /** Re-saving a taken dose keeps its site and shows the row. */
    @Test
    fun resavingASitedDoseKeepsItsSite() {
        planDaily()
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Mark Test C taken").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        compose.waitForData(TIMEOUT_MS) { container.repository.allLogsNow().isNotEmpty() }
        runBlocking { container.repository.restoreLog(todayLog().copy(site = "pec_l", note = "")) }

        // The sheet edits the log the row held when tapped, so wait until the row shows the restored one (it ends with
        // its site); waiting for the Undo snackbar raced the restore and opened the sheet on the site-less log.
        val takenRow = "· ${InjectionSites.label("pec_l")}"
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(takenRow, substring = true).fetchSemanticsNodes().isNotEmpty() }
        openTodayRow(taken = true)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Left pec").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(true, isSelected("pec_l"))
        assertEquals(true, count("SITE") > 0)
        assertEquals("pec_l", saveWithNote())
    }

    private companion object {
        /** Generous: a slow CI runner once needed more than 15 s for the first log and its snackbar. */
        const val TIMEOUT_MS = 60_000L
    }
}
