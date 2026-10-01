package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** AUD-8: dev day headers follow the date format (the year only when it is not this year); the BP card's latest reading has its own line. */
@RunWith(AndroidJUnit4::class)
class JournalHeadersTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val dev = BuildConfig.DEV_FEATURES

    @Test
    fun dayHeaders() {
        val today = LocalDate.of(2026, 9, 26)
        val sep24 = LocalDate.of(2026, 9, 24)
        val lastYear = LocalDate.of(2025, 10, 21)
        if (dev) {
            assertEquals("Today", journalDayHeader(today, today))
            assertEquals(sep24.format(Formats.dayShort), journalDayHeader(sep24, today))
            assertEquals(lastYear.format(Formats.dayYear), journalDayHeader(lastYear, today))
        } else {
            assertEquals("Today · 2026-09-26", journalDayHeader(today, today))
            assertEquals("${sep24.format(Formats.dayShort)} · 2026-09-24", journalDayHeader(sep24, today))
        }
    }

    @Test
    fun bloodPressureCardKeepsTheReadingOnOneLine() {
        val at = Instant.now().minus(Duration.ofHours(1))
        runBlocking { container.repository.saveJournal(JournalEntry.BloodPressure("bp", at, 123, 83, createdAt = at)) }
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        val dayAndTime = "${Formats.relativeDay(at.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())} ${Formats.time(at, ZoneId.systemDefault())}"
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Latest 123/83 mmHg", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val exact = compose.onAllNodesWithText("Latest 123/83 mmHg").fetchSemanticsNodes().size
        val joined = compose.onAllNodesWithText("Latest 123/83 mmHg · $dayAndTime").fetchSemanticsNodes().size
        assertEquals(if (dev) 1 to 0 else 0 to 1, exact to joined)
        if (dev) assertEquals(1, compose.onAllNodesWithText(dayAndTime).fetchSemanticsNodes().size)
    }
}
