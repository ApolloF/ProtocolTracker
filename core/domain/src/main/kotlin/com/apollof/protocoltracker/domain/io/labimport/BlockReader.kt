package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers
import java.time.LocalDate
import java.util.Locale

/**
 * Why a text is not read (import doc §4.5 M1-M8, M11, M12; the file messages M9, M10 and M13 are Later); [message] is
 * the line the Start step shows.
 */
sealed class InputProblem(val message: String) {
    data object Empty : InputProblem(ImportMessages.EMPTY)
    data object TooLong : InputProblem(ImportMessages.TOO_LONG)
    data class NewerVersion(val version: Int) : InputProblem(ImportMessages.newerVersion(version))
    data object JoinedLines : InputProblem(ImportMessages.JOINED_LINES)
    data object Prompt : InputProblem(ImportMessages.PROMPT)
    data object ShareLink : InputProblem(ImportMessages.SHARE_LINK)
    data object OtherFormat : InputProblem(ImportMessages.OTHER_FORMAT)
    data object RawReport : InputProblem(ImportMessages.RAW_REPORT)
    data object NoResults : InputProblem(ImportMessages.NO_RESULTS)
    data object NoBlock : InputProblem(ImportMessages.NO_BLOCK)
}

/** Warnings about the answer as a whole (import doc §4.5 N1-N3). */
enum class BlockNotice(val message: String) {
    /** A block without `end`: its last result line is never imported. */
    CUT_OFF(ImportMessages.CUT_OFF),

    /** `...`, `etc.`, `enz.` or "(the remaining results …)" inside a block. */
    LEFT_OUT(ImportMessages.LEFT_OUT),

    /** No header line; the block was recognized by its `date:`, result and `end` lines. */
    NO_HEADER(ImportMessages.NO_HEADER),
}

/** A line of a block that gives no result: not understood, or the last result line of a block without `end`. */
data class UnreadLine(val line: Int, val original: String, val cutOff: Boolean) {
    val message: String
        get() = if (cutOff) {
            ImportMessages.lineCutOff(line, original)
        } else {
            ImportMessages.lineNotUnderstood(line, original)
        }
}

/** A result line kept as printed: [line] is its 1-based number in the text, [source] the line, [block] its block. */
data class BlockRow(val line: Int, val source: String, val printed: PrintedRow, val block: Int)

/** One blood draw: its `date:` line ([DateQuestion.NoDate] for results before any), its lab and rows in report order. */
data class BlockDraw(val date: DrawDate, val lab: String, val rows: List<BlockRow>)

sealed interface BlockRead {
    data class Refused(val problem: InputProblem) : BlockRead

    /** The draws in report order; draws with the same date (and the same or a missing time) are one. */
    data class Found(val draws: List<BlockDraw>, val notices: Set<BlockNotice>, val unread: List<UnreadLine>) : BlockRead
}

/**
 * Finds `protocoltracker-bloodwork-1` blocks in pasted text (import doc §3.1-3.2, §3.7-3.8): through code fences,
 * chatter, whole-conversation copies and odd characters ([LabText.normalize]). It reads `lab:`, `date:` and result lines
 * into raw rows kept as printed, and refuses what is not an answer (the prompt, a share link, JSON, the report itself)
 * with the most specific message. It interprets no value; the row pipeline does.
 */
object BlockReader {
    /** [today] is for the date checks of [DrawDates]. */
    fun read(text: String, today: LocalDate): BlockRead {
        if (text.isBlank()) return BlockRead.Refused(InputProblem.Empty)
        if (text.length > BloodworkImport.MAX_CHARS) return BlockRead.Refused(InputProblem.TooLong)
        val normalized = LabText.normalize(text)
        if (normalized.isBlank()) return BlockRead.Refused(InputProblem.Empty)

        val lines = normalized.split('\n').mapIndexed { i, raw -> Line.of(i + 1, raw) }
        val segments = segments(lines)
        val readable = segments.filter { it.version == BloodworkImport.VERSION && !it.templateOnly && it.hasResults }
        if (readable.isNotEmpty()) return found(readable, today, emptySet())
        if (segments.isEmpty()) headerless(lines)?.let { return found(listOf(it), today, setOf(BlockNotice.NO_HEADER)) }
        return BlockRead.Refused(problem(normalized, lines, segments))
    }

