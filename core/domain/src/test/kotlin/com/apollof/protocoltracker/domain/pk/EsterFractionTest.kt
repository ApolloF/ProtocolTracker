package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.CompoundCategory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every injectable preset's F is its ester fraction: the parent molecule's molar mass over the ester's, from the
 * molecular formulas (review 2026-10, L1: Deca, DHB and Nebido were off by 14 %, 43 % and 3 %).
 */
class EsterFractionTest {
    /** IUPAC standard atomic weights (abridged). */
    private val weights = mapOf('C' to 12.011, 'H' to 1.008, 'N' to 14.007, 'O' to 15.999)

    /** Molar mass of a formula such as `C19H28O2` (single-letter elements only). */
    private fun mass(formula: String): Double {
        var total = 0.0
        var i = 0
        while (i < formula.length) {
            val element = formula[i++]
            val start = i
            while (i < formula.length && formula[i].isDigit()) i++
            val count = if (i > start) formula.substring(start, i).toInt() else 1
            total += weights.getValue(element) * count
        }
        return total
    }

    private val testosterone = "C19H28O2"
    private val nandrolone = "C18H26O2"
    private val trenbolone = "C18H22O2"
    private val drostanolone = "C20H32O2"
    private val methenolone = "C20H30O2"
    private val boldenone = "C19H26O2"
    private val stanozolol = "C21H32N2O"

    /** Preset id → (parent formula, ester formula); a suspension's ester is the parent itself. */
    private val formulas = mapOf(
        "test-enan" to (testosterone to "C26H40O3"),
        "test-cyp" to (testosterone to "C27H40O3"),
        "test-prop" to (testosterone to "C22H32O3"),
        "test-undec" to (testosterone to "C30H48O3"),
        "test-pp" to (testosterone to "C28H36O3"),
        "test-iso" to (testosterone to "C25H38O3"),
        "test-dec" to (testosterone to "C29H46O3"),
        "test-susp" to (testosterone to testosterone),
        "deca" to (nandrolone to "C28H44O3"),
        "npp" to (nandrolone to "C27H32O3"),
        "tren-ace" to (trenbolone to "C20H24O3"),
        "tren-enan" to (trenbolone to "C25H34O3"),
        "tren-hex" to (trenbolone to "C26H34O4"),
        "mast-prop" to (drostanolone to "C23H36O3"),
        "mast-enan" to (drostanolone to "C27H44O3"),
        "primo-enan" to (methenolone to "C27H42O3"),
        "eq" to (boldenone to "C30H44O3"),
        "bold-cyp" to (boldenone to "C27H38O3"),
        // 1-testosterone is an isomer of testosterone; trestolone (7α-methyl-19-nortestosterone) has the same formula.
        "dhb" to (testosterone to "C27H40O3"),
        "ment" to (testosterone to "C21H30O3"),
        "winstrol-depot" to (stanozolol to stanozolol),
    )

    @Test
    fun formulaMassesMatchTheMolarMassTable() {
        assertEquals(MolarMass.byGroup.getValue("Testosterone"), mass(testosterone), 0.02)
        assertEquals(MolarMass.byGroup.getValue("Nandrolone"), mass(nandrolone), 0.02)
        assertEquals(MolarMass.byGroup.getValue("Stanozolol"), mass(stanozolol), 0.02)
    }

    @Test
    fun everyInjectablePresetsFractionIsItsEsterFraction() {
        val injectables = Presets.all.filter { it.category == CompoundCategory.INJECTABLE_STEROID }
        assertEquals(injectables.map { it.id.removePrefix("preset:") }.toSet(), formulas.keys)
        for (preset in injectables) {
            val (parent, ester) = formulas.getValue(preset.id.removePrefix("preset:"))
            val expected = mass(parent) / mass(ester)
            val actual = preset.pk!!.activeFraction
            // The sheet rounds to two decimals; NPP's 0.67 for 0.678 is the widest rounding kept.
            assertTrue(abs(actual / expected - 1) < 0.015, "${preset.commonName}: F $actual, ester fraction ${"%.3f".format(expected)}")
        }
    }
}
