package com.apollof.protocoltracker.domain.model

import com.apollof.protocoltracker.domain.pk.LabUnits
import java.text.Normalizer
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Wide limits in a marker's stored unit, both inclusive. */
data class PlausibleLimits(val min: Double, val max: Double)

/**
 * Rules shared by the chatbot import, the web history import and the Bloodwork sheet (import doc §4.4, §5.2, §5.5, §7,
 * §9.2-9.3, §10.7), so every path names, checks and matches results the same way.
 */
object BloodworkRules {
    /** Draw time used when a report prints none: noon keeps the calendar day across zone shifts and matches the web app. */
    val UNKNOWN_DRAW_TIME: LocalTime = LocalTime.NOON

    /** Key prefix of unlisted results. `:` never occurs in a [BloodMarkers] key, so the two can never collide. */
    const val OTHER_PREFIX = "other:"

    private const val MAX_SLUG = 48

    fun isOther(key: String): Boolean = key.startsWith(OTHER_PREFIX)

    /**
     * Limits that catch unit, decimal and grouping slips; they never judge health. Each lies beyond the extreme values
     * reported in practice, high doses included. Unlisted results have none beyond "zero or more". The Bloodwork sheet
     * checks what is typed against them ([implausibleTyped]).
     */
    val limits: Map<String, PlausibleLimits> = mapOf(
        "total_testosterone" to PlausibleLimits(0.0, 20_000.0),
        "free_testosterone" to PlausibleLimits(0.0, 10_000.0),
        "estradiol" to PlausibleLimits(0.0, 10_000.0),
        "shbg" to PlausibleLimits(0.0, 500.0),
        "lh" to PlausibleLimits(0.0, 500.0),
        "fsh" to PlausibleLimits(0.0, 500.0),
        "prolactin" to PlausibleLimits(0.0, 5_000.0),
        "tsh" to PlausibleLimits(0.0, 500.0),
        "hemoglobin" to PlausibleLimits(2.0, 26.0),
        "hematocrit" to PlausibleLimits(5.0, 80.0),
        "cholesterol" to PlausibleLimits(10.0, 1_500.0),
        "hdl" to PlausibleLimits(0.0, 300.0),
        "ldl" to PlausibleLimits(0.0, 1_200.0),
        "non_hdl" to PlausibleLimits(0.0, 1_200.0),
        "triglycerides" to PlausibleLimits(0.0, 10_000.0),
        "glucose" to PlausibleLimits(10.0, 2_000.0),
        "creatinine" to PlausibleLimits(0.05, 40.0),
        "egfr" to PlausibleLimits(0.0, 250.0),
        "albumin" to PlausibleLimits(0.5, 10.0),
        "ast" to PlausibleLimits(0.0, 50_000.0),
        "alt" to PlausibleLimits(0.0, 50_000.0),
        "ggt" to PlausibleLimits(0.0, 20_000.0),
        "ck" to PlausibleLimits(0.0, 500_000.0),
        "psa" to PlausibleLimits(0.0, 10_000.0),
    )

    /** Whether [value] (in the stored unit; for a censored value its printed limit) can be a real result of [marker]. */
    fun plausible(marker: String, value: Double): Boolean {
        if (!value.isFinite() || value < 0) return false
        val l = limits[marker] ?: return true
        return value >= l.min && value <= l.max
    }

    /**
     * The markers among [typed] whose result in [results] cannot be real ([plausible]): usually a unit slip, such as a
     * hematocrit of 45 typed while the sheet asks for L/L (stored as 4500 %). Results that were not typed are not checked.
     */
    fun implausibleTyped(results: List<MarkerResult>, typed: Set<String>): Set<String> =
        results.filter { it.marker in typed && !plausible(it.marker, it.value) }.mapTo(HashSet()) { it.marker }

