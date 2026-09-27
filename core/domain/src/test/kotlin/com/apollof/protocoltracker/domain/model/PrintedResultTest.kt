package com.apollof.protocoltracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unlisted results read as the lab printed them: never converted, no float noise, sign and unit kept (import doc §7). */
class PrintedResultTest {
    private fun other(value: Double, qualifier: String? = null, unit: String? = "pmol/l") =
        MarkerResult("other:vrij_t4", value, qualifier = qualifier, name = "Vrij T4", unit = unit)

    @Test
    fun theNumberKeepsEveryPrintedDecimalWithoutNoise() {
        assertEquals("15.2", other(15.2).printedNumber())
        assertEquals("0.123456", other(0.123456).printedNumber())
        assertEquals("250", other(250.0).printedNumber())
        assertEquals("0.3", other(0.1 + 0.2).printedNumber())
        assertEquals("5", other(5.0, "<").printedNumber())
    }

    @Test
    fun theValueKeepsItsSignAndUnit() {
        assertEquals("15.2 pmol/l", other(15.2).printedValue())
        assertEquals("<5 mg/l", other(5.0, "<", "mg/l").printedValue())
        assertEquals(">90 mL/min", other(90.0, ">", "mL/min").printedValue())
        assertEquals("<5", other(5.0, "<", null).printedValue())
        assertEquals("<5", other(5.0, "<", " ").printedValue())
    }

    @Test
    fun aBlankUnitLeavesNoStraySpaceInReportsOrCaptions() {
        for (unit in listOf(null, "", " ")) {
            val r = MarkerResult("other:index", 1.2, refLow = 0.5, refHigh = 2.0, name = "Index", unit = unit)
            assertEquals("Index 1.2", r.printedText(), "unit = '$unit'")
            assertEquals("0.5–2", r.printedLabRange(), "unit = '$unit'")
        }
        val r = MarkerResult("other:crp", 5.0, "<", refHigh = 10.0, name = "CRP", unit = " mg/l ")
        assertEquals("CRP <5 mg/l", r.printedText())
        assertEquals("< 10 mg/l", r.printedLabRange())
        assertEquals("<5 mg/l", r.printedValue())
    }
}
