package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/** Callbacks of a level chart. Fractions are positions across the plot area, 0 = left edge, 1 = right edge. */
class ChartCallbacks(
    val onPan: (Float) -> Unit,
    val onZoom: (Float) -> Unit,
    /** A tap, or the finger's position while scrubbing. */
    val onPoint: (Float) -> Unit,
    /** Scrubbing started ([true]) or the finger lifted ([false]). */
    val onScrubbing: (Boolean) -> Unit = {},
)

/**
 * Chart gestures. Without [scrub]: drag pans, pinch zooms, tap reads a value.
 * With [scrub]: one finger moving sideways reads values continuously; two fingers pan and zoom; a mostly vertical
 * drag is left alone so the page still scrolls.
 */
@Composable
internal fun Modifier.chartInput(scrub: Boolean, plotLeft: Dp, callbacks: ChartCallbacks): Modifier {
    val cb by rememberUpdatedState(callbacks)
    // Keyed on the mode only: the window changes on every pan step and must not restart an ongoing gesture.
    return if (!scrub) this
        .pointerInput(false) {
            detectTransformGestures { _, pan, zoom, _ ->
                if (pan.x != 0f) cb.onPan(-pan.x / size.width)
                if (zoom != 1f) cb.onZoom(zoom)
            }
        }
        .pointerInput(false) {
            detectTapGestures { pos ->
                val left = plotLeft.toPx()
                cb.onPoint(((pos.x - left) / (size.width - left)).coerceIn(0f, 1f))
            }
        }
    else this.pointerInput(true) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val left = plotLeft.toPx()
            fun fraction(x: Float) = ((x - left) / (size.width - left)).coerceIn(0f, 1f)
            val slop = viewConfiguration.touchSlop
            var mode = Mode.UNDECIDED
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    when (mode) {
                        Mode.UNDECIDED -> cb.onPoint(fraction(down.position.x))
                        Mode.SCRUB -> cb.onScrubbing(false)
                        else -> Unit
                    }
                    break
                }
                // The list took the drag for scrolling.
                if (mode == Mode.UNDECIDED && event.changes.any { it.isConsumed }) break
                if (pressed.size >= 2 && mode == Mode.UNDECIDED) mode = Mode.TRANSFORM
                when (mode) {
                    Mode.UNDECIDED -> {
                        val change = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                        val d = change.positionChange()
                        dx += d.x
                        dy += d.y
                        if (abs(dx) > slop && abs(dx) > abs(dy)) {
                            mode = Mode.SCRUB
                            change.consume()
                            cb.onScrubbing(true)
                            cb.onPoint(fraction(change.position.x))
                        } else if (abs(dy) > slop) {
                            break
                        }
                    }
                    Mode.SCRUB -> {
                        val change = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                        change.consume()
                        cb.onPoint(fraction(change.position.x))
                    }
                    Mode.TRANSFORM -> {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (pan.x != 0f) cb.onPan(-pan.x / size.width)
                        if (zoom != 1f) cb.onZoom(zoom)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

private enum class Mode { UNDECIDED, SCRUB, TRANSFORM }
