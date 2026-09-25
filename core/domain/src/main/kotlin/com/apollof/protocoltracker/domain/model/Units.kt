package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.Serializable

/** Unit a dose is entered in. MG/MCG/IU are amounts; ML and TABLET need item conversion data. */
@Serializable
enum class DoseUnit(val label: String) {
    MG("mg"), MCG("mcg"), IU("IU"), ML("mL"), TABLET("tab");
}

/** Dimension a compound's kinetics are expressed in. */
@Serializable
enum class BaseUnit(val label: String) {
    MG("mg"), IU("IU");
}

@Serializable
data class Amount(val value: Double, val unit: DoseUnit) {
    init {
        require(value.isFinite() && value > 0) { "Amount must be positive" }
    }
}

/**
 * Conversion data attached to a plan item or log snapshot.
 * [perMl] is mg/mL or IU/mL (same dimension as the compound base unit); [perTablet] is mg or IU per tablet.
 */
@Serializable
data class Formulation(val perMl: Double? = null, val perTablet: Double? = null) {
    init {
        require(perMl == null || (perMl.isFinite() && perMl > 0)) { "Concentration must be positive" }
        require(perTablet == null || (perTablet.isFinite() && perTablet > 0)) { "Tablet strength must be positive" }
    }
}
