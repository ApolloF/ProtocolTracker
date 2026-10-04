package com.apollof.protocoltracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.DayOfWeek
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * POL-1's acceptance as a test: at 360 dp and 130 % font no text on the main screens is cut or ends in an ellipsis
 * (Today with the week strip, the Log dose sheet, the item editor, Level detail, Settings › Today and Times of day).
 * Each text reports its own layout, so a label clipped by a narrow chip or cell fails here without a screenshot.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi", fontScale = 1.3f, application = ScreenshotApp::class)
class LargeFontLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        container.settings.update { it.copy(weekBar = WeekBarMode.FULL) }
        val today = ScreenshotApp.NOW.atZone(ZoneId.systemDefault()).toLocalDate()
        container.repository.saveItem(
            PlanItem(
                "test", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
                Schedule.Weekdays(DayOfWeek.entries.toSet(), listOf(Timing.Slot(DaySlot.MORNING))), startDate = today.minusDays(20), sortOrder = 1,
            ),
        )
        container.repository.saveItem(
            PlanItem(
                "ai", null, "preset:anastrozole", Amount(0.5, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.EVENING))), startDate = today.minusDays(20), sortOrder = 2,
            ),
        )
    }

    private fun waitFor(text: String) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /** Every text on screen whose layout overflows its bounds or its line limit, as "screen: text". */
    private fun cutTexts(screen: String): List<String> {
        compose.waitForIdle()
        val cut = mutableListOf<String>()
        compose.runOnIdle {
            val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            for (node in nodes) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                // A line wider than its box (cut), more lines than it may show, or an ellipsis.
                val isCut = layouts.any { l ->
                    val last = l.lineCount - 1
                    l.multiParagraph.didExceedMaxLines || l.isLineEllipsized(last) ||
                        (0..last).any { l.getLineRight(it) - l.getLineLeft(it) > l.size.width + 1 }
                }
                if (isCut) cut += "$screen: " + node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")
            }
        }
        return cut
    }

    @Test
    fun noTextIsCutOnTheMainScreens() {
        compose.setContent { ProtocolTrackerTheme(ThemeMode.LIGHT) { AppNav() } }
        val cut = mutableListOf<String>()
        waitFor("Test C")
        cut += cutTexts("Today")

        compose.onAllNodes(hasText("Test C", substring = true) and clickLabel("Log with details"))[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Plan:")
        cut += cutTexts("Log dose")
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))[0].performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Plan:", substring = true).fetchSemanticsNodes().isEmpty() }

        compose.onAllNodes(hasText("Plan") and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick); waitFor("per week")
        compose.onAllNodes(hasText("Test C", substring = true) and clickLabel("Edit"))[0].performSemanticsAction(SemanticsActions.OnClick); waitFor("Starts")
        cut += cutTexts("Item editor")
        compose.onNodeWithContentDescription("Back").performClick(); waitFor("per week")

        compose.onAllNodes(hasText("Levels") and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick); waitFor("Testosterone")
        compose.onAllNodes(hasText("Testosterone") and clickLabel("Open details"))[0].performSemanticsAction(SemanticsActions.OnClick); waitFor("Plan only")
        cut += cutTexts("Level detail")
        compose.onNodeWithContentDescription("Back").performClick(); waitFor("Testosterone")

        compose.onNodeWithContentDescription("Settings").performClick(); waitFor("Appearance")
        for (page in listOf("Today", "Times of day")) {
            compose.onNode(hasText(page) and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size == 1 && compose.onAllNodesWithText(page).fetchSemanticsNodes().size == 1 }
            cut += cutTexts("Settings › $page")
            compose.onNodeWithContentDescription("Back").performClick(); waitFor("Appearance")
        }
        assertEquals(emptyList(), cut)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
