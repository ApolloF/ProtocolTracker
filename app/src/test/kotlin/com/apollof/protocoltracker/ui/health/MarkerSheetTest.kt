package com.apollof.protocoltracker.ui.health

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.RefRange
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.ui.ScreenshotApp
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A Bloodwork card row opens the marker sheet with every result newest first and, with 2+ plottable results, a chart. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = ScreenshotApp::class)
class MarkerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val zone = ZoneId.systemDefault()

    private fun at(month: Int, day: Int): Instant = LocalDateTime.of(2026, month, day, 8, 0).atZone(zone).toInstant()

    private fun draw(id: String, at: Instant, lab: String, vararg results: MarkerResult): Unit = runBlocking {
        container.repository.saveJournal(JournalEntry.Bloodwork(id, at, results.toList(), lab = lab, createdAt = at))
    }

    private fun title(at: Instant, lab: String) = "${at.atZone(zone).format(Formats.date)} · $lab"
    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
    private fun charts(name: String) = compose.onAllNodesWithContentDescription("$name chart", substring = true).fetchSemanticsNodes().size
    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private val opensSheet = SemanticsMatcher("opens the marker sheet") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show results over time" }

    private val showsBloodwork = SemanticsMatcher("opens the Bloodwork chip") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show bloodwork" }

    /** Dev Journal's All shows one Bloodwork row; the card's rows are under the Bloodwork chip. */
    private fun openBloodwork() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(showsBloodwork).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(showsBloodwork).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Shows the Journal and taps the card row named [name]; returns once the sheet is open. */
    private fun open(name: String) {
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        openBloodwork()
        tap(name)
    }

    private fun tap(name: String) {
        val row = hasText(name) and opensSheet
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { count("ALL RESULTS") > 0 }
    }

    @Test
    fun hematocritOverThreeDraws() {
        val hct = BloodMarkers.find("hematocrit")!!
        val latest = MarkerResult("hematocrit", 53.0, refLow = 40.0, refHigh = 50.0)
        draw("a", at(6, 12), "Lab A", MarkerResult("hematocrit", 47.0))
        draw("b", at(7, 12), "Lab B", MarkerResult("hematocrit", 50.5))
        draw("c", at(9, 23), "Lab C", latest)
        open("Hematocrit")

        assertEquals(0, count("before", substring = true), "card rows no longer show the result before")
        val tops = listOf(title(at(9, 23), "Lab C"), title(at(7, 12), "Lab B"), title(at(6, 12), "Lab A")).map(::top)
        assertEquals(tops.sorted(), tops, "results newest first")
        assertEquals(1, charts("Hematocrit"))
        assertEquals(1, count("Shaded: lab range of the latest result, ${hct.rangeText(RefRange(40.0, 50.0), LabUnits.CONVENTIONAL)}"))
        assertEquals(0, count("Results with < or > are listed, not plotted."))
        // The older rows show the typical range they are flagged against; the latest row's range is the band.
        assertEquals(2, count("ref ${hct.referenceText(LabUnits.CONVENTIONAL)}"))
        assertEquals(0, count("ref ${hct.rangeText(RefRange(40.0, 50.0), LabUnits.CONVENTIONAL)} (lab)"))
        // The latest value: once on the card row and once in the list, with no summary line repeating it.
        assertEquals(2, count(hct.formatResult(latest, LabUnits.CONVENTIONAL)))
    }

    @Test
    fun oneResultHasNoChartAndSiUnitsApply() {
        runBlocking { container.settings.update { it.copy(labUnits = LabUnits.SI) } }
        val t = BloodMarkers.find("total_testosterone")!!
        draw("a", at(9, 23), "Lab A", MarkerResult("total_testosterone", 900.0))
        open("Total testosterone")

        assertEquals(1, count(title(at(9, 23), "Lab A")))
        assertEquals(0, charts("Total testosterone"))
        assertEquals(0, count("Shaded:", substring = true))
        assertTrue(t.formatResult(MarkerResult("total_testosterone", 900.0), LabUnits.SI).endsWith("nmol/L"))
        assertEquals(2, count(t.formatResult(MarkerResult("total_testosterone", 900.0), LabUnits.SI)))
        // Without a chart there is no band, so the row keeps its range.
        assertEquals(1, count("ref ${t.referenceText(LabUnits.SI)}"))
    }

    @Test
    fun censoredEstradiolIsListedNotPlotted() {
        val e2 = BloodMarkers.find("estradiol")!!
        val censored = MarkerResult("estradiol", 5.0, qualifier = MarkerResult.BELOW)
        draw("a", at(6, 12), "Lab A", MarkerResult("estradiol", 30.0))
        draw("b", at(7, 12), "Lab A", censored)
        draw("c", at(9, 23), "Lab A", MarkerResult("estradiol", 42.0))
        open("Estradiol (E2)")

        listOf(at(6, 12), at(7, 12), at(9, 23)).forEach { assertEquals(1, count(title(it, "Lab A"))) }
        assertEquals(1, count(e2.formatResult(censored, LabUnits.CONVENTIONAL)))
        assertEquals(1, compose.onAllNodesWithContentDescription("Estradiol (E2) chart, 2 results").fetchSemanticsNodes().size)
        assertEquals(1, count("Results with < or > are listed, not plotted."))
        assertEquals(1, count("Shaded: typical adult male range, ${e2.referenceText(LabUnits.CONVENTIONAL)}"))
    }

    @Test
    fun unlistedTestSitsBehindOtherTestsAndOpensAList() {
        val ferritine = MarkerResult("other:ferritine", 120.0, name = "Ferritine", unit = "µg/L")
        draw("a", at(7, 12), "Lab A", ferritine, MarkerResult("hematocrit", 47.0))
        draw("b", at(9, 23), "Lab A", ferritine.copy(value = 140.0))
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        openBloodwork()
        compose.waitUntil(TIMEOUT_MS) { count("Other tests (1)") > 0 }
        assertEquals(0, count("Ferritine"))
        compose.onNode(hasText("Other tests (1)")).performSemanticsAction(SemanticsActions.OnClick)
        tap("Ferritine")

        listOf(at(7, 12), at(9, 23)).forEach { assertEquals(1, count(title(it, "Lab A"))) }
        assertEquals(1, count("120 µg/L"))
        assertEquals(0, charts("Ferritine"))
        assertEquals(0, count("Shaded:", substring = true))
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
