package com.apollof.protocoltracker.ui.today

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

/** The Bloodwork line in Today and Journal (import doc §6.5). */
class JournalLineTest {
    private val t = Instant.parse("2026-09-24T07:30:00Z")
    private val e2Factor = BloodMarkers.find("estradiol")!!.siToConventional

    private fun draw(vararg results: MarkerResult) = JournalEntry.Bloodwork("b", t, results.toList(), createdAt = t)

    @Test
    fun oneResultIsSingular() {
        assertEquals(
            "1 result · all in range",
            bloodworkSummary(draw(MarkerResult("total_testosterone", 900.0))),
        )
    }

    @Test
    fun aCensoredValueAcrossALimitIsNotAllInRange() {
        // E2 <40 pmol/L against the lab's 20–150 pmol/L: the true value may be below 20.
        val e2 = MarkerResult("estradiol", 40 * e2Factor, "<", 20 * e2Factor, 150 * e2Factor)
        assertEquals("1 result", bloodworkSummary(draw(e2)))
        assertEquals("2 results", bloodworkSummary(draw(e2, MarkerResult("total_testosterone", 900.0))))
    }

    @Test
    fun outOfRangeCountsTheLabRangeAndCertainCensoredValues() {
        assertEquals(
            "3 results · 2 out of range",
            bloodworkSummary(
                draw(
                    MarkerResult("total_testosterone", 900.0, refLow = 300.0, refHigh = 800.0), // high by the lab
                    MarkerResult("fsh", 0.3, "<"), // certainly low
                    MarkerResult("estradiol", 30.0),
                ),
            ),
        )
        // Plain results read exactly as before.
        assertEquals(
            "2 results · 1 out of range",
            bloodworkSummary(draw(MarkerResult("total_testosterone", 1200.0), MarkerResult("estradiol", 30.0))),
        )
        assertEquals("2 results · all in range", bloodworkSummary(draw(MarkerResult("total_testosterone", 900.0), MarkerResult("estradiol", 30.0))))
    }
}
