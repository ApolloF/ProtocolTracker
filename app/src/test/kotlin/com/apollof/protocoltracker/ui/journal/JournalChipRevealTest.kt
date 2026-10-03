package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.UiMessage
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** AUD-5: after an import Journal selects Bloodwork, the sixth chip; at 360 dp it must scroll into view. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp")
class JournalChipRevealTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        val now = Instant.now()
        container.repository.saveJournal(JournalEntry.BloodPressure("bp", now, 120, 80, createdAt = now))
    }

    @Test
    fun theBloodworkChipIsScrolledIntoViewAfterAnImport() {
        container.journalFocus.request(UiMessage("1 blood draw saved") {})
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        val chip = hasText("Bloodwork") and hasClickAction()
        compose.waitUntil(15_000) {
            compose.onAllNodes(chip).fetchSemanticsNodes().any { runCatching { it.config }.isSuccess } &&
                runCatching { compose.onNode(chip).assertIsSelected().assertIsDisplayed() }.isSuccess
        }
    }
}
