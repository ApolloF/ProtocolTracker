package com.apollof.protocoltracker.domain.model

import com.apollof.protocoltracker.domain.pk.LabUnits
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The shared bloodwork rules of import doc §4.4, §5.2, §5.5, §7, §9.2-9.3 and §10.7. */
class BloodworkRulesTest {
    private val rules = BloodworkRules

    // Plausibility limits (§4.4)

    @Test
    fun everyMarkerHasLimitsAndNothingElseDoes() {
        assertEquals(BloodMarkers.all.map { it.key }.toSet(), rules.limits.keys)
    }

    @Test
    fun limitsHoldAtTheEdges() {
        for ((key, l) in rules.limits) {
            assertTrue(rules.plausible(key, l.min), "$key min")
            assertTrue(rules.plausible(key, l.max), "$key max")
            assertTrue(rules.plausible(key, (l.min + l.max) / 2), "$key middle")
            assertFalse(rules.plausible(key, l.max * 1.001), "$key above max")
            val below = if (l.min == 0.0) -0.001 else l.min * 0.999
            assertFalse(rules.plausible(key, below), "$key below min")
            assertFalse(rules.plausible(key, Double.NaN), "$key NaN")
            assertFalse(rules.plausible(key, Double.POSITIVE_INFINITY), "$key infinity")
        }
    }

    @Test
    fun defaultRangesLieInsideTheLimits() {
        for (m in BloodMarkers.all) {
            val l = rules.limits.getValue(m.key)
            m.refLow?.let { assertTrue(it >= l.min && it <= l.max, "${m.key} low $it") }
            m.refHigh?.let { assertTrue(it >= l.min && it <= l.max, "${m.key} high $it") }
        }
    }

    @Test
    fun limitsCatchTheSlipsOfTheTableAndAllowTheExtremes() {
        val caught = listOf(
            "total_testosterone" to 20_001.0, // a pmol/L or grouping error
            "free_testosterone" to 118_821.0, // 412 pmol/L labelled nmol/L
            "estradiol" to 26_150.0, // 96 pmol/L labelled nmol/L
            "prolactin" to 9_912.0, // 210 mU/L labelled U/L
            "hemoglobin" to 152.0, // g/L as g/dL
            "hemoglobin" to 146.6, // a lost comma: 91 mmol/L
            "hematocrit" to 0.47, // an L/L fraction labelled %
            "hematocrit" to 4_700.0, // a % labelled L/L
            "cholesterol" to 4.6, // a mmol/L number labelled mg/dL
            "hdl" to 1_547.0, // 40 mg/dL labelled mmol/L
            "triglycerides" to 13_286.0, // 150 mg/dL labelled mmol/L
            "glucose" to 5.2, // mmol/L labelled mg/dL
            "glucose" to 520 * 18.0, // a lost comma of 5,20 mmol/L
            "creatinine" to 96.0, // µmol/L labelled mg/dL
            "albumin" to 42.0, // g/L labelled g/dL
        )
        for ((key, v) in caught) assertFalse(rules.plausible(key, v), "$key $v")
        val allowed = listOf(
            "total_testosterone" to 5_000.0,
            "hemoglobin" to 24.0,
            "hematocrit" to 75.0,
            "hematocrit" to 15.0,
            "hemoglobin" to 9.9 * 1.611,
            "ck" to 100_000.0,
            "creatinine" to 12.0,
            "egfr" to 180.0,
        )
        for ((key, v) in allowed) assertTrue(rules.plausible(key, v), "$key $v")
    }

    @Test
    fun unlistedResultsOnlyNeedZeroOrMore() {
        assertTrue(rules.plausible("other:vrij_t4", 0.0))
        assertTrue(rules.plausible("other:leukocyten", 1e9))
        assertFalse(rules.plausible("other:vrij_t4", -0.1))
        assertFalse(rules.plausible("other:vrij_t4", Double.NaN))
    }

    // Draw time (§9.2)

    @Test
    fun drawsWithoutAPrintedTimeKeepTheirDayAcrossZones() {
        assertEquals(LocalTime.of(12, 0), rules.UNKNOWN_DRAW_TIME)
        val home = ZoneId.of("Europe/Amsterdam")
        for (date in listOf(LocalDate.of(2025, 1, 15), LocalDate.of(2025, 7, 15))) {
            val at = date.atTime(rules.UNKNOWN_DRAW_TIME).atZone(home).toInstant()
            for (hours in -10..12) {
                assertEquals(date, at.atZone(ZoneOffset.ofHours(hours)).toLocalDate(), "$date at UTC$hours")
            }
        }
    }

    // Names and other keys (§5.2, §7)

