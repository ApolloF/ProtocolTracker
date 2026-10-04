package com.apollof.protocoltracker.ui.levels

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.LevelsOffApp
import com.apollof.protocoltracker.PlayGatedApp
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.entitlement.LEVELS_FEATURES
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

private fun ProtocolTrackerApp.seedTwo() = runBlocking {
    container.repository.seedPresets()
    val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
    val start = LocalDate.now().minusDays(10)
    container.repository.saveItem(PlanItem("a", null, "preset:anastrozole", Amount(0.5, DoseUnit.MG), schedule = daily, startDate = start))
    container.repository.saveItem(PlanItem("l", null, "preset:letrozole", Amount(2.5, DoseUnit.MG), schedule = daily, startDate = start))
    container.settings.startTrials(LEVELS_FEATURES, Instant.now().minus(Duration.ofDays(20)))
}

/** Texts on screen whose layout overflows its box or its line limit (as LargeFontLayoutTest). */
private fun ComposeContentTestRule.cutTexts(): List<String> {
    waitForIdle()
    val cut = mutableListOf<String>()
    runOnIdle {
        for (node in onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val isCut = layouts.any { l ->
                val last = l.lineCount - 1
                l.multiParagraph.didExceedMaxLines || l.isLineEllipsized(last) || (0..last).any { l.getLineRight(it) - l.getLineLeft(it) > l.size.width + 1 }
            }
            if (isCut) cut += node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")
        }
    }
    return cut
}

/** The overview after the Play trial at 360 dp and 130 % font: one chart, the limits stated without clipping, and taps on limited options never crash. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = PlayGatedApp::class, qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class LevelsLimitedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Before
    fun seed() = app.seedTwo()

    @Test
    fun limitedOverviewStatesItsLimits() {
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("One chart at a time", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Without Pro, charts reach back 14 days", substring = true).assertExists()
        compose.onNodeWithText("Estimates:", substring = true).assertExists()
        assertEquals(emptyList(), compose.cutTexts())
        compose.onNodeWithText("1Y").performClick()
        compose.onNodeWithText("All charts at once with Pro").performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}

/** A policy with no free Levels days: a message and the way to Pro instead of charts, never a crash. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = LevelsOffApp::class, qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 1.3f)
class LevelsLockedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Before
    fun seed() = app.seedTwo()

    @Test
    fun lockedLevelsShowsAMessageInsteadOfCharts() {
        compose.setContent { ProtocolTrackerTheme { LevelsScreen(onOpenSettings = {}, onOpenGroup = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Level charts need Pro").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Estimates:", substring = true).assertExists()
        assertEquals(emptyList(), compose.cutTexts())
        compose.onNodeWithText("See Pro").performClick()
        compose.waitForIdle()
    }

    @Test
    fun lockedDetailKeepsAdjustLevel() {
        val group = runBlocking { app.container.repository.protocolNow().compounds.getValue("preset:anastrozole").group }
        compose.setContent { ProtocolTrackerTheme { LevelDetailScreen(group, onBack = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Level charts need Pro").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Adjust level").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("See Pro").performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
