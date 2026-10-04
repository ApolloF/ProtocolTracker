package com.apollof.protocoltracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review 2026-10, F2: hedged estrogen headings over the original groups; labels name no disease; every stored key kept. */
class SymptomGroupsTest {
    /** Every key ever stored: logs keep them, so none may be renamed or dropped. */
    private val storedKeys = setOf(
        "dry_skin_lips", "dehydration_feeling", "dry_achy_joints", "loss_of_libido_low", "erectile_dysfunction",
        "loss_of_sensitivity", "dry_glans", "white_glans", "loss_of_girth", "irritability_low", "crying_no_reason", "dht_rage",
        "dull_orgasm", "urination_hesitation", "night_sweats", "loss_of_appetite", "constant_fatigue", "constipation_dehydr",
        "diuretic_effect", "itchy_scalp", "obsessive_thoughts", "acne", "loss_of_libido_high", "water_retention", "moon_face",
        "scrotum_high", "extreme_oiliness", "moodiness_high", "lethargy_high", "insomnia", "soft_erections", "sugar_cravings",
        "high_bp", "bp_spikes", "enlarged_prostate", "pressure_urinating", "thin_stream", "constipation_water", "itchy_nipples",
        "gynecomastia", "headache", "nausea", "injection_site_pain", "back_pumps", "shortness_of_breath",
    )

    @Test
    fun everyStoredKeyIsKept() {
        assertEquals(storedKeys, SymptomCatalog.all.map { it.key }.toSet())
    }

    @Test
    fun theTwoEstrogenHeadingsAreHedgedGenericLabels() {
        assertEquals(
            listOf("Often listed as low-estrogen signs", "Often listed as high-estrogen signs", "General"),
            SymptomGroup.entries.map { it.label },
        )
    }

    @Test
    fun symptomsKeepTheirOriginalGroup() {
        val low = SymptomCatalog.all.filter { it.group == SymptomGroup.LOW_E2 }.map { it.key }
        val high = SymptomCatalog.all.filter { it.group == SymptomGroup.HIGH_E2 }.map { it.key }
        val general = SymptomCatalog.all.filter { it.group == SymptomGroup.GENERAL }.map { it.key }
        assertEquals(21, low.size)
        assertEquals(19, high.size)
        assertEquals(listOf("headache", "nausea", "injection_site_pain", "back_pumps", "shortness_of_breath"), general)
        // The lists overlap on purpose: each pair keeps both keys, so old logs render as before.
        for (pair in listOf("loss_of_libido_low" to "loss_of_libido_high", "constant_fatigue" to "lethargy_high", "constipation_dehydr" to "constipation_water")) {
            assertTrue(pair.first in low && pair.second in high, "$pair")
        }
        assertTrue("gynecomastia" in high && "enlarged_prostate" in high && "high_bp" in high)
    }

    @Test
    fun labelsNameNoDiseaseOrBracketedCause() {
        val bannedWords = setOf("gynecomastia", "prostate")
        val bannedPhrases = listOf("moon face", "(dehydration)", "(water retention)")
        for (label in SymptomCatalog.all.map { it.label }) {
            val lower = label.lowercase()
            assertTrue(lower.split(Regex("[^a-z0-9]+")).none { it in bannedWords }, "\"$label\" names a disease")
            bannedPhrases.forEach { assertTrue(it !in lower, "\"$label\" names \"$it\"") }
        }
        assertEquals("Puffy face", SymptomCatalog.label("moon_face"))
        assertEquals("Breast tenderness / lump", SymptomCatalog.label("gynecomastia"))
        assertEquals("Urinary changes", SymptomCatalog.label("enlarged_prostate"))
    }

    @Test
    fun theHeadingsAreTheOnlyPlaceEstrogenIsNamed() {
        val named = SymptomCatalog.all.filter { "estrogen" in it.label.lowercase() || "e2" in it.label.lowercase().split(' ') }
        assertTrue(named.isEmpty(), "labels naming estrogen: $named")
    }
}