    @Test
    fun namesNormalizeAsTheImportDocShows() {
        val cases = listOf(
            "Vrij testosteron (berekend)" to ("vrij testosteron" to "vrij testosteron"),
            "eGFR (CKD-EPI 2021)" to ("egfr" to "egfr"),
            "Testosteron (LC-MS/MS)" to ("testosteron" to "testosteron"),
            "Testosteron (ochtend)" to ("testosteron ochtend" to "testosteron"),
            "SHBG (sex.horm.bind. gl.)" to ("shbg sex horm bind gl" to "shbg"),
            "Glucose (niet nuchter)" to ("glucose niet nuchter" to "glucose niet nuchter"),
            "Free Testosterone(Direct)" to ("free testosterone" to "free testosterone"),
            "Testosterone, Serum" to ("testosterone" to "testosterone"),
            "S-Testosteron" to ("testosteron" to "testosteron"),
            "Luteïniserend hormoon" to ("luteiniserend hormoon" to "luteiniserend hormoon"),
            "17-β-oestradiol" to ("17 beta oestradiol" to "17 beta oestradiol"),
            "ASAT/GOT" to ("asat / got" to "asat / got"),
            "Cholesterol/HDL" to ("cholesterol / hdl" to "cholesterol / hdl"),
            "Hemoglobine (1)" to ("hemoglobine" to "hemoglobine"),
            "Kreatinine*" to ("kreatinine" to "kreatinine"),
            "Neutrofielen (%)" to ("neutrofielen %" to "neutrofielen %"),
            "Testosteron totaal" to ("testosteron totaal" to "testosteron totaal"),
            "Gamma‑GT" to ("gamma gt" to "gamma gt"),
        )
        for ((printed, expected) in cases) {
            val n = rules.normalizeName(printed)
            assertEquals(expected, n.full to n.short, printed)
        }
    }

    @Test
    fun nestedAndUnbalancedParenthesesSeparateWords() {
        fun name(printed: String) = rules.normalizeName(printed).let { it.full to it.short }
        assertEquals("foo bar baz qux quux" to "foo quux", name("Foo (bar (baz) qux) quux"))
        assertEquals("foo bar" to "foo", name("Foo (bar"))
        assertEquals("foo bar" to "foo bar", name("Foo ) bar"))
        // A parenthetical with a keep-word stays in the short form.
        assertEquals("foo bar %" to "foo bar %", name("Foo (bar %)"))
    }

    @Test
    fun aLoneLetterIsANameNotAPrefix() {
        assertEquals("b", rules.normalizeName("B").full)
        assertEquals("b12", rules.normalizeName("B12").full)
    }

    @Test
    fun otherKeysFollowTheImportDocExamples() {
        val cases = listOf(
            "Vrij T4" to "other:vrij_t4",
            "Vitamine D (25-OH)" to "other:vitamine_d_25_oh",
            "HbA1c (IFCC)" to "other:hba1c",
            "CRP" to "other:crp",
            "Leukocyten" to "other:leukocyten",
            "Ferritine, serum" to "other:ferritine",
            // The web history import passes its key with spaces for underscores.
            "vitamin_d".replace('_', ' ') to "other:vitamin_d",
            "free_t4".replace('_', ' ') to "other:free_t4",
        )
        for ((printed, key) in cases) assertEquals(key, rules.otherKey(printed), printed)
    }

    @Test
    fun aPercentageAndACountOfOneTestGetDifferentKeys() {
        assertEquals("other:lymfocyten_pct", rules.otherKey("Lymfocyten %"))
        assertEquals("other:lymfocyten", rules.otherKey("Lymfocyten"))
    }

    @Test
    fun otherKeysAreBoundedAndNeverEmpty() {
        assertEquals("other:result", rules.otherKey(""))
        assertEquals("other:result", rules.otherKey("(serum) *"))
        // The 48th character is an underscore: cut, then trimmed.
        assertEquals(
            "other:antistoffen_tegen_cyclisch_gecitrullineerd_anti",
            rules.otherKey("Antistoffen tegen cyclisch gecitrullineerd anti-CCP"),
        )
        assertEquals(
            "other:antistoffen_tegen_cyclisch_gecitrullineerd_pepti",
            rules.otherKey("Antistoffen tegen cyclisch gecitrullineerd peptide (anti-CCP)"),
        )
    }

    @Test
    fun otherKeysNeverCollideWithAMarker() {
        val keys = BloodMarkers.all.map { it.key }.toSet()
        assertTrue(keys.none { ':' in it })
        for (m in BloodMarkers.all) {
            val key = rules.otherKey(m.name)
            assertTrue(rules.isOther(key), key)
            assertFalse(key in keys, key)
            assertEquals(1, key.count { it == ':' }, key)
        }
    }

    // Characters and units (§4.1, §5.5)

