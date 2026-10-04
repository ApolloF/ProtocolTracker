package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.pk.LevelWindowRules.bounds
import com.apollof.protocoltracker.domain.pk.LevelWindowRules.clampOffset
import com.apollof.protocoltracker.domain.pk.LevelWindowRules.clampZoom
import com.apollof.protocoltracker.domain.pk.LevelWindowRules.effectiveRange
import com.apollof.protocoltracker.domain.pk.LevelWindowRules.overviewCharts
import com.apollof.protocoltracker.domain.pk.LevelWindowRules.rangeAllowed
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelWindowRulesTest {
    private val ranges = listOf(14L, 30L, 91L, 182L, 365L)
    private val now = 1_790_000_000_000L
    private val day = Duration.ofDays(1).toMillis()

    @Test
    fun defaultWindowIsAThirdBehindNow() {
        val b = bounds(now, 30, 1.0, 0)
        assertEquals(10 * day, now - b.fromMs)
        assertEquals(20 * day, b.toMs - now)
    }

    @Test
    fun rangesUpToTheLimitAreAllowed() {
        assertTrue(rangeAllowed(365, null))
        assertTrue(rangeAllowed(14, 14))
        assertFalse(rangeAllowed(30, 14))
        assertFalse(rangeAllowed(14, 0))
    }

    @Test
    fun aRangeOverTheLimitFallsBackToTheLongestAllowed() {
        assertEquals(30, effectiveRange(ranges, 30, null))
        assertEquals(14, effectiveRange(ranges, 30, 14))
        assertEquals(91, effectiveRange(ranges, 365, 100))
        assertEquals(14, effectiveRange(ranges, 30, 7))
    }

    @Test
    fun zoomNeverShowsMoreThanTheLimit() {
        assertEquals(0.25, clampZoom(0.1, 14, null))
        assertEquals(12.0, clampZoom(40.0, 14, null))
        assertEquals(1.0, clampZoom(0.25, 14, 14))
        assertEquals(3.0, clampZoom(2.0, 30, 10))
        assertEquals(4.0, clampZoom(4.0, 14, 14))
    }

    @Test
    fun panningBackStopsAtTheLimit() {
        val far = -400 * day
        assertEquals(far, clampOffset(far, 14, 1.0, null))
        for (zoom in listOf(1.0, 2.0, 5.0)) {
            val offset = clampOffset(far, 14, zoom, 14)
            assertEquals(now - 14 * day, bounds(now, 14, zoom, offset).fromMs, "zoom $zoom")
        }
        // Panning ahead is never limited.
        assertEquals(50 * day, clampOffset(50 * day, 14, 1.0, 14))
    }

    @Test
    fun overviewShowsCurrentAndOpenedWithoutALimit() {
        val groups = listOf("T", "A", "V", "N")
        assertEquals(listOf("T", "A", "V"), overviewCharts(groups, setOf("T", "A"), setOf("V"), selected = null, maxCharts = null))
    }

    @Test
    fun overviewShowsTheSelectedChartWithALimit() {
        val groups = listOf("T", "A", "V")
        val current = setOf("T", "A")
        assertEquals(listOf("T"), overviewCharts(groups, current, setOf("V"), selected = null, maxCharts = 1))
        assertEquals(listOf("A"), overviewCharts(groups, current, emptySet(), selected = "A", maxCharts = 1))
        // A group not in use now replaces the chart.
        assertEquals(listOf("V"), overviewCharts(groups, current, emptySet(), selected = "V", maxCharts = 1))
        assertEquals(listOf("V", "T"), overviewCharts(groups, current, emptySet(), selected = "V", maxCharts = 2))
        // A selection that no longer exists falls back; off still shows one.
        assertEquals(listOf("T"), overviewCharts(groups, current, emptySet(), selected = "gone", maxCharts = 0))
        assertEquals(emptyList(), overviewCharts(groups, emptySet(), emptySet(), selected = null, maxCharts = 1))
    }
}
