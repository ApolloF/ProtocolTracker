package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodworkRules
import com.apollof.protocoltracker.domain.model.MarkerResult
import java.math.BigDecimal
import java.util.Locale

/**
 * A printed number read with a decimal point; [text] keeps the printed digits (`11,0` → `11.0`, `1.209,5` → `1209.5`),
 * except for the decimal reading of an ambiguous number ([NumberRead.Ambiguous]).
 */
data class LabNumber(val value: Double, val text: String)

/** How one printed number reads under the separator rules of import doc §3.3. */
sealed interface NumberRead {
    /** One reading. [decimalMark] is the mark it uses as a decimal mark by rule 4: a witness of the block's style. */
    data class Clear(val number: LabNumber, val decimalMark: Char? = null) : NumberRead

    /**
     * Rule 5: one mark and exactly three digits after a 1-3 digit integer part other than 0 (`1,050`, `1.209`). The
     * decimal reading's text drops trailing zeros (`1,050` → `1.05`), so a caption or question showing it cannot be
     * read as a grouped number.
     */
    data class Ambiguous(val mark: Char, val asDecimal: LabNumber, val asGrouping: LabNumber) : NumberRead

    /** Not a well-formed number (`1,2.5`, `12.03.2025`, `8,5 110`). */
    data object Invalid : NumberRead
}

/** The decimal marks of a block (§3.3 rule 5.1): how many of its values and ranges use `,` or `.` as a decimal mark by rule 4. */
data class DecimalStyle(val commas: Int = 0, val dots: Int = 0) {
    /**
     * The block's reading of [number]: decimal when 2 or more numbers use its mark as a decimal mark and none the other
     * mark; grouping when the reverse holds; null otherwise (mixed or too few witnesses).
     */
    fun reading(number: NumberRead.Ambiguous): LabNumber? {
        val same = if (number.mark == ',') commas else dots
        val other = if (number.mark == ',') dots else commas
        return when {
            same >= MIN_WITNESSES && other == 0 -> number.asDecimal
            other >= MIN_WITNESSES && same == 0 -> number.asGrouping
            else -> null
        }
    }

    companion object {
        private const val MIN_WITNESSES = 2
        val NONE = DecimalStyle()

        /** The style of a block from the texts of its value and range cells. */
        fun of(texts: Iterable<String>): DecimalStyle {
            var commas = 0
            var dots = 0
            for (text in texts) for (m in LabValues.NUMBER_TOKEN.findAll(text)) {
                val read = LabValues.number(m.value) as? NumberRead.Clear ?: continue
                when (read.decimalMark) {
                    ',' -> commas++
                    '.' -> dots++
                }
            }
            return DecimalStyle(commas, dots)
        }
    }
}

/** A value cell (§3.3). */
sealed interface ValueRead {
    /**
     * A number: [qualifier] is [MarkerResult.BELOW], [MarkerResult.ABOVE] or null; [number] is [NumberRead.Clear] or
     * [NumberRead.Ambiguous]; [unit] is the unit cell, or the unit written after the number when that cell is empty.
     */
    data class Number(val qualifier: String?, val number: NumberRead, val unit: String) : ValueRead

    /** No value. [word] is the printed text for the entry note; null for empty, `?`, `-` and `null`. */
    data class NoValue(val word: String?) : ValueRead

    /** Text that is not a number, or a unit after the number that contradicts the unit cell. */
    data class NotANumber(val text: String) : ValueRead

    data object Negative : ValueRead
}

/** Why a printed range is not used (caption C6); the default range applies. */
enum class RangeProblem { SEVERAL, NOT_A_RANGE, LOW_ABOVE_HIGH, NEGATIVE, WOMEN_ONLY, UNCLEAR }

/** A range cell (§3.4). */
sealed interface RangeRead {
    /** Empty, `-` or a no-value word: no lab range, no caption. */
    data object None : RangeRead