    @Test
    fun superscriptPowersSurviveNormalization() {
        assertEquals("10^9/l", rules.normalizeChars("10⁹/l"))
        assertEquals("x10^12/L", rules.normalizeChars("x10¹²/L"))
        assertEquals("1.73m2", rules.normalizeChars("1.73m²"))
        assertEquals("8,5 - 11,0 'a' \"b\"", rules.normalizeChars("8,5 – 11,0 ‘a’ “b”"))
        assertEquals("Hemoglobine", rules.normalizeChars("Hemo­glo​bine"))
        assertEquals("-5\t6", rules.normalizeChars("−5\t6"))
    }

    @Test
    fun digitsOfOtherScriptsBecomeAsciiDigits() {
        assertEquals("24,1 - 8.6", rules.normalizeChars("٢٤,١ - ٨.٦")) // Arabic-Indic
        assertEquals("24 2026", rules.normalizeChars("۲۴ २०२६")) // Persian, Devanagari
        assertEquals("57", rules.normalizeChars("𞥕𞥗")) // Adlam, outside the BMP
        assertEquals("a😀b", rules.normalizeChars("a😀b")) // other characters outside it stay
    }

    @Test
    fun unitSpellingsNormalizeToOneForm() {
        val groups = listOf(
            listOf("µmol/L", "μmol/l", "umol/l", "mcmol/L", "micromol/L", "µmol/I") to "umol/l",
            listOf("µg/L", "mcg/L", "ug/l") to "ug/l",
            listOf("E/l", "U/L", "IU/L", "IE/l", "[iU]/L", "U/Liter") to "u/l",
            listOf("mE/l", "mU/L", "mIU/L", "m[IU]/L", "mIE/l", "mIE/L") to "mu/l",
            listOf("mIU/mL", "mU/mL", "mIE/ml") to "mu/ml",
            listOf("µIU/mL", "uIU/ml", "µU/mL", "µIE/ml", "uIE/mL") to "uu/ml",
            listOf("x10^9/l", "×10⁹/L", "10E9/L", "x10e9/l", "10*9/l", "x109/l", "/nl", "10^3/µl", "K/uL", "10^9/L") to "10^9/l",
            listOf("x10^12/l", "10¹²/L", "x1012/l", "T/L", "/pl", "10^6/µL", "M/uL") to "10^12/l",
            listOf("ml/min/1,73m²", "mL/min/1.73m^2", "ml/min/1.73", "mL/min per 1.73 m2", "mL/min/{1.73_m2}") to "ml/min/1.73m2",
            listOf("mmol/l", " mmol / L ", "mmol/liter", "mmol/I") to "mmol/l",
            listOf("pmol/l{calc}", "pmol/L") to "pmol/l",
        )
        for ((spellings, expected) in groups) {
            for (s in spellings) assertEquals(expected, rules.normalizeUnit(s), s)
        }
    }

    @Test
    fun unitsThatOnlyLookAlikeStayApart() {
        assertEquals("1/1", rules.normalizeUnit("1/1"))
        assertNotEquals(rules.normalizeUnit("pmol/l"), rules.normalizeUnit("µmol/l"))
        assertNotEquals(rules.normalizeUnit("g/l"), rules.normalizeUnit("10^9/l"))
        assertEquals("ml/min", rules.normalizeUnit("mL/min"))
    }

    // Already saved (§9.3)

    private fun r(marker: String, value: Double, qualifier: String? = null, unit: String? = null) =
        MarkerResult(marker, value, qualifier = qualifier, unit = unit)

    @Test
    fun sameResultAllowsHalfAPercent() {
        assertTrue(rules.sameResult(r("total_testosterone", 1000.0), r("total_testosterone", 1004.0)))
        assertTrue(rules.sameResult(r("total_testosterone", 1004.0), r("total_testosterone", 1000.0)))
        assertFalse(rules.sameResult(r("total_testosterone", 1000.0), r("total_testosterone", 1006.0)))
        assertFalse(rules.sameResult(r("total_testosterone", 1006.0), r("total_testosterone", 1000.0)))
    }

    @Test
    fun sameResultHasAFloorOfOneHundredth() {
        assertTrue(rules.sameResult(r("psa", 0.05), r("psa", 0.059)))
        assertFalse(rules.sameResult(r("psa", 0.05), r("psa", 0.061)))
        assertTrue(rules.sameResult(r("psa", 0.0), r("psa", 0.009)))
    }

    @Test
    fun sameResultCoversUnitRoundTripsAndTheWebExport() {
        val hb = BloodMarkers.find("hemoglobin")!!
        // 9.9 mmol/L imported vs the same value typed in SI and exported by the web app with two decimals.
        val imported = r("hemoglobin", hb.toStored(9.9, LabUnits.SI))
        assertTrue(rules.sameResult(imported, r("hemoglobin", 15.95)))
        assertTrue(rules.sameResult(imported, r("hemoglobin", hb.toStored(9.9, LabUnits.SI))))
        assertFalse(rules.sameResult(imported, r("hemoglobin", hb.toStored(9.8, LabUnits.SI))))
    }

