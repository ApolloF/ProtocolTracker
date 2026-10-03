package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
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
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/** Editing a sited dose from Journal keeps its site, and the dose line shows it. */
@RunWith(AndroidJUnit4::class)
class JournalSiteTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        val testC = container.repository.compounds.first().first { it.id == "preset:test-cyp" }
        container.repository.logUnscheduled(
            testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minus(Duration.ofDays(1)), site = SiteWrite.Set("vg_r"),
        )
    }

    private fun waitFor(text: String) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun editingADoseKeepsItsSite() {
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        waitFor("125 mg · 0.63 mL")
        compose.onNode(hasClickAction() and hasText("125 mg", substring = true)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Delete entry")
        compose.onNode(hasSetTextAction() and hasText("Note", substring = true)).performTextReplacement("Left side sore")
        compose.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Left side sore")

        val log = runBlocking { container.repository.allLogsNow().single() }
        assertEquals("Left side sore", log.note)
        assertEquals("vg_r", log.site)
    }
}