    private enum class Kind { HEADER, TEMPLATE, LAB, DATE, END, BLANK, OMISSION, SILENT, RESULT, UNKNOWN }

    /** A line of the normalized text: [original] trimmed, [text] without decoration ([LabText.stripDecoration]). */
    private class Line(val number: Int, val original: String, val text: String, val kind: Kind) {
        val cells: List<String> get() = LabText.cells(text)

        companion object {
            fun of(number: Int, raw: String): Line {
                val text = LabText.stripDecoration(raw)
                return Line(number, raw.trim(), text, kindOf(text))
            }
        }
    }

    /** Import doc §3.2: header, template lines, `lab:`, `date:`, `end`, skipped lines, omissions, results. */
    private fun kindOf(s: String): Kind {
        val pipe = '|' in s
        return when {
            LabText.headerVersion(s) != null -> Kind.HEADER
            TEMPLATE.containsMatchIn(s) -> Kind.TEMPLATE
            LabText.LAB_LINE.matches(s) -> Kind.LAB
            LabText.DATE_LINE.matches(s) -> Kind.DATE
            LabText.END_LINE.matches(s) -> Kind.END
            s.isEmpty() || FENCE.matches(s) || s.lowercase(Locale.ROOT) in FENCE_LABELS -> Kind.BLANK
            OMISSIONS.any { it.containsMatchIn(s) } -> Kind.OMISSION
            DELIMITER_ROW.containsMatchIn(s) || isFieldNames(s) -> Kind.SILENT
            !pipe && s.none(Char::isDigit) && s.count { it == '\t' } < MIN_TABS -> Kind.SILENT
            isResult(LabText.cells(s)) -> Kind.RESULT
            else -> Kind.UNKNOWN
        }
    }

    /** A key that is a word, and a name; the value may be missing (no value). */
    private fun isResult(cells: List<String>) =
        cells.size >= 2 && LabText.normalizeKey(cells[0]) != null && cells[1].isNotEmpty()

    private fun isFieldNames(s: String): Boolean {
        val cells = LabText.cells(s)
        return cells.size >= 2 && cells.all { it.lowercase(Locale.ROOT) in FIELD_NAMES }
    }

    /** A block: from a header to `end`, the next header or the end of the text. */
    private class Segment(val version: Int, var ended: Boolean = false) {
        val lines = mutableListOf<Line>()
        val hasResults get() = lines.any { it.kind == Kind.RESULT }

        /** The prompt's own layout: placeholders and nothing else. */
        val templateOnly
            get() = lines.any { it.kind == Kind.TEMPLATE } &&
                lines.all { it.kind == Kind.TEMPLATE || it.kind == Kind.BLANK }
    }

    private fun segments(lines: List<Line>): List<Segment> {
        val segments = mutableListOf<Segment>()
        var open: Segment? = null
        for (line in lines) {
            when {
                line.kind == Kind.HEADER -> open = Segment(LabText.headerVersion(line.text)!!).also(segments::add)
                open == null -> Unit
                line.kind == Kind.END -> {
                    open.ended = true
                    open = null
                }
                else -> open.lines += line
            }
        }
        return segments
    }

    /**
     * A block without a header (import doc §3.7), read as version 1 when it has a `date:` line, a result line with a
     * known key and at least 4 cells, and `end`: from its first `lab:`, `date:` or result line up to `end`.
     */
    private fun headerless(lines: List<Line>): Segment? {
        val start = lines.indexOfFirst { it.kind == Kind.LAB || it.kind == Kind.DATE || it.kind == Kind.RESULT }
        if (start < 0) return null
        val end = (start until lines.size).firstOrNull { lines[it].kind == Kind.END } ?: return null
        val body = lines.subList(start, end)
        val knownRow = body.any { line ->
            line.kind == Kind.RESULT && line.cells.let { cells ->
                cells.size >= MIN_HEADERLESS_CELLS && LabText.normalizeKey(cells[0])?.let(BloodMarkers::find) != null
            }
        }
        if (body.none { it.kind == Kind.DATE } || !knownRow) return null
        return Segment(BloodworkImport.VERSION, ended = true).apply { this.lines += body }
    }

