package com.apollof.protocoltracker.domain.io.labimport

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Why a draw has no date yet (import doc §4.5 D1-D4). Each leaves the date empty until the owner chooses one. */
sealed interface DateQuestion {
    /** D1: `date: ?`, a date that cannot be read, or results before any date line. */
    data object NoDate : DateQuestion

    /** D2: the printed [label] names another date (birth, report, print). */
    data class NotDrawDate(val label: String) : DateQuestion

    /** D3: the date disagrees with the [printed] one, or a slash date reads both ways; [options] in that order. */
    data class WhichDate(val printed: String, val options: List<LocalDate>) : DateQuestion

    /** D4: more than a day after today, or before 1990. */
    data class Implausible(val date: LocalDate, val future: Boolean) : DateQuestion
}

/**
 * A `date:` line (import doc §3.5). [date] is null while [question] is open; [parsedDate] prefills the date dialog.
 * [time] is null when none is printed (or `00:00`); [printed] is the label and date as printed, "" when absent;
 * [received] marks a received date (a draw caption).
 */
data class DrawDate(
    val date: LocalDate?,
    val parsedDate: LocalDate?,
    val time: LocalTime?,
    val printed: String,
    val question: DateQuestion?,
    val received: Boolean,
)

/**
 * Reads draw dates (import doc §3.5): part 1 is the chatbot's `YYYY-MM-DD [HH:MM]` (day-first and month-name dates are
 * read too); part 2 is the label and date as printed, which part 1 must match (day-first unless it has AM or PM) and
 * whose label must not name another date. Anything uncertain becomes a [DateQuestion], never a guess.
 */
object DrawDates {
    val EARLIEST: LocalDate = LocalDate.of(1990, 1, 1)

    /** A whole `date:` line (after [LabText.stripDecoration]), or null when [line] is not one. */
    fun readLine(line: String, today: LocalDate): DrawDate? {
        val m = LabText.DATE_LINE.matchEntire(line.trim()) ?: return null
        return read(m.groupValues[1], m.groupValues[2], today)
    }

    /** [today] sets the century of two-digit years and the D4 limit. */
    fun read(part1: String, part2: String, today: LocalDate): DrawDate {
        val printed = part2.trim()
        val shown = find(printed)
        val label = label(printed, shown)
        val kind = kindOf(label)
        val first = readPart1(part1.trim(), today)
        fun ask(question: DateQuestion, parsed: LocalDate?) =
            DrawDate(null, parsed, first?.time, printed, question, kind == Kind.RECEIVED)

        if (first == null) return ask(DateQuestion.NoDate, null)
        val parsed = first.dates.first()
        if (kind == Kind.NEVER) return ask(DateQuestion.NotDrawDate(label), parsed)
        if (first.dates.size > 1) return ask(DateQuestion.WhichDate(shown?.text ?: first.text, first.dates), parsed)
        val reading = shown?.dates(today, amPm = AM_PM.containsMatchIn(printed), bothWhenUnsure = false)?.singleOrNull()
        if (shown != null && reading != null && reading != parsed) {
            return ask(DateQuestion.WhichDate(shown.text, listOf(parsed, reading)), parsed)
        }
        if (parsed.isAfter(today.plusDays(1))) return ask(DateQuestion.Implausible(parsed, future = true), parsed)
        if (parsed.isBefore(EARLIEST)) return ask(DateQuestion.Implausible(parsed, future = false), parsed)
        return DrawDate(parsed, parsed, first.time, printed, null, kind == Kind.RECEIVED)
    }

    private enum class Kind { DRAW, RECEIVED, NEVER, OTHER }

    private class Part1(val text: String, val dates: List<LocalDate>, val time: LocalTime?)

