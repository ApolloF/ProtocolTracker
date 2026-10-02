package com.apollof.protocoltracker.ui.plan

import com.apollof.protocoltracker.BuildConfig
import org.junit.Test
import kotlin.test.assertEquals

/** DEV-3: figure band values that fit their narrow cells in dev; stable keeps its text. */
class FigureTextTest {
    private val dev = BuildConfig.DEV_FEATURES

    @Test
    fun totalPerDayIsShortAndHeldTogether() {
        assertEquals(if (dev) "40 mg/day" else "40 mg per day", totalFigure("40 mg per day"))
    }

    @Test
    fun theScheduleLineNamesDaysAndTiming() {
        assertEquals(if (dev) "Tue, Wed, Fri, Sat, Sun · Morning" else "Morning", scheduleLine("Tue, Wed, Fri, Sat, Sun", "Morning"))
        assertEquals("Every 3.5 days", scheduleLine("Every 3.5 days", "Every 3.5 days"))
        assertEquals("Daily", scheduleLine("Daily", ""))
    }
}
