package com.apollof.protocoltracker.ui.levels

import com.apollof.protocoltracker.BuildConfig
import org.junit.Test
import kotlin.test.assertEquals

/** DEV-5: the level reading rounds like the figures in dev. */
class LevelTextTest {
    @Test
    fun wholeNumbersFromOneHundred() {
        val dev = BuildConfig.DEV_FEATURES
        assertEquals(if (dev) "1214" else "1214.2", levelText(1214.2))
        assertEquals("12.3", levelText(12.34))
        assertEquals(if (dev) "100" else "100.4", levelText(100.4))
    }
}
