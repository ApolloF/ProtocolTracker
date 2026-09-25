package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.Serializable

/** Plan and picker sections, in display order. */
@Serializable
enum class CompoundCategory(val label: String, val plural: String, val tag: String) {
    INJECTABLE_STEROID("Injectable steroid", "Injectable steroids", "INJ"),
    ORAL_STEROID("Oral steroid", "Oral steroids", "ORAL"),
    SUPPORT("Support", "Support", "SUPPORT"),
    PEPTIDE("Peptide", "Peptides", "PEPTIDE"),
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
enum class Route(val label: String) { INJECTION("Injection"), ORAL("Oral"), TOPICAL("Topical") }

/** Display unit of an absolute level curve; the engine works in ng/dL. */
@Serializable
enum class LevelUnit(val label: String, val perNgDl: Double) {
    NG_DL("ng/dL", 1.0),
    NG_ML("ng/mL", 0.01),
    PG_ML("pg/mL", 10.0),
}

/**
 * Steroid Plotter style dose curve (docs/MODELS.md): a dose rises linearly to its peak at [tmaxH],
 * then decays with [halfLifeH]. [peakPerUnit] is the single-dose peak in ng/dL per base unit (mg or IU);
 * null means no study peak, and levels are shown relative as active amount ([activeFraction] × dose).
 */
@Serializable
data class PkParams(
    val halfLifeH: Double,
    val tmaxH: Double,
    val peakPerUnit: Double? = null,
    val activeFraction: Double = 1.0,
    val levelUnit: LevelUnit = LevelUnit.NG_DL,
) {
    init {
        require(halfLifeH.isFinite() && halfLifeH > 0) { "Half-life must be positive" }
        require(tmaxH.isFinite() && tmaxH > 0) { "Time to peak must be positive" }
        require(peakPerUnit == null || (peakPerUnit.isFinite() && peakPerUnit > 0)) { "Peak must be positive" }
        require(activeFraction > 0 && activeFraction <= 1) { "Active fraction must be in (0, 1]" }
    }

    /** Area under one dose's curve per unit of peak, in hours: rise triangle plus exponential tail. */
    val areaPerPeakH: Double get() = tmaxH / 2 + halfLifeH / LN2

    companion object {
        const val LN2 = 0.6931471805599453
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
