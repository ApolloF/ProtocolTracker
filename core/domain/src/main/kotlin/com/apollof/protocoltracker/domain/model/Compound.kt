package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompoundCategory(val label: String) {
    INJECTABLE("Injectable"), ORAL("Oral"), HCG("hCG / peptide"), AI("Aromatase inhibitor"), SERM("SERM"), OTHER("Other");
}

/**
 * One-compartment model with first-order absorption, expressed as half-lives.
 * [eliminationHalfLifeH] is the apparent terminal half-life (for depot esters this includes release).
 * [activeFraction] converts ester mass to active-moiety mass. [bioavailability] scales the absorbed amount.
 */
@Serializable
data class PkParams(
    val absorptionHalfLifeH: Double,
    val eliminationHalfLifeH: Double,
    val activeFraction: Double = 1.0,
    val bioavailability: Double = 1.0,
) {
    init {
        require(absorptionHalfLifeH.isFinite() && absorptionHalfLifeH > 0) { "Absorption half-life must be positive" }
        require(eliminationHalfLifeH.isFinite() && eliminationHalfLifeH > 0) { "Elimination half-life must be positive" }
        require(activeFraction > 0 && activeFraction <= 1) { "Active fraction must be in (0, 1]" }
        require(bioavailability > 0 && bioavailability <= 1) { "Bioavailability must be in (0, 1]" }
    }

    val ka: Double get() = LN2 / absorptionHalfLifeH
    val ke: Double get() = LN2 / eliminationHalfLifeH

    private companion object {
        const val LN2 = 0.6931471805599453
    }
}

@Serializable
data class Compound(
    val id: String,
    val name: String,
    /** Analyte group; compounds sharing a group are summed on the levels chart (e.g. all testosterone esters). */
    val group: String,
    val category: CompoundCategory,
    val baseUnit: BaseUnit,
    val colorArgb: Long,
    val pk: PkParams,
    val defaultFormulation: Formulation = Formulation(),
    val sourceNote: String = "",
    val isPreset: Boolean = false,
    val archived: Boolean = false,
)
