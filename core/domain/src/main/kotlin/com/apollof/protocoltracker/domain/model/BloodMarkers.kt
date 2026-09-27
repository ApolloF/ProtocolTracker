package com.apollof.protocoltracker.domain.model

import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.units.formatNumber
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Marker categories, in display order. */
enum class MarkerCategory(val label: String) {
    HORMONES("Hormones"),
    HEMATOLOGY("Blood count"),
    LIPIDS("Lipids"),
    METABOLIC("Metabolic"),
    KIDNEY("Kidney"),
    LIVER("Liver"),
    OTHER("Other"),
}

/**
 * A blood marker. Results are stored in [unit] (conventional); [siUnit] × [siToConventional] converts an SI entry.
 * [refLow]/[refHigh] are typical adult male reference limits in [unit]; a lab's own range can differ.
 */
data class BloodMarker(
    val key: String,
    val name: String,
    val category: MarkerCategory,
    val unit: String,
    val siUnit: String,
    val siToConventional: Double,
    val refLow: Double?,
    val refHigh: Double?,
) {
    val hasSi: Boolean get() = siUnit != unit

    fun unitFor(units: LabUnits): String = if (units == LabUnits.SI) siUnit else unit

    /** Converts a value entered in [units] to the stored (conventional) unit. */
    fun toStored(value: Double, units: LabUnits): Double = if (units == LabUnits.SI) value * siToConventional else value

    fun fromStored(value: Double, units: LabUnits): Double = if (units == LabUnits.SI) value / siToConventional else value

    fun flag(value: Double): MarkerFlag = when {
        refLow != null && value < refLow -> MarkerFlag.LOW
        refHigh != null && value > refHigh -> MarkerFlag.HIGH
        else -> MarkerFlag.NORMAL
    }

    /** "12.3 nmol/L" in [units]. */
    fun format(value: Double, units: LabUnits): String {
        val shown = fromStored(value, units)
        return "${formatNumber(shown, decimalsFor(shown))} ${unitFor(units)}"
    }

    /** "264–916 ng/dL", "< 130 mg/dL" or null without limits. */
    fun referenceText(units: LabUnits): String? = rangeText(RefRange(refLow, refHigh), units)

    /** [range] (in the stored unit) as "264–916 ng/dL", "< 130 mg/dL" or "> 60 mL/min", in [units]; null without limits. */
    fun rangeText(range: RefRange, units: LabUnits): String? {
        fun f(v: Double) = fromStored(v, units).let { formatNumber(it, decimalsFor(it)) }
        return limitsText(range.low?.let(::f), range.high?.let(::f), unitFor(units))
    }

    /** [result]'s value in [units] as reported: "<0.3 IU/L" for a censored value, else as [format]. */
    fun formatResult(result: MarkerResult, units: LabUnits): String = result.qualifier.orEmpty() + format(result.value, units)

    private fun decimalsFor(v: Double) = when {
        v >= 100 -> 0
        v >= 10 -> 1
        else -> 2
    }
}

/** Result against the reference range; shown as text, never by colour alone. */
enum class MarkerFlag(val label: String) { LOW("Low"), NORMAL("In range"), HIGH("High") }

/**
 * One measured value, stored in the marker's conventional unit (an unlisted `other:` result: in [unit] as printed).
 * The optional fields are never written when null, so a plain result is stored exactly as before they existed.
 * They get no `require`: a bad stored value must never break the journal or a backup restore, so readers
 * validate instead ([labRange], [flag]).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarkerResult(
    val marker: String,
    val value: Double,
    /** [BELOW] or [ABOVE] when the lab reported only a limit ("<0.1"); [value] is that limit. Anything else: no flag. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val qualifier: String? = null,
    /** The lab's own limits, in the same unit as [value]. A null side has no limit; both null = no lab range. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val refLow: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val refHigh: Double? = null,
    /** Unlisted results (`other:` keys) only: the test name and unit as printed. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
) {
    init {
        require(value.isFinite() && value >= 0) { "Results must be zero or more" }
    }

    companion object {
        /** [qualifier]: the true value is below [value]. */
        const val BELOW = "<"

        /** [qualifier]: the true value is above [value]. */
        const val ABOVE = ">"
    }
}

/** Reference limits in a result's stored unit. Limits count as in range; a null side has no limit. */
data class RefRange(val low: Double?, val high: Double?)

