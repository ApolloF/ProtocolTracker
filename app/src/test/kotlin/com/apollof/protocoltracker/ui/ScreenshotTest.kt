package com.apollof.protocoltracker.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.Palette
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.data.WeekBarMode
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
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    /** Saves the screen once it has settled: Room and DataStore load on real threads, so wait for three identical frames. */
    private fun save(name: String) {
        var last = capture()
        var same = 0
        for (attempt in 1..50) {
            Thread.sleep(150)
            val next = capture()
            same = if (next.sameAs(last)) same + 1 else 0
            last = next
            if (same == 2) break
        }
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { last.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(): Bitmap {
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
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
        compose.onAllNodes(SemanticsMatcher("day cell") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open day" })[0]
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

    @Test
    fun light() = shoot(ThemeMode.LIGHT, "light")

    @Test
    fun dark() = shoot(ThemeMode.DARK, "dark")
}

/** Pins the clock so every render shows the same moment (Saturday 26 September 2026, 10:00 local time) and can be compared pixel by pixel. */
class ScreenshotApp : ProtocolTrackerApp() {
    override fun createContainer() = AppContainer(this, clock = { NOW })

    companion object {
        val NOW: Instant = LocalDateTime.of(2026, 9, 26, 10, 0).atZone(ZoneId.systemDefault()).toInstant()
    }
}
