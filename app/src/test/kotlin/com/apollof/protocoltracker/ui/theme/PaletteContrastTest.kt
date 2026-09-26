package com.apollof.protocoltracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.apollof.protocoltracker.data.Palette
import org.junit.Test
import kotlin.test.assertTrue

/** Every fixed scheme keeps WCAG AA contrast (4.5:1) for the text pairs the app draws. */
class PaletteContrastTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun textPairsMeetAa() {
        val failures = buildList {
            for (palette in Palette.entries.filter { it != Palette.DYNAMIC }) {
                for ((dark, black) in listOf(false to false, true to false, true to true)) {
                    val t = trackerColors(palette, dark, black)
                    val pairs = mapOf(
                        "ink/bg" to (t.ink to t.bg), "ink/surface" to (t.ink to t.surface),
                        "muted/bg" to (t.muted to t.bg), "muted/surface" to (t.muted to t.surface),
                        "body2/surface" to (t.body2 to t.surface), "body2/band" to (t.body2 to t.band),
                        "accentText/surface" to (t.accentText to t.surface), "accentText/bg" to (t.accentText to t.bg),
                        "accentText/accentSoft" to (t.accentText to t.accentSoft), "accentText/band" to (t.accentText to t.band),
                        "onAccent/accent" to (t.onAccent to t.accent), "danger/surface" to (t.danger to t.surface),
                        "ink/surface2" to (t.ink to t.surface2),
                    )
                    for ((name, pair) in pairs) {
                        val ratio = contrast(pair.first, pair.second)
                        if (ratio < 4.5) add("$palette dark=$dark black=$black $name ${"%.2f".format(ratio)}")
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
