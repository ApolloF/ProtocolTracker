package com.apollof.protocoltracker.ui.theme

import androidx.compose.animation.ExitTransition
import com.apollof.protocoltracker.data.Motion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A back swipe has its own exit, so Navigation's default (shrink, then gone in one frame) never runs. */
class PredictiveBackTest {
    @Test
    fun aBackSwipeAnimatesUnlessMotionIsOff() {
        assertEquals(ExitTransition.None, Motions.predictivePopExit(Motion.OFF, edge = 0))
        for (motion in listOf(Motion.REDUCED, Motion.FULL)) for (edge in 0..1) {
            assertNotEquals(ExitTransition.None, Motions.predictivePopExit(motion, edge))
        }
    }
}
