package com.apollof.protocoltracker.ui.journal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** AUD-8: day headers follow the date format (the year only when it is not this year); the BP card's latest reading has its own line. */
@RunWith(AndroidJUnit4::class)
class JournalHeadersTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Test
    fun dayHeaders() {
        val today = LocalDate.of(2026, 9, 26)
        val sep24 = LocalDate.of(2026, 9, 24)
        val lastYear = LocalDate.of(2025, 10, 21)
        assertEquals("Today", journalDayHeader(today, today))
        assertEquals(sep24.format(Formats.dayShort), journalDayHeader(sep24, today))
        assertEquals(lastYear.format(Formats.dayYear), journalDayHeader(lastYear, today))
    }

    @Test
    fun bloodPressureCardKeepsTheReadingOnOneLine() {
        container.dayStartsAtMidnight()
        val at = Instant.now().minus(Duration.ofHours(1))
        runBlocking { container.repository.saveJournal(JournalEntry.BloodPressure("bp", at, 123, 83, createdAt = at)) }
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        val dayAndTime = "${Formats.relativeDay(at.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())} ${Formats.time(at, ZoneId.systemDefault())}"
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Latest 123/83 mmHg", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val exact = compose.onAllNodesWithText("Latest 123/83 mmHg").fetchSemanticsNodes().size
        val joined = compose.onAllNodesWithText("Latest 123/83 mmHg · $dayAndTime").fetchSemanticsNodes().size
        assertEquals(1 to 0, exact to joined)
        assertEquals(1, compose.onAllNodesWithText(dayAndTime).fetchSemanticsNodes().size)
    }

    @Test
    fun aNoteWrittenBeforeTheDayStartIsListedUnderTheDayBefore() {
        val zone = ZoneId.systemDefault()
        val night = LocalDate.now().minusDays(3)
        val at = night.atTime(1, 30).atZone(zone).toInstant()
        runBlocking {
            container.settings.update { it.copy(slotTimes = it.slotTimes.copy(dayStart = LocalTime.of(4, 0))) }
            container.repository.saveJournal(JournalEntry.Note("n", at, "Late night", at))
        }
        compose.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Late night").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, compose.onAllNodesWithText(night.minusDays(1).format(Formats.dayShort), substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText(night.format(Formats.dayShort), substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }
}
