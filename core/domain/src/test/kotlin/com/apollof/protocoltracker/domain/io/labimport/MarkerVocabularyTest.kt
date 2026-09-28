package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.BloodworkRules
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The marker vocabulary of import doc §5.4-5.7: names, never words, exclusions, accepted units and factors. */
class MarkerVocabularyTest {
    private val vocab = MarkerVocabulary

    // Completeness and guards (§5.1, §5.3)

    @Test
    fun everyMarkerHasNamesUnitsAndLimitsInTableOrder() {
        val keys = BloodMarkers.all.map { it.key }
        assertEquals(keys, vocab.names.map { it.key }, "names in BloodMarkers order")
        assertEquals(keys.toSet(), vocab.units.keys, "units for exactly the known markers")
        for (key in keys) {
            assertTrue(vocab.names(key)!!.aliases.isNotEmpty(), "$key has aliases")
            assertTrue(vocab.units.getValue(key).isNotEmpty(), "$key has units")
            assertNotNull(BloodworkRules.limits[key], "$key has plausibility limits")
        }
    }

    @Test
    fun noNormalizedNameBelongsToTwoKeys() {
        val owners = vocab.names.flatMap { n ->
            (n.aliases + n.variants + n.confirm).map { BloodworkRules.normalizeName(it).full to n.key }
        }.groupBy({ it.first }, { it.second })
        for ((form, keys) in owners) {
            assertEquals(1, keys.toSet().size, "'$form' belongs to ${keys.toSet()}")
            assertTrue(form.isNotEmpty(), "every name normalizes to something")
        }
    }

    @Test
    fun everyAliasAndVariantMatchesItsOwnMarker() {
        for (n in vocab.names) {
            for (name in n.aliases + n.variants) {
                assertEquals(NameMatch.Known(n.key), vocab.match(name), name)
                assertEquals(Identity.Agreed(n.key), vocab.identify(name, n.key), name)
            }
        }
    }

    @Test
    fun noNameHoldsItsOwnNeverWordOrAnExclusionWord() {
        for (n in vocab.names) {
            for (name in n.aliases + n.variants + n.confirm) {
                assertFalse(vocab.hasNeverWord(n.key, name), "$name holds a never word of ${n.key}")
                val words = BloodworkRules.normalizeName(name).full.split(' ')
                assertTrue(words.none { it in setOf("ratio", "quotient", "index", "verhouding") }, name)
            }
            for (confirm in n.confirm) assertEquals(NameMatch.Confirm(n.key), vocab.match(confirm), confirm)
        }
    }

    // The brief's Dutch names (§5.4)

    @Test
    fun theBriefsDutchNamesMapWithoutAKey() {
        val brief = mapOf(
            "Testosteron totaal" to "total_testosterone",
            "Hematocriet" to "hematocrit",
            "Hemoglobine" to "hemoglobin",
            "Vrij testosteron" to "free_testosterone",
            "Oestradiol" to "estradiol",
            "Kreatinine" to "creatinine",
            "ALAT" to "alt",
            "ASAT" to "ast",
            "Gamma-GT" to "ggt",
            "PSA totaal" to "psa",
        )
        for ((name, key) in brief) {
            assertTrue(name in vocab.names(key)!!.aliases, "$name is listed in the prompt")
            assertEquals(NameMatch.Known(key), vocab.match(name), name)
            assertEquals(Identity.ByName(key), vocab.identify(name, null), name)
            assertEquals(Identity.ByName(key), vocab.identify(name, "other"), name)
        }
    }

