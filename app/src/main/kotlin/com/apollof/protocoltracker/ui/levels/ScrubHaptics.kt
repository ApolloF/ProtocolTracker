package com.apollof.protocoltracker.ui.levels

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Short ticks while scrubbing a chart: a light tick per day (or week) passed, a firmer one on a dose or log.
 *
 * Effects are picked per device. Composition primitives give the crispest ticks where fully supported (Pixel and
 * most recent phones); Samsung devices map the predefined tick and click effects to their own tuned patterns and
 * often play primitives weakly or not at all, so they use the predefined effects. View haptic feedback is the
 * fallback. Ticks use touch vibration attributes, so the system's touch vibration setting and intensity apply,
 * and are spaced at least [MIN_GAP_MS] apart so fast scrubs never buzz.
 */
class ScrubHaptics(context: Context, private val view: View) {
    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else -> @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }?.takeIf { it.hasVibrator() }

    private val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private val tickEffect: VibrationEffect? = effect(light = true)
    private val markEffect: VibrationEffect? = effect(light = false)
    private var lastMs = 0L

    private fun effect(light: Boolean): VibrationEffect? {
        val v = vibrator ?: return null
        if (!samsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val primitive = if (light) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else VibrationEffect.Composition.PRIMITIVE_TICK
            if (v.areAllPrimitivesSupported(primitive)) {
                return VibrationEffect.startComposition().addPrimitive(primitive, if (light) 0.7f else 1f).compose()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return VibrationEffect.createPredefined(if (light) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK)
        }
        return null
    }

    /** A day or week boundary passed. */
    fun tick() = play(tickEffect, HapticFeedbackConstants.CLOCK_TICK)

    /** The finger crossed a dose or a log entry. */
    fun mark() = play(markEffect, HapticFeedbackConstants.KEYBOARD_TAP)

    private fun play(effect: VibrationEffect?, fallback: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastMs < MIN_GAP_MS) return
        lastMs = now
        val v = vibrator
        if (effect == null || v == null) {
            view.performHapticFeedback(fallback)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            v.vibrate(effect)
        }
    }

    private companion object {
        const val MIN_GAP_MS = 35L
    }
}

@Composable
fun rememberScrubHaptics(): ScrubHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) { ScrubHaptics(context, view) }
}