/** The lab's own range; null when it gave none or it is invalid (a side negative or not finite, or low above high). */
fun MarkerResult.labRange(): RefRange? {
    val low = refLow
    val high = refHigh
    if (low == null && high == null) return null
    if (low != null && !(low.isFinite() && low >= 0)) return null
    if (high != null && !(high.isFinite() && high >= 0)) return null
    if (low != null && high != null && low > high) return null
    return RefRange(low, high)
}

/** The lab's range when it gave one (used alone, never mixed with defaults), else the known marker's typical range. */
fun MarkerResult.range(): RefRange? = labRange() ?: BloodMarkers.find(marker)?.let { RefRange(it.refLow, it.refHigh) }

/**
 * The result against [range]. Null when there is no range, the qualifier is unknown, or a censored value's true value
 * could lie on both sides of a limit (E2 `<40` against 20–150). A plain result flags exactly as [BloodMarker.flag].
 */
fun MarkerResult.flag(): MarkerFlag? {
    val r = range() ?: return null
    val low = r.low
    val high = r.high
    val x = value
    return when (qualifier) {
        null -> when {
            low != null && x < low -> MarkerFlag.LOW
            high != null && x > high -> MarkerFlag.HIGH
            else -> MarkerFlag.NORMAL
        }
        MarkerResult.BELOW -> when {
            low != null && x <= low -> MarkerFlag.LOW
            (low == null || low == 0.0) && (high == null || x <= high) -> MarkerFlag.NORMAL
            else -> null
        }
        MarkerResult.ABOVE -> when {
            high != null && x >= high -> MarkerFlag.HIGH
            high == null && (low == null || x >= low) -> MarkerFlag.NORMAL
            else -> null
        }
        else -> null
    }
}

/** A result with a range but no flag: a censored value across a limit, or an unknown qualifier. */
val MarkerResult.unclear: Boolean get() = range() != null && flag() == null

/** Decimals kept for an unlisted result, which is stored as printed: enough for any printed value, no float noise. */
private const val PRINTED_DECIMALS = 6

/**
 * An unlisted result (no known marker) as printed: "Vrij T4 15.2 pmol/l" or "CRP <1 mg/l". Without a printed name it
 * reads "<key> <value>" as it always has.
 */
fun MarkerResult.printedText(): String {
    val number = qualifier.orEmpty() + formatNumber(value, if (name != null) PRINTED_DECIMALS else 2)
    return listOfNotNull(name ?: marker, number, printedUnit()).joinToString(" ")
}

/** An unlisted result's lab range as printed: "10–23 pmol/l", "< 10 mg/l"; null without a valid lab range. */
fun MarkerResult.printedLabRange(): String? = labRange()?.let { r ->
    limitsText(r.low?.let { formatNumber(it, PRINTED_DECIMALS) }, r.high?.let { formatNumber(it, PRINTED_DECIMALS) }, printedUnit())
}

/** An unlisted result's number as printed, without its sign or unit: "15.2", "0.3". */
fun MarkerResult.printedNumber(): String = formatNumber(value, PRINTED_DECIMALS)

/** An unlisted result's value as printed, with its sign and unit: "15.2 pmol/l", "<5 mg/l", "<5" without a unit. */
fun MarkerResult.printedValue(): String = listOfNotNull(qualifier.orEmpty() + printedNumber(), printedUnit()).joinToString(" ")

/** The printed unit, or null when none was printed (a blank unit is left out, never shown as a stray space). */
private fun MarkerResult.printedUnit(): String? = unit?.trim()?.takeIf { it.isNotEmpty() }

/** "low–high unit", "< high unit", "> low unit" or null without limits; [unit] may be null (left out). */
private fun limitsText(low: String?, high: String?, unit: String?): String? {
    val u = unit?.let { " $it" }.orEmpty()
    return when {
        low != null && high != null -> "$low–$high$u"
        high != null -> "< $high$u"
        low != null -> "> $low$u"
        else -> null
    }
}

/**
 * Marker list and unit factors from the CycleTracker web app (backend/units.py): conventional units as stored,
 * SI units for entry and display. Keys are stored in results: never rename one, only add.
 */
