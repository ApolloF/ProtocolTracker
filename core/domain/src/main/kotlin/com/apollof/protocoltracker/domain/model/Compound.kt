package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Plan and picker sections, in display order. */
@Serializable
enum class CompoundCategory(val label: String, val plural: String, val tag: String) {
    INJECTABLE_STEROID("Injectable steroid", "Injectable steroids", "INJ"),
    ORAL_STEROID("Oral steroid", "Oral steroids", "ORAL"),
    /** Hormone therapy by any route: estradiol, progesterone, and testosterone as gel or sublingual. */
    HORMONE("Hormone", "Hormones", "HORMONE"),
    /** SARMs and other compounds never approved as medicines (cardarine is not a SARM but is listed with them). */
    RESEARCH("Research compound", "SARMs and research compounds", "RESEARCH"),
    SUPPORT("Support", "Support", "SUPPORT"),
    PEPTIDE("Peptide", "Peptides", "PEPTIDE"),
}

/**
 * Sections the custom-compound editor offers. Without [offerEmptyResearch] (Play) the research section shows only
 * when one of [compounds] already uses it or the compound being edited, [editing], is in it.
 */
fun categoryChoices(offerEmptyResearch: Boolean, compounds: Collection<Compound>, editing: CompoundCategory?): List<CompoundCategory> {
    val showResearch = offerEmptyResearch || editing == CompoundCategory.RESEARCH || compounds.any { it.category == CompoundCategory.RESEARCH }
    return CompoundCategory.entries.filter { it != CompoundCategory.RESEARCH || showResearch }
}

/** Order of ancillaries inside the Support section. */
@Serializable
enum class SupportKind(val label: String) {
    AI("Aromatase inhibitor"),
    SERM("SERM"),
    FERTILITY("Fertility"),
    PROLACTIN("Prolactin"),
    CARDIO("Blood pressure"),
    SEXUAL_HEALTH("Sexual health"),
    THYROID("Thyroid"),
    OTHER("Other"),
}

@Serializable
enum class Route(val label: String, val countsInTablets: Boolean = false) {
    INJECTION("Injection"), ORAL("Oral", true), TOPICAL("Topical"), SUBLINGUAL("Sublingual", true), VAGINAL("Vaginal", true),
}

/** Display unit of an absolute level curve; the engine works in ng/dL. */
@Serializable
enum class LevelUnit(val label: String, val perNgDl: Double) {
    NG_DL("ng/dL", 1.0),
    NG_ML("ng/mL", 0.01),
    PG_ML("pg/mL", 10.0),
}

/**
 * How a dose rises to its peak (docs/MODELS.md). Compounds with a study peak ("Advanced") are drawn
 * as a straight line to the peak and compounds without one ("Basic") as first-order absorption with a half-time
 * of a third of the time to peak (an eighth of the half-life), which reaches 87.5 % of the absorbed dose at the peak.
 */
@Serializable
enum class Rise { LINEAR, FIRST_ORDER }

/**
 * Dose curve (docs/MODELS.md): a dose rises to its peak at [tmaxH] (see [rise]),
 * then decays with [halfLifeH]. [peakPerUnit] is the single-dose peak in ng/dL per base unit (mg or IU);
 * null means no peak, and levels are shown relative as active amount ([activeFraction] × dose).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PkParams(
    val halfLifeH: Double,
    val tmaxH: Double,
    val peakPerUnit: Double? = null,
    val activeFraction: Double = 1.0,
    val levelUnit: LevelUnit = LevelUnit.NG_DL,
    /** Absent in data stored before presets-2026-10b, which all rose linearly. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rise: Rise = Rise.LINEAR,
) {
    init {
        require(halfLifeH.isFinite() && halfLifeH > 0) { "Half-life must be positive" }
        require(tmaxH.isFinite() && tmaxH > 0) { "Time to peak must be positive" }
        require(peakPerUnit == null || (peakPerUnit.isFinite() && peakPerUnit > 0)) { "Peak must be positive" }
        require(activeFraction > 0 && activeFraction <= 1) { "Active fraction must be in (0, 1]" }
    }

    /** Area under one dose's curve per unit of peak, in hours: the rise plus the exponential tail. */
    val areaPerPeakH: Double get() = when (rise) {
        Rise.LINEAR -> tmaxH / 2
        // ∫₀ᵀ (1 − 2^(−3t/T)) dt ÷ (1 − 2^−3) = T / 0.875 − T / (3 ln 2)
        Rise.FIRST_ORDER -> tmaxH / FIRST_ORDER_PEAK_SHARE - tmaxH / (3 * LN2)
    } + halfLifeH / LN2

    companion object {
        const val LN2 = 0.6931471805599453
        /** Share of the absorbed dose reached at the peak with first-order absorption: 1 − 2^−3. */
        const val FIRST_ORDER_PEAK_SHARE = 0.875
    }
}

@Serializable
data class Compound(
    val id: String,
    /** Scientific name, e.g. "oxandrolone" or "testosterone enanthate". */
    val name: String,
    /** Colloquial name, e.g. "Anavar"; empty when the compound goes by its scientific name. */
    val commonName: String = "",
    /** Analyte group; compounds sharing a group are summed on the levels chart (e.g. all testosterone esters). */
    val group: String,
    val category: CompoundCategory,
    val supportKind: SupportKind? = null,
    val route: Route,
    val baseUnit: BaseUnit,
    val colorArgb: Long,
    /** Null: no reliable level data. The compound can be planned and logged but has no curve. */
    val pk: PkParams?,
    val defaultFormulation: Formulation = Formulation(),
    val sourceNote: String = "",
    val isPreset: Boolean = false,
    /** A preset the user changed; preset refreshes leave it alone. */
    val edited: Boolean = false,
    val archived: Boolean = false,
) {
    val displayName: String get() = displayName(commonName, name)
}

/** "Anavar (oxandrolone)", or the scientific name alone with a capital first letter. */
fun displayName(commonName: String, name: String): String = when {
    commonName.isBlank() -> name.replaceFirstChar { it.titlecase() }
    commonName.equals(name, ignoreCase = true) || name.isBlank() -> commonName
    else -> "$commonName ($name)"
}

/** Plan and picker order: category, support kind, then display name. */
val compoundOrder: Comparator<Compound> = compareBy<Compound>(
    { it.category.ordinal },
    { it.supportKind?.ordinal ?: -1 },
    { it.displayName.lowercase() },
)
