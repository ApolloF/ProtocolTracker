package com.apollof.protocoltracker.ui.levels

import org.junit.Test
import kotlin.test.assertEquals

/** DEV-5: the level reading rounds like the figures. */
class LevelTextTest {
    @Test
    fun wholeNumbersFromOneHundred() {
        assertEquals("1214", levelText(1214.2))
        assertEquals("12.3", levelText(12.34))
        assertEquals("100", levelText(100.4))
    }
}