    @Test
    fun sameResultNeedsTheSameMarkerAndQualifier() {
        assertFalse(rules.sameResult(r("lh", 5.0), r("fsh", 5.0)))
        assertFalse(rules.sameResult(r("psa", 0.1, "<"), r("psa", 0.1)))
        assertFalse(rules.sameResult(r("psa", 0.1, "<"), r("psa", 0.1, ">")))
        assertTrue(rules.sameResult(r("psa", 0.1, "<"), r("psa", 0.1, "<")))
    }

    @Test
    fun unlistedResultsAlsoNeedTheSameUnit() {
        assertTrue(rules.sameResult(r("other:vrij_t4", 15.2, unit = "pmol/l"), r("other:vrij_t4", 15.2, unit = "pmol/L")))
        assertTrue(rules.sameResult(r("other:ferritine", 80.0, unit = "µg/L"), r("other:ferritine", 80.0, unit = "ug/l")))
        assertFalse(rules.sameResult(r("other:vrij_t4", 15.2, unit = "pmol/l"), r("other:vrij_t4", 15.2, unit = "ng/dl")))
        assertFalse(rules.sameResult(r("other:vrij_t4", 15.2, unit = "pmol/l"), r("other:vrij_t4", 15.2)))
        assertTrue(rules.sameResult(r("other:vitamin_d", 70.0), r("other:vitamin_d", 70.0, unit = " ")))
        // Known markers are stored in the marker's unit; a printed unit on them does not matter.
        assertTrue(rules.sameResult(r("estradiol", 30.0, unit = "pmol/l"), r("estradiol", 30.0)))
    }

    // Editing a draw (§10.7)

    private val lh = MarkerResult("lh", 0.3, qualifier = "<", refLow = 1.7, refHigh = 8.6)
    private val hb = MarkerResult("hemoglobin", 15.9489, refLow = 13.6935, refHigh = 17.721)
    private val creatinine = MarkerResult("creatinine", 1.085952)
    private val ft4 = MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l")
    private val crp = MarkerResult("other:crp", 5.0, qualifier = "<", name = "CRP", unit = "mg/l")
    private val existing = listOf(lh, hb, creatinine, ft4, crp)

    @Test
    fun untouchedResultsAreKeptExactly() {
        for (units in LabUnits.entries) {
            assertEquals(listOf(lh, hb, creatinine, ft4, crp), rules.editResults(existing, emptyMap(), units))
        }
    }

    @Test
    fun knownMarkersComeFirstInTableOrderThenUnlistedInTheirOrder() {
        val shuffled = listOf(crp, creatinine, ft4, hb, lh)
        assertEquals(listOf(lh, hb, creatinine, crp, ft4), rules.editResults(shuffled, emptyMap(), LabUnits.CONVENTIONAL))
    }

    @Test
    fun aTypedNumberKeepsTheRangeAndDropsTheQualifier() {
        val out = rules.editResults(existing, mapOf("lh" to 0.5, "hemoglobin" to 10.1), LabUnits.SI)
        assertEquals(MarkerResult("lh", 0.5, refLow = 1.7, refHigh = 8.6), out[0])
        assertEquals(hb.copy(value = 10.1 * 1.611), out[1])
        assertEquals(listOf(creatinine, ft4, crp), out.drop(2))
    }

    @Test
    fun aTypedUnlistedNumberIsNeverConverted() {
        val out = rules.editResults(existing, mapOf("other:vrij_t4" to 16.0, "other:crp" to 7.5), LabUnits.SI)
        assertEquals(ft4.copy(value = 16.0), out[3])
        assertEquals(MarkerResult("other:crp", 7.5, name = "CRP", unit = "mg/l"), out[4])
    }

    @Test
    fun aClearedFieldRemovesTheResult() {
        val out = rules.editResults(existing, mapOf("lh" to null, "other:vrij_t4" to null), LabUnits.CONVENTIONAL)
        assertEquals(listOf(hb, creatinine, crp), out)
    }

    @Test
    fun aTypedNewMarkerIsAddedInTableOrder() {
        val out = rules.editResults(existing, mapOf("total_testosterone" to 20.0, "ck" to 300.0), LabUnits.SI)
        assertEquals(MarkerResult("total_testosterone", 20.0 * 28.84), out[0])
        assertEquals(listOf(lh, hb, creatinine), out.subList(1, 4))
        assertEquals(MarkerResult("ck", 300.0), out[4])
        assertEquals(listOf(ft4, crp), out.drop(5))
    }

    @Test
    fun aTypedUnlistedKeyWithoutAResultIsIgnored() {
        assertEquals(existing, rules.editResults(existing, mapOf("other:ferritine" to 80.0), LabUnits.CONVENTIONAL))
    }
}
