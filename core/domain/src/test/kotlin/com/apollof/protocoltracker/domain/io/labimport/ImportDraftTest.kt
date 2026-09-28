package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.MarkerFlag
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.flag
import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The row pipeline at draft level (import doc §4.2-4.3, §5.3, §7, §13.1): each value is proven by the tables or the
 * report's numbers, or left out with its exact reason.
 */
class ImportDraftTest {
    private val today = LocalDate.of(2026, 9, 27)

    private fun draft(text: String) = assertIs<ImportRead.Found>(BloodworkImport.read(text, today)).draft

    private fun ImportDraft.rows() = draws.flatMap { it.rows }

    private fun ImportDraft.row(name: String) = rows().single { it.printed.name == name }

    private fun ImportDraft.ready(name: String) = assertIs<RowRead.Ready>(row(name).read)

    private fun ImportDraft.result(name: String) = ready(name).result

    private fun ImportDraft.reason(name: String) = assertIs<RowRead.Uncertain>(row(name).read).reason

    private fun ImportDraft.captions() = rows().mapNotNull { (it.read as? RowRead.Ready)?.caption }

    private fun assertNear(expected: Double, actual: Double?, what: String) {
        assertTrue(actual != null && kotlin.math.abs(expected - actual) < 1e-9, "$what: expected $expected, was $actual")
    }

    private fun date(d: LocalDate) = DisplayFormat.current.date.format(d)

    // Clean answers leave nothing out and say nothing

    @Test
    fun cleanDutchReport() {
        val d = draft(Fixtures.F01)
        val draw = d.draws.single()
        assertEquals(LocalDate.of(2025, 3, 12), draw.date)
        assertEquals(LocalTime.of(8, 15), draw.time)
        assertEquals("Saltro", draw.lab)
        assertNull(draw.leftOutReason)
        assertEquals(20, draw.rows.size)
        assertTrue(draw.rows.all { it.read is RowRead.Ready }, "nothing is left out")
        assertEquals(emptyList(), d.captions(), "a clean report has no caption")

        val t = d.result("Testosteron totaal")
        assertNear(530.656, t.value, "18,4 nmol/L in ng/dL")
        assertNear(248.024, t.refLow, "lab low in ng/dL")
        assertNear(836.36, t.refHigh, "lab high in ng/dL")
        val fsh = d.result("FSH")
        assertEquals(MarkerResult.BELOW, fsh.qualifier)
        assertEquals(MarkerFlag.LOW, fsh.flag(), "<0,3 against 1,5 - 12,4")
        assertEquals(MarkerFlag.NORMAL, d.result("HDL-cholesterol").flag(), "in range by the lab's > 1,0")
        assertEquals(MarkerFlag.NORMAL, d.result("eGFR (CKD-EPI)").flag(), ">90 against > 60")
        assertEquals(
            MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"),
            d.result("Vrij T4"),
            "unlisted results are kept as printed",
        )
        assertEquals("<0.3", d.ready("FSH").value)
        assertEquals("E/l", d.ready("FSH").unit)
    }

    @Test
    fun cleanReportWithTwoDraws() {
        val d = draft(Fixtures.F02)
        val (june, march) = d.draws
        assertEquals(LocalTime.of(8, 40), june.time)
        assertNull(march.time, "no time printed")
        assertTrue(d.rows().all { it.read is RowRead.Ready && (it.read as RowRead.Ready).caption == null })
        val hct = june.rows.single { it.printed.name == "Hematocriet" }.read as RowRead.Ready
        assertEquals(MarkerFlag.HIGH, hct.result.flag(), "52 % against 41-51")
        val e2 = june.rows.single { it.printed.name == "Oestradiol" }.read as RowRead.Ready
        assertNull(e2.result.refLow)
        assertNear(54.48, e2.result.refHigh, "M <200 pmol/L")
    }

