package com.apollof.protocoltracker.ui.today

import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.ui.components.Formats
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import org.junit.Test

/** The picked-time chip names the day against the logical today, like the Today header. */
class PickedLabelTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val night = SlotTimes(dayStart = LocalTime.of(4, 0))
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant()

    @Test
    fun beforeTheDayStartTheEveningBeforeIsStillToday() {
        val morning = at(2, 8)
        val now = at(3, 2)
        assertEquals(Formats.time(morning, zone), pickedLabel(morning, now, zone, night))
        assertEquals("Yesterday ${Formats.time(morning, zone)}", pickedLabel(morning, now, zone))
    }
}
