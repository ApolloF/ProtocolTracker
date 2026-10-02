package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/** SYMP-2 (dev): the Symptoms chip shows the mood chart with ratings on two or more days. */
@RunWith(AndroidJUnit4::class)
class MoodTrendScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Test
    fun theSymptomsChipShowsTheMoodChart() {
        assumeTrue(BuildConfig.DEV_FEATURES)
        val now = Instant.now()
        runBlocking {
            listOf(9L to 5, 4L to 7, 1L to 6).forEach { (days, mood) ->
                val at = now.minus(Duration.ofDays(days))
                container.repository.saveJournal(JournalEntry.Symptoms("s$days", at, listOf("acne"), mood = mood, createdAt = at))
            }
        }
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        val chip = hasText("Symptoms") and hasClickAction()
        compose.waitUntil(15_000) { compose.onAllNodes(chip).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(chip).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(MOOD_TREND_CAPTION).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Mood chart, 3 ratings", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
