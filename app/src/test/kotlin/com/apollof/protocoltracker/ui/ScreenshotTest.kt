package com.apollof.protocoltracker.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.Palette
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Renders the main screens to PNG files for design review. Runs only when the system property
 * `screenshots.dir` is set, e.g. `-Pscreenshots.dir=...` wired in app/build.gradle.kts.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir: String? = System.getProperty("screenshots.dir")
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed() = runBlocking {
        assumeTrue(outDir != null)
        container.repository.seedPresets()
        val today = LocalDate.now()
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

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shoot(mode: ThemeMode, suffix: String) {
        compose.setContent { ProtocolTrackerTheme(mode) { AppNav() } }
        waitFor("Test C")
        save("today-$suffix")
        compose.onAllNodesWithText("Plan")[0].performClick(); waitFor("Test C"); save("plan-$suffix")
        compose.onAllNodesWithText("Levels")[0].performClick(); waitFor("Testosterone"); save("levels-$suffix")
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
    fun light() = shoot(ThemeMode.LIGHT, "light")

    @Test
    fun dark() = shoot(ThemeMode.DARK, "dark")
}