    private class DrawBuilder(var date: DrawDate, var lab: String) {
        val rows = mutableListOf<BlockRow>()

        fun sameDraw(other: DrawBuilder): Boolean {
            val a = date
            val b = other.date
            return a.date != null && a.date == b.date && (a.time == null || b.time == null || a.time == b.time)
        }
    }

    private fun found(segments: List<Segment>, today: LocalDate, notices: Set<BlockNotice>): BlockRead.Found {
        val allNotices = notices.toMutableSet()
        val unread = mutableListOf<UnreadLine>()
        val draws = mutableListOf<DrawBuilder>()
        var lab = ""
        segments.forEachIndexed { block, segment ->
            var draw: DrawBuilder? = null
            // The cut-off rule: a block without `end` may stop inside its last result line (`0,4` of `0,49`).
            val cutOff = if (segment.ended) null else segment.lines.lastOrNull { it.kind == Kind.RESULT }
            for (line in segment.lines) {
                when (line.kind) {
                    Kind.LAB -> {
                        lab = labName(line.text)
                        draw?.takeIf { it.rows.isEmpty() }?.lab = lab
                    }
                    Kind.DATE -> {
                        draw = DrawBuilder(DrawDates.readLine(line.text, today) ?: NO_DATE, lab)
                        draws += draw
                    }
                    Kind.OMISSION -> allNotices += BlockNotice.LEFT_OUT
                    Kind.RESULT -> if (line === cutOff) {
                        unread += UnreadLine(line.number, line.original, cutOff = true)
                        allNotices += BlockNotice.CUT_OFF
                    } else {
                        val target = draw ?: DrawBuilder(NO_DATE, lab).also { draws += it }
                        draw = target
                        target.rows += BlockRow(line.number, line.original, LabText.printedRow(line.cells), block)
                    }
                    Kind.UNKNOWN -> unread += UnreadLine(line.number, line.original, cutOff = false)
                    else -> Unit
                }
            }
        }
        return BlockRead.Found(merge(draws), allNotices, unread)
    }

    /** Drops draws without rows and merges draws of the same date: reports split into sections, answers into pages. */
    private fun merge(draws: List<DrawBuilder>): List<BlockDraw> {
        val merged = mutableListOf<DrawBuilder>()
        for (draw in draws.filter { it.rows.isNotEmpty() }) {
            val same = merged.firstOrNull { it.sameDraw(draw) }
            if (same == null) {
                merged += draw
                continue
            }
            same.rows += draw.rows
            if (same.date.time == null) same.date = same.date.copy(time = draw.date.time)
            if (same.lab.isEmpty()) same.lab = draw.lab
        }
        return merged.map { BlockDraw(it.date, it.lab, it.rows.toList()) }
    }

    private fun labName(line: String): String {
        val name = LabText.LAB_LINE.matchEntire(line)?.groupValues?.get(1)?.trim().orEmpty()
        return if (name == "?") "" else name.take(MAX_LAB).trim()
    }

    /** Import doc §3.8 step 6: the first problem that applies. */
    private fun problem(text: String, lines: List<Line>, segments: List<Segment>): InputProblem {
        val newer = segments.maxOfOrNull { it.version }?.takeIf { it > BloodworkImport.VERSION }
        fun pipes(line: Line) = line.text.count { it == '|' }
        return when {
            newer != null -> InputProblem.NewerVersion(newer)
            lines.any { HEADER_TOKEN.containsMatchIn(it.text) && pipes(it) >= JOINED_PIPES } -> InputProblem.JoinedLines
            segments.isNotEmpty() && segments.all { it.templateOnly } ||
                text.contains(BloodworkImport.PROMPT_MARK, ignoreCase = true) -> InputProblem.Prompt
            SHARE_LINK.containsMatchIn(text) || LONE_URL.matches(text.trim()) -> InputProblem.ShareLink
            '{' in text && JSON_FIELD.containsMatchIn(text) ||
                segments.isEmpty() && lines.count { pipes(it) >= 2 } >= MIN_TABLE_LINES ||
                segments.any { it.version == BloodworkImport.VERSION && it.lines.any(::isOtherSeparatorRow) } ->
                InputProblem.OtherFormat
            lines.count { isReportRow(it.original) } >= MIN_REPORT_ROWS -> InputProblem.RawReport
            segments.any { it.version == BloodworkImport.VERSION && !it.templateOnly } -> InputProblem.NoResults
            else -> InputProblem.NoBlock
        }
    }

