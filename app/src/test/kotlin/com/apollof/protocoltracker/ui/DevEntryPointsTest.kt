package com.apollof.protocoltracker.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
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
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.ui.health.BloodworkSheet
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.levels.LevelsViewModel
import com.apollof.protocoltracker.ui.settings.PendingData
import com.apollof.protocoltracker.ui.settings.SettingsViewModel
import com.apollof.protocoltracker.ui.settings.WebExportSample
import com.apollof.protocoltracker.ui.settings.awaitMain
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import com.apollof.protocoltracker.ui.today.TodayScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * Dev-only entry points are there in the dev build and absent in stable. One test per `BuildConfig.DEV_FEATURES`
 * gate or `devOr` switch in the app (grep both to find them); each test fails in one flavor when its gate is removed.
 * Every check comes after an anchor that both flavors show, so an absent entry point is never a screen that has not
 * loaded yet. A new gate gets a new test here.
 */
@RunWith(AndroidJUnit4::class)
// Wide and tall enough that every Journal chip and the first cards are composed, so absence means absence.
@Config(qualifiers = "w840dp-h1280dp")
class DevEntryPointsTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val dev = BuildConfig.DEV_FEATURES

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
    private fun countSubstring(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size
    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT_MS) { count(text) > 0 }
    private fun waitForDescription(description: String) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }

    private fun assertDevOnlyText(text: String) =
        assertEquals(dev, count(text) > 0, "\"$text\" should be shown only in the dev build (dev = $dev)")

    private fun assertDevOnlyDescription(description: String, inDev: Boolean = true) = assertEquals(
        dev == inDev, compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty(),
        "\"$description\" should be shown only in the ${if (inDev) "dev" else "stable"} build (dev = $dev)",
    )

    private fun showJournal() = compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }

    private fun seedNote() = runBlocking {
        val now = Instant.now()
        container.repository.saveJournal(JournalEntry.Note("n", now.minus(Duration.ofDays(1)), "Slept badly", now))
    }

    private fun seedDraw() = runBlocking {
        val now = Instant.now()
        container.repository.saveJournal(
            JournalEntry.Bloodwork("b", now.minus(Duration.ofDays(3)), listOf(MarkerResult("total_testosterone", 900.0)), createdAt = now),
        )
    }

    /** Testosterone cypionate every morning since ten days ago. */
    private fun seedPlan() = runBlocking {
        container.repository.seedPresets()
        container.repository.saveItem(
            PlanItem(
                "t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING))), startDate = LocalDate.now().minusDays(10),
            ),
        )
    }

    /** TodayScreen › Log menu: the Symptoms and Bloodwork rows. */
    @Test
    fun logMenuSymptomsAndBloodworkRows() {
        seedPlan()
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        // The Log button merges no text, so find it by its label in the unmerged tree.
        val logButton = hasClickAction() and hasAnyDescendant(hasText("Log"))
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(logButton, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(logButton, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)

        waitFor("Systolic, diastolic and pulse")
        assertDevOnlyText("Symptoms")
        assertDevOnlyText("Bloodwork")
    }

    /** TodayViewModel.lastDraw: with a restored draw the dev Bloodwork row reads "Last draw …"; stable has neither. */
    @Test
    fun logMenuLastDraw() {
        seedPlan()
        seedDraw()
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        val logButton = hasClickAction() and hasAnyDescendant(hasText("Log"))
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(logButton, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(logButton, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)

        waitFor("Systolic, diastolic and pulse")
        // The subtitle follows the menu once the draw times load; in stable nothing would ever appear.
        if (dev) compose.waitUntil(TIMEOUT_MS) { countSubstring("Last draw") > 0 }
        assertEquals(dev, countSubstring("Last draw") > 0, "\"Last draw\" should be shown only in the dev build (dev = $dev)")
    }

    /** JournalScreen header: one Add button with a menu in dev, the blood pressure and note buttons in stable. */
    @Test
    fun journalHeaderAddButtons() {
        showJournal()
        waitForDescription("Settings")
        assertDevOnlyDescription("Add entry")
        assertDevOnlyDescription("Add blood pressure", inDev = false)
        assertDevOnlyDescription("Add note", inDev = false)
    }

    /** JournalScreen empty state: the dev text names symptoms and bloodwork (a `devOr` switch). */
    @Test
    fun journalEmptyText() {
        showJournal()
        waitFor("Nothing logged yet")
        assertDevOnlyText("Doses you check on Today, blood pressure, notes, symptoms and bloodwork appear here by day.")
        assertEquals(!dev, count("Doses you check on Today, blood pressure readings and notes appear here by day.") > 0)
    }

    /** JournalFilter.available: the Symptoms and Bloodwork chips. */
    @Test
    fun journalFilterChips() {
        seedNote()
        showJournal()
        waitFor("All")
        assertDevOnlyText("Symptoms")
        assertDevOnlyText("Bloodwork")
    }

    /** JournalViewModel.bloodwork and lastDraw: a draw restored from a dev backup shows the Bloodwork card, labelled with the draw's age, only in dev. */
    @Test
    fun journalBloodworkCard() {
        seedNote()
        seedDraw()
        showJournal()
        // The chips show while the Journal is still loading; the note row means the entries are in.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Slept badly", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(dev, countSubstring("Bloodwork · last draw") > 0, "the Bloodwork card should be shown only in the dev build (dev = $dev)")
    }

    /** JournalLine (bloodworkSummary): a one-result draw reads "1 result" in dev; stable keeps "1 results". */
    @Test
    fun journalLineResultCount() {
        seedNote()
        seedDraw()
        showJournal()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Slept badly", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertDevOnlyText("1 result · all in range")
        assertEquals(!dev, count("1 results · all in range") > 0, "\"1 results\" should be kept only in stable (dev = $dev)")
    }

    /** LevelsViewModel: lab results on the curve (GroupView.measured). */
    @Test
    fun levelsLabPoints() {
        seedPlan()
        seedDraw()
        val vm = LevelsViewModel(container)
        compose.setContent { vm.state.collectAsState() }
        compose.waitUntil(TIMEOUT_MS) { vm.state.value.views.any { it.name == "Testosterone" } }

        val measured = vm.state.value.views.single { it.name == "Testosterone" }.measured
        assertEquals(if (dev) 1 else 0, measured.size, "lab points only in the dev build (dev = $dev)")
    }

    /** BloodworkSheet: lab-range and "Reported as" captions and the "Other tests" section. */
    @Test
    fun bloodworkSheetLabDetails() {
        val at = Instant.now()
        val results = listOf(
            MarkerResult("fsh", 0.3, "<", refLow = 1.5, refHigh = 12.4),
            MarkerResult("other:crp", 5.0, name = "CRP", unit = "mg/l"),
        )
        compose.setContent {
            ProtocolTrackerTheme {
                BloodworkSheet(at, ZoneId.systemDefault(), LabUnits.CONVENTIONAL, {}, {}, JournalEntry.Bloodwork("b", at, results, createdAt = at))
            }
        }
        waitFor("FSH")
        assertDevOnlyText("Other tests".uppercase())
        assertEquals(dev, compose.onAllNodesWithText("Lab range", substring = true).fetchSemanticsNodes().isNotEmpty(), "lab range (dev = $dev)")
        assertEquals(dev, compose.onAllNodesWithText("Reported as", substring = true).fetchSemanticsNodes().isNotEmpty(), "reported as (dev = $dev)")
    }

    /** SettingsViewModel › Import CycleTracker export: a web app export gets the history dialog; stable rejects it. */
    @Test
    fun settingsImportReadsWebExport() {
        val app = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
        val vm = SettingsViewModel(container, app.contentResolver)
        vm.readLegacy(WebExportSample.uri())
        // Both flavors read the file and answer with a dialog or a message.
        awaitMain { vm.pending.value ?: vm.message.value }
        assertEquals(dev, vm.pending.value is PendingData.WebImport, "web history dialog (dev = $dev, message = ${vm.message.value})")
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
