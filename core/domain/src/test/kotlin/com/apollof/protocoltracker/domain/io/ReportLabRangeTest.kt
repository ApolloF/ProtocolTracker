package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.model.RefRange
import com.apollof.protocoltracker.domain.model.markerTrends
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.pk.LevelScale
import com.apollof.protocoltracker.domain.pk.labPoints
import com.apollof.protocoltracker.domain.pk.levelDisplay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lab ranges, censored values and unlisted results in reports and on Levels (import doc §6.5, §7). */
class ReportLabRangeTest {
    private val t = Instant.parse("2026-09-24T07:30:00Z")
    private val time = LocalTime.of(9, 30)
    private val e2Factor = BloodMarkers.find("estradiol")!!.siToConventional

    private fun lines(vararg results: MarkerResult) = bloodworkReport(time, draw(*results)).results

    private fun draw(vararg results: MarkerResult) = JournalEntry.Bloodwork("b", t, results.toList(), createdAt = t)

    private fun markdown(vararg results: MarkerResult) = MarkdownReport.render(
        ReportBuilder.build(
            Protocol(emptyList(), emptyList(), emptyMap()), emptyList(), listOf(draw(*results)),
            LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-24"), t, ZoneId.of("Europe/Amsterdam"), locale = Locale.ENGLISH,
        ),
    )

    @Test
    fun plainResultsReadAsBefore() {
        assertEquals(
            listOf(
                "Total testosterone 1200 ng/dL (41.6 nmol/L) · ref 264–916 ng/dL · high",
                "FSH 3 IU/L · ref 1.5–12.4 IU/L · in range",
                "some_key 1.23",
            ),
            lines(MarkerResult("total_testosterone", 1200.0), MarkerResult("fsh", 3.0), MarkerResult("some_key", 1.234)),
        )
    }

    @Test
    fun aLabRangeIsMarkedAndDecidesTheFlag() {
        // 1000 ng/dL is above the typical 916 but inside the lab's 248–1100.
        assertEquals(
            listOf("Total testosterone 1000 ng/dL (34.7 nmol/L) · ref 248–1100 ng/dL (lab) · in range"),
            lines(MarkerResult("total_testosterone", 1000.0, refLow = 248.0, refHigh = 1100.0)),
        )
        assertEquals(
            listOf("Hematocrit 51 % (0.51 L/L) · ref < 50 % (lab) · high"),
            lines(MarkerResult("hematocrit", 51.0, refHigh = 50.0)),
        )
    }

    @Test
    fun censoredValuesKeepTheirSignAndFlagOnlyWhenCertain() {
        // E2 <40 pmol/L against 20–150 pmol/L: the true value may be below 20, so no flag.
        assertEquals(
            listOf("Estradiol (E2) <10.9 pg/mL (<40 pmol/L) · ref 5.45–40.9 pg/mL (lab)"),
            lines(MarkerResult("estradiol", 40 * e2Factor, "<", 20 * e2Factor, 150 * e2Factor)),
        )
        // FSH <0.3 against the typical 1.5–12.4: certainly low.
        assertEquals(listOf("FSH <0.3 IU/L · ref 1.5–12.4 IU/L · low"), lines(MarkerResult("fsh", 0.3, "<")))
        // An unknown qualifier from a later version is shown, never flagged.
        assertEquals(listOf("FSH ~3 IU/L · ref 1.5–12.4 IU/L"), lines(MarkerResult("fsh", 3.0, "~")))
    }

    @Test
    fun unlistedResultsShowAsPrinted() {
        assertEquals(
            listOf(
                "Vrij T4 15.2 pmol/l · ref 10–23 pmol/l (lab) · in range",
                "CRP <1 mg/l · ref < 10 mg/l (lab) · in range",
                "Vitamine D 60 nmol/l",
                "Ferritine 0.035 ug/l · ref > 0.03 ug/l (lab) · in range",
                "Trombocyten 250 · ref 150–400 (lab) · in range",
            ),
            lines(
                MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"),
                MarkerResult("other:crp", 1.0, "<", refHigh = 10.0, name = "CRP", unit = "mg/l"),
                MarkerResult("other:vitamine_d", 60.0, name = "Vitamine D", unit = "nmol/l"),
                MarkerResult("other:ferritine", 0.035, refLow = 0.03, name = "Ferritine", unit = "ug/l"),
                MarkerResult("other:trombocyten", 250.0, refLow = 150.0, refHigh = 400.0, name = "Trombocyten"),
            ),
        )
    }

    @Test
    fun anInvalidLabRangeFallsBackToTheTypicalRange() {
        assertEquals(
            listOf("FSH 3 IU/L · ref 1.5–12.4 IU/L · in range"),
            lines(MarkerResult("fsh", 3.0, refLow = 5.0, refHigh = 2.0)),
        )
    }

