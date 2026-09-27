package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarker
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.BloodworkRules
import com.apollof.protocoltracker.domain.model.MarkerResult
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How one result row reads (import doc §4.2). Nothing uncertain is [Ready]: what the full design asks (Q1-Q4) is
 * [Uncertain], left out with the question as its reason, because v1 asks nothing.
 */
sealed interface RowRead {
    /**
     * Saved unless left out or already saved. [value] is the printed number with its sign (`<0.3`, decimal point);
     * [unit] the printed unit, or the app's spelling when it chose one; [factor] turns [value] into the stored unit.
     */
    data class Ready(
        val result: MarkerResult,
        val value: String,
        val unit: String,
        val factor: Double,
        val caption: String? = null,
    ) : RowRead

    /** Left out: [reason] is the question the full design asks; [label] names the result (S2). */
    data class Uncertain(val reason: String, val label: String) : RowRead

    /** Never saved: no value, not a number, negative, impossible, or no unit fits. */
    data class NotImported(val reason: String) : RowRead
}

/**
 * The row pipeline of one block, whose decimal marks are [style] (import doc §4.2-4.3, §5.3, §7): the value, the marker
 * from the name and the chatbot's key, the unit, the plausibility limits and the meaning checks. A marker the name and
 * the key agree on may be left out with a reason; one only the name or only the key names must have clean numbers, else
 * it is kept as an unlisted result (C12).
 */
internal class RowReader(private val style: DecimalStyle) {
    fun read(p: PrintedRow): RowRead {
        val v = when (val read = LabValues.value(p.value, p.unit)) {
            is ValueRead.NoValue -> return RowRead.NotImported(ImportMessages.noResult(read.word ?: p.note))
            is ValueRead.NotANumber -> return RowRead.NotImported(ImportMessages.notANumber(read.text))
            ValueRead.Negative -> return RowRead.NotImported(ImportMessages.NEGATIVE)
            is ValueRead.Number -> read
        }
        val row = Row(p.name.trim(), p, v)
        val chatbotKey = LabText.normalizeKey(p.key)
        return when (val id = MarkerVocabulary.identify(row.name, chatbotKey)) {
            is Identity.Agreed -> known(row, id.key, byName = true, strict = false)!!
            is Identity.ByName -> known(row, id.key, byName = true, strict = true)
                ?: unlisted(row, ImportMessages.numbersDoNotFit(row.name, markerName(id.key)))
            is Identity.ByKey -> known(row, id.key, byName = false, strict = true)
                ?: unlisted(row, ImportMessages.numbersDoNotFit(row.name, markerName(id.key)))
            is Identity.Unlisted -> unlisted(
                row,
                when {
                    id.ruledOut != null -> ImportMessages.keptAs(row.name, markerName(id.ruledOut))
                    id.confirm != null && id.confirm == chatbotKey -> ImportMessages.fastingNotStated(row.name)
                    else -> null
                },
            )
        }
    }

    private class Row(val name: String, val printed: PrintedRow, val value: ValueRead.Number) {
        val unit = value.unit.trim()
    }

