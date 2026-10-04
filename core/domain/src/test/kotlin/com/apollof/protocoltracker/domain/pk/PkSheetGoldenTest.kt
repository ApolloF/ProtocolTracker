package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Preset curves against a reference implementation of the model (docs/MODELS.md). The expected values were computed
 * outside this code base by a separate JavaScript implementation of the per-dose formula, run in Node with the sheet
 * parameters and an hourly summation (each dose and each sum rounded to 0.001 ng/dL).
 * Doses start at hour 0; the reference samples whole hours, so grid values are compared at whole hours.
 * Values are in ng/dL, the model's unit, whatever unit a preset is shown in.
 */
class PkSheetGoldenTest {
    private val scale = LevelScale(relative = false, unit = LevelUnit.NG_DL, baseUnit = BaseUnit.MG)
    private val h = 3_600_000L

    private fun pk(id: String) = assertNotNull(Presets.byId("preset:$id")?.pk, id)

    /**
     * The reference grid for [dose] mg of preset [id] every [everyH] hours for [weeks] weeks, at ascending [hours],
     * with the level adjusted by [adjustPercent] %.
     */
    private fun grid(id: String, dose: Double, everyH: Double, weeks: Int, hours: List<Int>, adjustPercent: Int = 0): DoubleArray {
        val adjusted = scale.copy(factor = LevelAdjustments.factorOf(adjustPercent))
        val doses = generateSequence(0.0) { it + everyH }.takeWhile { it < weeks * 168.0 }
            .map { adjusted.curve(DoseEvent((it * h).toLong(), dose, pk(id), planned = true)) }.toList()
        return CurveEngine.simulate(doses, LongArray(hours.size) { hours[it] * h })
    }

    /** Within 0.05 ng/dL: the reference's 0.001 rounding of up to a few dozen terms. */
    private fun assertGrid(name: String, expected: List<Pair<Int, Double>>, actual: DoubleArray) {
        expected.forEachIndexed { i, (hour, value) ->
            assertTrue(abs(actual[i] - value) <= 0.05, "$name at h $hour: ${actual[i]} vs reference $value")
        }
    }

    private fun check(name: String, id: String, dose: Double, everyH: Double, weeks: Int, expected: List<Pair<Int, Double>>, adjustPercent: Int = 0) =
        assertGrid(name, expected, grid(id, dose, everyH, weeks, expected.map { it.first }, adjustPercent))

    @Test
    fun testEnanthateSingleDose() = check(
        "Test E 250 mg once", "test-enan", 250.0, 168.0, 1,
        listOf(1 to 28.019, 12 to 336.228, 24 to 672.457, 33 to 924.628, 34 to 930.414, 48 to 879.535, 168 to 543.143, 336 to 276.591, 672 to 71.728),
    )

    @Test
    fun testEnanthateTwiceWeekly() = check(
        "Test E 100 mg / 3.5 d", "test-enan", 100.0, 84.0, 12,
        listOf(
            1848 to 1062.425, 1860 to 1146.919, 1881 to 1300.378, 1882 to 1298.966, 1908 to 1170.145, 1932 to 1062.607,
            1944 to 1147.092, 1965 to 1300.537, 1966 to 1299.125, 1992 to 1170.288, 2015 to 1067.013, 2351 to 276.706,
        ),
    )

    @Test
    fun testCypionateWeekly() = check(
        "Test C 150 mg / 7 d", "test-cyp", 150.0, 168.0, 12,
        listOf(1848 to 770.495, 1872 to 808.052, 1956 to 990.681, 1968 to 942.149, 2015 to 773.897, 2184 to 381.481, 2351 to 189.626),
    )

    @Test
    fun testPropionateEveryOtherDay() = check(
        "Test P 50 mg / 2 d", "test-prop", 50.0, 48.0, 6,
        listOf(840 to 1706.834, 852 to 1316.572, 865 to 967.793, 866 to 993.604, 887 to 1669.497, 1007 to 969.302, 1100 to 72.801),
    )

    @Test
    fun nppEveryOtherDay() = check(
        "NPP 100 mg / 2 d", "npp", 100.0, 48.0, 6,
        listOf(840 to 2031.68, 852 to 1758.493, 864 to 1522.039, 876 to 1763.114, 887 to 2008.367, 1007 to 1540.506, 1100 to 503.066),
    )

    @Test
    fun trenEnanthateBasicModel() = check(
        "Tren E 200 mg / 3.5 d", "tren-enan", 200.0, 84.0, 12,
        listOf(1848 to 8908.624, 1860 to 9202.203, 1932 to 8922.76, 1947 to 9271.11, 1948 to 9278.364, 2015 to 8946.022, 2351 to 3774.519),
    )