    /**
     * A result line with `;` or `,` between its cells instead of `|` (`hemoglobin; Hemoglobine; 9,9; mmol/l`): it starts
     * with a listed key or `other` and has at least 3 such marks, so chatter inside a block never counts.
     */
    private fun isOtherSeparatorRow(line: Line): Boolean {
        if (line.kind != Kind.UNKNOWN || line.text.count { it == ';' || it == ',' } < MIN_OTHER_SEPARATORS) return false
        val key = LabText.normalizeKey(line.text.substringBefore(';').substringBefore(',')) ?: return false
        return key == OTHER_KEY || BloodMarkers.find(key) != null
    }

    /**
     * A row of the lab report itself (M11): a name, a number and a unit, with no pipe (`Hemoglobine 9.9 mmol/l 8.5 -
     * 11.0`, `Testosterone, Serum  1050  High  ng/dL`). The shape is enough to pick this message; no alias is needed.
     */
    private fun isReportRow(line: String): Boolean {
        if ('|' in line) return false
        val unit = REPORT_ROW.find(line)?.groupValues?.get(1) ?: return false
        return LabValues.unitLike(unit) != null
    }

    private val NO_DATE = DrawDate(null, null, null, "", DateQuestion.NoDate, received = false)

    private const val MIN_TABS = 3
    private const val MIN_HEADERLESS_CELLS = 4
    private const val JOINED_PIPES = 5
    private const val MIN_TABLE_LINES = 3
    private const val MIN_REPORT_ROWS = 4
    private const val MIN_OTHER_SEPARATORS = 3
    private const val OTHER_KEY = "other"
    private const val MAX_LAB = 80

    /** A placeholder such as `<lab name>`; `< 120` is a value, not a placeholder. */
    private val TEMPLATE = Regex("<[A-Za-z][^<>|]*>")
    private val FENCE = Regex("^(?:`{3,}|~{3,}).*$")
    private val FENCE_LABELS = setOf("text", "plaintext", "txt")
    private val OMISSIONS = listOf(
        Regex("^\\.{3,}"),
        Regex("(?i)^(?:etc|enz)\\.?$"),
        Regex("(?i)^\\(.*(?<!\\p{L})(?:remaining|rest|more|overige|overig|verdere)(?!\\p{L}).*\\)$"),
        Regex("^//"),
    )
    private val DELIMITER_ROW = Regex("^\\|?\\s*:?-{2,}")
    private val FIELD_NAMES = setOf(
        "key", "name", "value", "unit", "range", "note", "naam", "waarde", "eenheid", "bereik", "opmerking",
    )
    private val HEADER_TOKEN = Regex("(?i)protocol\\W*tracker\\W*blood\\W*work")
    private val SHARE_LINK = Regex(
        "(?i)(?:chatgpt\\.com|chat\\.openai\\.com|claude\\.ai|g\\.co/gemini|gemini\\.google\\.com)/share" +
            "|copilot\\.microsoft\\.com|chat\\.mistral\\.ai",
    )
    private val LONE_URL = Regex("(?i)^(?:https?://|www\\.)\\S+$")
    private val JSON_FIELD = Regex("\"(?:results|value|key)\"\\s*:")
    private val REPORT_ROW = Regex("""^\p{L}[^\d|:]{0,60}?\s(?:[↑↓*!]+\s*)?(?:<=|>=|[<>≤≥])?\s*\d[\d.,]*\s*(\S+)""")
}