    /**
     * Limits in the row's unit (a null side has no limit). [mensRange]: the men's of several ranges (caption C5).
     * [unit]: a unit printed after the range that differs from the row's unit, left for the row checks to judge.
     */
    data class Found(
        val low: Double?,
        val high: Double?,
        val mensRange: Boolean = false,
        val unit: String? = null,
    ) : RangeRead

    data class NotUsed(val problem: RangeProblem) : RangeRead
}

/**
 * The strict value grammar of the bloodwork import (import doc §3.3-3.4): numbers with their separators, value cells
 * with qualifiers, copied flags and units, and range cells. Nothing here guesses: a number the rules cannot settle
 * stays [NumberRead.Ambiguous] for the row checks (question Q3).
 */
object LabValues {
    /** Reads one printed number (digits, `.`, `,` and grouping spaces) by the separator rules 1-5 of §3.3. */
    fun number(token: String): NumberRead {
        val t = token.trim()
        if (!NUMBER_SHAPE.matches(t)) return NumberRead.Invalid
        if (' ' in t) return spaced(t)
        val marks = t.filter { it == '.' || it == ',' }
        return when {
            marks.isEmpty() -> clear(t, null)
            marks.toSet().size == 2 -> bothMarks(t)
            marks.length >= 2 -> grouped(t.split(marks[0]))
            else -> oneMark(t, marks[0])
        }
    }

    /**
     * Settles an ambiguous number (§3.3 rule 5): the block's [style] first, else the one reading that is [possible]
     * for the marker in its unit. Null means question Q3; a number means caption C9 "Read as <number>."
     */
    fun settle(number: NumberRead.Ambiguous, style: DecimalStyle, possible: (Double) -> Boolean = { true }): LabNumber? =
        style.reading(number) ?: listOf(number.asDecimal, number.asGrouping).filter { possible(it.value) }.singleOrNull()

    /** Reads a value cell with its row's [unit] cell (§3.3). */
    fun value(value: String, unit: String): ValueRead {
        val text = value.trim()
        if (isNoValue(text)) return ValueRead.NoValue(text.takeUnless { noValueKey(it) in SILENT_NO_VALUE })
        val m = NUMBER_TOKEN.find(text) ?: return ValueRead.NotANumber(text)
        val prefix = PREFIX_FLAGS.replace(text.substring(0, m.range.first), " ").replace(WHITESPACE, "")
        val sign = PREFIX.matchEntire(prefix) ?: return ValueRead.NotANumber(text)
        val read = number(m.value)
        if (read == NumberRead.Invalid) return ValueRead.NotANumber(text)
        if (sign.groupValues[2].isNotEmpty()) return ValueRead.Negative
        val tail = stripSuffixFlags(text.substring(m.range.last + 1))
        val printedUnit = when {
            tail.isEmpty() -> unit.trim()
            unitLike(tail) == null -> return ValueRead.NotANumber(text)
            unit.isBlank() -> tail
            BloodworkRules.normalizeUnit(tail) == BloodworkRules.normalizeUnit(unit) -> unit.trim()
            else -> return ValueRead.NotANumber(text)
        }
        val qualifier = when (sign.groupValues[1]) {
            "" -> null
            "<", "<=", "≤" -> MarkerResult.BELOW
            else -> MarkerResult.ABOVE
        }
        return ValueRead.Number(qualifier, read, printedUnit)
    }