    @Test
    fun testIsocaproateBasicModelWithMultiplier() = check(
        "Test Iso 100 mg / 7 d", "test-iso", 100.0, 168.0, 4,
        listOf(
            0 to 0.0, 1 to 45.248, 6 to 227.165, 12 to 372.418, 27 to 545.786, 28 to 550.737, 29 to 545.63, 48 to 457.111,
            167 to 150.847, 600 to 368.834, 839 to 39.793,
        ),
    )

    @Test
    fun dianabolDailyOralBasicModel() = check(
        "Dianabol 30 mg / day", "methandienone", 30.0, 24.0, 4,
        listOf(624 to 198.789, 625 to 2776.614, 626 to 3629.531, 627 to 3180.618, 628 to 2787.226, 630 to 2140.396, 636 to 969.305, 647 to 226.846, 700 to 4.93),
    )

    // Presets of presets-2026-10c: one case per new compound, with the reference model's parameters.

    @Test
    fun testosteroneGelDaily() = check(
        "Testosterone gel 50 mg / day", "test-gel", 50.0, 24.0, 3,
        listOf(0 to 0.0, 1 to 22.2, 5 to 111.0, 10 to 222.0, 11 to 219.861, 24 to 193.862, 34 to 397.974, 480 to 926.102, 490 to 1062.648, 503 to 936.989),
    )

    @Test
    fun testosteroneBaseSublingualEvery8h() = check(
        "Test base 2.5 mg / 8 h", "test-base-sl", 2.5, 8.0, 1,
        listOf(0 to 0.0, 1 to 1195.454, 2 to 388.487, 3 to 126.247, 8 to 0.458, 9 to 1195.603, 10 to 388.535, 100 to 41.031),
    )

    @Test
    fun estradiolGelDaily() = check(
        "Estradiol gel 1.5 mg / day", "estradiol-gel", 1.5, 24.0, 2,
        listOf(1 to 0.812, 4 to 3.248, 12 to 2.785, 24 to 2.21, 25 to 2.98, 28 to 5.294, 300 to 7.507, 313 to 6.655, 336 to 5.964),
    )

    @Test
    fun estradiolCypionateWeekly() = check(
        "Estradiol cypionate 5 mg / 7 d", "estradiol-cyp", 5.0, 168.0, 6,
        listOf(6 to 4.991, 17 to 13.982, 24 to 13.245, 168 to 4.35, 169 to 5.149, 840 to 5.973, 857 to 19.22, 1008 to 5.98),
    )

    @Test
    fun estradiolValerateBasicModelWithMultiplier() = check(
        "Estradiol valerate 5 mg / 5 d", "estradiol-val", 5.0, 120.0, 6,
        listOf(1 to 2.012, 10 to 15.221, 31 to 27.43, 32 to 27.449, 48 to 24.054, 120 to 13.279, 600 to 20.979, 631 to 43.674, 1000 to 40.878),
    )

    @Test
    fun progesteroneOralWithMultiplier() = check(
        "Progesterone oral 200 mg / day", "progesterone-oral", 200.0, 24.0, 2,
        listOf(1 to 1356.24, 2 to 2003.644, 12 to 1477.998, 24 to 1025.883, 25 to 2351.378, 26 to 2968.958, 312 to 1979.477, 313 to 3276.393, 320 to 3221.084),
    )

    @Test
    fun progesteroneVaginalDaily() = check(
        "Progesterone vaginal 100 mg / day", "progesterone-vaginal", 100.0, 24.0, 2,
        listOf(3 to 615.0, 6 to 1230.0, 7 to 1189.418, 12 to 1005.729, 24 to 672.408, 30 to 1779.806, 312 to 1215.886, 318 to 2224.188),
    )

    @Test
    fun ostarineBasicModel() = check(
        "Ostarine 25 mg / day", "ostarine", 25.0, 24.0, 3,
        listOf(1 to 1083.072, 3 to 2625.0, 9 to 4593.75, 10 to 4462.975, 24 to 2978.678, 480 to 5957.351, 489 to 9187.496, 503 to 6131.918),
    )

    @Test
    fun ligandrolDaily() = check(
        "Ligandrol 10 mg / day", "ligandrol", 10.0, 24.0, 3,
        listOf(1 to 1618.5, 3 to 4855.5, 4 to 4874.913, 24 to 3071.003, 480 to 7214.73, 483 to 11587.085, 500 to 7913.346),
    )

    @Test
    fun andarineTwiceDaily() = check(
        "Andarine 50 mg / 12 h", "andarine", 50.0, 12.0, 2,
        listOf(1 to 24484.143, 2 to 20497.351, 12 to 3465.979, 13 to 27385.751, 300 to 3931.954, 301 to 27775.85, 310 to 5610.257),
    )

