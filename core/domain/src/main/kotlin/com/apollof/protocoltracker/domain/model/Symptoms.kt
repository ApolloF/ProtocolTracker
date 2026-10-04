package com.apollof.protocoltracker.domain.model

/** Symptom groups by body area, in the order the picker shows them. They name no cause (review 2026-10, F2). */
enum class SymptomGroup(val label: String) {
    MOOD("Mood and sleep"),
    ENERGY("Energy and appetite"),
    SEXUAL("Sexual"),
    SKIN("Skin and hair"),
    BODY("Body"),
    CHEST("Chest"),
    URINARY("Urinary"),
    HEART("Heart and breathing"),
    GENERAL("General"),
}

/** [sameAs]: an older duplicate of that key; it keeps its label for old logs but has no chip of its own. */
data class Symptom(val key: String, val label: String, val group: SymptomGroup, val sameAs: String? = null)

/**
 * Symptoms that can be ticked in a symptom log, taken from the CycleTracker web app, grouped by body area. The app
 * only records them: no group or label suggests a cause, and nothing is counted or advised.
 * Keys are stored in logs: never rename one, only add. Labels may change.
 */
object SymptomCatalog {
    val all: List<Symptom> = listOf(
        Symptom("irritability_low", "Irritability / mood swings", SymptomGroup.MOOD),
        Symptom("moodiness_high", "Moodiness (aggression / low mood)", SymptomGroup.MOOD),
        Symptom("crying_no_reason", "Crying for no reason", SymptomGroup.MOOD),
        Symptom("dht_rage", "Aggression towards others", SymptomGroup.MOOD),
        Symptom("obsessive_thoughts", "Obsessive thoughts", SymptomGroup.MOOD),
        Symptom("insomnia", "Insomnia", SymptomGroup.MOOD),

        Symptom("constant_fatigue", "Constant fatigue / lethargy", SymptomGroup.ENERGY),
        Symptom("lethargy_high", "Lethargy", SymptomGroup.ENERGY, sameAs = "constant_fatigue"),
        Symptom("loss_of_appetite", "Loss of appetite", SymptomGroup.ENERGY),
        Symptom("sugar_cravings", "Sugar / chocolate cravings", SymptomGroup.ENERGY),

        Symptom("loss_of_libido_low", "Loss of libido", SymptomGroup.SEXUAL),
        Symptom("loss_of_libido_high", "Loss of libido", SymptomGroup.SEXUAL, sameAs = "loss_of_libido_low"),
        Symptom("erectile_dysfunction", "Erectile dysfunction", SymptomGroup.SEXUAL),
        Symptom("soft_erections", "Soft erections", SymptomGroup.SEXUAL),
        Symptom("loss_of_sensitivity", "Loss of sensitivity", SymptomGroup.SEXUAL),
        Symptom("dull_orgasm", "Dull orgasm", SymptomGroup.SEXUAL),
        Symptom("dry_glans", "Dry glans", SymptomGroup.SEXUAL),
        Symptom("white_glans", "White glans", SymptomGroup.SEXUAL),
        Symptom("loss_of_girth", "Loss of girth", SymptomGroup.SEXUAL),
        Symptom("scrotum_high", "Scrotum hanging high", SymptomGroup.SEXUAL),

        Symptom("dry_skin_lips", "Dry skin / lips", SymptomGroup.SKIN),
        Symptom("acne", "Acne", SymptomGroup.SKIN),
        Symptom("extreme_oiliness", "Oily skin all over", SymptomGroup.SKIN),
        Symptom("itchy_scalp", "Itchy scalp", SymptomGroup.SKIN),

        Symptom("water_retention", "Water retention (bloat)", SymptomGroup.BODY),
        Symptom("moon_face", "Puffy face", SymptomGroup.BODY),
        Symptom("dehydration_feeling", "Feeling of dehydration", SymptomGroup.BODY),
        Symptom("dry_achy_joints", "Dry, achy joints", SymptomGroup.BODY),
        Symptom("night_sweats", "Night sweats", SymptomGroup.BODY),
        Symptom("constipation_dehydr", "Constipation", SymptomGroup.BODY),
        Symptom("constipation_water", "Constipation", SymptomGroup.BODY, sameAs = "constipation_dehydr"),

        Symptom("itchy_nipples", "Itchy nipples", SymptomGroup.CHEST),
        Symptom("gynecomastia", "Breast tenderness / lump", SymptomGroup.CHEST),

        Symptom("urination_hesitation", "Hesitation before urinating", SymptomGroup.URINARY),
        Symptom("diuretic_effect", "Urinating a lot", SymptomGroup.URINARY),
        Symptom("pressure_urinating", "Pressure in lower abdomen when urinating", SymptomGroup.URINARY),
        Symptom("thin_stream", "Thin stream when urinating", SymptomGroup.URINARY),
        Symptom("enlarged_prostate", "Urinary changes", SymptomGroup.URINARY),

        Symptom("high_bp", "High blood pressure", SymptomGroup.HEART),
        Symptom("bp_spikes", "Blood pressure spikes", SymptomGroup.HEART),
        Symptom("shortness_of_breath", "Shortness of breath", SymptomGroup.HEART),

        Symptom("headache", "Headache", SymptomGroup.GENERAL),
        Symptom("nausea", "Nausea", SymptomGroup.GENERAL),
        Symptom("injection_site_pain", "Injection site pain", SymptomGroup.GENERAL),
        Symptom("back_pumps", "Back pumps / cramps", SymptomGroup.GENERAL),
    )

    /** The symptoms with a chip in the picker: every one but the older duplicates. */
    val picker: List<Symptom> = all.filter { it.sameAs == null }

    /** Keys that tick [key]'s chip: the key and its older duplicates. */
    fun keysOf(key: String): List<String> = all.filter { it.key == key || it.sameAs == key }.map { it.key }

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
