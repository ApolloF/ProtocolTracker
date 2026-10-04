package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Rise
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurveEngineTest {
    private val h = 3_600_000L

    @Test
    fun singleDoseRisesLinearlyThenHalvesPerHalfLife() {
        val d = CurveDose(0, peak = 100.0, tmaxH = 10.0, halfLifeH = 24.0)
        assertEquals(0.0, CurveEngine.contribution(d, 0), 1e-12)
        assertEquals(50.0, CurveEngine.contribution(d, 5 * h), 1e-9)
        assertEquals(100.0, CurveEngine.contribution(d, 10 * h), 1e-9)
        assertEquals(50.0, CurveEngine.contribution(d, 34 * h), 1e-9)
        assertEquals(25.0, CurveEngine.contribution(d, 58 * h), 1e-9)
        assertEquals(0.0, CurveEngine.contribution(d, -h), 1e-12)
    }

    @Test
    fun firstOrderDoseReachesThePeakAtTmaxThenHalvesPerHalfLife() {
        // The Basic model: absorption half-time Tmax / 3, so 87.5 % of the asymptote is the peak at Tmax.
        val d = CurveDose(0, peak = 87.5, tmaxH = 30.0, halfLifeH = 80.0, rise = Rise.FIRST_ORDER)
        assertEquals(0.0, CurveEngine.contribution(d, 0), 1e-12)
        assertEquals(50.0, CurveEngine.contribution(d, 10 * h), 1e-9)
        assertEquals(75.0, CurveEngine.contribution(d, 20 * h), 1e-9)
        assertEquals(87.5, CurveEngine.contribution(d, 30 * h), 1e-9)
        assertEquals(43.75, CurveEngine.contribution(d, 110 * h), 1e-9)
    }

    @Test
    fun areaPerPeakMatchesTheIntegratedCurve() {
        for (rise in Rise.entries) {
            val pk = PkParams(halfLifeH = 60.0, tmaxH = 20.0, peakPerUnit = 1.0, rise = rise)
            val d = CurveDose(0, peak = 1.0, tmaxH = pk.tmaxH, halfLifeH = pk.halfLifeH, rise = rise)
            val step = 0.01
            val area = (1..400_000).sumOf { CurveEngine.contribution(d, ((it - 0.5) * step * h).toLong()) } * step
            assertTrue(abs(area / pk.areaPerPeakH - 1) < 1e-4, "$rise: $area vs ${pk.areaPerPeakH}")
        }
        assertEquals(20 / 0.875 - 20 / (3 * PkParams.LN2) + 60 / PkParams.LN2, PkParams(60.0, 20.0, rise = Rise.FIRST_ORDER).areaPerPeakH, 1e-9)
        assertEquals(1 - 0.5.pow(3), PkParams.FIRST_ORDER_PEAK_SHARE, 1e-12)
    }

    @Test
    fun simulateMatchesNaiveSuperposition() {
        val rnd = Random(7)
        val doses = List(300) {
            val kind = it % 4
            CurveDose(
                rnd.nextLong(0, 90L * 24 * h), rnd.nextDouble(1.0, 500.0), doubleArrayOf(1.6, 33.3, 240.0, 99.0)[kind],
                doubleArrayOf(6.7, 172.6, 813.6, 264.0)[kind], if (kind == 3) Rise.FIRST_ORDER else Rise.LINEAR,
            )
        }
        val samples = LongArray(800) { it * (100L * 24 * h / 800) }
        val fast = CurveEngine.simulate(doses, samples)
        for (i in samples.indices) {
            val naive = doses.sumOf { CurveEngine.contribution(it, samples[i]) }
            assertTrue(abs(fast[i] - naive) <= 1e-9 * maxOf(1.0, naive), "sample $i: $fast[i] vs $naive")
        }
    }

    @Test
    fun steadyStateAverageEqualsDoseAreaOverInterval() {
        // Linear superposition: the long-run average is the area of one dose divided by the interval.
        val tmax = 33.0
        val half = 172.0
        val tau = 84.0
        val doses = List(200) { CurveDose(it * (tau * h).toLong(), 100.0, tmax, half) }
        val start = (150 * tau * h).toLong()
        val samples = LongArray(2001) { start + it * (tau * h).toLong() / 2000 }
        val values = CurveEngine.simulate(doses, samples)
        val average = values.average()
        val expected = 100.0 * (tmax / 2 + half / 0.6931471805599453) / tau
        assertTrue(abs(average - expected) / expected < 0.005, "$average vs $expected")
    }

    @Test
    fun hoursUntilBelowFraction() {
        assertEquals(10.0 + 24.0, CurveEngine.hoursUntilBelow(10.0, 24.0, 0.5), 1e-9)
    }
}