    /** A date found in text: ISO or month-name dates have one reading; numeric ones follow the separator rules. */
    private class Found(val text: String, val range: IntRange, private val kind: Int, private val groups: List<String>) {
        fun dates(today: LocalDate, amPm: Boolean, bothWhenUnsure: Boolean): List<LocalDate> {
            fun date(y: String, m: Int, d: Int) = dateOrNull(year(y, today), m, d)
            val g = groups
            return when (kind) {
                ISO -> listOfNotNull(dateOrNull(g[1].toInt(), g[3].toInt(), g[4].toInt()))
                NAMED -> listOfNotNull(date(g[3], month(g[2]), g[1].toInt()))
                NAMED_US -> listOfNotNull(date(g[3], month(g[1]), g[2].toInt()))
                else -> {
                    val a = g[1].toInt()
                    val b = g[3].toInt()
                    val dayFirst = date(g[4], b, a)
                    val monthFirst = date(g[4], a, b)
                    when {
                        g[2] != "/" || a > MONTHS_IN_YEAR -> listOfNotNull(dayFirst)
                        b > MONTHS_IN_YEAR || amPm -> listOfNotNull(monthFirst)
                        bothWhenUnsure -> listOfNotNull(dayFirst, monthFirst).distinct()
                        else -> listOfNotNull(dayFirst)
                    }
                }
            }
        }
    }

    private fun find(text: String): Found? =
        DATE_PATTERNS.withIndex()
            .mapNotNull { (kind, regex) -> regex.find(text)?.let { Found(it.value, it.range, kind, it.groupValues) } }
            .minByOrNull { it.range.first }

    private fun readPart1(text: String, today: LocalDate): Part1? {
        if (text.isEmpty() || text == "?") return null
        val s = WEEKDAY.replaceFirst(text, "")
        val found = find(s)?.takeIf { it.range.first == 0 } ?: return null
        val rest = s.substring(found.range.last + 1)
        var time: LocalTime? = null
        var amPm = false
        if (rest.isNotBlank()) {
            val t = TIME_REST.matchEntire(rest) ?: return null
            val suffix = t.groupValues[4].lowercase(Locale.ROOT)
            amPm = suffix.startsWith("a") || suffix.startsWith("p")
            time = clockTime(t.groupValues[1].toInt(), t.groupValues[2].toInt(), t.groupValues[3], suffix) ?: return null
            if (time == LocalTime.MIDNIGHT) time = null
        }
        val dates = found.dates(today, amPm, bothWhenUnsure = true)
        return if (dates.isEmpty()) null else Part1(found.text, dates, time)
    }

    private fun clockTime(hour: Int, minute: Int, second: String, suffix: String): LocalTime? {
        if (minute > MAX_MINUTE || (second.isNotEmpty() && second.toInt() > MAX_MINUTE)) return null
        val am = suffix.startsWith("a")
        val pm = suffix.startsWith("p")
        val h = when {
            am || pm -> if (hour in 1..HALF_DAY) hour % HALF_DAY + (if (pm) HALF_DAY else 0) else return null
            hour <= MAX_HOUR -> hour
            else -> return null
        }
        return LocalTime.of(h, minute)
    }

    /** The printed label: the text before the printed date, else after it, without times and punctuation. */
    private fun label(printed: String, found: Found?): String {
        fun clean(s: String) = TIME_ANY.replace(s, " ").replace(WHITESPACE, " ")
            .trim { it.isWhitespace() || it in LABEL_PUNCTUATION }
        if (found == null) return clean(printed)
        return clean(printed.substring(0, found.range.first)).ifEmpty { clean(printed.substring(found.range.last + 1)) }
    }

