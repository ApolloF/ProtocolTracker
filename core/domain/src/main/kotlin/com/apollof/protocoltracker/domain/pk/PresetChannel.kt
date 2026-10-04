package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.Compound

/**
 * Where the app is distributed. It decides which presets are seeded and how a preset's name is shown, never its
 * kinetics: [Presets.all] stays whole in every build, because lookups, migrations, imports and restores need every
 * preset.
 */
enum class PresetChannel { FOSS, PLAY }

/** Whether this channel seeds the preset with [id] ("preset:test-enan" or "test-enan"). */
fun PresetChannel.allows(id: String): Boolean = when (this) {
    PresetChannel.FOSS -> true
    PresetChannel.PLAY -> id.removePrefix(PRESET_PREFIX) in PLAY_PRESET_IDS
}

/**
 * [preset] as this channel shows it. Play shows presets by INN or compound name only: the common (brand or slang)
 * name is dropped, except hCG's, which is its usual abbreviation.
 */
fun PresetChannel.present(preset: Compound): Compound = when {
    this == PresetChannel.FOSS || !preset.isPreset -> preset
    preset.id.removePrefix(PRESET_PREFIX) in PLAY_COMMON_NAMES -> preset
    else -> preset.copy(commonName = "")
}

/** The presets this channel seeds, as it shows them. */
fun PresetChannel.presets(): List<Compound> = Presets.all.filter { allows(it.id) }.map { present(it) }

private const val PRESET_PREFIX = "preset:"

/** Play presets that keep their common name. */
private val PLAY_COMMON_NAMES = setOf("hcg")

/**
 * The presets the Play build seeds (owner decision 2026-10-04; Melanotan II is in, as on PepPedia). Edit this list to
 * change what Play ships; PresetChannelTest checks every id exists. Everything else is FOSS-only, among them
 * every nandrolone, trenbolone, drostanolone, methenolone, boldenone, 1-testosterone, stanozolol and trestolone preset,
 * the other oral steroids, test-undec-mct, test-susp, the SARMs and research compounds, enclomiphene, clenbuterol and
 * mk-677.
 */
val PLAY_PRESET_IDS: Set<String> = setOf(
    // Testosterone and mesterolone
    "test-enan", "test-cyp", "test-prop", "test-undec", "test-pp", "test-iso", "test-dec", "test-gel", "test-undec-oral", "proviron",
    // test-base-sl: owner decision, currently out.
    // deca: owner decision, currently out.
    // oxymetholone: owner decision, currently out.
    // Estradiol and progesterone
    "estradiol-gel", "estradiol-cyp", "estradiol-val", "progesterone-oral", "progesterone-vaginal",
    // Support
    "anastrozole", "exemestane", "letrozole", "tamoxifen", "clomiphene", "hcg", "cabergoline", "telmisartan", "nebivolol", "tadalafil",
    "liothyronine",
    // Approved peptides
    "semaglutide", "semaglutide-oral", "tirzepatide", "liraglutide", "hgh", "tesamorelin", "pt-141",
    // Other peptides
    "retatrutide", "mazdutide", "cagrilintide", "ipamorelin", "cjc-1295", "cjc-1295-dac", "bpc-157", "tb-500", "ghk-cu", "aod-9604", "mots-c",
    "melanotan-2",
    // mk-677: owner decision, currently out.
)
