package com.apollof.protocoltracker.domain.units

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation

/** Quick-step amounts for adjusting a single logged dose. */
object DoseAdjust {
    private val niceSteps = doubleArrayOf(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0)

    /**
     * Two step sizes (small, large) in [planned]'s unit. Tablet doses step by half and whole tablets;
     * mass doses with a known tablet strength step by half and whole tablets of that strength;
     * everything else steps by a round number near 10 % of the dose and twice that.
     */
    fun steps(planned: Amount, formulation: Formulation): Pair<Double, Double> {
        if (planned.unit == DoseUnit.TABLET) return 0.5 to 1.0
        val tablet = formulation.perTablet
        if (tablet != null && (planned.unit == DoseUnit.MG || planned.unit == DoseUnit.IU)) return tablet / 2 to tablet
        if (tablet != null && planned.unit == DoseUnit.MCG) return tablet * 500 to tablet * 1000
        val small = niceSteps.lastOrNull { it <= planned.value * 0.1 + 1e-12 } ?: niceSteps.first()
        return small to small * 2
    }

    /** [planned] ± [delta], or null when the result would not be a positive amount. */
    fun apply(planned: Amount, delta: Double): Amount? {
        val value = planned.value + delta
        return if (value > 1e-9) Amount(roundTo(value, 4), planned.unit) else null
    }

    private fun roundTo(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        return Math.round(value * factor) / factor
    }
}