    /**
     * Reads a range cell in the row's [unit] (§3.4). Ranges labelled by sex give the men's range; ranges by age or any
     * other several ranges, a bare number, low above high, negative limits, a women's range alone, and a limit the
     * block's [style] cannot settle are not used.
     */
    fun range(range: String, unit: String, style: DecimalStyle): RangeRead {
        val text = TARGET_NOTE.replace(range.trim(), "").trim()
        if (isNoValue(text)) return RangeRead.None
        if (AGE_WORD.containsMatchIn(text)) return RangeRead.NotUsed(RangeProblem.SEVERAL)
        val labels = SEX_LABEL.findAll(text).toList()
        if (labels.isEmpty()) return single(text, unit, style, mensRange = false)
        if (text.substring(0, labels.first().range.first).isNotBlank()) return RangeRead.NotUsed(RangeProblem.SEVERAL)
        val segments = labels.mapIndexed { i, m ->
            val end = labels.getOrNull(i + 1)?.range?.first ?: text.length
            sexOf(m.groupValues[1]) to text.substring(m.range.last + 1, end).trim().trimEnd(';', ',', '/', '|').trim()
        }
        val men = segments.filter { it.first == Sex.MEN }
        val women = segments.count { it.first == Sex.WOMEN }
        val both = segments.filter { it.first == Sex.BOTH }
        return when {
            segments.size == 1 && both.size == 1 -> single(both.single().second, unit, style, mensRange = false)
            men.size == 1 && both.isEmpty() -> single(men.single().second, unit, style, mensRange = women > 0)
            men.isEmpty() && both.isEmpty() && women == 1 -> RangeRead.NotUsed(RangeProblem.WOMEN_ONLY)
            else -> RangeRead.NotUsed(RangeProblem.SEVERAL)
        }
    }

    /** A unit written after a number or range, or null when [text] does not look like one (`zie opm`, `(5,4)`). */
    internal fun unitLike(text: String): String? {
        val compact = text.trim()
            .replace(UNIT_OPERATOR_SPACE, "$1")
            .replace(DIGIT_LETTER_SPACE, "")
            .replace(LEADING_TIMES, "$1")
        if (compact.isEmpty() || compact.length > MAX_UNIT || compact.any(Char::isWhitespace)) return null
        return if (UNIT.matches(compact)) text.trim() else null
    }

    private enum class Sex { MEN, WOMEN, BOTH }

    private fun sexOf(label: String): Sex = when (label.lowercase(Locale.ROOT)) {
        in BOTH_LABELS -> Sex.BOTH
        in MEN_LABELS -> Sex.MEN
        else -> Sex.WOMEN
    }

    private fun single(range: String, unit: String, style: DecimalStyle, mensRange: Boolean): RangeRead {
        val text = range.trim().trimEnd('.', ';', ',').trim()
        fun unreadable(): RangeRead.NotUsed {
            val several = NUMBER_TOKEN.findAll(text).count() >= SEVERAL_NUMBERS
            return RangeRead.NotUsed(if (several) RangeProblem.SEVERAL else RangeProblem.NOT_A_RANGE)
        }
        val between = BETWEEN.matchEntire(text)
        val upper = UPPER.matchEntire(text)
        val lower = LOWER.matchEntire(text)
        val (signs, low, high, rest) = when {
            between != null -> between.groupValues.let { g -> Parts(g[1] + g[3], g[2], g[4], g[5]) }
            upper != null -> upper.groupValues.let { g -> Parts(g[1], null, g[2], g[3]) }
            lower != null -> lower.groupValues.let { g -> Parts(g[1], g[2], null, g[3]) }
            else -> return unreadable()
        }
        val restUnit = rest.trim()
        if (restUnit.isNotEmpty() && unitLike(restUnit) == null) return unreadable()
        if (signs.isNotBlank()) return RangeRead.NotUsed(RangeProblem.NEGATIVE)
        val lowValue = low?.let { limit(it, style) ?: return notUsed(it) }
        val highValue = high?.let { limit(it, style) ?: return notUsed(it) }
        if (lowValue != null && highValue != null && lowValue > highValue) {
            return RangeRead.NotUsed(RangeProblem.LOW_ABOVE_HIGH)
        }
        val otherUnit = restUnit.takeIf {
            it.isNotEmpty() && BloodworkRules.normalizeUnit(it) != BloodworkRules.normalizeUnit(unit)
        }
        return RangeRead.Found(lowValue, highValue, mensRange, otherUnit)
    }

