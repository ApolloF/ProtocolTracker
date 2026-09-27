package com.apollof.protocoltracker.ui.health

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.io.labimport.BloodworkImport
import com.apollof.protocoltracker.domain.io.labimport.ImportMessages
import com.apollof.protocoltracker.domain.io.labimport.ImportRead
import com.apollof.protocoltracker.domain.io.labimport.LabPrompt
import com.apollof.protocoltracker.domain.io.labimport.Review
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The dev bloodwork import screen (import doc §10, §13.3): paste, refusals, leave out and keep, Back, save, again. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class BloodworkImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
    private val container get() = app.container
    private val zone = ZoneId.systemDefault()
    private var saved = false
    private var closed = false

    @Before
    fun devOnly() = assumeTrue(BuildConfig.DEV_FEATURES)

    private fun show() = compose.setContent {
        ProtocolTrackerTheme { BloodworkImportScreen(onBack = { closed = true }, onSaved = { saved = true }) }
    }

    private fun clip(text: String?) {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        if (text == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("answer", text))
    }

    private fun count(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, ignoreCase = substring).fetchSemanticsNodes().size

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(TIMEOUT_MS) { count(text, substring) > 0 }

    private fun paste() {
        compose.onNodeWithText("Paste answer").performScrollTo().performClick()
    }

    /** A row of the Check step by its tap label ("Leave out" or "Keep") and name. */
    private fun tapRow(name: String, label: String) = compose.onNode(
        hasText(name) and hasClickAction() and SemanticsMatcher("tap: $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label },
    ).performSemanticsAction(SemanticsActions.OnClick)

    private fun journal() = runBlocking { container.repository.journal.first() }

    @Test
    fun pasteOpensCheckWithOneCardPerDraw() {
        clip(TWO_DRAWS)
        show()
        paste()
        waitFor("Save 2 draws")
        assertEquals(1, count("Check results"))
        val june = LocalDate.of(2025, 6, 2).format(Formats.dayYear)
        val march = LocalDate.of(2025, 3, 12).format(Formats.dayYear)
        val time = LocalTime.of(8, 40).format(Formats.time)
        assertEquals(1, count("$june · $time · Atalmedial", substring = true), "header: date, time, lab")
        assertEquals(1, count("$march · Atalmedial", substring = true))
        assertEquals(2, count("Hematocrit"))
        assertEquals(1, count("0.52 l/l"))
        assertEquals(1, count("High"), "0.52 against the lab's 0.41-0.51")
    }

    @Test
    fun refusalsStayOnStartWithTheirMessage() {
        show()
        clip(null)
        paste()
        waitFor(ImportMessages.EMPTY)
        clip(LabPrompt.text)
        paste()
        waitFor(ImportMessages.PROMPT)
        assertEquals(0, count(ImportMessages.EMPTY))
        clip("Hemoglobine 9,9 mmol/l\nKreatinine 96 µmol/l")
        paste()
        waitFor(ImportMessages.NO_BLOCK)
        assertEquals(1, count("Import results"))
        assertTrue(journal().isEmpty())
    }

    @Test
    fun aTapLeavesARowOutAndKeepsItAgain() {
        clip(ONE_DRAW)
        show()
        paste()
        waitFor("Save 2 results")
        tapRow("Hematocrit", "Leave out")
        waitFor("Save 1 result")
        assertEquals(1, count("Left out."))
        tapRow("Hematocrit", "Keep")
        waitFor("Save 2 results")
        assertEquals(0, count("Left out."))
    }

    @Test
    fun backReturnsToStartAndAsksAfterAChange() {
        clip(ONE_DRAW)
        show()
        paste()
        waitFor("Save 2 results")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Paste answer")
        assertEquals(false, closed)

        paste()
        waitFor("Save 2 results")
        tapRow("Hematocrit", "Leave out")
        waitFor("Save 1 result")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Discard this import?")
        compose.onNodeWithText("Keep checking").performClick()
        compose.waitUntil(TIMEOUT_MS) { count("Discard this import?") == 0 }
        assertEquals(1, count("Save 1 result"))

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Discard this import?")
        compose.onNodeWithText("Discard").performClick()
        waitFor("Paste answer")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.waitForIdle(); closed }
        assertTrue(journal().isEmpty())
    }

    @Test
    fun saveWritesOneEntryPerDraw() {
        clip(TWO_DRAWS)
        show()
        paste()
        waitFor("Save 2 draws")
        compose.onNodeWithText("Save 2 draws").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.waitForIdle(); saved }

        val draws = journal().filterIsInstance<JournalEntry.Bloodwork>().sortedByDescending { it.at }
        assertEquals(2, draws.size)
        val (june, march) = draws
        assertEquals(LocalDate.of(2025, 6, 2).atTime(8, 40), june.at.atZone(zone).toLocalDateTime())
        assertEquals(LocalTime.NOON, march.at.atZone(zone).toLocalTime(), "no printed time: 12:00")
        assertEquals("Atalmedial", june.lab)
        val hct = june.results.single { it.marker == "hematocrit" }
        assertEquals(listOf(52.0, 41.0, 51.0), listOf(hct.value, hct.refLow!!, hct.refHigh!!).map { Math.round(it * 1000) / 1000.0 }, "lab range kept")
        assertEquals("2 blood draws saved", container.journalFocus.pending.value?.text)
    }

    @Test
    fun theSameAnswerAgainSavesNothing() {
        runBlocking {
            val read = BloodworkImport.read(TWO_DRAWS, LocalDate.now(zone)) as ImportRead.Found
            val entries = Review.of(read.draft, emptyMap(), emptyList(), zone).entries(zone, container.clock()) { "e" + nextId() }
            container.repository.saveJournal(entries)
        }
        clip(TWO_DRAWS)
        show()
        paste()
        waitFor(ImportMessages.ALL_SAVED)
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("4 already saved").performScrollTo().performClick()
        waitFor("0.49 l/l · " + LocalDate.of(2025, 3, 12).format(Formats.dayYear))
    }

    @Test
    fun saveIsEnabledWithResults() {
        clip(ONE_DRAW)
        show()
        paste()
        waitFor("Save 2 results")
        compose.onNodeWithText("Save 2 results").assertIsEnabled()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        private var n = 0
        fun nextId() = (++n).toString()

        /** Synthetic chatbot answers (Dutch, decimal commas, lab ranges); the second draw has no time. */
        val TWO_DRAWS = """
            Here is the block:

            ```
            protocoltracker-bloodwork-1
            lab: Atalmedial
            date: 2025-06-02 08:40 | Datum afname: 02-06-2025 08:40
            total_testosterone | Testosteron | 24,1 | nmol/l | 6,7 - 29 |
            hematocrit | Hematocriet | 0,52 | l/l | 0,41 - 0,51 |
            date: 2025-03-12 | Datum afname: 12-03-2025
            total_testosterone | Testosteron | 18,4 | nmol/l | 6,7 - 29 |
            hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
            end
            ```
        """.trimIndent()

        val ONE_DRAW = """
            protocoltracker-bloodwork-1
            lab: Saltro
            date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
            total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
            hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
            end
        """.trimIndent()
    }
}
