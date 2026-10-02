package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/** SIM-15 (dev): one editor per logged dose, the dose sheet's edit mode, opened from Journal and from Today's extras. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class DoseEditSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private lateinit var original: DoseLog

    @Before
    fun seed(): Unit = runBlocking {
        assumeTrue(BuildConfig.DEV_FEATURES)
        container.repository.seedPresets()
        val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
        // Today, an hour ago (but never before midnight), so it lists under Today's extras too.
        val at = maxOf(Instant.now().minus(Duration.ofHours(1)), java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
        container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, at, site = SiteWrite.Set("vg_r"))
        original = container.repository.allLogsNow().single() // as stored, in milliseconds
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT_MS) { shown(text) }
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    private fun stored() = runBlocking { container.repository.allLogsNow() }

    @Test
    fun journalEditsTheDoseInPlace() {
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        waitFor("125 mg · 0.63 mL")
        compose.onNode(hasClickAction() and hasText("125 mg", substring = true)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("EDIT DOSE")
        // Opens at its own site; another one replaces it.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription(InjectionSites.longLabel("vg_r")).fetchSemanticsNodes().isNotEmpty() }
        if (shown("All sites")) click("All sites")
        compose.onNode(hasContentDescription(InjectionSites.longLabel("delt_l"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasSetTextAction() and hasText("Dose")).performTextReplacement("150")
        compose.onNode(hasSetTextAction() and hasText("Note", substring = true)).performTextReplacement("Moved")
        click("Save")
        compose.waitUntil(TIMEOUT_MS) { stored().single().note == "Moved" }

        val log = stored().single()
        assertEquals(original.copy(amount = Amount(150.0, DoseUnit.MG), note = "Moved", site = "delt_l"), log.copy(takenAt = original.takenAt))
        assertEquals(original.takenAt.epochSecond / 60, log.takenAt.epochSecond / 60, "the time stays to the minute")
    }

    @Test
    fun todaysExtraOpensTheEditorAndDeletesWithUndo() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        waitFor("ALSO LOGGED TODAY")
        compose.onNode(hasClickAction() and hasText("125 mg", substring = true)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("EDIT DOSE")
        click("Delete entry")
        compose.waitUntil(TIMEOUT_MS) { stored().isEmpty() && shown("Undo") }
        click("Undo")
        compose.waitUntil(TIMEOUT_MS) { stored().isNotEmpty() }
        assertEquals(listOf(original), stored())
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
