package com.apollof.protocoltracker.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The flag rule of import doc §6.4: lab range first, limits in range, censored values only when certain. */
class ResultFlagTest {
    private val low = MarkerFlag.LOW
    private val normal = MarkerFlag.NORMAL
    private val high = MarkerFlag.HIGH

    private fun r(value: Double, qualifier: String? = null, refLow: Double? = null, refHigh: Double? = null, marker: String = "other:test") =
        MarkerResult(marker, value, qualifier = qualifier, refLow = refLow, refHigh = refHigh)

    @Test
    fun plainResultsFlagExactlyAsTheMarkerDefault() {
        for (marker in BloodMarkers.all) {
            val limits = listOfNotNull(marker.refLow, marker.refHigh)
            val values = listOf(0.0, 0.001, 1.0, 10_000.0) + limits.flatMap { listOf(it * 0.999, it, it * 1.001) }
            for (v in values) {
                val result = MarkerResult(marker.key, v)
                assertEquals(marker.flag(v), result.flag(), "${marker.key} $v")
                assertFalse(result.unclear, "${marker.key} $v")
            }
        }
    }

    @Test
    fun exactValuesAgainstALabRange() {
        assertEquals(low, r(9.9, refLow = 10.0, refHigh = 20.0).flag())
        assertEquals(normal, r(10.0, refLow = 10.0, refHigh = 20.0).flag())
        assertEquals(normal, r(20.0, refLow = 10.0, refHigh = 20.0).flag())
        assertEquals(high, r(20.1, refLow = 10.0, refHigh = 20.0).flag())
        assertEquals(normal, r(0.0, refHigh = 45.0).flag())
        assertEquals(high, r(46.0, refHigh = 45.0).flag())
        assertEquals(normal, r(500.0, refLow = 60.0).flag())
        assertEquals(low, r(59.0, refLow = 60.0).flag())
    }

    @Test
    fun theLabRangeIsUsedAloneAndANullSideHasNoLimit() {
        // Hematocrit's default is 40–52: the lab's "< 54" drops the low limit, not only the high one.
        assertEquals(low, MarkerResult("hematocrit", 38.0).flag())
        assertEquals(normal, MarkerResult("hematocrit", 38.0, refHigh = 54.0).flag())
        assertEquals(normal, MarkerResult("hematocrit", 53.0, refHigh = 54.0).flag())
        assertEquals(high, MarkerResult("hematocrit", 53.0).flag())
        assertEquals(RefRange(null, 54.0), MarkerResult("hematocrit", 38.0, refHigh = 54.0).range())
        assertEquals(RefRange(40.0, 52.0), MarkerResult("hematocrit", 38.0).range())
    }

    @Test
    fun belowValues() {
        assertEquals(low, r(1.5, "<", 1.5, 12.4).flag()) // x = low: the true value is below the limit
        assertEquals(low, r(0.3, "<", 1.5, 12.4).flag())
        assertNull(r(1.6, "<", 1.5, 12.4).flag())
        assertEquals(normal, r(5.0, "<", 0.0, 44.0).flag())
        assertEquals(normal, r(44.0, "<", 0.0, 44.0).flag())
        assertNull(r(45.0, "<", 0.0, 44.0).flag())
        assertEquals(normal, r(1.0, "<", refHigh = 10.0).flag())
        assertNull(r(11.0, "<", refHigh = 10.0).flag())
        assertNull(r(70.0, "<", refLow = 60.0).flag())
        assertEquals(low, r(60.0, "<", refLow = 60.0).flag())
    }

    @Test
    fun aboveValues() {
        assertEquals(high, r(57.0, ">", 10.0, 57.0).flag()) // x = high: the true value is above the limit
        assertEquals(high, r(200.0, ">", 10.0, 57.0).flag())
        assertNull(r(56.0, ">", 10.0, 57.0).flag())
        assertNull(r(5.0, ">", 10.0, 57.0).flag())
        assertEquals(normal, r(90.0, ">", refLow = 60.0).flag())
        assertEquals(normal, r(60.0, ">", refLow = 60.0).flag())
        assertNull(r(59.0, ">", refLow = 60.0).flag())
        assertEquals(high, r(10.0, ">", refHigh = 10.0).flag())
        assertNull(r(9.0, ">", refHigh = 10.0).flag())
    }

