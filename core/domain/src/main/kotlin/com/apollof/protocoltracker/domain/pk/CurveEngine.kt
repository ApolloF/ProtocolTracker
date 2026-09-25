package com.apollof.protocoltracker.domain.pk

import kotlin.math.exp

/**
 * One dose on a level curve. [peak] is the single-dose peak in the curve's display unit
 * (an absolute concentration, or active amount for relative curves).
 */
data class CurveDose(val atMs: Long, val peak: Double, val tmaxH: Double, val halfLifeH: Double)

/**
 * Steroid Plotter style curves (docs/MODELS.md): each dose rises linearly from 0 to its peak at tmax,
 * then decays exponentially with its half-life; doses add up.
 */
object CurveEngine {
    private const val MS_PER_HOUR = 3_600_000.0
    private const val LN2 = 0.6931471805599453

    /** Level at [atMs] contributed by a single [dose]. */
    fun contribution(dose: CurveDose, atMs: Long): Double {
        val t = (atMs - dose.atMs) / MS_PER_HOUR
        return when {
            t <= 0 -> 0.0
            t < dose.tmaxH -> dose.peak * t / dose.tmaxH
            else -> dose.peak * exp(-LN2 * (t - dose.tmaxH) / dose.halfLifeH)
        }
    }

    /**
     * Level at each of [sampleMs] (must be ascending).
     *
     * Doses are grouped by (tmax, half-life). Within a group, peaks happen in dose order, so the decaying part
     * is one running sum advanced by e^(−k·Δt) and only doses still rising are summed individually.
     * Cost is O(doses + samples × rising doses) per group.
     */
    fun simulate(doses: List<CurveDose>, sampleMs: LongArray): DoubleArray {
        val out = DoubleArray(sampleMs.size)
        if (doses.isEmpty() || sampleMs.isEmpty()) return out
        for ((params, group) in doses.groupBy { it.tmaxH to it.halfLifeH }) {
            val (tmaxH, halfLifeH) = params
            val k = LN2 / halfLifeH
            val tmaxMs = Math.round(tmaxH * MS_PER_HOUR)
            val sorted = group.sortedBy { it.atMs }
            var decaying = 0.0 // sum of post-peak contributions at time [decayTime]
            var decayTime = Long.MIN_VALUE
            var peaked = 0 // doses [0, peaked) have reached their peak
            var started = 0 // doses [0, started) have been given
            for (i in sampleMs.indices) {
                val t = sampleMs[i]
                if (decayTime != Long.MIN_VALUE && t > decayTime) {
                    decaying *= exp(-k * (t - decayTime) / MS_PER_HOUR)
                    decayTime = t
                }
                while (peaked < sorted.size && sorted[peaked].atMs + tmaxMs <= t) {
                    val d = sorted[peaked]
                    val sincePeakH = (t - d.atMs - tmaxMs) / MS_PER_HOUR
                    decaying += d.peak * exp(-k * sincePeakH)
                    if (decayTime == Long.MIN_VALUE) decayTime = t
                    peaked++
                }
                while (started < sorted.size && sorted[started].atMs <= t) started++
                var rising = 0.0
                for (j in peaked until started) {
                    val d = sorted[j]
                    rising += d.peak * ((t - d.atMs) / MS_PER_HOUR) / tmaxH
                }
                out[i] += decaying + rising
            }
        }
        return out
    }

    fun levelAt(doses: List<CurveDose>, atMs: Long): Double = simulate(doses, longArrayOf(atMs))[0]

    /** Time from dose to peak and back down to [fraction] of the peak. */
    fun hoursUntilBelow(tmaxH: Double, halfLifeH: Double, fraction: Double): Double =
        tmaxH + halfLifeH * kotlin.math.ln(1 / fraction) / LN2
}
