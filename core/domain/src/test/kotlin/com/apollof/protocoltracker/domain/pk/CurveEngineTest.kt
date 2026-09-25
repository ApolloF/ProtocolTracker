package com.apollof.protocoltracker.domain.pk

import kotlin.math.abs
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
    fun simulateMatchesNaiveSuperposition() {
        val rnd = Random(7)
        val doses = List(300) {
            val kind = it % 3
            CurveDose(rnd.nextLong(0, 90L * 24 * h), rnd.nextDouble(1.0, 500.0), doubleArrayOf(1.6, 33.3, 240.0)[kind], doubleArrayOf(6.7, 172.6, 813.6)[kind])
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
