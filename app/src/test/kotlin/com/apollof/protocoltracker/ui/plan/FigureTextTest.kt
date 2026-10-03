package com.apollof.protocoltracker.ui.plan

import org.junit.Test
import kotlin.test.assertEquals

/** DEV-3: figure band values that fit their narrow cells. */
class FigureTextTest {
    @Test
    fun totalPerDayIsShortAndHeldTogether() {
        assertEquals("40 mg/day", totalFigure("40 mg per day"))
    }

    @Test
    fun theScheduleLineNamesDaysAndTiming() {
        assertEquals("Tue, Wed, Fri, Sat, Sun · Morning", scheduleLine("Tue, Wed, Fri, Sat, Sun", "Morning"))
        assertEquals("Every 3.5 days", scheduleLine("Every 3.5 days", "Every 3.5 days"))
        assertEquals("Daily", scheduleLine("Daily", ""))
    }
}
