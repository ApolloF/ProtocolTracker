package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.dosePerOccurrence
import com.apollof.protocoltracker.domain.model.dosesPerWeek
import com.apollof.protocoltracker.domain.model.timings
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.domain.units.formatVolume
import com.apollof.protocoltracker.domain.units.tablets
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import com.apollof.protocoltracker.domain.units.volumeMl
import java.util.Locale

/** Everything a plan card shows for one item, as display strings. */
data class PlanFigures(
    /** Headline amount, e.g. "500 mg". */
    val total: String,
    /** "per week", "per day" or "per dose". */
    val totalLabel: String,
    val perDoseLabel: String,
    /** Amount of one dose, e.g. "250 mg". */
    val perDose: String,
    val detailLabel: String?,
    /** Volume or tablets of one dose, e.g. "1.0 mL" or "5 × 10 mg". */
    val detail: String?,
    val days: String,
    /** "Any time", "Morning, Pre-workout"; empty for interval schedules. */
    val timing: String,
    /** "250 mg/mL" or "10 mg tabs". */
    val strength: String?,
    /** "2 / week" for weekly-dosed items. */
    val dosesPerWeek: String?,
)

fun planFigures(
    item: PlanItem,
    compound: Compound,
    locale: Locale = Locale.getDefault(),
    format: DisplayFormat = DisplayFormat.current,
): PlanFigures {
    val formulation = Formulation(
        perMl = item.formulation.perMl ?: compound.defaultFormulation.perMl,
        perTablet = item.formulation.perTablet ?: compound.defaultFormulation.perTablet,
    )
    val unitLabel = compound.baseUnit.label
    val one = item.dosePerOccurrence()
    val oneBase = toBaseOrNull(one, compound.baseUnit, formulation)
    val perWeek = item.schedule.dosesPerWeek()

    val perDose = when {
        one.unit == DoseUnit.ML || one.unit == DoseUnit.TABLET -> oneBase?.let { "${formatNumber(it, 1)} $unitLabel" }
            ?: "${formatNumber(one.value, 2)} ${one.unit.label}"
        else -> "${formatNumber(one.value, 2)} ${one.unit.label}"
    }
    val volume = oneBase?.let { volumeMl(it, formulation) }?.takeIf { compound.route == Route.INJECTION }
    val tabs = oneBase?.let { tablets(it, formulation) }?.takeIf { compound.route != Route.INJECTION }
    val (detailLabel, detail) = when {
        volume != null -> "VOLUME" to formatVolume(volume, format)
        tabs != null -> "TABS" to "${formatNumber(tabs, 2)} × ${formatNumber(formulation.perTablet!!)} $unitLabel"
        else -> null to null
    }

    val (total, totalLabel) = when {
        item.doseBasis == DoseBasis.PER_WEEK -> "${formatNumber(item.dose.value, 2)} ${item.dose.unit.label}" to "per week"
        oneBase == null || perWeek == null -> perDose to "per dose"
        item.schedule is Schedule.Daily && one.unit != DoseUnit.ML && one.unit != DoseUnit.TABLET ->
            "${formatNumber(one.value * item.schedule.timings.size, 2)} ${one.unit.label}" to "per day"
        item.schedule is Schedule.Daily -> "${formatNumber(oneBase * item.schedule.timings.size, 2)} $unitLabel" to "per day"
        else -> "${formatNumber(oneBase * perWeek, 1)} $unitLabel" to "per week"
    }

    return PlanFigures(
        total = total,
        totalLabel = totalLabel,
        perDoseLabel = if (compound.route == Route.INJECTION) "PER PIN" else "PER DOSE",
        perDose = perDose,
        detailLabel = detailLabel,
        detail = detail,
        days = describeDays(item.schedule, locale),
        timing = describeTimings(item.schedule.timings),
        strength = formulation.perMl?.takeIf { compound.route == Route.INJECTION }?.let { "${formatNumber(it)} $unitLabel/mL" }
            ?: formulation.perTablet?.let { "${formatNumber(it)} $unitLabel tabs" },
        dosesPerWeek = perWeek?.takeIf { item.doseBasis == DoseBasis.PER_WEEK }?.let { "${formatNumber(it, 1)} / week" },
    )
}
