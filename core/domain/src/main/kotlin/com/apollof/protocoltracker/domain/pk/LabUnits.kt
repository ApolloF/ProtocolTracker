package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LevelUnit
import java.time.Instant

/** Units for level curves and lab results: conventional (ng/dL, pg/mL) or SI (nmol/L, pmol/L). */
enum class LabUnits(val label: String) {
    CONVENTIONAL("ng/dL, pg/mL"),
    SI("nmol/L, pmol/L"),
}

/** Unit label and the factor that turns a curve value into it. */
data class LevelDisplay(val label: String, val factor: Double)

/**
 * Molar masses (g/mol, PubChem) of the parent molecules plotted per group, used to show absolute curves in SI units.
 * Peptides and hormones measured by mass (hCG, somatropin, GLP-1 agonists) are not listed and keep mass units.
 */
object MolarMass {
    val byGroup: Map<String, Double> = mapOf(
        "Testosterone" to 288.42,
        "Nandrolone" to 274.40,
        "Trenbolone" to 270.37,
        "Drostanolone" to 304.47,
        "Methenolone" to 302.45,
        "Boldenone" to 286.41,
        "1-Testosterone" to 288.42,
        "Stanozolol" to 328.49,
        "Trestolone" to 288.42,
        "Oxandrolone" to 306.44,
        "Methandienone" to 300.44,
        "Oxymetholone" to 332.48,
        "Chlorodehydromethyltestosterone" to 334.88,
        "Fluoxymesterone" to 336.44,
        "Methasterone" to 318.49,
        "Mesterolone" to 304.47,
        "Anastrozole" to 293.37,
        "Exemestane" to 296.40,
        "Letrozole" to 285.30,
        "Tamoxifen" to 371.51,
        "Clomiphene" to 405.96,
        "Enclomiphene" to 405.96,
        "Cabergoline" to 451.60,
        "Telmisartan" to 514.62,
        "Nebivolol" to 405.44,
        "Tadalafil" to 389.40,
        "Liothyronine" to 650.97,
        "Clenbuterol" to 277.19,
        "Ibutamoren" to 528.67,
    )
}

/** A lab result placed on a level curve, in the curve's display unit. */
data class LabPoint(val at: Instant, val value: Double, val marker: String)

/** Blood markers that measure a plotted group directly. */
private val markerGroups = mapOf("total_testosterone" to "Testosterone")

/**
 * Lab results of [group] from [journal], converted to the unit the curve is shown in. Only absolute curves in the
 * marker's own unit qualify, so an estimate and a measurement are never mixed across units.
 */
fun labPoints(
    group: String,
    scale: LevelScale,
    display: LevelDisplay,
    journal: List<JournalEntry>,
): List<LabPoint> {
    if (scale.relative) return emptyList()
    val keys = markerGroups.filterValues { it == group }.keys
    return journal.filterIsInstance<JournalEntry.Bloodwork>().flatMap { entry ->
        entry.results.mapNotNull { r ->
            val marker = BloodMarkers.find(r.marker)?.takeIf { it.key in keys } ?: return@mapNotNull null
            if (marker.unit != scale.unit.label) return@mapNotNull null
            LabPoint(entry.at, r.value * display.factor, marker.key)
        }
    }.sortedBy { it.at }
}

/**
 * How to show [scale]'s values for [group]. Relative curves and groups without a molar mass keep their own unit;
 * SI turns mass per volume into amount per litre (ng/dL and ng/mL into nmol/L, pg/mL into pmol/L).
 */
fun levelDisplay(scale: LevelScale, group: String, units: LabUnits): LevelDisplay {
    val own = LevelDisplay(scale.label, 1.0)
    if (units == LabUnits.CONVENTIONAL || scale.relative) return own
    val mass = MolarMass.byGroup[group] ?: return own
    return when (scale.unit) {
        // ng/dL × 10 = ng/L; ng/L ÷ (g/mol) = nmol/L.
        LevelUnit.NG_DL -> LevelDisplay("nmol/L", 10.0 / mass)
        LevelUnit.NG_ML -> LevelDisplay("nmol/L", 1_000.0 / mass)
        // pg/mL = ng/L, so ÷ (g/mol) gives nmol/L; × 1000 gives pmol/L.
        LevelUnit.PG_ML -> LevelDisplay("pmol/L", 1_000.0 / mass)
    }
}
