package com.apollof.protocoltracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Review 2026-10, F2: symptoms are grouped by body area and no group or label names a hormonal cause or a disease. */
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
    fun noGroupOrLabelNamesACause() {
        val bannedWords = setOf("estrogen", "e2", "hormone", "hormonal", "testosterone", "gynecomastia", "prostate")
        // A cause in brackets ("Constipation (water retention)") or a clinical sign named after a disease.
        val bannedPhrases = listOf("moon face", "(dehydration)", "(water retention)")
        val texts = SymptomGroup.entries.map { it.label } + SymptomCatalog.all.map { it.label }
        for (text in texts) {
            val lower = text.lowercase()
            val words = lower.split(Regex("[^a-z0-9]+")).toSet()
            assertTrue((words intersect bannedWords).isEmpty(), "\"$text\" names ${words intersect bannedWords}")
            bannedPhrases.forEach { assertTrue(it !in lower, "\"$text\" names \"$it\"") }
        }
    }

    @Test
    fun thePickerShowsEachSymptomOnceAndOldDuplicatesTickTheirChip() {
        val picker = SymptomCatalog.picker
        assertEquals(picker.size, picker.map { it.label }.distinct().size, "no two chips read the same")
        for (old in SymptomCatalog.all.filter { it.sameAs != null }) {
            val chip = picker.single { it.key == old.sameAs }
            assertEquals(chip.group, old.group)
            assertTrue(old.key in SymptomCatalog.keysOf(chip.key))
        }
        assertEquals(listOf("loss_of_libido_low", "loss_of_libido_high"), SymptomCatalog.keysOf("loss_of_libido_low"))
        assertEquals(listOf("acne"), SymptomCatalog.keysOf("acne"))
        assertEquals("Urinary changes", SymptomCatalog.label("enlarged_prostate"))
        assertEquals("Breast tenderness / lump", SymptomCatalog.label("gynecomastia"))
    }
}
