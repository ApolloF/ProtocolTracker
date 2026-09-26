package com.apollof.protocoltracker.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.apollof.protocoltracker.data.Motion

internal val LocalMotion = staticCompositionLocalOf { Motion.REDUCED }

/**
 * Animation durations for the chosen motion level. Screens switch with a short fade (Reduced) or a fade and a
 * small slide (Full); with Off everything changes at once. Screen changes never animate size, so content is
 * not clipped while it moves.
 */
object Motions {
    val current: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    /** [full] ms at Full, about half at Reduced, 0 at Off. */
    fun duration(motion: Motion, full: Int): Int = when (motion) {
        Motion.FULL -> full
        Motion.REDUCED -> (full / 2).coerceAtLeast(60)
        Motion.OFF -> 0
    }

    fun <T> spec(motion: Motion, full: Int): FiniteAnimationSpec<T> =
        if (motion == Motion.OFF) snap() else tween(duration(motion, full))

    /** Opening a screen: fade, plus a short slide in from the side at Full. */
    fun enter(motion: Motion, push: Boolean): EnterTransition = when (motion) {
        Motion.OFF -> EnterTransition.None
        Motion.REDUCED -> fadeIn(tween(120))
        Motion.FULL -> fadeIn(tween(200)) + if (push) slideInHorizontally(tween(220)) { it / 12 } else EnterTransition.None
    }

    fun exit(motion: Motion, push: Boolean): ExitTransition = when (motion) {
        Motion.OFF -> ExitTransition.None
        // The old screen goes at once so it never shows through the new one.
        Motion.REDUCED -> fadeOut(tween(60))
        Motion.FULL -> fadeOut(tween(120)) + if (push) slideOutHorizontally(tween(220)) { -it / 24 } else ExitTransition.None
    }
}
