package com.apollof.protocoltracker.domain.units

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class ConversionException(message: String) : IllegalArgumentException(message)

/** Converts [amount] to the compound's base unit (mg or IU). Mass and IU are never interconverted. */
fun toBase(amount: Amount, base: BaseUnit, formulation: Formulation): Double = when (amount.unit) {
    DoseUnit.MG -> if (base == BaseUnit.MG) amount.value else incompatible(amount.unit, base)
    DoseUnit.MCG -> if (base == BaseUnit.MG) amount.value / 1000.0 else incompatible(amount.unit, base)
    DoseUnit.IU -> if (base == BaseUnit.IU) amount.value else incompatible(amount.unit, base)
    DoseUnit.ML -> amount.value * (formulation.perMl ?: throw ConversionException("Set a concentration to dose in mL"))
    DoseUnit.TABLET -> amount.value * (formulation.perTablet ?: throw ConversionException("Set a tablet strength to dose in tablets"))
}

fun toBaseOrNull(amount: Amount, base: BaseUnit, formulation: Formulation): Double? =
    try { toBase(amount, base, formulation) } catch (_: ConversionException) { null }

private fun incompatible(unit: DoseUnit, base: BaseUnit): Nothing =
    throw ConversionException("Cannot convert ${unit.label} to ${base.label}")

/** Volume in mL for a base amount, or null without a concentration. */
fun volumeMl(baseAmount: Double, formulation: Formulation): Double? = formulation.perMl?.let { baseAmount / it }

fun tablets(baseAmount: Double, formulation: Formulation): Double? = formulation.perTablet?.let { baseAmount / it }

/** Compact number: up to [maxDecimals] decimals, trailing zeros removed. */
fun formatNumber(value: Double, maxDecimals: Int = 2): String {
    if (abs(value - value.roundToLong()) < 1e-9) return value.roundToLong().toString()
    return String.format(Locale.ROOT, "%.${maxDecimals}f", value).trimEnd('0').trimEnd('.')
}

/** Human dose text, e.g. "125 mg · 0.5 mL" or "1 tab · 25 mg". */
fun describeDose(amount: Amount, base: BaseUnit, formulation: Formulation): String {
    val primary = "${formatNumber(amount.value)} ${amount.unit.label}"
    val baseValue = toBaseOrNull(amount, base, formulation) ?: return primary
    val secondary = when (amount.unit) {
        DoseUnit.ML, DoseUnit.TABLET -> "${formatNumber(baseValue)} ${base.label}"
        else -> volumeMl(baseValue, formulation)?.let { "${formatNumber(it)} mL" }
            ?: tablets(baseValue, formulation)?.let { "${formatNumber(it)} tab" }
    }
    return if (secondary != null) "$primary · $secondary" else primary
}
