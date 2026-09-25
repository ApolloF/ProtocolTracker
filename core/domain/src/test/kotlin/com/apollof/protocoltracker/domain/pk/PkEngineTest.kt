package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.PkParams
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkEngineTest {
    private val hour = 3_600_000L

    private fun superposition(doses: List<PkDose>, t: Long): Double =
        doses.sumOf { it.effective * PkEngine.bateman((t - it.atMs) / 3_600_000.0, it.pk.ka, it.pk.ke) }

    private fun assertRel(expected: Double, actual: Double, tol: Double, msg: String = "") {
        val scale = max(abs(expected), 1e-12)
        assertTrue(abs(expected - actual) / scale <= tol, "$msg expected $expected got $actual")
    }

    @Test
    fun propagationMatchesSuperpositionForIrregularMixedDoses() {
        val rnd = Random(42)
        val params = listOf(PkParams(8.66, 189.0, 0.72), PkParams(0.5, 46.0), PkParams(6.0, 6.0000001), PkParams(24.0, 3.0))
        val doses = List(300) { PkDose(rnd.nextLong(0, 2000 * hour), rnd.nextDouble(1.0, 250.0), params[it % params.size]) }
        val samples = LongArray(2500) { it * hour }
        val fast = PkEngine.simulate(doses, samples)
        for (i in samples.indices) {
            val ref = superposition(doses, samples[i])
            if (ref > 1e-9) assertRel(ref, fast[i], 1e-9, "t=${samples[i] / hour}h")
        }
    }

    @Test
    fun equalRatesUseLimitForm() {
        val k = ln(2.0) / 10
        val t = 7.0
        assertRel(k * t * exp(-k * t), PkEngine.bateman(t, k, k), 1e-12)
        assertRel(PkEngine.bateman(t, k, k), PkEngine.bateman(t, k * (1 + 1e-9), k), 1e-7)
    }

    @Test
    fun batemanHandlesFlipFlopWithoutOverflow() {
        val v = PkEngine.bateman(20_000.0, 0.001, 0.5)
        assertTrue(v.isFinite() && v >= 0)
    }

    @Test
    fun singleDoseAreaEqualsDoseOverKe() {
        val pk = PkParams(2.0, 12.0)
        val dose = PkDose(0, 100.0, pk)
        val step = 0.05
        var area = 0.0
        var t = 0.0
        while (t < 400) { area += PkEngine.bateman(t + step / 2, pk.ka, pk.ke) * step; t += step }
        assertRel(dose.effective / pk.ke, area * dose.amount, 1e-4)
    }

    @Test
    fun activeFractionAndBioavailabilityScaleLinearly() {
        val a = PkEngine.levelAt(listOf(PkDose(0, 100.0, PkParams(8.0, 100.0))), 48 * hour)
        val b = PkEngine.levelAt(listOf(PkDose(0, 100.0, PkParams(8.0, 100.0, 0.7, 0.5))), 48 * hour)
        assertRel(a * 0.35, b, 1e-12)
    }

    @Test
    fun matchesRk4OdeReference() {
        val pk = PkParams(3.0, 30.0)
        val doseTimes = listOf(0.0, 36.0, 60.0, 130.0)
        val doses = doseTimes.map { PkDose((it * hour).toLong(), 50.0, pk) }
        val dt = 0.001
        var a = 0.0; var c = 0.0; var t = 0.0; var next = 0
        val checkpoints = mapOf(10.0 to 0, 59.0 to 1, 131.5 to 2, 200.0 to 3)
        val results = DoubleArray(4)
        while (t < 200.0 + 1e-9) {
            while (next < doseTimes.size && doseTimes[next] <= t + 1e-9) { a += 50.0; next++ }
            checkpoints.entries.firstOrNull { abs(it.key - t) < dt / 2 }?.let { results[it.value] = c }
            fun f(a: Double, c: Double) = Pair(-pk.ka * a, pk.ka * a - pk.ke * c)
            val k1 = f(a, c); val k2 = f(a + dt / 2 * k1.first, c + dt / 2 * k1.second)
            val k3 = f(a + dt / 2 * k2.first, c + dt / 2 * k2.second); val k4 = f(a + dt * k3.first, c + dt * k3.second)
            a += dt / 6 * (k1.first + 2 * k2.first + 2 * k3.first + k4.first)
            c += dt / 6 * (k1.second + 2 * k2.second + 2 * k3.second + k4.second)
            t += dt
        }
        checkpoints.forEach { (time, idx) ->
            assertRel(results[idx], PkEngine.levelAt(doses, (time * hour).toLong()), 1e-6, "t=$time")
        }
    }

    /** Closed-form steady state for dose D every tau hours (reference for the numerical steady-state). */
    private fun analyticSteadyState(d: Double, tau: Double, ka: Double, ke: Double): Triple<Double, Double, Double> {
        fun css(t: Double) = d * ka / (ka - ke) * (exp(-ke * t) / (1 - exp(-ke * tau)) - exp(-ka * t) / (1 - exp(-ka * tau)))
        val tPeak = ln(ka * (1 - exp(-ke * tau)) / (ke * (1 - exp(-ka * tau)))) / (ka - ke)
        return Triple(css(tPeak), css(0.0), d / (ke * tau))
    }

    @Test
    fun longSimulationConvergesToAnalyticSteadyState() {
        val pk = PkParams(8.66, 189.0, 0.72)
        val tau = 84.0
        val doses = List(60) { PkDose((it * tau * hour).toLong(), 125.0, pk) }
        val start = (59 * tau * hour).toLong()
        val samples = LongArray(8401) { start + (it * tau / 8400 * hour).toLong() }
        val values = PkEngine.simulate(doses, samples)
        val (peak, trough, avg) = analyticSteadyState(125.0 * 0.72, tau, pk.ka, pk.ke)
        assertRel(peak, values.max(), 2e-3)
        assertRel(trough, values[0], 2e-3)
        var area = 0.0
        for (i in 1 until samples.size) area += (samples[i] - samples[i - 1]) * (values[i] + values[i - 1]) / 2
        assertRel(avg, area / (samples.last() - samples.first()), 2e-3)
    }
}