object BloodMarkers {
    private val H = MarkerCategory.HORMONES
    val all: List<BloodMarker> = listOf(
        BloodMarker("total_testosterone", "Total testosterone", H, "ng/dL", "nmol/L", 28.84, 264.0, 916.0),
        BloodMarker("free_testosterone", "Free testosterone", H, "pg/mL", "pmol/L", 0.2884, 46.0, 224.0),
        BloodMarker("estradiol", "Estradiol (E2)", H, "pg/mL", "pmol/L", 0.2724, 10.0, 40.0),
        BloodMarker("shbg", "SHBG", H, "nmol/L", "nmol/L", 1.0, 18.3, 54.1),
        BloodMarker("lh", "LH", H, "IU/L", "IU/L", 1.0, 1.7, 8.6),
        BloodMarker("fsh", "FSH", H, "IU/L", "IU/L", 1.0, 1.5, 12.4),
        BloodMarker("prolactin", "Prolactin", H, "ng/mL", "mIU/L", 0.0472, 4.0, 15.2),
        BloodMarker("tsh", "TSH", H, "mIU/L", "mIU/L", 1.0, 0.27, 4.2),
        BloodMarker("hemoglobin", "Hemoglobin", MarkerCategory.HEMATOLOGY, "g/dL", "mmol/L", 1.611, 13.5, 17.5),
        BloodMarker("hematocrit", "Hematocrit", MarkerCategory.HEMATOLOGY, "%", "L/L", 100.0, 40.0, 52.0),
        BloodMarker("cholesterol", "Total cholesterol", MarkerCategory.LIPIDS, "mg/dL", "mmol/L", 38.67, null, 200.0),
        BloodMarker("hdl", "HDL cholesterol", MarkerCategory.LIPIDS, "mg/dL", "mmol/L", 38.67, 40.0, null),
        BloodMarker("ldl", "LDL cholesterol", MarkerCategory.LIPIDS, "mg/dL", "mmol/L", 38.67, null, 130.0),
        BloodMarker("non_hdl", "Non-HDL cholesterol", MarkerCategory.LIPIDS, "mg/dL", "mmol/L", 38.67, null, 130.0),
        BloodMarker("triglycerides", "Triglycerides", MarkerCategory.LIPIDS, "mg/dL", "mmol/L", 88.57, null, 150.0),
        BloodMarker("glucose", "Glucose (fasting)", MarkerCategory.METABOLIC, "mg/dL", "mmol/L", 18.0, 70.0, 99.0),
        BloodMarker("creatinine", "Creatinine", MarkerCategory.KIDNEY, "mg/dL", "µmol/L", 0.011312, 0.67, 1.17),
        BloodMarker("egfr", "eGFR", MarkerCategory.KIDNEY, "mL/min/1.73m²", "mL/min/1.73m²", 1.0, 90.0, null),
        BloodMarker("albumin", "Albumin", MarkerCategory.KIDNEY, "g/dL", "g/L", 0.1, 3.5, 5.2),
        BloodMarker("ast", "AST (GOT)", MarkerCategory.LIVER, "U/L", "U/L", 1.0, null, 40.0),
        BloodMarker("alt", "ALT (GPT)", MarkerCategory.LIVER, "U/L", "U/L", 1.0, null, 50.0),
        BloodMarker("ggt", "Gamma-GT", MarkerCategory.LIVER, "U/L", "U/L", 1.0, null, 60.0),
        BloodMarker("ck", "CK (creatine kinase)", MarkerCategory.OTHER, "U/L", "U/L", 1.0, null, 190.0),
        BloodMarker("psa", "PSA", MarkerCategory.OTHER, "µg/L", "µg/L", 1.0, null, 4.0),
    )

    private val byKey = all.associateBy { it.key }

    fun find(key: String): BloodMarker? = byKey[key]
}

/** Latest result of one known marker across all bloodwork: [result] with its qualifier and lab range, drawn at [at]. */
data class MarkerTrend(val marker: BloodMarker, val at: java.time.Instant, val result: MarkerResult) {
    val value: Double get() = result.value
}

/** Latest result per known marker, in [BloodMarkers] order. */
fun markerTrends(journal: List<JournalEntry>): List<MarkerTrend> {
    val draws = journal.filterIsInstance<JournalEntry.Bloodwork>().sortedByDescending { it.at }
    return BloodMarkers.all.mapNotNull { marker ->
        draws.firstNotNullOfOrNull { d -> d.result(marker.key)?.let { MarkerTrend(marker, d.at, it) } }
    }
}