    private data class Parts(val signs: String, val low: String?, val high: String?, val rest: String)

    private fun limit(token: String, style: DecimalStyle): Double? = when (val read = number(token)) {
        is NumberRead.Clear -> read.number.value
        is NumberRead.Ambiguous -> style.reading(read)?.value
        NumberRead.Invalid -> null
    }

    private fun notUsed(token: String): RangeRead.NotUsed =
        RangeRead.NotUsed(if (number(token) is NumberRead.Ambiguous) RangeProblem.UNCLEAR else RangeProblem.NOT_A_RANGE)

    /** Rule 1: spaces between digit groups group (`1 209`, `1 209,5`). */
    private fun spaced(t: String): NumberRead {
        val groups = t.split(' ')
        val last = SPACED_LAST.matchEntire(groups.last()) ?: return NumberRead.Invalid
        val leading = groups.dropLast(1)
        if (!validGroups(leading + last.groupValues[1]) || leading.any { g -> g.any { it == '.' || it == ',' } }) {
            return NumberRead.Invalid
        }
        val integer = leading.joinToString("") + last.groupValues[1]
        val fraction = last.groupValues[2]
        return clear(if (fraction.isEmpty()) integer else "$integer.$fraction", null)
    }

    /** Rule 2: both marks; the last one is the decimal mark (`1.209,5`). */
    private fun bothMarks(t: String): NumberRead {
        val decimal = t.last { it == '.' || it == ',' }
        if (t.count { it == decimal } != 1) return NumberRead.Invalid
        val groups = t.substringBefore(decimal).split(if (decimal == ',') '.' else ',')
        if (!validGroups(groups)) return NumberRead.Invalid
        return clear(groups.joinToString("") + "." + t.substringAfter(decimal), null)
    }

    /** Rule 3: the same mark twice or more groups (`1.209.000`). */
    private fun grouped(groups: List<String>): NumberRead =
        if (validGroups(groups)) clear(groups.joinToString(""), null) else NumberRead.Invalid

    /** Rules 4 and 5: one mark. */
    private fun oneMark(t: String, mark: Char): NumberRead {
        val integer = t.substringBefore(mark)
        val fraction = t.substringAfter(mark)
        val decimal = integer.ifEmpty { "0" } + "." + fraction
        val onlyDecimal = fraction.length != GROUP || integer.all { it == '0' } || integer.length > GROUP
        if (onlyDecimal) return clear(decimal, mark)
        val shortDecimal = BigDecimal(decimal).stripTrailingZeros().toPlainString()
        return NumberRead.Ambiguous(
            mark,
            asDecimal = LabNumber(decimal.toDouble(), shortDecimal),
            asGrouping = LabNumber((integer + fraction).toDouble(), integer + fraction),
        )
    }

    private fun validGroups(groups: List<String>): Boolean =
        groups.first().length in 1..GROUP && groups.drop(1).all { it.length == GROUP }

    private fun clear(text: String, mark: Char?) = NumberRead.Clear(LabNumber(text.toDouble(), text), mark)

    private fun noValueKey(text: String): String =
        text.lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim().removeSuffix(".")

    private fun isNoValue(text: String): Boolean = noValueKey(text) in NO_VALUE

    private fun stripSuffixFlags(suffix: String): String {
        var s = suffix.trim()
        while (true) {
            val next = SUFFIX_TRAIL.replaceFirst(SUFFIX_LEAD.replaceFirst(s, "").trim(), "").trim()
            if (next == s) return s
            s = next
        }
    }

    private const val GROUP = 3
    private const val MAX_UNIT = 25
    private const val SEVERAL_NUMBERS = 3

    /** A number: digit groups with `.` or `,`, grouping spaces before groups of exactly three digits, or `.5`. */
    private const val NUM = """(?:\d+(?:[.,]\d+)*(?: \d{3}(?!\d)(?:[.,]\d+)*)*|[.,]\d+)"""