    /** A known marker's row; null when [strict] (name or key alone) and the numbers are not clean. */
    private fun known(row: Row, key: String, byName: Boolean, strict: Boolean): RowRead? {
        val m = BloodMarkers.find(key) ?: return null
        fun unsure(reason: String) = if (strict) null else RowRead.Uncertain(reason, m.name)
        fun skip(reason: String) = if (strict) null else RowRead.NotImported(reason)
        val name = row.name
        val printedUnit = row.unit
        if (printedUnit.isNotEmpty() && MarkerVocabulary.neverUnit(key, printedUnit)) {
            return unlisted(row, ImportMessages.keptAs(name, m.name))
        }
        val accepted = printedUnit.takeIf { it.isNotEmpty() }?.let { MarkerVocabulary.unit(key, it, byName) }
        if (strict && accepted == null) return null

        val (number, readAs) = when (val n = row.value.number) {
            is NumberRead.Clear -> n.number to null
            is NumberRead.Ambiguous -> LabValues.settle(n, style) { x ->
                accepted != null && BloodworkRules.plausible(key, x * accepted.factor)
            }?.let { it to ImportMessages.readAsNumber(it.text) }
                ?: return unsure(thousands(row, n))
            NumberRead.Invalid -> return skip(ImportMessages.notANumber(row.printed.value.trim()))
        }
        val x = number.value
        val shown = row.value.qualifier.orEmpty() + number.text

        val rangeRead = LabValues.range(row.printed.range, printedUnit, style)
        var range = rangeRead as? RangeRead.Found
        var rangeCaption = when (rangeRead) {
            is RangeRead.NotUsed -> ImportMessages.rangeNotUsed(rangeRead.problem)
            is RangeRead.Found -> if (rangeRead.mensRange) ImportMessages.MENS_RANGE else null
            RangeRead.None -> null
        }
        // A range printed with its own unit counts only when that unit reads like the value's.
        val rangeUnit = range?.unit
        if (rangeUnit != null &&
            (accepted == null || MarkerVocabulary.unit(key, rangeUnit, byName)?.factor != accepted.factor)
        ) {
            if (strict) return null
            range = null
            rangeCaption = ImportMessages.rangeDoesNotFit(m.name)
        }

        val all = MarkerVocabulary.candidates(key, byName)
        var unitCaption: String? = null
        val unit = accepted ?: run {
            val fitting = all.filter { BloodworkRules.plausible(key, x * it.factor) && fits(distance(range, it.factor, m)) }
            if (printedUnit.isEmpty() && fitting.size == 1) {
                unitCaption = if (all.size == 1) ImportMessages.NO_UNIT else ImportMessages.onlyUnitFits(fitting[0].display)
                return@run fitting[0]
            }
            return when {
                printedUnit.isEmpty() && fitting.isEmpty() -> RowRead.NotImported(ImportMessages.NO_UNIT)
                printedUnit.isEmpty() -> RowRead.Uncertain(ImportMessages.noUnit(name, shown), m.name)
                fitting.isEmpty() -> RowRead.NotImported(ImportMessages.unitNotAccepted(printedUnit, m.name))
                else -> RowRead.Uncertain(ImportMessages.notAUnit(name, shown, printedUnit, m.name), m.name)
            }
        }
        val shownUnit = if (accepted != null) printedUnit else unit.display
        val f = unit.factor
        val d = distance(range, f, m)
        if (!BloodworkRules.plausible(key, x * f)) {
            return if (repairable(m, all, range, d, x, f)) {
                unsure(ImportMessages.notPossibleAsk(name, shown, shownUnit, m.name))
            } else {
                skip(ImportMessages.notPossible(shown, shownUnit, m.name))
            }
        }
        val printedRange by lazy { printedRange(row.printed.range) }
        betterUnit(m, all, range, d, x, f, shownUnit)?.let { other ->
            return unsure(ImportMessages.rangeFits(name, shown, shownUnit, printedRange, other.display))
        }
        lostDecimal(range, d, x, number)?.let { suggestion ->
            return unsure(ImportMessages.lostDecimal(name, shown, shownUnit, printedRange, suggestion))
        }
        if (range != null && d != null && d > LN_3 * (1 + EPS) && key != FREE_T) {
            if (strict) return null
            range = null
            rangeCaption = ImportMessages.rangeDoesNotFit(m.name)
        }
        val identityCaption = when {
            !strict -> null
            byName -> ImportMessages.readAsMarker(m.name)
            else -> ImportMessages.MATCHED_BY_CHATBOT
        }
        return RowRead.Ready(
            MarkerResult(key, x * f, row.value.qualifier, range?.low?.times(f), range?.high?.times(f)),
            shown, shownUnit, f, listOfNotNull(identityCaption, unitCaption, rangeCaption, readAs).firstOrNull(),
        )
    }

    /** An unlisted result (import doc §7): the number, qualifier, name, unit and range as printed, never converted. */
    private fun unlisted(row: Row, caption: String?): RowRead {
        val (number, readAs) = when (val n = row.value.number) {
            is NumberRead.Clear -> n.number to null
            is NumberRead.Ambiguous -> style.reading(n)?.let { it to ImportMessages.readAsNumber(it.text) }
                ?: return RowRead.Uncertain(thousands(row, n), row.name)
            NumberRead.Invalid -> return RowRead.NotImported(ImportMessages.notANumber(row.printed.value.trim()))
        }
        val rangeRead = LabValues.range(row.printed.range, row.unit, style)
        val range = (rangeRead as? RangeRead.Found)?.takeIf { it.unit == null }
        val rangeCaption = when (rangeRead) {
            is RangeRead.NotUsed -> ImportMessages.rangeNotUsed(rangeRead.problem)
            is RangeRead.Found -> if (range != null && range.mensRange) ImportMessages.MENS_RANGE else null
            RangeRead.None -> null
        }
        val result = MarkerResult(
            BloodworkRules.otherKey(row.name), number.value, row.value.qualifier, range?.low, range?.high,
            name = row.name.take(MAX_NAME).trim(), unit = row.unit.take(MAX_UNIT).trim().ifEmpty { null },
        )
        return RowRead.Ready(
            result, row.value.qualifier.orEmpty() + number.text, row.unit, 1.0,
            listOfNotNull(caption, rangeCaption, readAs).firstOrNull(),
        )
    }

    private fun thousands(row: Row, n: NumberRead.Ambiguous) =
        ImportMessages.thousands(row.name, row.printed.value.trim(), n.asGrouping.text, n.asDecimal.text)