    /**
     * Character normalization of report text (import doc §4.1 steps 4-6): `10` with superscript digits becomes `10^N`
     * before NFKC (which would turn `10⁹` into `109`); NFKC; every dash and minus becomes `-`; every space separator a
     * plain space; zero-width characters and soft hyphens are removed; smart quotes become straight quotes.
     */
    fun normalizeChars(text: String): String {
        val powers = SUPERSCRIPT_POWER.replace(text) { m ->
            "10^" + m.groupValues[1].map { SUPERSCRIPT_DIGITS.indexOf(it) }.joinToString("")
        }
        val nfkc = Normalizer.normalize(powers, Normalizer.Form.NFKC)
        val out = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            when {
                ch in DASHES -> out.append('-')
                ch in INVISIBLE -> Unit
                ch in SINGLE_QUOTES -> out.append('\'')
                ch in DOUBLE_QUOTES -> out.append('"')
                Character.getType(ch) == Character.SPACE_SEPARATOR.toInt() -> out.append(' ')
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * A printed test name for matching and `other:` keys (import doc §5.2). [full] turns parentheses into spaces;
     * [short] drops every parenthetical without a keep-word. Lookups try [full], then [short], and match exactly.
     * The display always keeps the printed name.
     */
    data class NormalizedName(val full: String, val short: String)

    fun normalizeName(printed: String): NormalizedName {
        var s = normalizeChars(printed).lowercase(Locale.ROOT)
            .replace("α", "alpha").replace("β", "beta").replace("γ", "gamma")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        for (pattern in METHOD_PATTERNS) s = s.replace(pattern, " ")
        s = s.replace(FOOTNOTE_MARKS, " ").replace(DIGIT_PARENS, " ")

        val segments = splitParentheses(s).map { (text, inParens) -> inParens to tokens(text) }.toMutableList()
        // A leading lone s, p or b is a specimen prefix (S-Testosteron), unless it is the whole name.
        val first = segments.firstOrNull { it.second.isNotEmpty() }
        if (first != null && !first.first && first.second.first() in SPECIMEN_PREFIXES &&
            segments.sumOf { it.second.size } > 1
        ) {
            segments[segments.indexOf(first)] = false to first.second.drop(1)
        }
        val kept = segments.filter { it.second.isNotEmpty() }
        val full = kept.flatMap { it.second }.joinToString(" ")
        val short = kept.filter { (inParens, words) -> !inParens || words.any(::isKeepWord) }
            .flatMap { it.second }.joinToString(" ")
        return NormalizedName(full, short.ifEmpty { full })
    }

    /**
     * The key of an unlisted result: `other:` + a slug of the [normalizeName] full form (`Vrij T4` → `other:vrij_t4`),
     * with `%` written as `pct` so a percentage and an absolute count of one test differ. At most 48 characters after
     * the prefix; `other:result` when nothing is left. The web history import passes its key with spaces for `_`.
     */
    fun otherKey(printed: String): String {
        val slug = normalizeName(printed).full
            .replace("%", " pct ")
            .replace(NON_SLUG, "_")
            .trim('_')
            .take(MAX_SLUG)
            .trimEnd('_')
        return OTHER_PREFIX + slug.ifEmpty { "result" }
    }

    /**
     * A unit for lookup and comparison (import doc §5.5); the display keeps the printed unit. `µmol/L`, `umol/l` and
     * `mcmol/L` are equal; `E/l` is `U/L` and `mIE/l` is `mIU/L`; cell counts are written `10^9/l` or `10^12/l`; eGFR spellings become
     * `ml/min/1.73m2`. `1/1` for `l/l` or `pmol` for `µmol` is not repaired.
     */
    fun normalizeUnit(printed: String): String {
        var u = normalizeChars(printed).lowercase(Locale.ROOT).replace(WHITESPACE, "")
        if (u.endsWith("/i")) u = u.dropLast(1) + "l"
        u = u.replace('µ', 'u').replace('μ', 'u')
            .replace("micro", "u")
            .replace(MC_PREFIX, "u")
            .replace("^2", "2")
            .replace("1,73", "1.73")
            .replace("liter", "l").replace("litre", "l")
            .replace("[iu]", "iu")
            .replace("{1.73_m2}", "1.73m2")
            .replace(ANNOTATION, "")
        return when {
            CELLS_9.matches(u) -> "10^9/l"
            CELLS_12.matches(u) -> "10^12/l"
            u in EGFR_SPELLINGS -> "ml/min/1.73m2"
            else -> {
                val dutch = DUTCH_UNITS[u] ?: u
                INTERNATIONAL_UNITS[dutch] ?: dutch
            }
        }
    }

    /**
     * Whether [a] and [b] are the same measurement: same marker and qualifier, for unlisted results the same
     * [normalizeUnit] (a missing unit matches only a missing unit), and values within 0.5 % of the larger, at least
     * 0.01, in the stored unit. That covers SI and conventional round trips and the web export's two decimals.
     */
    fun sameResult(a: MarkerResult, b: MarkerResult): Boolean {
        if (a.marker != b.marker || a.qualifier != b.qualifier) return false
        if (BloodMarkers.find(a.marker) == null && unitKey(a) != unitKey(b)) return false
        val tolerance = max(0.005 * max(abs(a.value), abs(b.value)), 0.01)
        return abs(a.value - b.value) <= tolerance
    }

    /**
     * Results after editing a draw by hand. [typed] holds only the fields the user typed in (null = cleared): known
     * markers in [units], unlisted results in their own unit. Untouched results are kept exactly; a typed number keeps
     * the lab range, name and unit and drops the qualifier; a cleared field removes the result. A typed unlisted key
     * without an existing result is ignored (it has no name or unit). Known markers come first in table order, then
     * unlisted results in their existing order. Typed numbers must be zero or more, as for any result.
     */
    fun editResults(existing: List<MarkerResult>, typed: Map<String, Double?>, units: LabUnits): List<MarkerResult> {
        fun edit(key: String, old: MarkerResult?, toStored: (Double) -> Double): MarkerResult? {
            if (key !in typed) return old
            val stored = toStored(typed[key] ?: return null)
            return old?.copy(value = stored, qualifier = null) ?: MarkerResult(key, stored)
        }
        val known = BloodMarkers.all.mapNotNull { m ->
            edit(m.key, existing.firstOrNull { it.marker == m.key }) { m.toStored(it, units) }
        }
        val unlisted = existing.filter { BloodMarkers.find(it.marker) == null }.mapNotNull { r -> edit(r.marker, r) { it } }
        return known + unlisted
    }

    private fun unitKey(r: MarkerResult): String? = r.unit?.takeIf { it.isNotBlank() }?.let(::normalizeUnit)

    /** Splits [s] into top-level text and parenthesized parts; nested or unbalanced parentheses act as separators. */
    private fun splitParentheses(s: String): List<Pair<String, Boolean>> {
        val parts = mutableListOf<Pair<String, Boolean>>()
        val current = StringBuilder()
        var depth = 0
        for (ch in s) {
            when {
                ch == '(' && depth == 0 -> { parts += current.toString() to false; current.clear(); depth = 1 }
                ch == '(' -> { depth++; current.append(' ') }
                ch == ')' && depth == 1 -> { parts += current.toString() to true; current.clear(); depth = 0 }
                ch == ')' && depth > 1 -> { depth--; current.append(' ') }
                ch == ')' -> current.append(' ')
                else -> current.append(ch)
            }
        }
        parts += current.toString() to (depth > 0)
        return parts
    }

    /** Words of one part: `/` between two letters is its own word; other runs outside `[a-z0-9%/]` separate. */
    private fun tokens(text: String): List<String> =
        text.replace(SLASH_BETWEEN_LETTERS, " / ")
            .replace(NON_NAME, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in DROPPED_WORDS }

    private fun isKeepWord(word: String) = word in KEEP_WORDS || '%' in word

    private const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val SUPERSCRIPT_POWER = Regex("(?<![0-9])10([$SUPERSCRIPT_DIGITS]+)")
    private val DASHES = setOf('‐', '‑', '‒', '–', '—', '―', '−', '﹣', '－')
    private val INVISIBLE = setOf('​', '‌', '‍', '⁠', '­')
    private val SINGLE_QUOTES = setOf('‘', '’', '‚', '‛')
    private val DOUBLE_QUOTES = setOf('“', '”', '„', '‟')
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val METHOD_PATTERNS = listOf("lc-ms/ms", "lc/ms/ms", "id-ms", "lc-ms", "ms/ms")
    private val FOOTNOTE_MARKS = Regex("[°*†‡#]")
    private val DIGIT_PARENS = Regex("\\(\\s*\\d+\\s*\\)")
    private val SLASH_BETWEEN_LETTERS = Regex("(?<=[a-z])/(?=[a-z])")
    private val NON_NAME = Regex("[^a-z0-9%/]+")
    private val NON_SLUG = Regex("[^a-z0-9]+")
    private val SPECIMEN_PREFIXES = setOf("s", "p", "b")
    private val DROPPED_WORDS = setOf(
        "serum", "plasma", "bloed", "blood", "volbloed", "veneus", "venous", "berekend", "calculated", "calc",
        "gemeten", "measured", "direct", "indirect", "ifcc", "ecl", "eclia", "cmia", "immunoassay", "ckd", "epi",
        "mdrd", "2009", "2021",
    )
    private val KEEP_WORDS = setOf(
        "vrij", "free", "index", "ratio", "urine", "nuchter", "niet", "non", "fasting", "a1c", "mb", "klaring",
        "clearance", "uur", "random",
    )

    private val WHITESPACE = Regex("\\s+")
    private val MC_PREFIX = Regex("(?<![a-z])mc(?=g|mol|l|iu|u)")
    private val ANNOTATION = Regex("\\{[^\\}]*\\}")
    private val CELLS_9 = Regex("[x×]?10[\\^e*]9/l|[x×]109/l|/nl|10\\^3/ul|k/ul")
    private val CELLS_12 = Regex("[x×]?10[\\^e*]12/l|[x×]1012/l|t/l|/pl|10\\^6/ul|m/ul")
    private val EGFR_SPELLINGS = setOf("ml/min/1.73m2", "ml/min/1.73", "ml/minper1.73m2")
    private val DUTCH_UNITS = mapOf(
        "e/l" to "u/l", "me/l" to "mu/l", "ie/l" to "iu/l",
        "mie/l" to "miu/l", "mie/ml" to "miu/ml", "uie/ml" to "uiu/ml",
    )
    private val INTERNATIONAL_UNITS = mapOf("iu/l" to "u/l", "miu/l" to "mu/l", "miu/ml" to "mu/ml", "uiu/ml" to "uu/ml")
}