    @Test
    fun otherDutchNamesAndSpellingsMap() {
        val names = mapOf(
            "S-Testosteron" to "total_testosterone",
            "Testosteron (LC-MS/MS)" to "total_testosterone",
            "Testosteron, vrij" to "free_testosterone",
            "Vrij testosteron (berekend, Vermeulen)" to "free_testosterone",
            "17β-oestradiol" to "estradiol",
            "Luteïniserend hormoon" to "lh",
            "Follikel stimulerend hormoon" to "fsh",
            "Prolactine" to "prolactin",
            "SHBG (sex.horm.bind. gl.)" to "shbg",
            "HEMOGLOBINE" to "hemoglobin",
            "Ht" to "hematocrit",
            "Cholesterol totaal" to "cholesterol",
            "HDL-cholesterol" to "hdl",
            "LDL-cholesterol" to "ldl",
            "Niet-HDL-cholesterol" to "non_hdl",
            "Triglyceriden" to "triglycerides",
            "Glucose (nuchter)" to "glucose",
            "eGFR (CKD-EPI 2021)" to "egfr",
            "Albumine" to "albumin",
            "ASAT (GOT)" to "ast",
            "ASAT/GOT" to "ast",
            "ALAT/GPT" to "alt",
            "γ-GT" to "ggt",
            "Creatinekinase" to "ck",
            "Free Testosterone(Direct)" to "free_testosterone",
            "Testosterone, Serum" to "total_testosterone",
        )
        for ((name, key) in names) assertEquals(NameMatch.Known(key), vocab.match(name), name)
    }

    // The settled cases (§5.4): the name part here, the numbers part in the row pipeline

    @Test
    fun settledCases() {
        assertEquals(Identity.ByName("free_testosterone"), vocab.identify("Vrij testosteron", "total_testosterone"))
        assertEquals(Identity.Unlisted(ruledOut = "free_testosterone"), vocab.identify("Vrij testosteron index", "free_testosterone"))
        assertEquals(Identity.Unlisted(ruledOut = "free_testosterone"), vocab.identify("Vrij T4", "free_testosterone"))
        assertEquals("other:vrij_t4", BloodworkRules.otherKey("Vrij T4"))
        assertEquals(Identity.Unlisted(ruledOut = "tsh"), vocab.identify("FT4", "tsh"))
        assertEquals(Identity.Unlisted(ruledOut = "hemoglobin"), vocab.identify("HbA1c", "hemoglobin"))
        assertEquals(Identity.Agreed("hemoglobin"), vocab.identify("Hemoglobine", "hemoglobin"))
        assertEquals(1.611, vocab.unit("hemoglobin", "mmol/l", byName = true)!!.factor)
        assertEquals(Identity.Unlisted(ruledOut = "glucose"), vocab.identify("Glucose (niet nuchter)", "glucose"))
        assertEquals(Identity.Unlisted(confirm = "glucose"), vocab.identify("Glucose", "glucose"))
        assertEquals("other:glucose", BloodworkRules.otherKey("Glucose"))
        assertEquals(Identity.Unlisted(ruledOut = "creatinine"), vocab.identify("Kreatinine urine", "creatinine"))
        assertEquals(Identity.ByName("creatinine"), vocab.identify("Kreatinine", "other"))
        assertNull(vocab.unit("creatinine", "mmol/l", byName = true), "mmol/l does not fit creatinine")
        assertEquals("other:kreatinine", BloodworkRules.otherKey("Kreatinine"))
        assertEquals(Identity.Unlisted(ruledOut = "hdl"), vocab.identify("Cholesterol/HDL", "hdl"))
        assertEquals(Identity.Unlisted(ruledOut = "ck"), vocab.identify("CK-MB", "ck"))
        assertEquals(Identity.ByKey("free_testosterone"), vocab.identify("Vrije testosteronfractie", "free_testosterone"))
        assertEquals(288.4, vocab.unit("free_testosterone", "nmol/l", byName = false)!!.factor)
        assertEquals(Identity.Agreed("total_testosterone"), vocab.identify("Testosteron (ochtend)", "total_testosterone"))
        assertEquals(Identity.Agreed("total_testosterone"), vocab.identify("Testosteron (LC-MS/MS)", "total_testosterone"))
    }

    @Test
    fun exclusionsAndTraps() {
        for (name in listOf("LDL/HDL", "Cholesterol/HDL ratio", "Albumine/kreatinine ratio", "LH/FSH quotient", "Testosteron/SHBG verhouding", "Vrije androgenen index")) {
            assertEquals(NameMatch.Excluded, vocab.match(name), name)
        }
        assertEquals(Identity.Unlisted(), vocab.identify("LDL/HDL", "other"))
        assertEquals(Identity.Unlisted(confirm = "glucose"), vocab.identify("Glucose (serum)", "hdl"), "a confirm name ignores the key")
        val traps = listOf(
            "Glucose 2 uur" to "glucose", "Glucose random" to "glucose", "Macroprolactine" to "prolactin",
            "Vrij PSA" to "psa", "PSA vrij/totaal" to "psa", "Vrij PSA" to "free_testosterone", "Estron" to "estradiol",
            "MCHC" to "hemoglobin", "Kreatinineklaring" to "creatinine", "Creatinine klaring" to "creatinine",
            "Micro-albumine" to "albumin", "TSH-receptor antistoffen" to "tsh", "DHT" to "total_testosterone",
            "Testosteron vrij %" to "total_testosterone", "% vrij testosteron" to "free_testosterone",
            "Non-HDL" to "cholesterol",
        )
        for ((name, key) in traps) {
            val identity = vocab.identify(name, key)
            assertTrue(identity is Identity.Unlisted || identity is Identity.ByName && identity.key != key, "$name as $key: $identity")
        }
    }

