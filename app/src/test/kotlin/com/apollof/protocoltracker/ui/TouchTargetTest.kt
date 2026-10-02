package com.apollof.protocoltracker.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Accessibility: every tappable element on the four tabs has a touch area of at least 48 × 48 dp. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class TouchTargetTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(
            PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = LocalDate.now().minusDays(10)),
        )
        val at = Instant.now().minus(Duration.ofDays(1))
        container.repository.saveJournal(JournalEntry.BloodPressure("bp", at, 120, 80, createdAt = at))
    }

    private var scanned = 0

    private fun small(): List<String> {
        val minPx = with(compose.density) { 47.5.dp.toPx() }
        val out = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            if (n.config.getOrNull(SemanticsActions.OnClick) != null) {
                scanned++
                val b = n.touchBoundsInRoot
                if (b.width < minPx || b.height < minPx) {
                    val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                        ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: "?"
                    out += "$label ${(b.width / compose.density.density).toInt()}x${(b.height / compose.density.density).toInt()}"
                }
            }
            n.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return out
    }

    @Test
    fun everyTabHasLargeEnoughTargets() {
        compose.setContent { ProtocolTrackerTheme { AppNav() } }
        val found = mutableListOf<String>()
        for (tab in listOf("Today", "Plan", "Levels", "Journal")) {
            compose.onAllNodesWithText(tab)[compose.onAllNodesWithText(tab).fetchSemanticsNodes().size - 1].performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Test C", substring = true).fetchSemanticsNodes().isNotEmpty() || tab == "Journal" || tab == "Levels" }
            Thread.sleep(500)
            compose.waitForIdle()
            found += small().map { "$tab: $it" }
        }
        assertEquals(emptyList(), found)
        assertTrue(scanned > 20, "only $scanned tappable elements scanned")
    }
}
