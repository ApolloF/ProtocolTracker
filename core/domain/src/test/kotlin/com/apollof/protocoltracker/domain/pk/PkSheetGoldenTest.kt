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
 */
class PkSheetGoldenTest {
    private val scale = LevelScale(relative = false, unit = LevelUnit.NG_DL, baseUnit = BaseUnit.MG)
    private val h = 3_600_000L

    private fun pk(id: String) = assertNotNull(Presets.byId("preset:$id")?.pk, id)

    /** The reference grid for [dose] mg of preset [id] every [everyH] hours for [weeks] weeks, at ascending [hours]. */
    private fun grid(id: String, dose: Double, everyH: Double, weeks: Int, hours: List<Int>): DoubleArray {
        val doses = generateSequence(0.0) { it + everyH }.takeWhile { it < weeks * 168.0 }
            .map { scale.curve(DoseEvent((it * h).toLong(), dose, pk(id), planned = true)) }.toList()
        return CurveEngine.simulate(doses, LongArray(hours.size) { hours[it] * h })
    }

    /** Within 0.05 ng/dL: the reference's 0.001 rounding of up to a few dozen terms. */
    private fun assertGrid(name: String, expected: List<Pair<Int, Double>>, actual: DoubleArray) {
        expected.forEachIndexed { i, (hour, value) ->
            assertTrue(abs(actual[i] - value) <= 0.05, "$name at h $hour: ${actual[i]} vs reference $value")
        }
    }

    private fun check(name: String, id: String, dose: Double, everyH: Double, weeks: Int, expected: List<Pair<Int, Double>>) =
        assertGrid(name, expected, grid(id, dose, everyH, weeks, expected.map { it.first }))

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

    // Steady state: expected [peak, trough, average] of the reference per-dose curve summed over 400 past doses,
    // at 1-minute steps plus the exact peak. Levels.steadyState settles for ≥ 10 half-lives and samples 1000 points,
    // so it is held to 0.5 %.

    private val anchor = Instant.parse("2026-01-05T00:00:00Z")

    private fun assertSteady(id: String, dose: Double, everyH: Double, peak: Double, trough: Double, average: Double) {
        val preset = assertNotNull(Presets.byId("preset:$id"))
        val item = PlanItem("i", null, preset.id, Amount(dose, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(), Schedule.EveryHours(everyH, anchor))
        val ss = assertNotNull(Levels.steadyState(listOf(item), mapOf(preset.id to preset), scale, anchor, ZoneId.of("UTC")))
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
    }
}
