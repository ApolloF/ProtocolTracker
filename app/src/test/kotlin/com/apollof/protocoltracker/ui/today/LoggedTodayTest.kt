package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POL-4: one "Logged today" by time, extras and entries alike, and every row opens its editor. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class LoggedTodayTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

    // Two moments today, in order, never before midnight; stored in milliseconds.
    private val first = maxOf(Instant.now().minus(2, ChronoUnit.HOURS), midnight).truncatedTo(ChronoUnit.MILLIS)
    private val second = maxOf(Instant.now().minus(1, ChronoUnit.HOURS), midnight.plusSeconds(60)).truncatedTo(ChronoUnit.MILLIS)
    private val note = JournalEntry.Note("n1", second, "Slept badly", second)

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
        val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
        container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, first)
        container.repository.saveJournal(note)
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun top(text: String) = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot.top
    private fun storedNote() = runBlocking { container.repository.journalNow() }.single()

    @Test
    fun oneSectionByTimeWhoseRowsOpenTheirEditors() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(TIMEOUT_MS) { shown("Slept badly") && shown("125 mg") }
        assertEquals(1, compose.onAllNodesWithText("LOGGED TODAY", substring = true).fetchSemanticsNodes().size)
        assertTrue(top("125 mg") < top("Slept badly"), "the earlier extra dose comes first")

        compose.onNodeWithText("Slept badly").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { shown("Delete entry") }
        compose.onNode(hasSetTextAction() and hasText("What happened")).performTextReplacement("Slept well")
        compose.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { (storedNote() as? JournalEntry.Note)?.text == "Slept well" }
        assertEquals(note.copy(text = "Slept well"), storedNote())
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