    internal val NUMBER_TOKEN = Regex("""(?<![\d.,])$NUM""")
    private val NUMBER_SHAPE = Regex("^$NUM$")
    private val SPACED_LAST = Regex("""^(\d{3})(?:[.,](\d+))?$""")
    private val WHITESPACE = Regex("\\s+")

    private val NO_VALUE = setOf(
        "", "?", "-", "null", "n/a", "na", "nb", "n.b", "unreadable", "onleesbaar", "volgt", "zie opm",
        "zie opmerking", "negatief", "positief", "niet bepaald", "niet te bepalen", "hemolytisch", "gehemolyseerd",
        "onvoldoende materiaal", "vervallen", "geannuleerd",
    )
    private val SILENT_NO_VALUE = setOf("", "?", "-", "null")

    private const val LETTER_FLAG = """(?:HH|LL|H|L|(?i:hoog|laag))"""
    private val PREFIX_FLAGS = Regex(
        """\((?i:h|l)\)|(?<![A-Za-z])(?:HH|LL|H|L)(?![A-Za-z])|[*!]+|[↑↓]+|(?<!\p{L})(?i:hoog|laag)(?!\p{L})""",
    )
    private val PREFIX = Regex("^(<=|>=|≤|≥|<|>)?(-)?$")
    private val SUFFIX_LEAD = Regex("""^(?:\((?i:h|l)\)|[*!+↑↓-]+|$LETTER_FLAG(?=\s|$))""")
    private val SUFFIX_TRAIL = Regex("""(?:\((?i:h|l)\)|[*!+↑↓]+|(?<=\s)(?:$LETTER_FLAG|-))$""")

    private val UNIT_OPERATOR_SPACE = Regex("""\s*([/^×*])\s*""")
    private val DIGIT_LETTER_SPACE = Regex("""(?<=\d)\s+(?=\p{L})""")
    private val LEADING_TIMES = Regex("""^([x×])\s+""")
    /** A cell count (`x10^9/l`, `10*9/l`, `109/l`) or a unit that starts with a letter, `%` or `/`. */
    private val UNIT = Regex("""^(?:(?:[x×*]10|10[\^*eE])\S*|10\d*/\S+|[\p{L}%/][\p{L}\d%/.,^*×\[\]\{\}()]*)$""")

    private val TARGET_NOTE = Regex("""(?i)\s*\((?:streefwaarde|target|optimaal)\)\s*$""")
    private val AGE_WORD = Regex("""(?i)(?<!\p{L})(?:jaar|jr|j|years?|yrs?|leeftijd|age)(?!\p{L})""")
    private val BOTH_LABELS = setOf("m/v", "v/m", "m/f", "f/m")
    private val MEN_LABELS = setOf("mannen", "man", "men", "male", "males", "heren", "m")
    private val SEX_LABEL = Regex(
        """(?i)(?<![\p{L}\d/])(m/v|v/m|m/f|f/m|mannen|man|men|males?|heren|vrouwen|vrouw|women|females?|dames|m|v|f)""" +
            """(?![\p{L}/])\s*[:.]?\s*(?=[<>≤≥\d.,-]|t/m|tot|max|min|vanaf)""",
    )
    private val BETWEEN = Regex("""(?i)^(-\s*)?($NUM)\s*(?:-|t/m|tot|to|à)\s*(-\s*)?($NUM)(.*)$""")
    private val UPPER = Regex("""(?i)^(?:<=|≤|<|tot|max(?:imaal|imum)?\.?)\s*:?\s*(-\s*)?($NUM)(.*)$""")
    private val LOWER = Regex("""(?i)^(?:>=|≥|>|min(?:imaal|imum)?\.?|vanaf)\s*:?\s*(-\s*)?($NUM)(.*)$""")
}
