package com.apollof.protocoltracker.domain.model

/**
 * Symptom groups, in the order the picker shows them. The two estrogen headings are fixed, generic labels for common
 * community lists, like a leaflet; they say nothing about the user's own data (review 2026-10, F2).
 */
enum class SymptomGroup(val label: String) {
    LOW_E2("Often listed as low-estrogen signs"),
    HIGH_E2("Often listed as high-estrogen signs"),
    GENERAL("General"),
}

data class Symptom(val key: String, val label: String, val group: SymptomGroup)

/**
 * Symptoms that can be ticked in a symptom log, taken from the CycleTracker web app. The two estrogen lists overlap,
 * so a few symptoms sit in both on purpose, each under its own key. The app only records ticks: it never counts,
 * scores or links them to a result, and gives no advice.
 * Keys are stored in logs: never rename one, only add. Labels may change.
 */
object SymptomCatalog {
    val all: List<Symptom> = listOf(
        Symptom("dry_skin_lips", "Dry skin / lips", SymptomGroup.LOW_E2),
        Symptom("dehydration_feeling", "Feeling of dehydration", SymptomGroup.LOW_E2),
        Symptom("dry_achy_joints", "Dry, achy joints", SymptomGroup.LOW_E2),
        Symptom("loss_of_libido_low", "Loss of libido", SymptomGroup.LOW_E2),
        Symptom("erectile_dysfunction", "Erectile dysfunction", SymptomGroup.LOW_E2),
        Symptom("loss_of_sensitivity", "Loss of sensitivity", SymptomGroup.LOW_E2),
        Symptom("dry_glans", "Dry glans", SymptomGroup.LOW_E2),
        Symptom("white_glans", "White glans", SymptomGroup.LOW_E2),
        Symptom("loss_of_girth", "Loss of girth", SymptomGroup.LOW_E2),
        Symptom("irritability_low", "Irritability / mood swings", SymptomGroup.LOW_E2),
        Symptom("crying_no_reason", "Crying for no reason", SymptomGroup.LOW_E2),
        Symptom("dht_rage", "Aggression towards others", SymptomGroup.LOW_E2),
        Symptom("dull_orgasm", "Dull orgasm", SymptomGroup.LOW_E2),
        Symptom("urination_hesitation", "Hesitation before urinating", SymptomGroup.LOW_E2),
        Symptom("night_sweats", "Night sweats", SymptomGroup.LOW_E2),
        Symptom("loss_of_appetite", "Loss of appetite", SymptomGroup.LOW_E2),
        Symptom("constant_fatigue", "Constant fatigue / lethargy", SymptomGroup.LOW_E2),
        Symptom("constipation_dehydr", "Constipation", SymptomGroup.LOW_E2),
        Symptom("diuretic_effect", "Urinating a lot", SymptomGroup.LOW_E2),
        Symptom("itchy_scalp", "Itchy scalp", SymptomGroup.LOW_E2),
        Symptom("obsessive_thoughts", "Obsessive thoughts", SymptomGroup.LOW_E2),

        Symptom("acne", "Acne", SymptomGroup.HIGH_E2),
        Symptom("loss_of_libido_high", "Loss of libido", SymptomGroup.HIGH_E2),
        Symptom("water_retention", "Water retention (bloat)", SymptomGroup.HIGH_E2),
        Symptom("moon_face", "Puffy face", SymptomGroup.HIGH_E2),
        Symptom("scrotum_high", "Scrotum hanging high", SymptomGroup.HIGH_E2),
        Symptom("extreme_oiliness", "Oily skin all over", SymptomGroup.HIGH_E2),
        Symptom("moodiness_high", "Moodiness (aggression / low mood)", SymptomGroup.HIGH_E2),
        Symptom("lethargy_high", "Lethargy", SymptomGroup.HIGH_E2),
        Symptom("insomnia", "Insomnia", SymptomGroup.HIGH_E2),
        Symptom("soft_erections", "Soft erections", SymptomGroup.HIGH_E2),
        Symptom("sugar_cravings", "Sugar / chocolate cravings", SymptomGroup.HIGH_E2),
        Symptom("high_bp", "High blood pressure", SymptomGroup.HIGH_E2),
        Symptom("bp_spikes", "Blood pressure spikes", SymptomGroup.HIGH_E2),
        Symptom("enlarged_prostate", "Urinary changes", SymptomGroup.HIGH_E2),
        Symptom("pressure_urinating", "Pressure in lower abdomen when urinating", SymptomGroup.HIGH_E2),
        Symptom("thin_stream", "Thin stream when urinating", SymptomGroup.HIGH_E2),
        Symptom("constipation_water", "Constipation", SymptomGroup.HIGH_E2),
        Symptom("itchy_nipples", "Itchy nipples", SymptomGroup.HIGH_E2),
        Symptom("gynecomastia", "Breast tenderness / lump", SymptomGroup.HIGH_E2),

        Symptom("headache", "Headache", SymptomGroup.GENERAL),
        Symptom("nausea", "Nausea", SymptomGroup.GENERAL),
        Symptom("injection_site_pain", "Injection site pain", SymptomGroup.GENERAL),
        Symptom("back_pumps", "Back pumps / cramps", SymptomGroup.GENERAL),
        Symptom("shortness_of_breath", "Shortness of breath", SymptomGroup.GENERAL),
    )

    private val byKey = all.associateBy { it.key }

    fun find(key: String): Symptom? = byKey[key]

    /** Label of a stored key; unknown keys (from a newer version) show as stored. */
    fun label(key: String): String = byKey[key]?.label ?: key.replace('_', ' ')

    /** Like [label], but an unknown key (kept from the web app, e.g. `high_e2`) reads as a name: "High E2". */
    fun readableLabel(key: String): String = byKey[key]?.label
        ?: key.split('_').filter { it.isNotEmpty() }.joinToString(" ") { if (it.any(Char::isDigit)) it.uppercase() else it }
            .replaceFirstChar { it.uppercase() }

}

/** Hair shedding levels for a symptom log; 0 is not recorded. */
val HAIR_SHEDDING_LABELS = listOf("Slight", "Mild", "Moderate", "Marked", "Severe")