    @Test
    fun unknownNamesFollowAKnownKeyOnly() {
        assertEquals(Identity.ByKey("tsh"), vocab.identify("Thyroidea stimulerend", "tsh"))
        assertEquals(Identity.ByKey("ldl"), vocab.identify("", "ldl"), "no name: the key alone")
        assertEquals(Identity.Unlisted(), vocab.identify("Ferritine", "other"))
        assertEquals(Identity.Unlisted(), vocab.identify("Ferritine", "ferritin"), "an unknown key counts as none")
        assertEquals(Identity.Unlisted(), vocab.identify("Ferritine", null))
        assertEquals(NameMatch.Unknown, vocab.match("Ferritine"))
    }

    // Accepted units and factors (§5.6)

    @Test
    fun everyMarkersUnitsArePinned() {
        val expected = mapOf(
            "total_testosterone" to "nmol/L 28.84, ng/dL 1.0, ng/mL 100.0, µg/L 100.0",
            "free_testosterone" to "pmol/L 0.2884, nmol/L 288.4, pg/mL 1.0, ng/L 1.0, ng/dL 10.0",
            "estradiol" to "pmol/L 0.2724, nmol/L 272.4, pg/mL 1.0, ng/L 1.0",
            "shbg" to "nmol/L 1.0",
            "lh" to "U/L 1.0, IU/L 1.0, E/L 1.0, IE/L 1.0, mIU/mL 1.0, mU/mL 1.0",
            "fsh" to "U/L 1.0, IU/L 1.0, E/L 1.0, IE/L 1.0, mIU/mL 1.0, mU/mL 1.0",
            "prolactin" to "mU/L 0.0472, mIU/L 0.0472, mE/L 0.0472, µIU/mL 0.0472, U/L 47.2, IU/L 47.2, E/L 47.2, ng/mL 1.0, µg/L 1.0",
            "tsh" to "mU/L 1.0, mIU/L 1.0, mE/L 1.0, µIU/mL 1.0, µU/mL 1.0",
            "hemoglobin" to "mmol/L 1.611, g/dL 1.0, g/L 0.1",
            "hematocrit" to "L/L 100.0, % 1.0",
            "cholesterol" to "mmol/L 38.67, mg/dL 1.0",
            "hdl" to "mmol/L 38.67, mg/dL 1.0",
            "ldl" to "mmol/L 38.67, mg/dL 1.0",
            "non_hdl" to "mmol/L 38.67, mg/dL 1.0",
            "triglycerides" to "mmol/L 88.57, mg/dL 1.0",
            "glucose" to "mmol/L 18.0, mg/dL 1.0",
            "creatinine" to "µmol/L 0.011312, mg/dL 1.0",
            "egfr" to "mL/min/1.73m² 1.0, mL/min 1.0 by name",
            "albumin" to "g/L 0.1, g/dL 1.0",
            "ast" to "U/L 1.0, IU/L 1.0, E/L 1.0, µkat/L 60.0",
            "alt" to "U/L 1.0, IU/L 1.0, E/L 1.0, µkat/L 60.0",
            "ggt" to "U/L 1.0, IU/L 1.0, E/L 1.0, µkat/L 60.0",
            "ck" to "U/L 1.0, IU/L 1.0, E/L 1.0, µkat/L 60.0",
            "psa" to "µg/L 1.0, ng/mL 1.0",
        )
        val actual = vocab.units.mapValues { (_, units) ->
            units.joinToString { "${it.display} ${it.factor}" + if (it.onlyByName) " by name" else "" }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun storedUnitsHaveFactorOneAndSiUnitsTheAppsOwnConversion() {
        for (m in BloodMarkers.all) {
            assertEquals(1.0, vocab.unit(m.key, m.unit, byName = false)?.factor, "${m.key} stored ${m.unit}")
            assertEquals(m.siToConventional, vocab.unit(m.key, m.siUnit, byName = false)?.factor, "${m.key} SI ${m.siUnit}")
        }
    }

    @Test
    fun molarFactorsOfFreeTestosteroneAndEstradiolAgree() {
        for (key in listOf("free_testosterone", "estradiol")) {
            val pmol = vocab.unit(key, "pmol/L", byName = true)!!.factor
            val nmol = vocab.unit(key, "nmol/L", byName = true)!!.factor
            assertTrue(abs(nmol - 1000 * pmol) < 1e-9, key)
        }
    }

    @Test
    fun theWebAppsFactorsAgree() {
        // backend/units.py of the web app, copied: lowercased unit -> factor to the stored unit.
        val web = mapOf(
            "total_testosterone" to mapOf("ng/dl" to 1.0, "nmol/l" to 28.84),
            "free_testosterone" to mapOf("pg/ml" to 1.0, "pmol/l" to 0.2884, "nmol/l" to 288.4, "ng/dl" to 10.0),
            "estradiol" to mapOf("pg/ml" to 1.0, "pmol/l" to 0.2724),
            "shbg" to mapOf("nmol/l" to 1.0),
            "lh" to mapOf("iu/l" to 1.0),
            "fsh" to mapOf("iu/l" to 1.0),
            "tsh" to mapOf("miu/l" to 1.0, "mu/l" to 1.0),
            "hemoglobin" to mapOf("g/dl" to 1.0, "mmol/l" to 1.611),
            "hematocrit" to mapOf("%" to 1.0, "l/l" to 100.0),
            "cholesterol" to mapOf("mg/dl" to 1.0, "mmol/l" to 38.67),
            "hdl" to mapOf("mg/dl" to 1.0, "mmol/l" to 38.67),
            "ldl" to mapOf("mg/dl" to 1.0, "mmol/l" to 38.67),
            "non_hdl" to mapOf("mg/dl" to 1.0, "mmol/l" to 38.67),
            "triglycerides" to mapOf("mg/dl" to 1.0, "mmol/l" to 88.57),
            "glucose" to mapOf("mg/dl" to 1.0, "mmol/l" to 18.0),
            "creatinine" to mapOf("mg/dl" to 1.0, "umol/l" to 0.011312, "µmol/l" to 0.011312),
            "albumin" to mapOf("g/dl" to 1.0, "g/l" to 0.1),
            "ast" to mapOf("u/l" to 1.0),
            "alt" to mapOf("u/l" to 1.0),
            "ggt" to mapOf("u/l" to 1.0),
            "ck" to mapOf("u/l" to 1.0),
            "psa" to mapOf("µg/l" to 1.0, "ug/l" to 1.0, "ng/ml" to 1.0),
        )
        for ((key, units) in web) {
            for ((unit, factor) in units) assertEquals(factor, vocab.unit(key, unit, byName = true)?.factor, "$key $unit")
        }
    }

    @Test
    fun spellingsOfOneUnitShareOneFactor() {
        for ((key, units) in vocab.units) {
            val byForm = units.groupBy({ BloodworkRules.normalizeUnit(it.display) }, { it.factor })
            for ((form, factors) in byForm) assertEquals(1, factors.toSet().size, "$key $form")
        }
    }

    @Test
    fun printedSpellingsAreFound() {
        val spellings = listOf(
            Triple("estradiol", "pmol/l", 0.2724),
            Triple("creatinine", "umol/L", 0.011312),
            Triple("creatinine", "μmol/l", 0.011312),
            Triple("creatinine", "mcmol/L", 0.011312),
            Triple("creatinine", "micromol/l", 0.011312),
            Triple("prolactin", "mE/l", 0.0472),
            Triple("prolactin", "mIU/L", 0.0472),
            Triple("prolactin", "uIU/mL", 0.0472),
            Triple("prolactin", "U/l", 47.2),
            Triple("tsh", "mE/l", 1.0),
            Triple("tsh", "µU/ml", 1.0),
            Triple("tsh", "mIE/l", 1.0),
            Triple("tsh", "µIE/ml", 1.0),
            Triple("prolactin", "mIE/l", 0.0472),
            Triple("lh", "E/l", 1.0),
            Triple("fsh", "IE/l", 1.0),
            Triple("alt", "E/l", 1.0),
            Triple("ggt", "U/I", 1.0),
            Triple("ast", "µkat/l", 60.0),
            Triple("hematocrit", "l/l", 100.0),
            Triple("egfr", "ml/min/1,73m²", 1.0),
            Triple("egfr", "mL/min/1.73m^2", 1.0),
            Triple("egfr", "mL/min/{1.73_m2}", 1.0),
            Triple("psa", "ug/l", 1.0),
            Triple("total_testosterone", "nmol / L", 28.84),
        )
        for ((key, unit, factor) in spellings) assertEquals(factor, vocab.unit(key, unit, byName = true)?.factor, "$key $unit")
    }

    @Test
    fun slipsAndOtherUnitsAreNotAccepted() {
        assertNull(vocab.unit("hematocrit", "1/1", byName = true), "1/1 is not repaired to l/l")
        assertNull(vocab.unit("creatinine", "pmol/l", byName = true), "pmol is not repaired to µmol")
        assertNull(vocab.unit("hemoglobin", "10^9/l", byName = true))
        assertNull(vocab.unit("free_testosterone", "%", byName = true))
        assertNull(vocab.unit("shbg", "", byName = true))
        assertNull(vocab.unit("unknown", "nmol/l", byName = true))
    }

    @Test
    fun bareMlPerMinuteCountsOnlyUnderAnEgfrName() {
        assertEquals(1.0, vocab.unit("egfr", "ml/min", byName = true)?.factor)
        assertNull(vocab.unit("egfr", "ml/min", byName = false))
        assertEquals(listOf("mL/min/1.73m²"), vocab.candidates("egfr", byName = false).map { it.display })
    }

    @Test
    fun candidatesHaveOnePerFactor() {
        assertEquals(listOf("mU/L", "U/L", "ng/mL"), vocab.candidates("prolactin", byName = true).map { it.display })
        assertEquals(listOf("U/L", "µkat/L"), vocab.candidates("ck", byName = true).map { it.display })
        assertEquals(listOf("nmol/L"), vocab.candidates("shbg", byName = true).map { it.display })
        assertEquals(emptyList(), vocab.candidates("unknown", byName = true))
    }

    @Test
    fun percentAndRatioUnitsRuleAMarkerOut() {
        assertTrue(vocab.neverUnit("free_testosterone", "%"), "percent free is another quantity")
        assertTrue(vocab.neverUnit("total_testosterone", " % "))
        assertTrue(vocab.neverUnit("cholesterol", "ratio"))
        assertFalse(vocab.neverUnit("hematocrit", "%"))
        assertFalse(vocab.neverUnit("free_testosterone", "pmol/l"))
        assertFalse(vocab.neverUnit("unknown", "nmol/l"))
    }

    // The Dutch units found in the research (§5.7): printed value -> stored value

    @Test
    fun dutchReportUnitsStoreAsExpected() {
        fun stored(key: String, value: Double, unit: String) = value * vocab.unit(key, unit, byName = true)!!.factor
        assertEquals(24.516, stored("estradiol", 0.09, "nmol/l"), 1e-9)
        assertEquals(51.756, stored("estradiol", 0.19, "nmol/l"), 1e-9)
        assertEquals(9.912, stored("prolactin", 0.21, "U/l"), 1e-9)
        assertEquals(100.94, stored("free_testosterone", 0.35, "nmol/l"), 1e-9)
        assertEquals(57.1032, stored("free_testosterone", 0.198, "nmol/l"), 1e-9)
        assertEquals(178.808, stored("free_testosterone", 0.62, "nmol/l"), 1e-9)
        assertEquals(15.9489, stored("hemoglobin", 9.9, "mmol/l"), 1e-9)
        assertEquals(9.912, stored("prolactin", 210.0, "mE/l"), 1e-9)
        assertEquals(49.0, stored("hematocrit", 0.49, "l/l"), 1e-9)
        assertEquals(95.0, stored("egfr", 95.0, "ml/min/1,73m²"), 1e-9)
    }
}
