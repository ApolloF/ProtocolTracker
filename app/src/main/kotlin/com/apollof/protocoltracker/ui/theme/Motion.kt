package com.apollof.protocoltracker.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.apollof.protocoltracker.data.Motion

internal val LocalMotion = staticCompositionLocalOf { Motion.REDUCED }

/**
 * Animation durations for the chosen motion level. Screens fade through (Reduced) or fade through with a small
 * slide (Full); with Off everything changes at once. Screen changes never animate size, so content is
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

    /** Length of a screen change; the old screen is gone after the first [OUT_PART] of it. */
    private fun screenMs(motion: Motion): Int = if (motion == Motion.FULL) 300 else 200
    private const val OUT_PART = 0.35f

    /**
     * Screen changes fade through: the old screen fades out over the first part ([screenExit]), then the new one fades
     * in over the rest, sliding in a little at Full (from the right on a push, from the left on a pop; tabs only fade).
     * The two never show at once, so no text is drawn over other text.
     */
    fun screenEnter(motion: Motion, move: NavMove): EnterTransition {
        if (motion == Motion.OFF) return EnterTransition.None
        val out = (screenMs(motion) * OUT_PART).toInt()
        val inMs = screenMs(motion) - out
        val fade = fadeIn(tween(inMs, delayMillis = out, easing = LinearOutSlowInEasing))
        if (motion != Motion.FULL || move == NavMove.TAB) return fade
        val from = if (move == NavMove.POP) -1 else 1
        return fade + slideInHorizontally(tween(inMs, delayMillis = out, easing = FastOutSlowInEasing)) { from * it / 16 }
    }

    /** The old screen of [screenEnter]: a quick fade out, so the new one fades in over the empty background. */
    fun screenExit(motion: Motion): ExitTransition =
        if (motion == Motion.OFF) ExitTransition.None else fadeOut(tween((screenMs(motion) * OUT_PART).toInt(), easing = FastOutLinearInEasing))

    /**
     * A back swipe (predictive back), with the screen below already in place: while the finger moves (the first half,
     * about as far as a swipe goes) the screen shrinks a little, following the finger at Full; after release it slides
     * off toward the side the finger went, fully opaque, so nothing fades over the screen below. Navigation's own
     * default dropped the screen in one frame on release. Navigation plays what is left after release ((1 - swipe
     * progress) of the length), so the length is twice [screenMs]. [edge] is `BackEventCompat.EDGE_LEFT` (0) or
     * `EDGE_RIGHT` (1).
     */
    fun predictivePopExit(motion: Motion, edge: Int): ExitTransition {
        if (motion == Motion.OFF) return ExitTransition.None
        val ms = 2 * screenMs(motion)
        val shrink = scaleOut(tween(ms, easing = { f -> (2f * f).coerceAtMost(1f) }), targetScale = 0.9f)
        val follow = if (motion == Motion.FULL) 1f / 12 else 0f
        val away = if (edge == 1) -1 else 1
        val slide = slideOutHorizontally(tween(ms, easing = { f -> if (f < 0.5f) 2f * f * follow else follow + (2f * f - 1f) * (1f - follow) })) { away * it }
        return shrink + slide
    }

    /**
     * Today switching days: the old day fades out quickly, the new one fades in, sliding in from the side it lies
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