    @Test
    fun workedCasesFromTheImportDoc() {
        assertEquals(low, MarkerResult("fsh", 0.3, "<", 1.5, 12.4).flag())
        val e2 = MarkerResult("estradiol", 40 * 0.2724, "<", 20 * 0.2724, 150 * 0.2724)
        assertNull(e2.flag())
        assertTrue(e2.unclear)
        assertEquals(normal, MarkerResult("egfr", 90.0, ">", refLow = 60.0).flag())
        assertEquals(high, MarkerResult("shbg", 200.0, ">", 10.0, 57.0).flag())
        assertEquals(normal, r(1.0, "<", refHigh = 10.0).flag())
        assertEquals(normal, r(5.0, "<", 0.0, 44.0).flag())
    }

    @Test
    fun censoredValuesAgainstTheMarkerDefault() {
        assertEquals(low, MarkerResult("fsh", 0.3, "<").flag())
        assertNull(MarkerResult("estradiol", 20.0, "<").flag()) // default 10–40
        assertEquals(normal, MarkerResult("ldl", 100.0, "<").flag()) // default < 130
        assertEquals(normal, MarkerResult("egfr", 90.0, ">").flag()) // default > 90
    }

    @Test
    fun noRangeOrAnUnknownQualifierGivesNoFlag() {
        val unlisted = r(15.2)
        assertNull(unlisted.range())
        assertNull(unlisted.flag())
        assertFalse(unlisted.unclear)
        val approx = MarkerResult("ldl", 100.0, qualifier = "~")
        assertNull(approx.flag())
        assertTrue(approx.unclear)
        assertNull(MarkerResult("ldl", 100.0, qualifier = "").flag())
        assertNull(MarkerResult("ldl", 100.0, qualifier = "<=").flag())
    }

    @Test
    fun anInvalidLabRangeIsIgnoredAndNeverThrows() {
        val bad = listOf(
            MarkerResult("hematocrit", 53.0, refLow = 60.0, refHigh = 40.0),
            MarkerResult("hematocrit", 53.0, refLow = -1.0, refHigh = 54.0),
            MarkerResult("hematocrit", 53.0, refHigh = Double.NaN),
            MarkerResult("hematocrit", 53.0, refLow = Double.POSITIVE_INFINITY),
        )
        for (result in bad) {
            assertNull(result.labRange(), result.toString())
            assertEquals(RefRange(40.0, 52.0), result.range(), result.toString())
            assertEquals(high, result.flag(), result.toString())
        }
        assertNull(r(1.0, refLow = 5.0, refHigh = 2.0).flag())
        assertEquals(RefRange(3.0, 3.0), r(3.0, refLow = 3.0, refHigh = 3.0).labRange())
    }

    @Test
    fun drawsCountOutOfRangeAndUnclearResults() {
        val t = Instant.parse("2026-09-24T07:30:00Z")
        val draw = JournalEntry.Bloodwork(
            "b", t,
            listOf(
                MarkerResult("hematocrit", 53.0), // High by default
                MarkerResult("hemoglobin", 17.0, refLow = 13.0, refHigh = 16.5), // High by the lab, in range by default
                MarkerResult("total_testosterone", 1000.0, refLow = 300.0, refHigh = 1100.0), // in range by the lab
                MarkerResult("fsh", 0.3, "<"), // Low
                MarkerResult("estradiol", 11.0, "<", 5.4, 40.9), // unclear
                MarkerResult("other:crp", 1.0, "<", refHigh = 10.0, name = "CRP", unit = "mg/l"), // in range
                MarkerResult("other:vrij_t4", 25.0, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"), // High
                MarkerResult("other:vitamine_d", 60.0, name = "Vitamine D", unit = "nmol/l"), // no range
            ),
            createdAt = t,
        )
        assertEquals(4, draw.outOfRange)
        assertEquals(1, draw.unclear)
        assertEquals(draw.results[4], draw.result("estradiol"))
        assertNull(draw.result("ldl"))
    }

    @Test
    fun trendsCarryTheLatestResult() {
        val t = Instant.parse("2026-09-01T07:30:00Z")
        val later = t.plusSeconds(86_400 * 30)
        val first = JournalEntry.Bloodwork("b1", t, listOf(MarkerResult("estradiol", 30.0)), createdAt = t)
        val latest = MarkerResult("estradiol", 10.9, "<", 5.4, 40.9)
        val second = JournalEntry.Bloodwork("b2", later, listOf(latest), createdAt = later)
        val trend = markerTrends(listOf(first, second)).single()
        assertEquals(latest, trend.result)
        assertEquals(10.9, trend.value)
        assertEquals(later, trend.at)
    }
}