    @Test
    fun englishLabCorpReportWithDirectFreeTestosterone() {
        val d = draft(Fixtures.F08)
        val draw = d.draws.single()
        assertEquals(LocalDate.of(2025, 3, 12), draw.date, "03/12/2025 with AM reads month first")
        assertEquals(LocalTime.of(7, 50), draw.time)
        assertTrue(d.rows().all { it.read is RowRead.Ready })
        assertEquals("Read as 1050.", d.ready("Testosterone, Serum").caption, "the block's decimal dots group 1,050")
        assertEquals(1050.0, d.result("Testosterone, Serum").value)
        assertEquals("Read as 2.11.", d.ready("TSH").caption)
        val freeT = d.ready("Free Testosterone(Direct)")
        assertNull(freeT.caption, "a direct assay's low range asks nothing")
        assertEquals(8.7, freeT.result.refLow, "and is kept")
        assertEquals(MarkerFlag.HIGH, freeT.result.flag())
        assertEquals(MarkerFlag.HIGH, d.result("Hematocrit").flag())
        assertEquals(listOf("Read as 1050.", "Read as 2.11."), d.captions())
    }

    @Test
    fun rangesBySex() {
        val d = draft(Fixtures.F20)
        assertTrue(d.rows().all { it.read is RowRead.Ready })
        assertNull(d.ready("Oestradiol").caption, "one range with a prefix")
        assertNear(54.48, d.result("Oestradiol").refHigh, "M <200")
        assertEquals("Men's range used.", d.ready("Hemoglobine").caption)
        assertNear(8.5 * 1.611, d.result("Hemoglobine").refLow, "the men's low")
        assertEquals(MarkerResult.ABOVE, d.result("SHBG").qualifier)
        assertEquals(MarkerFlag.HIGH, d.result("SHBG").flag(), ">200 against 18 - 54")
    }

    // Units

    @Test
    fun missingUnitsUseTheOnlyUnitThatFitsOrLeaveTheResultOut() {
        val d = draft(Fixtures.N02)
        assertEquals("No unit on the report.", d.ready("SHBG").caption, "SHBG has one unit")
        assertEquals("No unit on the report; only L/L fits.", d.ready("Hematocriet").caption)
        assertEquals(49.0, d.result("Hematocriet").value)
        assertEquals("L/L", d.ready("Hematocriet").unit, "the app's spelling of the unit it chose")
        assertEquals("No unit on the report; only U/L fits.", d.ready("ALAT").caption, "µkat/L does not fit < 45")
        assertEquals("Hemoglobine 9.9: no unit on the report.", d.reason("Hemoglobine"), "mmol/L and g/dL both fit")
        assertEquals("ASAT 29: no unit on the report.", d.reason("ASAT"), "no range to tell U/L from µkat/L")
        assertEquals("AST (GOT)", assertIs<RowRead.Uncertain>(d.row("ASAT").read).label)
    }

    @Test
    fun unitSlipsAndImpossibleValuesAreLeftOutWithTheirReason() {
        val d = draft(Fixtures.N16)
        assertEquals(
            "Hemoglobine 15.9 mmol/l: the range 13.5 - 17.5 fits g/dL, not mmol/l.",
            d.reason("Hemoglobine"),
        )
        assertEquals("Oestradiol 96 pg/ml: the range < 150 fits pmol/L, not pg/ml.", d.reason("Oestradiol"))
        assertEquals(
            "Vrij testosteron 412 pg/ml: the range 225 - 725 fits pmol/L, not pg/ml.",
            d.reason("Vrij testosteron"),
            "free T compares molar with mass units",
        )
        assertEquals("Hematocriet 0.47 % is not possible for Hematocrit.", d.reason("Hematocriet"))
        assertEquals("Prolactine 210 U/l is not possible for Prolactin.", d.reason("Prolactine"))
        assertEquals("Kreatinine 96 pmol/l: pmol/l is not a unit for Creatinine.", d.reason("Kreatinine"))
        assertEquals(
            RowRead.NotImported("600000 U/l is not possible for CK (creatine kinase)."),
            d.row("CK").read,
            "no unit or decimal shift lands near the typical range",
        )
        val t = d.ready("Testosteron")
        assertEquals("Range not used: it doesn't fit Total testosterone.", t.caption, "FSH's range on the T row")
        assertNear(530.656, t.result.value, "the value is kept")
        assertNull(t.result.refLow)
        assertNull(t.result.refHigh)

        val ownUnit = draft(
            "protocoltracker-bloodwork-1\ndate: 2025-04-01 | Afnamedatum: 01-04-2025\n" +
                "hemoglobin | Hemoglobine | 9,9 | mmol/l | 13,5 - 17,5 g/dl |\nend",
        )
        assertEquals("Range not used: it doesn't fit Hemoglobin.", ownUnit.ready("Hemoglobine").caption)
        assertNull(ownUnit.result("Hemoglobine").refLow, "a range in another unit is not converted")
    }

