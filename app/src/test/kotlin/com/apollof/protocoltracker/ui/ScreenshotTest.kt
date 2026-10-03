package com.apollof.protocoltracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.Palette
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.io.WebExportImport
import com.apollof.protocoltracker.domain.io.labimport.LabPrompt
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
import com.apollof.protocoltracker.ui.components.TrendChartSamples
import com.apollof.protocoltracker.ui.journal.BP_TREND_CAPTION
import com.apollof.protocoltracker.ui.settings.WebExportSample
import com.apollof.protocoltracker.ui.settings.WebImportDialog
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders the main screens to PNG files for design review. Runs only when the system property
 * `screenshots.dir` is set, e.g. `-Pscreenshots.dir=...` wired in app/build.gradle.kts.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", application = ScreenshotApp::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir: String? = System.getProperty("screenshots.dir")
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed() = runBlocking {
        assumeTrue(outDir != null)
        container.repository.seedPresets()
        val today = ScreenshotApp.NOW.atZone(ZoneId.systemDefault()).toLocalDate()
        container.repository.saveItem(
            PlanItem(
                "test", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Weekdays(DayOfWeek.entries.toSet(), listOf(Timing.Slot(DaySlot.MORNING))), startDate = today.minusDays(20), sortOrder = 1,
            ),
        )
        container.repository.saveItem(
            PlanItem(
                "hcg", null, "preset:hcg", Amount(500.0, DoseUnit.IU), DoseBasis.PER_DOSE, Formulation(),
                Schedule.EveryNDays(3, today.minusDays(1), listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = today.minusDays(20), sortOrder = 2,
            ),
        )
        container.repository.saveItem(
            PlanItem(
                "ai", null, "preset:anastrozole", Amount(0.5, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.EVENING))), startDate = today.minusDays(20), sortOrder = 3,
            ),
        )
    }

    /** Dev Journal's All shows one Bloodwork row; this opens the Bloodwork chip with the card. */
    private fun openBloodwork() {
        val row = SemanticsMatcher("opens the Bloodwork chip") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show bloodwork" }
        compose.waitUntil(15_000) { compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Saves the screen once it has settled: Room and DataStore load on real threads, so wait for three identical frames. */
    private fun save(name: String, node: () -> SemanticsNodeInteraction = { compose.onRoot() }) {
        var last = capture(node)
        var same = 0
        for (attempt in 1..50) {
            Thread.sleep(150)
            val next = capture(node)
            same = if (next.sameAs(last)) same + 1 else 0
            last = next
            if (same == 2) break
        }
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { last.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(node: () -> SemanticsNodeInteraction): Bitmap {
        compose.waitForIdle()
        return node().captureToImage().asAndroidBitmap()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shoot(mode: ThemeMode, suffix: String) {
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        save("today-$suffix")
        compose.onAllNodesWithText("Plan")[0].performClick(); waitFor("Test C"); save("plan-$suffix")
        compose.onAllNodesWithText("Levels")[0].performClick(); waitFor("Testosterone"); save("levels-$suffix")
        compose.onNode(hasText("Testosterone") and SemanticsMatcher("details") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open details" }).performClick()
        waitFor("Estimated"); save("level-detail-$suffix")
        compose.onNodeWithContentDescription("Back").performClick(); waitFor("Testosterone")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("Journal"); save("journal-$suffix")
        compose.onNodeWithContentDescription("Settings").performClick(); waitFor("Appearance"); save("settings-$suffix")
    }

    @Test
    fun palettes() {
        var palette by mutableStateOf(Palette.SAGE)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode, palette) { AppNav() } }
        waitFor("Test C")
        for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for (p in Palette.entries.filter { it != Palette.DYNAMIC }) {
            mode = m; palette = p
            save("scheme-${p.name.lowercase()}-${m.name.lowercase()}")
        }
        mode = ThemeMode.LIGHT; palette = Palette.OCEAN
        compose.onNodeWithContentDescription("Settings").performClick(); waitFor("Appearance")
        compose.onNodeWithText("Appearance").performClick(); waitFor("Colour scheme".uppercase())
        save("appearance-ocean-light")
    }

    @Test
    fun compare() {
        assumeTrue(!BuildConfig.DEV_FEATURES) // compare mode is stable only (AUD-12)
        runBlocking { container.settings.update { it.copy(experimentalCompare = true) } }
        compose.setContent { ProtocolTrackerTheme(ThemeMode.LIGHT) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Levels")[0].performClick(); waitFor("Compare")
        compose.onNodeWithText("Compare").performClick(); waitFor("100% MEANS")
        save("compare-plan-light")
        compose.onNodeWithText("Shared dose").performSemanticsAction(SemanticsActions.OnClick); waitFor("ANCHOR")
        save("compare-shared-light")
    }

    /** Screens added in 0.4: day sheet, units page, scrubbing, and the dev build's symptom and bloodwork entry. */
    @Test
    fun additions() {
        runBlocking {
            container.settings.update { it.copy(weekBar = WeekBarMode.FULL, experimentalScrub = true) }
            val testC = container.repository.protocolNow().compounds.getValue("preset:test-cyp")
            container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, ScreenshotApp.NOW.minusSeconds(86_400 * 2))
            container.repository.saveJournal(JournalEntry.Note("n", ScreenshotApp.NOW.minusSeconds(86_400), "Slept badly", ScreenshotApp.NOW))
            if (BuildConfig.DEV_FEATURES) container.repository.saveJournal(
                JournalEntry.Bloodwork(
                    "b", ScreenshotApp.NOW.minusSeconds(86_400 * 3),
                    listOf(MarkerResult("total_testosterone", 1100.0), MarkerResult("estradiol", 45.0), MarkerResult("hematocrit", 49.0)),
                    lab = "Lab A", createdAt = ScreenshotApp.NOW,
                ),
            )
        }
        compose.setContent { ProtocolTrackerTheme(ThemeMode.LIGHT) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Levels")[0].performClick(); waitFor("Testosterone")
        compose.onAllNodes(SemanticsMatcher("chart") { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith("Testosterone estimated") }
        })[0].performTouchInput { click(center) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Last dose:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        save("levels-scrub-light")

        compose.onNodeWithContentDescription("Settings").performClick(); waitFor("Units and formats")
        compose.onNodeWithText("Units and formats").performClick(); waitFor("Injection volume".uppercase())
        save("units-light")
        compose.onNodeWithContentDescription("Back").performClick(); waitFor("Appearance")
        compose.onNodeWithContentDescription("Back").performClick(); waitFor("Testosterone")

        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("Slept badly")
        save("journal-additions-light")

        compose.onAllNodesWithText("Today")[0].performClick(); waitFor("Test C")
        // A day other than today (dev marks today's cell as the selected one).
        compose.onAllNodes(SemanticsMatcher("day cell") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open day" && it.config.getOrNull(SemanticsProperties.Selected) != true })[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Previous day")).fetchSemanticsNodes().isNotEmpty() }
        save("day-sheet-light")
    }

    /** Dev Journal with lab ranges, censored values and an unlisted result: the Bloodwork card and the draw lines. */
    @Test
    fun journalLabRanges() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val e2 = 0.2724 // pmol/L to pg/mL
        fun daysAgo(n: Long) = ScreenshotApp.NOW.minusSeconds(86_400 * n)
        runBlocking {
            container.repository.saveJournal(
                JournalEntry.Bloodwork("b1", daysAgo(9), listOf(MarkerResult("estradiol", 30.0)), createdAt = ScreenshotApp.NOW),
            )
            container.repository.saveJournal(
                JournalEntry.Bloodwork("b2", daysAgo(5), listOf(MarkerResult("estradiol", 40 * e2, "<", 20 * e2, 150 * e2)), createdAt = ScreenshotApp.NOW),
            )
            container.repository.saveJournal(
                JournalEntry.Bloodwork(
                    "b3", daysAgo(2),
                    listOf(
                        MarkerResult("total_testosterone", 1000.0, refLow = 248.0, refHigh = 1100.0),
                        MarkerResult("fsh", 0.3, "<"),
                        MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"),
                    ),
                    lab = "Lab A", createdAt = ScreenshotApp.NOW,
                ),
            )
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("3 results · 1 out of range")
        openBloodwork(); waitFor("Other tests (1)")
        save("journal-lab-ranges-light")
        mode = ThemeMode.DARK
        save("journal-lab-ranges-dark")
    }

    /** Dev Bloodwork sheet editing an imported draw: lab ranges, censored values as reported, and "Other tests". */
    @Test
    fun bloodworkSheetLab() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val e2 = 0.2724 // pmol/L to pg/mL
        runBlocking {
            container.repository.saveJournal(
                JournalEntry.Bloodwork(
                    "b", ScreenshotApp.NOW.minusSeconds(86_400 * 2),
                    listOf(
                        MarkerResult("total_testosterone", 1000.0, refLow = 248.0, refHigh = 836.0),
                        MarkerResult("estradiol", 40 * e2, "<", 20 * e2, 150 * e2),
                        MarkerResult("lh", 0.3, "<", refLow = 1.7, refHigh = 8.6),
                        MarkerResult("hemoglobin", 9.9 * 1.611, refLow = 8.5 * 1.611, refHigh = 11.0 * 1.611),
                        MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"),
                        MarkerResult("other:ferritine", 120.0, refLow = 30.0, refHigh = 400.0, name = "Ferritine", unit = "µg/l"),
                        MarkerResult("other:vitamine_d_25_oh", 64.0, refLow = 50.0, name = "Vitamine D (25-OH)", unit = "nmol/l"),
                        MarkerResult("other:crp", 1.0, "<", refHigh = 10.0, name = "CRP", unit = "mg/l"),
                    ),
                    lab = "Lab A", createdAt = ScreenshotApp.NOW,
                ),
            )
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("Bloodwork · Lab A")
        compose.onAllNodesWithText("Bloodwork · Lab A")[0].performSemanticsAction(SemanticsActions.OnClick); waitFor("Lab range 248")
        save("bloodwork-sheet-edit-lab-light")
        mode = ThemeMode.DARK
        save("bloodwork-sheet-edit-lab-dark")
        compose.onNodeWithText("Save").performScrollTo()
        save("bloodwork-sheet-edit-other-dark")
        mode = ThemeMode.LIGHT
        save("bloodwork-sheet-edit-other-light")
    }

    /** Dev confirm dialog for the web app history (Settings › Export and data › Import CycleTracker export). */
    @Test
    fun webImportDialog() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val result = WebExportImport.parse(WebExportSample.JSON, ZoneId.systemDefault())
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { WebImportDialog(result, {}, {}) } }
        waitFor("Import CycleTracker history?")
        save("web-import-dialog-light") { compose.onNode(isDialog()) }
        mode = ThemeMode.DARK
        save("web-import-dialog-dark") { compose.onNode(isDialog()) }
    }

    /** Dev draw hint at 360 dp: the Journal Bloodwork card label (11 months, then 25 weeks) and the Log menu row. */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun lastDraw() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        fun draw(id: String, daysAgo: Long) = runBlocking {
            container.repository.saveJournal(
                JournalEntry.Bloodwork(id, ScreenshotApp.NOW.minusSeconds(86_400 * daysAgo), listOf(MarkerResult("hematocrit", 49.0)), createdAt = ScreenshotApp.NOW),
            )
        }
        draw("b1", 340)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("Last draw 11 months ago".uppercase())
        save("journal-last-draw-months-light")
        draw("b2", 175); waitFor("Last draw 25 weeks ago".uppercase())
        save("journal-last-draw-weeks-light")
        compose.onAllNodesWithText("Today")[0].performClick(); waitFor("Test C")
        val logButton = hasClickAction() and hasAnyDescendant(hasText("Log"))
        compose.onNode(logButton, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Last draw 25 weeks ago")
        save("log-menu-light")
        mode = ThemeMode.DARK
        save("log-menu-dark")
    }

    /** Dev Log dose sheet of Test C with two sited extra doses: the Site row, then with every site after "All sites". */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun logDoseSite() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        runBlocking {
            val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
            for ((daysAgo, site) in listOf(2L to "vg_r", 1L to "delt_l")) {
                container.repository.logUnscheduled(
                    testC, Amount(35.7, DoseUnit.MG), testC.defaultFormulation, ScreenshotApp.NOW.minusSeconds(86_400 * daysAgo), site = SiteWrite.Set(site),
                )
            }
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodes(hasClickAction() and hasText("Test C", substring = true))[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Last: L delt")
        save("log-dose-site-light")
        mode = ThemeMode.DARK
        save("log-dose-site-dark")
        compose.onNodeWithText("All sites").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitFor("L thigh")
        save("log-dose-site-all-dark")
    }

    /** Dev Today after two sited Test C doses: pending Test C rows end with the suggested site. */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun todaySite() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        runBlocking {
            val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
            for ((daysAgo, site) in listOf(2L to "vg_r", 1L to "delt_l")) {
                container.repository.logUnscheduled(
                    testC, Amount(35.7, DoseUnit.MG), testC.defaultFormulation, ScreenshotApp.NOW.minusSeconds(86_400 * daysAgo), site = SiteWrite.Set(site),
                )
            }
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor(" · R delt")
        save("today-site-light")
        mode = ThemeMode.DARK
        save("today-site-dark")
    }

    /** Dev Journal after two sited Test C doses: each dose line ends with its site. */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun journalSite() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        runBlocking {
            val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
            for ((daysAgo, site) in listOf(2L to "vg_r", 1L to "delt_l")) {
                container.repository.logUnscheduled(
                    testC, Amount(35.7, DoseUnit.MG), testC.defaultFormulation, ScreenshotApp.NOW.minusSeconds(86_400 * daysAgo), site = SiteWrite.Set(site),
                )
            }
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor(" · L delt")
        save("journal-site-light")
        mode = ThemeMode.DARK
        save("journal-site-dark")
    }

    /** Dev marker sheet from the Bloodwork card: four hematocrit draws, the latest high against its lab range. */
    @Test
    fun markerSheet() = shootMarkerSheet("411")

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun markerSheetNarrow() = shootMarkerSheet("360")

    private fun shootMarkerSheet(width: String) {
        assumeTrue(BuildConfig.DEV_FEATURES)
        fun daysAgo(n: Long) = ScreenshotApp.NOW.minusSeconds(86_400 * n)
        runBlocking {
            listOf(
                Triple(290L, 46.8, "Star-shl"), Triple(200L, 49.5, "Star-shl"), Triple(106L, 51.2, "Saltro"), Triple(3L, 53.4, "Saltro"),
            ).forEachIndexed { i, (days, value, lab) ->
                val result = if (lab == "Saltro") MarkerResult("hematocrit", value, refLow = 40.0, refHigh = 50.0) else MarkerResult("hematocrit", value)
                container.repository.saveJournal(JournalEntry.Bloodwork("h$i", daysAgo(days), listOf(result), lab = lab, createdAt = ScreenshotApp.NOW))
            }
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var black by mutableStateOf(false)
        compose.setContent { ProtocolTrackerTheme(mode, pureBlack = black) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick()
        openBloodwork(); waitFor("Hematocrit")
        compose.onNode(hasText("Hematocrit") and SemanticsMatcher("row") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show results over time" })
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor("ALL RESULTS")
        save("marker-sheet-$width-light")
        mode = ThemeMode.DARK
        save("marker-sheet-$width-dark")
        black = true
        save("marker-sheet-$width-black")
    }

    /** Dev Journal › Blood pressure: 12 weeks of readings give the 7-day-average chart; then an older week selected. */
    @Test
    fun journalBp() = shootJournalBp("411")

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun journalBpNarrow() = shootJournalBp("360")

    private fun shootJournalBp(label: String) {
        assumeTrue(BuildConfig.DEV_FEATURES)
        runBlocking {
            for (w in 0..11) {
                // Week 1 has ten readings, for the longest caption.
                val days = if (w == 1) (0 until 10).map { 7.0 * w + 0.6 + it * 0.65 } else listOf(7.0 * w + 1, 7.0 * w + 4)
                days.forEachIndexed { j, d ->
                    val at = ScreenshotApp.NOW.minusSeconds((86_400 * d).toLong())
                    val sys = (124 + 6 * sin(w / 2.0) + (j % 3) - 1).roundToInt()
                    val dia = (80 + 4 * sin(w / 2.0 + 1) + (j % 2)).roundToInt()
                    container.repository.saveJournal(JournalEntry.BloodPressure("bp$w-$j", at, sys, dia, createdAt = at))
                }
            }
        }
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var black by mutableStateOf(false)
        compose.setContent { ProtocolTrackerTheme(mode, pureBlack = black) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("BLOOD PRESSURE")
        compose.onNode(hasText("Blood pressure") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(BP_TREND_CAPTION)
        save("journal-bp-$label-light")
        mode = ThemeMode.DARK
        save("journal-bp-$label-dark")
        black = true
        save("journal-bp-$label-black")
        // Week 1 sits 1/11 of the plot left of the newest point (40 dp labels, 8 dp inset on both sides).
        compose.onNodeWithContentDescription("Blood pressure chart", substring = true).performTouchInput {
            val start = 48.dp.toPx()
            val end = width - 8.dp.toPx()
            click(Offset(end - (end - start) / 11, centerY))
        }
        waitFor("7 days to")
        mode = ThemeMode.LIGHT
        black = false
        save("journal-bp-$label-selected-light")
    }

    /** Dev harness for the trend chart at 360 dp: bands, a one-sided band and 26 weeks of BP, light, dark and pure black. */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun trendChart() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var black by mutableStateOf(false)
        compose.setContent { ProtocolTrackerTheme(mode, pureBlack = black) { TrendChartSamples(ScreenshotApp.NOW) } }
        save("trend-chart-light")
        mode = ThemeMode.DARK
        save("trend-chart-dark")
        black = true
        save("trend-chart-black")
    }

    /** Dev bloodwork import: the new draw's sheet with Import results, Start (and a refusal), Check with two draws. */
    @Test
    fun bloodworkImport() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val clipboard = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().getSystemService(ClipboardManager::class.java)
        var mode by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        compose.onAllNodesWithText("Journal")[0].performClick(); waitFor("Nothing logged yet")
        compose.onNodeWithContentDescription("Add entry").performClick(); waitFor("Symptoms")
        compose.onNodeWithText("Bloodwork").performClick(); waitFor("Import results")
        save("bloodwork-sheet-import-light")
        mode = ThemeMode.DARK
        save("bloodwork-sheet-import-dark")
        compose.onNodeWithText("Import results").performClick(); waitFor("Paste answer")
        save("import-start-dark")
        mode = ThemeMode.LIGHT
        save("import-start-light")
        clipboard.setPrimaryClip(ClipData.newPlainText("prompt", LabPrompt.text))
        compose.onNodeWithText("Paste answer").performClick(); waitFor("This is the prompt")
        save("import-start-message-light")
        clipboard.setPrimaryClip(ClipData.newPlainText("answer", IMPORT_ANSWER))
        compose.onNodeWithText("Paste answer").performClick(); waitFor("Save 2 draws")
        compose.onNode(hasText("Hemoglobin") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick); waitFor("Left out.")
        save("import-check-light")
        mode = ThemeMode.DARK
        save("import-check-dark")
        compose.onNodeWithText("1 not imported").performScrollTo().performClick(); waitFor("PSA totaal")
        compose.onNodeWithText("Save 2 draws").performScrollTo()
        save("import-check-end-dark")
        mode = ThemeMode.LIGHT
        save("import-check-end-light")
    }

    @Test
    fun light() = shoot(ThemeMode.LIGHT, "light")

    @Test
    fun dark() = shoot(ThemeMode.DARK, "dark")
}

/** Pins the clock so every render shows the same moment (Saturday 26 September 2026, 10:00 local time) and can be compared pixel by pixel. */
/** A synthetic chatbot answer with two draws for the import screenshots (before the pinned clock). */
private val IMPORT_ANSWER = """
    protocoltracker-bloodwork-1
    lab: Saltro
    date: 2026-09-21 08:15 | Afnamedatum: 21-09-2026 08:15
    total_testosterone | Testosteron totaal | 24,1 | nmol/l | 8,6 - 29,0 |
    estradiol | Oestradiol | 142 | pmol/l | < 200 |
    hematocrit | Hematocriet | 0,52 | l/l | 0,41 - 0,51 |
    hemoglobin | Hemoglobine | 10,8 | mmol/l | 8,5 - 11,0 |
    fsh | FSH | <0,3 | E/l | 1,5 - 12,4 |
    other | Vrij T4 | 15,2 | pmol/l | 10 - 23 |
    psa | PSA totaal | ? | µg/l | < 4,0 | onleesbaar
    date: 2026-06-02 | Afnamedatum: 02-06-2026
    total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
    hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
    end
""".trimIndent()

class ScreenshotApp : ProtocolTrackerApp() {
    override fun createContainer() = AppContainer(this, clock = { NOW })

    companion object {
        val NOW: Instant = LocalDateTime.of(2026, 9, 26, 10, 0).atZone(ZoneId.systemDefault()).toInstant()
    }
}
