package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodworkRules
import java.util.Locale

/** The six cells of a result line as printed (import doc §3.2); missing cells are empty. */
data class PrintedRow(
    val key: String,
    val name: String,
    val value: String,
    val unit: String,
    val range: String,
    val note: String,
)

/**
 * The forgiving text layer of the bloodwork import (import doc §3.2, §4.1): it undoes what copying does to a chatbot
 * answer (line ends, citation debris, entities, odd characters, Markdown decoration) and splits result lines into
 * cells. It never interprets a number; [LabValues] and [DrawDates] do that.
 */
object LabText {
    /**
     * Steps 1-6 of §4.1 on the whole text: line ends become LF and byte order marks go; citation debris and other
     * private-use characters are removed; `&lt; &gt; &le; &ge; &nbsp; &amp;` are decoded (`&#124;` only later, inside
     * a cell); then [BloodworkRules.normalizeChars] (superscript powers before NFKC, dashes, spaces, invisible
     * characters, quotes). `≤`, `≥` and tabs stay.
     */
    fun normalize(text: String): String {
        var s = text.replace("\r\n", "\n").replace('\r', '\n')
            .replace('\u2028', '\n').replace('\u2029', '\n').replace('\u0085', '\n')
            .replace("\uFEFF", "")
        s = CITATION_SPAN.replace(s, "")
        s = BRACKET_CITATION.replace(s, "")
        s = PRIVATE_USE.replace(s, "")
        s = CITE_WORD.replace(s, "")
        for ((entity, char) in ENTITIES) s = s.replace(entity, char)
        return BloodworkRules.normalizeChars(s)
    }

    /**
     * Step 7 of §4.1: list markers, blockquotes, headings, a code view's line number, and bold or backticks around the
     * whole line or the key are stripped, but only when what remains is a header, `lab:`, `date:`, `end` or a result
     * line, so a value never loses a `>`. Returns the trimmed line otherwise.
     */
    fun stripDecoration(line: String): String {
        val trimmed = line.trim()
        if (recognized(trimmed)) return trimmed
        var current = trimmed
        repeat(MAX_LAYERS) {
            val next = stripOneLayer(current) ?: return trimmed
            if (recognized(next)) return next
            current = next
        }
        return trimmed
    }