    @Test
    fun dutchInternationalUnitsAreRead() {
        val d = draft(
            "protocoltracker-bloodwork-1\ndate: 2025-04-01 | Afnamedatum: 01-04-2025\n" +
                "tsh | TSH | 1,9 | mIE/l | 0,5 - 4,0 |\nprolactin | Prolactine | 250 | mIE/l | |\n" +
                "lh | LH | 4,1 | mIE/ml | |\nend",
        )
        assertNull(d.ready("TSH").caption)
        assertNear(1.9, d.result("TSH").value, "TSH in mIU/L")
        assertNear(11.8, d.result("Prolactine").value, "250 mIE/l in ng/mL")
        assertNear(4.1, d.result("LH").value, "LH in U/L")
    }

    @Test
    fun lostDecimalsAreLeftOutOneSidedRangesNever() {
        val d = draft(Fixtures.N15)
        assertEquals(
            "Glucose nuchter 52 mmol/l is far outside the range 4.0 - 5.6. Does the report say 5.2?",
            d.reason("Glucose nuchter"),
        )
        assertEquals(
            "Testosteron 184 nmol/l is far outside the range 8.6 - 29.0. Does the report say 18.4?",
            d.reason("Testosteron"),
        )
        assertEquals(1209.0, d.result("CK").value, "a training CK against < 190 is never asked")
        assertEquals(0.4, d.result("LH").value, "a suppressed LH is below the range, not a slip")
    }

    @Test
    fun ambiguousThousandsSettleByTheMarkerOrAreLeftOut() {
        val d = draft(Fixtures.N18)
        assertEquals("Testosteron 1,050: this can mean 1050 or 1.05.", d.reason("Testosteron"))
        assertEquals("CK 1.200: this can mean 1200 or 1.2.", d.reason("CK"))
        assertEquals("Read as 9.9.", d.ready("Hemoglobine").caption, "9900 mmol/L is not possible")
        assertNear(9.9 * 1.611, d.result("Hemoglobine").value, "Hb")
    }

    // Duplicates, names and keys

    @Test
    fun aLaterBlockCorrectsTwoValuesInOneBlockAreLeftOut() {
        val d = draft(Fixtures.F18)
        val draw = d.draws.single()
        assertEquals(listOf("Hematocriet", "LDL-cholesterol", "LDL-cholesterol", "Hemoglobine"), draw.rows.map { it.printed.name })
        assertEquals("Changed later in the answer (was 9.1).", d.ready("Hemoglobine").caption)
        assertNear(9.7 * 1.611, d.result("Hemoglobine").value, "the later value wins")
        val twoValues = "Two values for LDL cholesterol on ${date(LocalDate.of(2025, 4, 1))}."
        assertEquals(
            listOf(twoValues, twoValues),
            draw.rows.filter { it.printed.name == "LDL-cholesterol" }.map { (it.read as RowRead.Uncertain).reason },
        )
        assertEquals(draw.rows.map { it.id }.distinct(), draw.rows.map { it.id })
    }

