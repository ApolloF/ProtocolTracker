package com.apollof.protocoltracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TrendAxisTest {
    private val t0 = Instant.parse("2026-06-12T08:00:00Z")
    private fun day(n: Long) = t0.plus(Duration.ofDays(n))
    private fun assertRange(lo: Double, hi: Double, r: ClosedFloatingPointRange<Double>) {
        assertEquals(lo, r.start, 1e-9)
        assertEquals(hi, r.endInclusive, 1e-9)
    }

    @Test
    fun yIsPaddedByTenPercentOfTheSpan() = assertRange(38.0, 62.0, TrendAxis.yRange(listOf(40.0, 55.0, 60.0)))

    @Test
    fun yIsWidenedToTheBandBeforePadding() {
        // Data 45..60, band 35..50: span 35..60 = 25, padded by 2.5.
        assertRange(32.5, 62.5, TrendAxis.yRange(listOf(45.0, 60.0), TrendBand(35.0, 50.0)))
        // A one-sided band widens its own side only.
        assertRange(36.0, 84.0, TrendAxis.yRange(listOf(40.0, 60.0), TrendBand(null, 80.0)))
    }

    @Test
    fun aFixedRangeWins() = assertRange(1.0, 10.0, TrendAxis.yRange(listOf(3.0, 7.0), TrendBand(0.0, 50.0), fixed = 1.0..10.0))

    @Test
    fun yStaysAtOrAboveZeroForPositiveData() {
        assertRange(0.0, 10.95, TrendAxis.yRange(listOf(0.5, 10.0)))
        assertRange(-12.0, 12.0, TrendAxis.yRange(listOf(-10.0, 10.0)))
    }

    @Test
    fun aSingleValueGetsASpan() {
        val r = TrendAxis.yRange(listOf(49.0))
        assertTrue(r.start < 49.0 && r.endInclusive > 49.0)
        assertEquals(49.0, (r.start + r.endInclusive) / 2, 1e-9)
        assertTrue(TrendAxis.yRange(listOf(0.0)).let { it.start == 0.0 && it.endInclusive > 0.0 })
    }

    @Test
    fun aOneSidedBandReachesThePlotEdge() {
        val range = 30.0..60.0
        assertRange(40.0, 60.0, TrendAxis.bandSpan(TrendBand(40.0, null), range)!!)
        assertRange(30.0, 52.0, TrendAxis.bandSpan(TrendBand(null, 52.0), range)!!)
        assertRange(30.0, 60.0, TrendAxis.bandSpan(TrendBand(10.0, 90.0), range)!!)
        assertNull(TrendAxis.bandSpan(TrendBand(70.0, 90.0), range))
        assertNull(TrendAxis.bandSpan(TrendBand(null, null), range))
    }

    @Test
    fun theWindowCoversEveryPointAndAMinimumSpan() {
        // Draws 20 days apart: widened back from the latest to 60 days.
        val (from, to) = TrendAxis.xWindow(listOf(day(0), day(20)), Duration.ofDays(60))
        assertEquals(day(20), to)
        assertEquals(day(-40), from)
        // Draws a year apart: every draw, nothing added.
        assertEquals(day(0) to day(365), TrendAxis.xWindow(listOf(day(365), day(0)), Duration.ofDays(60)))
        // A later end moves the right edge; no points gives the span up to the end.
        assertEquals(day(0) to day(30), TrendAxis.xWindow(listOf(day(0), day(20)), Duration.ofDays(14), end = day(30)))
        assertEquals(day(16) to day(30), TrendAxis.xWindow(emptyList(), Duration.ofDays(14), end = day(30)))
    }

    @Test
    fun pointsMapProportionallyToTime() {
        assertEquals(100f, TrendAxis.x(day(0), day(0), day(40), 100f, 500f), 1e-3f)
        assertEquals(200f, TrendAxis.x(day(10), day(0), day(40), 100f, 500f), 1e-3f)
        assertEquals(500f, TrendAxis.x(day(40), day(0), day(40), 100f, 500f), 1e-3f)
        assertEquals(300f, TrendAxis.x(day(5), day(5), day(5), 100f, 500f), 1e-3f)
        assertEquals(0.25, TrendAxis.yFraction(45.0, 40.0..60.0), 1e-9)
    }

    @Test
    fun ticksAreRoundAndTwoOrThree() {
        fun labels(r: ClosedFloatingPointRange<Double>) = TrendAxis.yTicks(r).map { it.label }
        assertEquals(listOf("75", "100", "125"), labels(69.0..141.0))
        assertEquals(listOf("45", "50"), labels(43.2..52.8))
        assertEquals(listOf("0.5", "1.0", "1.5"), labels(0.4..1.6))
        assertEquals(listOf("0.46", "0.48", "0.50"), labels(0.451..0.509))
        assertEquals(listOf("5", "10"), labels(1.0..10.0))
        assertEquals(listOf("0.8", "1.0"), labels(0.77..1.13))
        for (r in listOf(0.0..1.0, 3.3..3.9, 12.0..980.0, 43.2..52.8, 0.07..0.31)) {
            val ticks = TrendAxis.yTicks(r)
            assertTrue("$r: ${ticks.size}", ticks.size in 2..5)
            assertTrue("$r", ticks.all { it.value in r })
        }
    }

    @Test
    fun theNearestPointCountsOnlyWithinTheDistance() {
        val xs = listOf(48f, 170f, 292f)
        assertEquals(1, TrendAxis.nearest(xs, 180f, 24f))
        assertNull(TrendAxis.nearest(xs, 230f, 24f))
        assertEquals(2, TrendAxis.nearest(xs, 240f))
        assertNull(TrendAxis.nearest(emptyList(), 10f))
    }
}
