package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seedPlan() = runBlocking {
        container.repository.seedPresets()
        // Fixed 24 h interval anchored 10 minutes ago: always exactly one pending dose.
        val anchor = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES)
        container.repository.saveItem(
            PlanItem("test-item", null, "preset:test-cyp", Amount(0.5, DoseUnit.ML), Formulation(perMl = 200.0), Schedule.EveryHours(24.0, anchor)),
        )
    }

    @Test
    fun tapChecksDoseAndUndoRemovesIt() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithContentDescription("Mark Testosterone cypionate taken").assertExists() }.isSuccess }
        compose.onNodeWithText("0.5 mL · 100 mg").assertIsDisplayed()

        compose.onNodeWithContentDescription("Mark Testosterone cypionate taken").performClick()
        compose.waitForIdle()
        compose.waitUntil(15_000) { runBlocking { container.repository.allLogs.first().isNotEmpty() } }
        val log = runBlocking { container.repository.allLogs.first().single() }
        assertEquals(LogStatus.TAKEN, log.status)
        assertEquals("test-item", log.planItemId)

        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Undo").assertExists() }.isSuccess }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { runBlocking { container.repository.allLogs.first().isEmpty() } }
        assertTrue(runBlocking { container.repository.allLogs.first().isEmpty() })
    }
}