    @Test
    fun namesWinOverKeysAndOnlyCleanNumbersKeepAChatbotsKey() {
        val d = draft(Fixtures.N22)
        val ratio = d.ready("Cholesterol/HDL")
        assertEquals("other:cholesterol_hdl", ratio.result.marker)
        assertEquals("Kept as Cholesterol/HDL, not HDL cholesterol.", ratio.caption)
        assertEquals("ast", d.result("ASAT/GOT").marker, "an exact variant with a slash")
        assertNull(d.ready("ASAT/GOT").caption)
        val freeT = d.ready("Vrije testosteronfractie")
        assertEquals("free_testosterone", freeT.result.marker)
        assertNear(129.78, freeT.result.value, "0,45 nmol/L")
        assertEquals("Matched by the chatbot.", freeT.caption)
        assertEquals("other:hb_totaal", d.result("Hb totaal").marker)
        assertEquals("Kept as Hb totaal: the numbers don't fit Hemoglobin.", d.ready("Hb totaal").caption, "no unit")
        assertEquals("hemoglobin", d.result("Hemoglobine").marker)
        assertEquals("Read as Hemoglobin.", d.ready("Hemoglobine").caption, "the name beats the key")
        assertEquals("other:kreatinine", d.result("Kreatinine").marker)
        assertEquals("Kept as Kreatinine: the numbers don't fit Creatinine.", d.ready("Kreatinine").caption)
        val crp = d.rows().filter { it.printed.name == "CRP" }.map { (it.read as RowRead.Ready).result }
        assertEquals(listOf("other:crp" to 4.0, "other:crp_2" to 7.0), crp.map { it.marker to it.value }, "the same 4 once")
        val glucose = d.ready("Glucose")
        assertEquals("other:glucose", glucose.result.marker)
        assertEquals("Kept as Glucose: fasting not stated.", glucose.caption)
    }

    // Dates, notes and broken answers

    @Test
    fun uncertainDatesLeaveTheWholeDrawOut() {
        val d = draft(Fixtures.N08)
        assertEquals(
            listOf(
                "This date is from \"Geboortedatum\", not the blood draw.",
                "No draw date on the report.",
                "The report says \"04-03-2025\". Which date is it?",
                "${date(LocalDate.of(2027, 1, 5))} is in the future.",
                null,
            ),
            d.draws.map { it.leftOutReason },
        )
        assertEquals(listOf(null, null, null, null, LocalDate.of(2025, 3, 14)), d.draws.map { it.date })
        assertEquals("Received date; the draw can be a day earlier.", d.draws.last().caption)
    }

    @Test
    fun valuesWithoutANumberGoToTheNote() {
        val d = draft(Fixtures.F03)
        assertEquals(4, d.rows().count { it.read is RowRead.Ready })
        val psa = d.row("PSA totaal")
        assertEquals(RowRead.NotImported("No result: onleesbaar (vlek op de foto)."), psa.read)
        assertEquals("PSA totaal: onleesbaar (vlek op de foto)", psa.noteLine)
        val f21 = draft(Fixtures.F21)
        assertEquals(RowRead.NotImported("No result: negatief."), f21.row("HBsAg").read)
        assertEquals("Anti-HCV: negatief; niet reactief", f21.row("Anti-HCV").noteLine)
        assertTrue(f21.rows().none { it.hasNumber })
    }

    @Test
    fun aCutOffAnswerNeverImportsItsLastLine() {
        val d = draft(Fixtures.F09)
        assertEquals(setOf(BlockNotice.CUT_OFF), d.notices)
        assertEquals(listOf("Testosteron totaal", "Hemoglobine"), d.rows().map { it.printed.name })
        assertEquals(listOf(6), d.unread.map { it.line })
    }

    /** N1's advice: the chatbot writes the whole answer again; copied alone or with the cut answer, every result reads. */
    @Test
    fun theWholeAnswerAgainReadsEveryResult() {
        val again = Fixtures.F09.replace(
            "hematocrit | Hematocriet | 0,4", "hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |\nend\n```",
        )
        val names = listOf("Testosteron totaal", "Hemoglobine", "Hematocriet")
        val alone = draft(again)
        assertEquals(emptySet(), alone.notices)
        assertEquals(names, alone.rows().map { it.printed.name })
        assertTrue(alone.rows().all { it.read is RowRead.Ready })

        val conversation = draft(Fixtures.F09 + "\n\nWrite the whole answer again.\n\n" + again)
        assertEquals(names, conversation.rows().map { it.printed.name }, "the repeated lines are one row each")
        assertNear(49.0, conversation.result("Hematocriet").value, "the complete line, never the cut 0,4")
        assertEquals(listOf(6), conversation.unread.map { it.line })
    }

    @Test
    fun refusalsPassThrough() {
        assertEquals(ImportRead.Refused(InputProblem.Empty), BloodworkImport.read(" \n", today))
        assertEquals(ImportRead.Refused(InputProblem.RawReport), BloodworkImport.read(Fixtures.R01, today))
    }
}