    /**
     * Check C4's repairs (import doc §4.3): another unit that brings the value closer to the typical range and fits the
     * printed range (or, without one, lands near the typical range), or a decimal shift in the printed unit.
     */
    private fun repairable(
        m: BloodMarker,
        units: List<AcceptedUnit>,
        range: RangeRead.Found?,
        d: Double?,
        x: Double,
        f: Double,
    ): Boolean {
        val dvPrinted = valueDistance(x * f, m)
        val otherUnit = units.any { u ->
            u.factor != f && BloodworkRules.plausible(m.key, x * u.factor) &&
                valueDistance(x * u.factor, m) <= dvPrinted + EPS &&
                if (range != null) fits(distance(range, u.factor, m)) else near(x * u.factor, m)
        }
        if (otherUnit) return true
        if (!fits(d)) return false
        val low = range?.low
        val high = range?.high
        return (1..MAX_SHIFT).any { k ->
            val y = x / TEN.pow(k)
            BloodworkRules.plausible(m.key, y * f) &&
                if (low != null && high != null) y in low..high else near(y * f, m)
        }
    }

    /**
     * Check C1 (import doc §4.3): the printed range lies far from the typical range in the printed unit and close to it
     * in another unit that also brings the value no further away. Free testosterone compares only molar with mass
     * units, so a direct assay's low range asks nothing.
     */
    private fun betterUnit(
        m: BloodMarker,
        units: List<AcceptedUnit>,
        range: RangeRead.Found?,
        d: Double?,
        x: Double,
        f: Double,
        printedUnit: String,
    ): AcceptedUnit? {
        if (range == null || d == null || d < LN_1_5 * (1 - EPS)) return null
        val dvPrinted = valueDistance(x * f, m)
        val molar = isMolar(printedUnit)
        return units
            .filter { it.factor != f && (m.key != FREE_T || isMolar(it.display) != molar) }
            .mapNotNull { u -> distance(range, u.factor, m)?.let { u to it } }
            .filter { (u, dB) ->
                dB <= LN_2 * (1 + EPS) && d - dB >= LN_1_4 * (1 - EPS) && valueDistance(x * u.factor, m) <= dvPrinted + EPS
            }
            .minByOrNull { it.second }?.first
    }

    /**
     * Check C2 (import doc §4.3): a value over 5× the high limit of a two-sided range that fits its unit, whose ÷10,
     * ÷100 or ÷1000 lies inside the range. Returns the suggested number with the printed digits.
     */
    private fun lostDecimal(range: RangeRead.Found?, d: Double?, x: Double, number: LabNumber): String? {
        val low = range?.low ?: return null
        val high = range.high ?: return null
        if (low <= 0 || !fits(d) || x <= LOST_DECIMAL * high) return null
        val k = (1..MAX_SHIFT).firstOrNull { x / TEN.pow(it) in low..high } ?: return null
        return BigDecimal(number.text).movePointLeft(k).stripTrailingZeros().toPlainString()
    }

    private companion object {
        const val FREE_T = "free_testosterone"
        const val MAX_NAME = 60
        const val MAX_UNIT = 20
        const val MAX_SHIFT = 3
        const val TEN = 10.0
        const val LOST_DECIMAL = 5
        const val EPS = 1e-9
        val LN_1_3 = ln(1.3)
        val LN_1_4 = ln(1.4)
        val LN_1_5 = ln(1.5)
        val LN_2 = ln(2.0)
        val LN_3 = ln(3.0)

        fun markerName(key: String) = BloodMarkers.find(key)?.name ?: key

        fun isMolar(unit: String) = "mol" in BloodworkRules.normalizeUnit(unit)

        fun fits(d: Double?) = d == null || d <= LN_2 * (1 + EPS)

        fun near(stored: Double, m: BloodMarker) = valueDistance(stored, m) <= LN_1_3 + EPS

        /**
         * d(u): how far [range], read in a unit with [factor], lies from [m]'s typical range, on a log scale. Both
         * two-sided: their middles (geometric, or arithmetic when the low limit is 0); else a shared side; else null.
         */
        fun distance(range: RangeRead.Found?, factor: Double, m: BloodMarker): Double? {
            range ?: return null
            val low = range.low?.times(factor)
            val high = range.high?.times(factor)
            val dl = m.refLow
            val dh = m.refHigh
            return when {
                low != null && high != null && dl != null && dh != null -> logRatio(middle(low, high), middle(dl, dh))
                high != null && dh != null -> logRatio(high, dh)
                low != null && dl != null -> logRatio(low, dl)
                else -> null
            }
        }

        /** dv(u): 0 inside [m]'s typical range, else how far [stored] lies from the nearest limit, on a log scale. */
        fun valueDistance(stored: Double, m: BloodMarker): Double {
            val low = m.refLow
            val high = m.refHigh
            return when {
                low != null && stored < low -> ln(low / stored)
                high != null && stored > high -> ln(stored / high)
                else -> 0.0
            }
        }

        private fun middle(low: Double, high: Double) = if (low == 0.0) high / 2 else sqrt(low * high)

        private fun logRatio(a: Double, b: Double) = if (a > 0 && b > 0) abs(ln(a / b)) else null

        /** The range cell with each clear number written with a decimal point (`8,5 - 11,0` → `8.5 - 11.0`). */
        fun printedRange(cell: String): String = LabValues.NUMBER_TOKEN.replace(cell.trim()) { m ->
            (LabValues.number(m.value) as? NumberRead.Clear)?.number?.text ?: m.value
        }
    }
}
