package com.apollof.protocoltracker.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.reminders.AlarmReceiver
import com.apollof.protocoltracker.ui.health.BloodworkSheet
import com.apollof.protocoltracker.ui.health.SymptomSheet
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.levels.LevelsViewModel
import com.apollof.protocoltracker.ui.settings.PendingData
import com.apollof.protocoltracker.ui.settings.SettingsViewModel
import com.apollof.protocoltracker.ui.settings.WebExportSample
import com.apollof.protocoltracker.ui.settings.awaitMain
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import com.apollof.protocoltracker.ui.today.TodayScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
        // AUD-10: the Note and Symptoms rows no longer claim the same things in dev.
        assertDevOnlyText("Anything else, in your own words")
        assertDevOnlyText("Symptoms, mood and hair shedding")
        assertEquals(!dev, count("Side effects, how you feel, anything else") > 0, "the old Note subtitle only in stable")
    }

    /** Journal symptom lines and the SymptomSheet: dev drops the advice caption, uses "Often listed with …" headings and no group counts (AUD-10). */
    @Test
    fun symptomCopyWithoutVerdicts() {
        val now = Instant.now()
        val entry = JournalEntry.Symptoms("s", now.minus(Duration.ofHours(1)), listOf("night_sweats", "acne"), mood = 7, createdAt = now)
        runBlocking { container.repository.saveJournal(entry) }
        showJournal()
        compose.waitUntil(TIMEOUT_MS) { countSubstring("Night sweats, Acne") > 0 }
        assertEquals(!dev, countSubstring("low-E2") > 0, "group counts on the symptom line only in stable")
        assertEquals(1, countSubstring("mood 7/10"))
    }

    @Test
    fun symptomSheetWithoutAdvice() {
        val now = Instant.now()
        val entry = JournalEntry.Symptoms("s", now.minus(Duration.ofHours(1)), listOf("night_sweats", "acne"), mood = 7, createdAt = now)
        compose.setContent {
            ProtocolTrackerTheme { SymptomSheet(now, java.time.ZoneId.systemDefault(), onDismiss = {}, onSave = {}, existing = entry) }
        }
        compose.waitUntil(TIMEOUT_MS) { countSubstring("Night sweats") > 0 }
        assertEquals(!dev, countSubstring("Bloodwork is the way") > 0, "the advice caption only in stable")
        assertDevOnlyText("Often listed with low estrogen · 1".uppercase())
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

    /** TodayScreen › Log dose sheet: the `sites` hook adds the Site row for an injectable in dev only. */
    @Test
    fun logDoseSheetSiteRow() {
        seedPlan()
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        val row = hasClickAction() and hasText("Test C", substring = true)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(row)[0].performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(TIMEOUT_MS) { countSubstring("Plan:") > 0 }
        assertDevOnlyText("SITE")
        assertDevOnlyText("Choose site")
    }

    /** LevelsViewModel.compareAvailable: the Compare segment is gone from dev even with the stored switch on (AUD-12). */
    @Test
    fun levelsCompareSegment() {
        seedPlan()
        runBlocking { container.settings.update { it.copy(experimentalCompare = true) } }
        compose.setContent { ProtocolTrackerTheme { com.apollof.protocoltracker.ui.levels.LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        compose.waitUntil(TIMEOUT_MS) { countSubstring("Testosterone") > 0 }
        assertEquals(!dev, count("Compare") > 0, "the Compare segment only in stable (dev = $dev)")
    }

    /** LevelsScreen overview: the mode row and the figure tiles only in stable; the detail screen keeps both (AUD-13). */
    @Test
    fun levelsOverviewIsAGlance() {
        seedPlan()
        compose.setContent { ProtocolTrackerTheme { com.apollof.protocoltracker.ui.levels.LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        compose.waitUntil(TIMEOUT_MS) { countSubstring("Testosterone") > 0 && countSubstring("6M") > 0 }
        compose.waitForIdle()
        assertEquals(!dev, count("Plan only") > 0, "the mode row on the overview only in stable (dev = $dev)")
        assertEquals(!dev, countSubstring("Steady avg") > 0, "figure tiles on the overview only in stable (dev = $dev)")
    }

    @Test
    fun levelsDetailKeepsModeAndFigures() {
        seedPlan()
        compose.setContent { ProtocolTrackerTheme { com.apollof.protocoltracker.ui.levels.LevelDetailScreen("Testosterone", onBack = {}) } }
        compose.waitUntil(TIMEOUT_MS) { countSubstring("Steady avg") > 0 }
        assertEquals(1, count("Plan only"))
    }

    /** TodayScreen › rowTag: Today's dose rows carry the category tag in stable only (AUD-9). */
    @Test
    fun todayRowsHaveNoCategoryTag() {
        seedPlan()
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(TIMEOUT_MS) { countSubstring("Test C") > 0 }
        assertEquals(!dev, count("INJ") > 0, "the INJ tag on a Today row only in stable (dev = $dev)")
    }

    /** TodayViewModel › dose rows: an injectable's suggested site ends its row in dev only. */
    @Test
    fun todayRowSiteSuffix() {
        seedPlan()
        runBlocking {
            val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
            container.repository.logUnscheduled(
                testC, Amount(100.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minus(Duration.ofDays(1)), site = SiteWrite.Set("delt_l"),
            )
        }
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        waitForDescription("Mark Test C taken")
        // The suggestion follows once every log is read; in stable nothing would ever appear.
        if (dev) compose.waitUntil(TIMEOUT_MS) { countSubstring(" · R delt") > 0 }
        assertEquals(dev, countSubstring(" · R delt") > 0, "\" · R delt\" should be shown only in the dev build (dev = $dev)")
    }

    /** AlarmReceiver › dose reminder: an injectable's suggested site ends its line in dev only. */
    @Test
    fun reminderSiteSuffix() {
        seedPlan()
        val app = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notification = runBlocking {
            val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
            container.repository.logUnscheduled(
                testC, Amount(100.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minus(Duration.ofDays(1)), site = SiteWrite.Set("delt_l"),
            )
            val protocol = container.repository.protocolNow()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val due = occurrences(
                protocol.phases, protocol.items, today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant(), zone,
                IntervalAnchors.NONE, container.settings.current().slotTimes,
            ).single()
            AlarmReceiver.handle(
                app,
                Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DOSE)
                    .putExtra(AlarmReceiver.EXTRA_SLOT, due.at.epochSecond).putExtra(AlarmReceiver.EXTRA_KEYS, arrayOf(due.key)),
            )
            shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        }
        // Both flavors post the reminder for Test C.
        assertEquals("Morning: Test C (testosterone cypionate)", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals(dev, text.endsWith(" · R delt"), "\"$text\" should end with the site only in the dev build (dev = $dev)")
    }

    /** JournalViewModel › dose detail: a sited dose ends with its site in dev only ("125 mg · 0.63 mL · R VG"). */
    @Test
    fun journalDoseSite() {
        runBlocking {
            container.repository.seedPresets()
            val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
            container.repository.logUnscheduled(
                testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minus(Duration.ofDays(1)), site = SiteWrite.Set("vg_r"),
            )
        }
        showJournal()
        // Both flavors show the dose; only dev adds the site.
        waitFor(if (dev) "125 mg · 0.63 mL · R VG" else "125 mg · 0.63 mL")
        assertEquals(dev, countSubstring("R VG") > 0, "the site should be shown only in the dev build (dev = $dev)")
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

    /** BloodworkCard rows open the marker sheet (JournalViewModel.markerSheet); stable shows no card and no such row. */
    @Test
    fun journalMarkerSheetRows() {
        seedNote()
        seedDraw()
        showJournal()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Slept badly", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Dev: All shows one Bloodwork row; its rows are under the Bloodwork chip.
        val showsBloodwork = SemanticsMatcher("opens the Bloodwork chip") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show bloodwork" }
        assertEquals(dev, compose.onAllNodes(showsBloodwork).fetchSemanticsNodes().isNotEmpty(), "the Bloodwork row on All only in dev")
        if (dev) {
            compose.onNode(showsBloodwork).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Total testosterone", substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        val opensSheet = SemanticsMatcher("opens the marker sheet") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show results over time" }
        assertEquals(dev, compose.onAllNodes(opensSheet).fetchSemanticsNodes().isNotEmpty(), "\"Show results over time\" should be offered only in the dev build (dev = $dev)")
    }

    /** JournalViewModel.bpWeeks: under the Blood pressure chip the dev card adds the 7-day-average chart; stable's card is unchanged. */
    @Test
    fun journalBpChart() {
        runBlocking {
            val now = Instant.now()
            listOf(1L, 3L, 9L, 16L).forEachIndexed { i, days ->
                val at = now.minus(Duration.ofDays(days))
                container.repository.saveJournal(JournalEntry.BloodPressure("bp$i", at, 125, 80, createdAt = at))
            }
        }
        showJournal()
        waitFor("7-day average (2)")
        compose.onNode(hasText("Blood pressure") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasText("Blood pressure") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, count("7-day average (2)"))
        assertEquals(
            dev, compose.onAllNodesWithContentDescription("Blood pressure chart", substring = true).fetchSemanticsNodes().isNotEmpty(),
            "the BP chart should be shown only in the dev build (dev = $dev)",
        )
        assertDevOnlyText("Each point is a 7-day average")
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

    /** BloodworkSheet › "Import results": on a new draw in dev only, never when editing a saved one. */
    @Test
    fun bloodworkSheetImportRow() {
        val at = Instant.now()
        var existing by mutableStateOf<JournalEntry.Bloodwork?>(null)
        compose.setContent {
            ProtocolTrackerTheme { BloodworkSheet(at, ZoneId.systemDefault(), LabUnits.CONVENTIONAL, {}, {}, existing, onImport = {}) }
        }
        waitFor("Blood draw")
        assertDevOnlyText("Import results")
        existing = JournalEntry.Bloodwork("b", at, listOf(MarkerResult("hematocrit", 49.0)), createdAt = at)
        compose.waitForIdle()
        assertEquals(0, count("Import results"), "no import when editing a draw")
    }

    /** AppNav: the bloodwork import route exists only in dev (navigating to it throws in stable). */
    @Test
    fun bloodworkImportRoute() {
        lateinit var nav: NavHostController
        compose.setContent { ProtocolTrackerTheme { nav = rememberNavController(); AppNav(nav) } }
        waitFor("Today")
        val opened = runCatching { compose.runOnUiThread { nav.navigate(BloodworkImportRoute) } }
        assertEquals(dev, opened.isSuccess, "import route (dev = $dev, ${opened.exceptionOrNull()})")
        if (dev) waitFor("Paste answer")
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
