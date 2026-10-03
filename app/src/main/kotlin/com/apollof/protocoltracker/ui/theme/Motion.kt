package com.apollof.protocoltracker.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.apollof.protocoltracker.data.Motion

internal val LocalMotion = staticCompositionLocalOf { Motion.REDUCED }

/**
 * Animation durations for the chosen motion level. Screens switch with a short fade (Reduced) or a fade and a
 * small slide (Full); with Off everything changes at once. Screen changes never animate size, so content is
 * not clipped while it moves; tab screens carry their own bar, so the screen area never resizes.
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

    /** How the app moves between two screens. */
    enum class NavMove { TAB, PUSH, POP }

    /** One length for both sides of a screen change, so neither is cut short (also while a back swipe seeks it). */
    private fun screenMs(motion: Motion): Int = if (motion == Motion.FULL) 300 else 150

    /**
     * The screen that ends on top fades in over the one below, which stays opaque: a new screen or tab comes in
     * (sliding in a little at Full); on a pop the screen below is already there.
     */
    fun screenEnter(motion: Motion, move: NavMove): EnterTransition = when {
        motion == Motion.OFF || move == NavMove.POP -> EnterTransition.None
        motion == Motion.FULL && move == NavMove.PUSH ->
            fadeIn(tween(screenMs(motion))) + slideInHorizontally(tween(screenMs(motion))) { it / 12 }
        else -> fadeIn(tween(screenMs(motion)))
    }

    /** Mirror of [screenEnter]: a popped screen fades out (sliding back at Full); a covered one stays until it is hidden. */
    fun screenExit(motion: Motion, move: NavMove): ExitTransition = when {
        motion == Motion.OFF -> ExitTransition.None
        // Held fully opaque under the incoming screen, then hidden at once when that one is in.
        move != NavMove.POP -> fadeOut(tween(durationMillis = 0, delayMillis = screenMs(motion)))
        motion == Motion.FULL -> fadeOut(tween(screenMs(motion))) + slideOutHorizontally(tween(screenMs(motion))) { it / 12 }
        else -> fadeOut(tween(screenMs(motion)))
    }

    /**
     * Dev Today switching days: the old day fades out quickly, the new one fades in, sliding in from the side it lies
     * on at Full ([forward]: a later day). The height changes at once.
     */
    fun daySwitch(motion: Motion, forward: Boolean): ContentTransform {
        val transform = when (motion) {
            Motion.OFF -> EnterTransition.None togetherWith ExitTransition.None
            Motion.REDUCED -> fadeIn(tween(150, delayMillis = 50)) togetherWith fadeOut(tween(80))
            Motion.FULL -> (fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(tween(300)) { (if (forward) it else -it) / 8 }) togetherWith
                (fadeOut(tween(100)) + slideOutHorizontally(tween(300)) { (if (forward) -it else it) / 8 })
        }
        return ContentTransform(transform.targetContentEnter, transform.initialContentExit, sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
    }
}