    @Test
    fun theLegendNamesLabRangesOnlyWhenThereAreAny() {
        val old = "- Bloodwork results are in conventional units with SI units in brackets; reference ranges are typical adult male ranges, not the lab's own."
        val new = "- Bloodwork results are in conventional units with SI units in brackets. Ranges marked (lab) are the lab's own; " +
            "the others are typical adult male ranges. Results the app does not list are shown as printed."
        val plain = markdown(MarkerResult("fsh", 3.0))
        assertTrue(old in plain, plain)
        assertFalse(new in plain, plain)
        for (result in listOf(
            MarkerResult("fsh", 3.0, refLow = 1.0, refHigh = 8.0),
            MarkerResult("fsh", 0.3, "<"),
            MarkerResult("other:vrij_t4", 15.2, name = "Vrij T4", unit = "pmol/l"),
        )) {
            val md = markdown(result)
            assertTrue(new in md, md)
            assertFalse(old in md, md)
        }
    }

    private fun html(vararg results: MarkerResult) = HtmlReport.render(
        ReportBuilder.build(
            Protocol(emptyList(), emptyList(), emptyMap()), emptyList(), listOf(draw(*results)),
            LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-24"), t, ZoneId.of("Europe/Amsterdam"), locale = Locale.ENGLISH,
        ),
    )

    @Test
    fun htmlEscapesCensoredValues() {
        val html = html(MarkerResult("fsh", 0.3, "<"))
        assertTrue("FSH &lt;0.3 IU/L · ref 1.5–12.4 IU/L · low" in html, html)
    }

    @Test
    fun theHtmlReportExplainsLabDetailsOnlyWhenThereAreAny() {
        val legend = "<p class=\"meta\">Bloodwork results are in conventional units with SI units in brackets. Ranges marked (lab) " +
            "are the lab's own; the others are typical adult male ranges. Results the app does not list are shown as printed.</p>"
        val lab = html(MarkerResult("fsh", 3.0, refLow = 1.0, refHigh = 8.0))
        assertTrue(legend in lab, lab)
        assertTrue(lab.indexOf("<h2>Journal</h2>") < lab.indexOf(legend), lab)
        // A report of plain results reads exactly as before: no legend in HTML.
        val plain = html(MarkerResult("fsh", 3.0))
        assertFalse("Ranges marked (lab)" in plain, plain)
        assertFalse("SI units in brackets" in plain, plain)
    }

    @Test
    fun rangeAndResultTextInEitherUnit() {
        val e2 = BloodMarkers.find("estradiol")!!
        assertEquals("20–150 pmol/L", e2.rangeText(RefRange(20 * e2Factor, 150 * e2Factor), LabUnits.SI))
        assertEquals("> 20 pmol/L", e2.rangeText(RefRange(20 * e2Factor, null), LabUnits.SI))
        assertEquals(null, e2.rangeText(RefRange(null, null), LabUnits.SI))
        assertEquals(e2.referenceText(LabUnits.SI), e2.rangeText(RefRange(e2.refLow, e2.refHigh), LabUnits.SI))
        assertEquals("<40 pmol/L", e2.formatResult(MarkerResult("estradiol", 40 * e2Factor, "<"), LabUnits.SI))
        assertEquals("30 pg/mL", e2.formatResult(MarkerResult("estradiol", 30.0), LabUnits.CONVENTIONAL))
    }

    @Test
    fun trendsCarryTheLatestCensoredResult() {
        val later = t.plusSeconds(86_400 * 30)
        val latest = MarkerResult("fsh", 0.3, "<")
        val trend = markerTrends(
            listOf(JournalEntry.Bloodwork("b2", t, listOf(MarkerResult("fsh", 2.0)), createdAt = t), draw(latest).copy(at = later)),
        ).single()
        assertEquals(latest, trend.result)
        assertEquals(later, trend.at)
    }

    @Test
    fun censoredResultsAreNotPlotted() {
        val scale = LevelScale(relative = false, unit = LevelUnit.NG_DL, baseUnit = BaseUnit.MG)
        val display = levelDisplay(scale, "Testosterone", LabUnits.CONVENTIONAL)
        val journal = listOf(
            draw(MarkerResult("total_testosterone", 1500.0, ">")),
            JournalEntry.Bloodwork("b2", t.plusSeconds(86_400), listOf(MarkerResult("total_testosterone", 700.0, refLow = 248.0, refHigh = 836.0)), createdAt = t),
        )
        assertEquals(listOf(700.0), labPoints("Testosterone", scale, display, journal).map { it.value })
    }
}
