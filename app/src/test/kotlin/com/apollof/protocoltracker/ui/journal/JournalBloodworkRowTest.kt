package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/** AUD-6 (dev): a big draw is one row on All; the Bloodwork chip lists every marker. */
@RunWith(AndroidJUnit4::class)
class JournalBloodworkRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val markers = BloodMarkers.all.take(20)

    @Before
    fun seed(): Unit = runBlocking {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val at = Instant.now().minus(Duration.ofDays(3))
        // The first marker out of its typical range, the rest at their midpoint or 1.
        val results = markers.mapIndexed { i, m ->
            val mid = listOfNotNull(m.refLow, m.refHigh).average().takeIf { !it.isNaN() } ?: 1.0
            MarkerResult(m.key, if (i == 0) (m.refHigh ?: mid) * 3 else mid)
        }
        container.repository.saveJournal(JournalEntry.Bloodwork("b", at, results, createdAt = at))
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().size

    @Test
    fun allShowsOneRowAndTheChipShowsEveryMarker() {
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(15_000) { count("Bloodwork · last draw 3 days ago · 1 out of range") > 0 }
        assertEquals(0, count(markers[5].name), "no marker rows on All")
        val row = SemanticsMatcher("opens the Bloodwork chip") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show bloodwork" }
        compose.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { count(markers[0].name) > 0 }
        val opensSheet = SemanticsMatcher("marker row") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show results over time" }
        assertEquals(20, compose.onAllNodes(opensSheet).fetchSemanticsNodes().size, "the card under the chip lists all 20")
    }
}