    private fun kindOf(label: String): Kind {
        val words = " " + label.lowercase(Locale.ROOT).replace(NON_WORD, " ").trim() + " "
        fun has(phrases: List<String>) = phrases.any { words.contains(" $it ") }
        return when {
            has(NEVER_LABELS) -> Kind.NEVER
            has(DRAW_LABELS) -> Kind.DRAW
            has(RECEIVED_LABELS) -> Kind.RECEIVED
            else -> Kind.OTHER
        }
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try { LocalDate.of(year, month, day) } catch (e: DateTimeException) { null }

    /** A two-digit year up to this year's two digits is this century, a later one the previous century. */
    private fun year(printed: String, today: LocalDate): Int {
        if (printed.length != 2) return printed.toInt()
        val yy = printed.toInt()
        val century = today.year / 100 * 100
        return if (yy <= today.year % 100) century + yy else century - 100 + yy
    }

    private fun month(name: String): Int {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.startsWith("mrt") || n.startsWith("maa") || n.startsWith("mar") -> 3
            n.startsWith("mei") || n.startsWith("may") -> 5
            n.startsWith("okt") || n.startsWith("oct") -> 10
            else -> MONTH_PREFIXES.indexOf(n.take(3)) + 1
        }
    }

    private const val ISO = 0
    private const val NAMED = 2
    private const val NAMED_US = 3
    private const val MONTHS_IN_YEAR = 12
    private const val HALF_DAY = 12
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val LABEL_PUNCTUATION = ":-,()|"

    private val MONTH_PREFIXES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTHS = "(?:januari|january|jan|februari|february|feb|maart|march|mrt|mar|april|apr|mei|may|" +
        "juni|june|jun|juli|july|jul|augustus|august|aug|september|sept|sep|oktober|october|okt|oct|november|nov|" +
        "december|dec)(?!\\p{L})"

    /** In [Found] kind order: ISO, numeric, month name first, month name last. */
    private val DATE_PATTERNS = listOf(
        Regex("(?<!\\d)(\\d{4})([-/.])(\\d{1,2})\\2(\\d{1,2})(?!\\d)"),
        Regex("(?<!\\d)(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{4}|\\d{2})(?!\\d)"),
        Regex("(?i)(?<!\\d)(\\d{1,2})\\.?[\\s-]*($MONTHS)\\.?[\\s,-]*(\\d{4}|\\d{2})(?!\\d)"),
        Regex("(?i)(?<!\\p{L})($MONTHS)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})(?!\\d)"),
    )
    private val WEEKDAY = Regex(
        "(?i)^(?:maandag|dinsdag|woensdag|donderdag|vrijdag|zaterdag|zondag|monday|tuesday|wednesday|thursday|" +
            "friday|saturday|sunday|ma|di|wo|do|vr|za|zo|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun)\\.?,?\\s+",
    )
    private const val TIME_SUFFIX = "(?:uur|u|h|am|pm|a\\.m\\.|p\\.m\\.)"
    private val TIME_REST = Regex(
        "(?i)^\\s*(?:T|,|om|at)?\\s*(\\d{1,2})[:.](\\d{2})(?:[:.](\\d{2}))?\\s*($TIME_SUFFIX)?\\s*$",
    )
    private val TIME_ANY = Regex("(?i)(?<!\\d)\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?(?:\\s*$TIME_SUFFIX(?!\\p{L}))?(?!\\d)")
    private val AM_PM = Regex("(?i)(?<!\\p{L})(?:am|pm|a\\.m\\.|p\\.m\\.)(?!\\p{L})")
    private val WHITESPACE = Regex("\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\d]+")

    private val DRAW_LABELS = listOf(
        "afnamedatum", "datum afname", "datum van afname", "afname", "afgenomen", "monsterafname", "collected",
        "collection date", "date collected", "specimen collected", "drawn",
    )
    private val RECEIVED_LABELS = listOf("ontvangstdatum", "datum van ontvangst", "ontvangen", "received", "binnenkomst")
    private val NEVER_LABELS = listOf(
        "geboortedatum", "dob", "date of birth", "birth date", "uitslagdatum", "rapportdatum", "datum rapport",
        "report date", "printdatum", "print date", "afdrukdatum", "validatiedatum", "geautoriseerd", "aanvraagdatum",
        "reported", "printed",
    )
}
