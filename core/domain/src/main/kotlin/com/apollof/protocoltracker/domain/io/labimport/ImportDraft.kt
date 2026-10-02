package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.BloodworkRules
import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.LocalDate
import java.time.LocalTime

/** What [BloodworkImport.read] makes of pasted text. */
sealed interface ImportRead {
    /** Not an answer: the Start step shows [problem]'s message. */
    data class Refused(val problem: InputProblem) : ImportRead

    data class Found(val draft: ImportDraft) : ImportRead
}

/** A read answer before the owner's choices (import doc §8): its draws in report order, notices and unread lines. */
data class ImportDraft(val draws: List<DraftDraw>, val notices: Set<BlockNotice>, val unread: List<UnreadLine>)

/**
 * One blood draw. [date] is null when the report's date is uncertain; v1 then leaves the whole draw out with
 * [leftOutReason] (D1-D4). [time] is null when none is printed (saved at [BloodworkRules.UNKNOWN_DRAW_TIME]).
 * [printed] is the date label and date as printed; [caption] marks a received date.
 */
data class DraftDraw(
    val id: Int,
    val date: LocalDate?,
    val time: LocalTime?,
    val printed: String,
    val lab: String,
    val caption: String?,
    val leftOutReason: String?,
    val rows: List<DraftRow>,
)

/**
 * A result line: [id] is unique in the draft, [line] its number in the pasted text, [source] the line as printed.
 * [hasNumber]: the value cell holds a number (S3, S4). [noteLine]: its line for the entry note ("PSA totaal:
 * onleesbaar"), from the chatbot's note or a no-value word.
 */
data class DraftRow(
    val id: Int,
    val line: Int,
    val source: String,
    val printed: PrintedRow,
    val read: RowRead,
    val hasNumber: Boolean,
    val noteLine: String?,
    internal val block: Int,
)

/** Builds the draft from the blocks: each row through [RowReader] with its block's decimal style, then duplicates. */
internal object ImportDrafts {
    fun of(found: BlockRead.Found): ImportDraft {
        val styles = found.draws.flatMap { it.rows }.groupBy { it.block }.mapValues { (_, rows) ->
            DecimalStyle.of(rows.flatMap { listOf(it.printed.value, it.printed.range) })
        }
        var nextId = 0
        val draws = found.draws.mapIndexed { i, draw ->
            val rows = draw.rows.map { row ->
                val p = row.printed
                val value = LabValues.value(p.value, p.unit)
                // A word without a digit ("niet reactief") is a qualitative result: it goes to the note like a
                // no-value word. Text with a digit ("ca. 5") is unclear and stays out.
                val word = when (value) {
                    is ValueRead.NoValue -> value.word
                    // A unit or a one- or two-letter flag ("H") in the value column is not a result.
                    is ValueRead.NotANumber -> value.text.trim().takeIf { t ->
                        t.none(Char::isDigit) && t.count(Char::isLetter) >= 3 && t.none { it == '/' || it == '%' }
                    }
                    else -> null
                }
                val note = listOfNotNull(word, p.note.trim().ifEmpty { null }).distinct().joinToString("; ")
                DraftRow(
                    id = nextId++,
                    line = row.line,
                    source = row.source,
                    printed = p,
                    read = RowReader(styles.getValue(row.block)).read(p),
                    hasNumber = value is ValueRead.Number || value is ValueRead.Negative,
                    noteLine = note.takeIf { it.isNotEmpty() }?.let { "${p.name.trim()}: $it" },
                    block = row.block,
                )
            }
            val date = draw.date
            DraftDraw(
                id = i,
                date = date.date,
                time = date.time,
                printed = date.printed,
                lab = draw.lab,
                caption = if (date.received) ImportMessages.RECEIVED else null,
                leftOutReason = date.question?.let(::reason),
                rows = duplicates(rows, date.date),
            )
        }
        return ImportDraft(draws, found.notices, found.unread)
    }

    private fun reason(question: DateQuestion): String = when (question) {
        DateQuestion.NoDate -> ImportMessages.NO_DATE
        is DateQuestion.NotDrawDate -> ImportMessages.notDrawDate(question.label)
        is DateQuestion.WhichDate -> ImportMessages.whichDate(question.printed)
        is DateQuestion.Implausible -> format(question.date).let {
            if (question.future) ImportMessages.futureDate(it) else ImportMessages.before1990(it)
        }
    }

    private fun format(date: LocalDate): String = DisplayFormat.current.date.format(date)

    /**
     * Import doc §4.2 step 10, within one draw: the same result twice is one row; another value from a later block
     * wins (C7, the chatbot's correction); two values in the latest block leave both out (Q4). Unlisted results follow
     * the same rule per unit (another unit is another test, e.g. % and absolute); those left with the same key, from
     * one block or in other units, are all kept, as `…_2`, `…_3`.
     */
    private fun duplicates(rows: List<DraftRow>, date: LocalDate?): List<DraftRow> {
        val ready = rows.filter { it.read is RowRead.Ready }
        fun DraftRow.ready() = read as RowRead.Ready
        val dropped = mutableSetOf<Int>()
        val changed = mutableMapOf<Int, DraftRow>()

        /** C7 and Q4 over distinct values of one result; [oneBlock] says whether values from one block only are Q4 too. */
        fun latestWins(distinct: List<DraftRow>, name: String, oneBlock: Boolean) {
            val latest = distinct.maxOf { it.block }
            val (winners, earlier) = distinct.partition { it.block == latest }
            if (earlier.isEmpty() && !oneBlock) return
            earlier.forEach { dropped += it.id }
            if (winners.size > 1) {
                val reason = ImportMessages.twoValues(name, date?.let(::format))
                winners.forEach { changed[it.id] = it.copy(read = RowRead.Uncertain(reason, name)) }
            } else {
                val w = winners.single()
                val caption = w.ready().caption ?: ImportMessages.changedLater(earlier.last().ready().value)
                changed[w.id] = w.copy(read = w.ready().copy(caption = caption))
            }
        }

        ready.groupBy { it.ready().result.marker }.forEach { (key, group) ->
            if (group.size < 2) return@forEach
            val distinct = mutableListOf<DraftRow>()
            for (row in group) {
                if (distinct.any { BloodworkRules.sameResult(it.ready().result, row.ready().result) }) {
                    dropped += row.id
                } else {
                    distinct += row
                }
            }
            if (distinct.size < 2) return@forEach
            if (!BloodworkRules.isOther(key)) {
                latestWins(distinct, BloodMarkers.find(key)?.name ?: key, oneBlock = true)
            } else {
                distinct.groupBy { BloodworkRules.normalizeUnit(it.ready().unit) }.values
                    .forEach { same -> latestWins(same, same.last().ready().result.name ?: key, oneBlock = false) }
            }
        }

        val used = ready.mapTo(HashSet()) { it.ready().result.marker }
        ready.map { changed[it.id] ?: it }
            .filter { it.id !in dropped && it.read is RowRead.Ready && BloodworkRules.isOther(it.ready().result.marker) }
            .groupBy { it.ready().result.marker }
            .forEach { (key, group) ->
                group.drop(1).forEach { row ->
                    val n = (2..Int.MAX_VALUE).first { "${key}_$it" !in used }
                    val newKey = "${key}_$n".also { used += it }
                    val r = row.ready()
                    changed[row.id] = row.copy(read = r.copy(result = r.result.copy(marker = newKey)))
                }
            }
        return rows.filter { it.id !in dropped }.map { changed[it.id] ?: it }
    }
}