    /**
     * The format version of a header line (`protocoltracker-bloodwork-1`, any case and separators, an optional `v`
     * and minor version), also as a fence's info string (```` ```protocoltracker-bloodwork-1 ````); null otherwise.
     */
    fun headerVersion(line: String): Int? {
        val body = FENCE_OPEN.replace(line.trim(), "")
        return HEADER.matchEntire(body)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * The cells of a result line (§3.2): split on pipes, `\|` being a literal pipe; a line with no pipe and at least 3
     * tabs is split on tabs (a copied rendered table). A leading empty cell (a leading pipe) and trailing empty cells
     * are dropped; cells are trimmed and `&#124;` becomes a pipe after splitting.
     */
    fun cells(line: String): List<String> {
        var parts = splitPipes(line)
        if (parts.size == 1 && line.count { it == '\t' } >= MIN_TABS) parts = line.split('\t')
        val cells = parts.map { it.trim().replace("&#124;", "|") }.toMutableList()
        if (cells.size > 1 && cells.first().isEmpty()) cells.removeAt(0)
        while (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.lastIndex)
        return cells
    }

    /** [cells] as the six fields of §3.2: missing cells are empty, cells 7 and beyond join the note with " | ". */
    fun printedRow(cells: List<String>): PrintedRow {
        fun cell(i: Int) = cells.getOrNull(i).orEmpty()
        val note = cells.drop(NOTE_CELL).joinToString(" | ")
        return PrintedRow(cell(0), cell(1), cell(2), cell(3), cell(4), if (note == "-") "" else note)
    }

    /**
     * A key cell for lookup (§3.2): lower-cased, runs of spaces and `-` become `_` (`Free Testosterone` →
     * `free_testosterone`). Null when the result is not a word (`^[a-z][a-z0-9_]*$`).
     */
    fun normalizeKey(cell: String): String? {
        val key = cell.trim().lowercase(Locale.ROOT).replace(KEY_SEPARATORS, "_")
        return key.takeIf { KEY.matches(it) }
    }

    /** `lab: <name>`; group 1 is the name. */
    internal val LAB_LINE = Regex("(?i)^lab\\s*:\\s*(.*)$")

    /** `date: <part 1> | <part 2>`; part 2 is optional. */
    internal val DATE_LINE = Regex("(?i)^date\\s*:\\s*([^|]*)(?:\\|(.*))?$")

    internal val END_LINE = Regex("(?i)^end\\.?$")

    private fun recognized(s: String): Boolean =
        headerVersion(s) != null || LAB_LINE.matches(s) || DATE_LINE.matches(s) || END_LINE.matches(s) ||
            RESULT_START.containsMatchIn(s) ||
            ('|' !in s && s.count { it == '\t' } >= MIN_TABS && TAB_RESULT_START.containsMatchIn(s))

    /** One layer of decoration off [s], or null when there is none. */
    private fun stripOneLayer(s: String): String? {
        WHOLE_LINE_WRAPPERS.firstNotNullOfOrNull { it.matchEntire(s) }?.let { return it.groupValues[1].trim() }
        KEY_WRAPPER.find(s)?.let { m ->
            return m.groupValues[1] + m.groupValues[3] + s.substring(m.range.last + 1)
        }
        LABEL_WRAPPER.find(s)?.let { m -> return m.groupValues[2] + ":" + s.substring(m.range.last + 1) }
        for (prefix in PREFIXES) {
            val m = prefix.find(s) ?: continue
            return s.substring(m.range.last + 1).trim()
        }
        return null
    }

    private fun splitPipes(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '\\' && line.getOrNull(i + 1) == '|' -> { current.append('|'); i++ }
                ch == '|' -> { parts += current.toString(); current.clear() }
                else -> current.append(ch)
            }
            i++
        }
        parts += current.toString()
        return parts
    }

    private const val MAX_LAYERS = 6
    private const val MIN_TABS = 3
    private const val NOTE_CELL = 5

    private val CITATION_SPAN = Regex("\uE200[^\uE201\n]*\uE201")
    private val BRACKET_CITATION = Regex("【[^】\n]*】")
    private val PRIVATE_USE = Regex("\\p{Co}")
    private val CITE_WORD = Regex("(?:file)?cite(?:turn\\d+[a-z]+\\d+)+")
    private val ENTITIES = listOf(
        "&lt;" to "<", "&gt;" to ">", "&le;" to "≤", "&ge;" to "≥", "&nbsp;" to " ", "&amp;" to "&",
    )

    private val HEADER = Regex("(?i)^protocol\\W*tracker\\W*blood\\W*work\\W*v?(\\d+)(?:\\.\\d+)?$")
    private val FENCE_OPEN = Regex("^(?:`{3,}|~{3,})\\s*")
    private val KEY_SEPARATORS = Regex("[\\s-]+")
    private val KEY = Regex("^[a-z][a-z0-9_]*$")
    private val RESULT_START = Regex("^\\|?\\s*[A-Za-z][A-Za-z0-9 _-]*\\s*\\|")
    private val TAB_RESULT_START = Regex("^[A-Za-z][A-Za-z0-9 _-]*\\t")

    private val WHOLE_LINE_WRAPPERS = listOf(
        Regex("^\\*\\*(.+)\\*\\*$"),
        Regex("^__(.+)__$"),
        Regex("^`+([^`]+)`+$"),
    )
    private val KEY_WRAPPER = Regex("^(\\|\\s*)?(\\*\\*|__|`)([A-Za-z][A-Za-z0-9 _-]*)\\2(?=\\s*\\|)")
    private val LABEL_WRAPPER = Regex("(?i)^(\\*\\*|__)(lab|date)\\s*(?::\\s*\\1|\\1\\s*:)")
    private val PREFIXES = listOf(
        Regex("^[-*•]\\s+"),
        Regex("^\\d{1,3}[.)]\\s+"),
        Regex("^>\\s?"),
        Regex("^#{1,6}\\s*"),
        Regex("^\\d{1,3}\\s+"),
    )
}