    @Test
    fun testoloneDaily() = check(
        "Testolone 10 mg / day", "testolone", 10.0, 24.0, 3,
        listOf(3 to 2235.0, 6 to 4470.0, 7 to 4401.22, 24 to 3381.329, 480 to 10874.616, 486 to 14378.485, 500 to 11572.595),
    )

    @Test
    fun cardarineBasicModel() = check(
        "Cardarine 20 mg / day", "cardarine", 20.0, 24.0, 3,
        listOf(1 to 745.796, 7 to 2637.751, 8 to 2648.702, 24 to 1521.28, 480 to 2693.842, 488 to 4690.251, 500 to 3094.413),
    )

    @Test
    fun s23BasicModel() = check(
        "S-23 10 mg / day", "s-23", 10.0, 24.0, 3,
        listOf(1 to 5775.0, 2 to 6178.315, 24 to 136.523, 25 to 5889.801, 26 to 6274.851, 480 to 138.69),
    )

    // Adjust level: every dose is multiplied by 1 + percent / 100.

    @Test
    fun adjustedPlus25() = check(
        "Test E 250 mg once, +25 %", "test-enan", 250.0, 168.0, 1,
        listOf(1 to 35.024, 12 to 420.285, 24 to 840.571, 33 to 1155.785, 34 to 1163.017, 48 to 1099.419, 168 to 678.928, 336 to 345.739, 672 to 89.66),
        adjustPercent = 25,
    )

    @Test
    fun adjustedMinus40() = check(
        "Test C 150 mg / 7 d, -40 %", "test-cyp", 150.0, 168.0, 12,
        listOf(1848 to 462.297, 1872 to 484.832, 1956 to 594.409, 1968 to 565.289, 2015 to 464.336, 2184 to 228.889, 2351 to 113.774),
        adjustPercent = -40,
    )

    @Test
    fun adjustedBasicModel() = check(
        "Estradiol valerate 5 mg / 5 d, +25 %", "estradiol-val", 5.0, 120.0, 6,
        listOf(1 to 2.515, 10 to 19.027, 31 to 34.288, 32 to 34.311, 48 to 30.068, 120 to 16.599, 600 to 26.223, 631 to 54.593, 1000 to 51.097),
        adjustPercent = 25,
    )

    // Steady state: expected [peak, trough, average] of the reference per-dose curve summed over 400 past doses,
    // at 1-minute steps plus the exact peak. Levels.steadyState settles for ≥ 10 half-lives and samples 1000 points,
    // so it is held to 0.5 %.

    private val anchor = Instant.parse("2026-01-05T00:00:00Z")

    private fun assertSteady(id: String, dose: Double, everyH: Double, peak: Double, trough: Double, average: Double, adjustPercent: Int = 0) {
        val preset = assertNotNull(Presets.byId("preset:$id"))
        val item = PlanItem("i", null, preset.id, Amount(dose, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(), Schedule.EveryHours(everyH, anchor))
        val adjusted = scale.copy(factor = LevelAdjustments.factorOf(adjustPercent))
        val ss = assertNotNull(Levels.steadyState(listOf(item), mapOf(preset.id to preset), adjusted, anchor, ZoneId.of("UTC")))
        for ((what, actual, expected) in listOf(Triple("peak", ss.peak, peak), Triple("trough", ss.trough, trough), Triple("average", ss.average, average))) {
            assertTrue(abs(actual / expected - 1) < 0.005, "${preset.commonName} $dose mg / $everyH h $what: $actual vs reference $expected")
        }
    }

    @Test
    fun steadyStates() {
        assertSteady("test-enan", 300.0, 168.0, 2281.455, 1328.093, 1770.108)
        assertSteady("test-enan", 100.0, 168.0, 760.485, 442.698, 590.036)
        assertSteady("test-cyp", 200.0, 168.0, 1321.194, 1027.774, 1163.272)
        assertSteady("test-prop", 100.0, 48.0, 3527.078, 1885.385, 2636.460)
        assertSteady("npp", 100.0, 48.0, 2031.741, 1522.086, 1766.201)
        assertSteady("tren-enan", 400.0, 168.0, 9836.650, 8137.903, 9244.312)
        assertSteady("estradiol-cyp", 5.0, 168.0, 19.253, 5.983, 11.479)
        assertSteady("estradiol-val", 5.0, 120.0, 43.854, 21.128, 32.624)
        assertSteady("test-gel", 50.0, 24.0, 1070.791, 935.07, 1001.747)
        assertSteady("ostarine", 25.0, 24.0, 9187.5, 5957.357, 7767.707)
        assertSteady("test-enan", 300.0, 168.0, 1368.873, 796.856, 1062.065, adjustPercent = -40)
        assertSteady("test-enan", 300.0, 168.0, 2851.819, 1660.116, 2212.635, adjustPercent = 25)
    }
}
