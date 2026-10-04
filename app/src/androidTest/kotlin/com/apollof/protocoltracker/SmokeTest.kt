package com.apollof.protocoltracker

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Runs the real app on a device or emulator and opens every screen and sheet once. JVM and Robolectric tests missed a
 * crash that every phone hit (an Android-only regex error), so this test exists to fail on any crash before a tag.
 * Run with an emulator up: `./gradlew connectedFossDebugAndroidTest` (or `connectedPlayDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    /** How the plan's compound shows: "Test C" in foss, "Testosterone cypionate" in play (no common names). */
    private lateinit var doseName: String
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(
            if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant(),
        )
        .around(compose)

    @Before
    fun seed(): Unit = runBlocking {
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ProtocolTrackerApp).container
        container.repository.seedPresets()
        val compound = container.repository.compounds.first().single { it.id == "preset:test-cyp" }
        doseName = compound.commonName.ifBlank { compound.displayName }
        container.settings.acknowledgeNotice(Instant.now())
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(
            PlanItem("smoke-t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = LocalDate.now().minusDays(14)),
        )
        val at = Instant.now().minus(Duration.ofDays(3))
        container.repository.saveJournal(JournalEntry.BloodPressure("smoke-bp", at, 124, 81, createdAt = at))
        container.repository.saveJournal(JournalEntry.Bloodwork("smoke-bw", at, listOf(MarkerResult("total_testosterone", 900.0)), createdAt = at))
    }

    private fun waitFor(matcher: SemanticsMatcher, unmerged: Boolean = false) =
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForText(text: String) = waitFor(hasText(text, substring = true))

    private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

    private fun tab(label: String) {
        val tab = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        waitFor(tab)
        compose.onNode(tab).click()
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun back() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private fun openLogMenu() {
        val log = hasClickAction() and hasAnyDescendant(hasText("Log"))
        waitFor(log, unmerged = true)
        compose.onNode(log, useUnmergedTree = true).click()
        waitForText("Systolic, diastolic and pulse")
    }

    /** Opens a Log menu row, waits for [shows] and closes the sheet. */
    private fun logRow(row: String, shows: String) {
        openLogMenu()
        compose.onNode(hasText(row) and hasClickAction()).click()
        waitForText(shows)
        back()
    }

    @Test
    fun everyScreenOpens() {
        waitForText(doseName)

        logRow("Blood pressure", "Diastolic")
        logRow("Note", "What happened")
        logRow("Symptoms", "Night sweats")
        openLogMenu()
        compose.onNode(hasText("Bloodwork") and hasClickAction()).click()
        waitForText("Total testosterone")
        compose.onNode(hasText("Import results") and hasClickAction()).click()
        waitForText("Copy AI prompt")
        back()
        // A planned dose's sheet.
        compose.onAllNodes(hasText(doseName, substring = true) and clickLabel("Log with details"))[0].click()
        waitForText("Plan:")
        back()

        tab("Plan")
        waitForText("per week")
        tab("Levels")
        waitFor(hasText("Testosterone") and clickLabel("Open details"))
        compose.onNode(hasText("Testosterone") and clickLabel("Open details")).click()
        waitForText("Plan only")
        back()

        tab("Journal")
        waitForText("124/81")
        compose.onNode(clickLabel("Show bloodwork")).click()
        waitFor(hasText("Total testosterone") and clickLabel("Show results over time"))
        compose.onNode(hasText("Total testosterone") and clickLabel("Show results over time")).click()
        waitForText("ALL RESULTS")
        back()

        compose.onNode(hasContentDescription("Settings")).click()
        val pages = listOf("Appearance", "Units and formats", "Today", "Times of day", "Reminders", "Export and data", "About")
        for (page in pages) {
            val row = hasText(page) and hasClickAction()
            waitFor(row)
            compose.onNode(row).performScrollTo().click()
            compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isNotEmpty() }
            waitFor(hasText(page))
            back()
        }
        back()
        tab("Today")
        waitForText(doseName)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
