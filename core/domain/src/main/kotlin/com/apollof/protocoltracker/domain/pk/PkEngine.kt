package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.PkParams
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.max

/** A dose entering the depot. [amount] is in base units before active-fraction/bioavailability. */
data class PkDose(val atMs: Long, val amount: Double, val pk: PkParams) {
    val effective: Double get() = amount * pk.activeFraction * pk.bioavailability
}

object PkEngine {
    private const val MS_PER_HOUR = 3_600_000.0

    /**
     * Central-compartment amount at [t] hours after a unit dose (Bateman function).
     * Uses expm1 and the smaller exponential to avoid cancellation and overflow; a series for ka ≈ ke.
     */
    fun bateman(t: Double, ka: Double, ke: Double): Double {
        if (t <= 0) return 0.0
        val delta = ka - ke
        val x = delta * t
        if (abs(x) < 1e-7) return ka * t * exp(-ke * t) * (1 - x / 2 + x * x / 6)
        return if (delta > 0) ka * exp(-ke * t) * -expm1(-x) / delta
        else ka * exp(-ka * t) * expm1(x) / delta
    }

    /** Time of peak (hours) after a single dose. */
    fun tmaxHours(ka: Double, ke: Double): Double =
        if (abs(ka - ke) < max(ka, ke) * 1e-8) 1 / ke else ln(ka / ke) / (ka - ke)

    /**
     * Amount in the central compartment at each of [sampleMs] (must be ascending).
     *
     * Doses are grouped by kinetic parameters; each group's (depot, central) state is advanced exactly
     * between consecutive event/sample times with the closed-form solution of the linear ODE system:
     *   A' = −ka·A,  C' = ka·A − ke·C.
     * Cost is O(doses + samples) per group instead of O(doses × samples) for naive superposition.
     */
    fun simulate(doses: List<PkDose>, sampleMs: LongArray): DoubleArray {
        val out = DoubleArray(sampleMs.size)
        if (doses.isEmpty() || sampleMs.isEmpty()) return out
        for ((params, group) in doses.groupBy { it.pk.ka to it.pk.ke }) {
            val (ka, ke) = params
            val sorted = group.sortedBy { it.atMs }
            var depot = 0.0
            var central = 0.0
            var time = sorted.first().atMs
            var next = 0
            fun advanceTo(targetMs: Long) {
                if (targetMs <= time) return
                val dt = (targetMs - time) / MS_PER_HOUR
                central = central * exp(-ke * dt) + depot * bateman(dt, ka, ke)
                depot *= exp(-ka * dt)
                time = targetMs
            }
            for (i in sampleMs.indices) {
                val target = sampleMs[i]
                while (next < sorted.size && sorted[next].atMs <= target) {
                    advanceTo(sorted[next].atMs)
                    depot += sorted[next].effective
                    next++
                }
                if (target < time) continue // sample before the group's first dose
                advanceTo(target)
                out[i] += central
            }
        }
        return out
    }

    fun levelAt(doses: List<PkDose>, atMs: Long): Double = simulate(doses, longArrayOf(atMs))[0]
}
