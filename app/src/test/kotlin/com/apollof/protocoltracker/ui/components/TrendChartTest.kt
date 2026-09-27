package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/**
 * TrendChart gestures inside a scrolling page. At mdpi 1 dp is 1 px: the chart is 300 wide, so the points at day 0,
 * 20 and 40 sit at x 48, 170 and 292 (40 dp labels, 8 dp inset on both sides).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-mdpi")
class TrendChartTest {
    @get:Rule
    val compose = createComposeRule()

    private val t0 = Instant.parse("2026-06-12T08:00:00Z")
    private fun day(n: Long) = t0.plus(Duration.ofDays(n))
    private val series = listOf(TrendSeries(listOf(TrendPoint(day(0), 45.0), TrendPoint(day(20), 52.0), TrendPoint(day(40), 48.0))))

    private lateinit var scroll: ScrollState
    private var selected by mutableStateOf<Instant?>(null)
    private val picks = mutableListOf<Instant>()

    private fun show(interactive: Boolean) {
        compose.setContent {
            ProtocolTrackerTheme {
                scroll = rememberScrollState()
                Column(Modifier.verticalScroll(scroll)) {
                    Spacer(Modifier.height(100.dp))
                    TrendChart(
                        series, day(0), day(40), "Test chart", Modifier.width(300.dp), selectedAt = selected,
                        onSelect = if (interactive) { at -> picks += at; selected = at } else null,
                        band = TrendBand(40.0, 50.0),
                    )
                    Spacer(Modifier.height(2000.dp))
                }
            }
        }
    }

    private val chart get() = compose.onNodeWithContentDescription("Test chart")

    @Test
    fun aStaticChartLetsThePageScrollAndIgnoresTaps() {
        show(interactive = false)
        chart.performTouchInput { click(Offset(170f, centerY)) }
        compose.waitForIdle()
        assertEquals(0, scroll.value)
        chart.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("page scrolled", scroll.value > 0)
    }

    @Test
    fun aTapSelectsTheNearestPointWithin24dp() {
        show(interactive = true)
        chart.performTouchInput { click(Offset(186f, centerY)) }
        compose.waitForIdle()
        assertEquals(day(20), selected)
        // 61 dp from both the middle and the last point: ignored.
        chart.performTouchInput { click(Offset(231f, centerY)) }
        compose.waitForIdle()
        assertEquals(listOf(day(20)), picks)
        chart.performTouchInput { click(Offset(40f, 20f)) }
        compose.waitForIdle()
        assertEquals(day(0), selected)
    }

    @Test
    fun aSidewaysSlideMovesTheSelectionPointByPoint() {
        show(interactive = true)
        chart.performTouchInput { swipe(Offset(50f, centerY), Offset(296f, centerY), durationMillis = 600) }
        compose.waitForIdle()
        assertEquals(listOf(day(0), day(20), day(40)), picks)
        assertEquals(0, scroll.value)
    }

    @Test
    fun aVerticalDragScrollsThePageAndSelectsNothing() {
        show(interactive = true)
        chart.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("page scrolled", scroll.value > 0)
        assertNull(selected)
    }
}
