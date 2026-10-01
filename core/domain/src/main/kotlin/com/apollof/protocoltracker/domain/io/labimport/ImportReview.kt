package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.BloodworkRules
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.units.formatNumber
import java.time.Instant
import java.time.ZoneId

/** The owner's tap on a row: leave a result out, or keep one that is left out (also a same-day result, C8). */
enum class Choice { LEAVE_OUT, KEEP }

/** Where a row ends (import doc §4.2): only [READY] rows of a dated draw are saved. */
enum class RowState {
    READY,

    /** Left out by the owner; a tap keeps it. */
    LEFT_OUT,

    /** Another value for this marker is saved on that day (C8): left out until the owner keeps it. */
    SAME_DAY,

    /** Left out with the question the full design asks as its reason. */
    UNCERTAIN,
    NOT_IMPORTED,
    ALREADY_SAVED,
}

/**
 * A row as the review shows it. [text] is its one muted line: the caption, or why it is not saved (C8, a question, a
 * not-imported reason). [toggles]: a tap leaves it out or keeps it.
 */
data class ReviewRow(val row: DraftRow, val state: RowState, val text: String?, val toggles: Boolean)

/** A draw with its rows; [leftOut] when its date is uncertain ([DraftDraw.leftOutReason]); [note] for the entry. */
data class ReviewDraw(val draw: DraftDraw, val rows: List<ReviewRow>, val note: String) {
    val leftOut: Boolean get() = draw.date == null

    /** The results this draw saves, in report order. */
    val saving: List<MarkerResult>
        get() = if (leftOut) emptyList() else rows.filter { it.state == RowState.READY }.map { it.result }
}

private val ReviewRow.result: MarkerResult get() = (row.read as RowRead.Ready).result

/** The row's name: the app's name for a listed marker, else the printed one. */
val ReviewRow.label: String
    get() = when (val read = row.read) {
        is RowRead.Ready -> BloodMarkers.find(read.result.marker)?.name ?: read.result.name ?: row.printed.name.trim()
        is RowRead.Uncertain -> read.label
        is RowRead.NotImported -> row.printed.name.trim()
    }

/**
 * The draft with the owner's choices and the saved entries applied (import doc §8-9). The screen shows it as is and
 * saves [entries].
 */
data class Review(val draft: ImportDraft, val draws: List<ReviewDraw>) {
    private val rows = draws.flatMap { it.rows }

    val resultsToSave: Int = draws.sumOf { it.saving.size }
    val drawsToSave: Int = draws.count { it.saving.isNotEmpty() }
    val alreadySaved: Int = rows.count { it.state == RowState.ALREADY_SAVED }

    /** Rows never saved plus lines of the answer that give no result. */
    val notImported: Int = rows.count { it.state == RowState.NOT_IMPORTED } + draft.unread.size

    val canSave: Boolean get() = resultsToSave > 0

    /** The one line above the Save button (S2-S5), or null; S2 names rows left out with a reason or by a tap. */
    val line: String? = if (resultsToSave == 0) {
        val numbered = rows.filter { it.row.hasNumber }
        when {
            numbered.isEmpty() -> ImportMessages.NO_NUMBERS
            numbered.all { it.state == RowState.ALREADY_SAVED } -> ImportMessages.ALL_SAVED
            else -> ImportMessages.NOTHING_TO_SAVE
        }
    } else {
        draws.filter { !it.leftOut }.flatMap { it.rows }
            .filter { it.state == RowState.LEFT_OUT || it.row.read is RowRead.Uncertain }
            .map { it.label }
            .takeIf { it.isNotEmpty() }
            ?.let(ImportMessages::leftOut)
    }

    val saveLabel: String get() = ImportMessages.save(resultsToSave, drawsToSave)

    /**
     * One entry per draw that saves results (import doc §9.1): at the printed time, else 12:00 in [zone]; known markers
     * in table order, then unlisted results in report order.
     */
    fun entries(zone: ZoneId, now: Instant, newId: () -> String): List<JournalEntry.Bloodwork> {
        require(canSave) { "Nothing to save" }
        return draws.filter { it.saving.isNotEmpty() }.map { d ->
            val results = d.saving
            val known = BloodMarkers.all.mapNotNull { m -> results.firstOrNull { it.marker == m.key } }
            val date = checkNotNull(d.draw.date)
            JournalEntry.Bloodwork(
                id = newId(),
                at = date.atTime(d.draw.time ?: BloodworkRules.UNKNOWN_DRAW_TIME).atZone(zone).toInstant(),
                results = known + results.filter { BloodMarkers.find(it.marker) == null },
                lab = d.draw.lab.take(MAX_LAB).trim(),
                note = d.note,
                createdAt = now,
            )
        }
    }

    companion object {
        private const val MAX_LAB = 80

        /**
         * Applies [choices] (by row id) and the [existing] bloodwork entries, matched by local date in [zone]: a result
         * already saved that day is skipped; another value for its marker that day starts it left out (C8).
         */
        fun of(
            draft: ImportDraft,
            choices: Map<Int, Choice>,
            existing: List<JournalEntry.Bloodwork>,
            zone: ZoneId,
        ): Review {
            val byDate = existing.groupBy { it.at.atZone(zone).toLocalDate() }
            val draws = draft.draws.map { draw ->
                val saved = draw.date?.let { byDate[it] }.orEmpty().flatMap { it.results }
                val rows = draw.rows.map { row(it, choices[it.id], saved, dated = draw.date != null) }
                ReviewDraw(draw, rows, note(rows))
            }
            return Review(draft, draws)
        }

        private fun row(row: DraftRow, choice: Choice?, saved: List<MarkerResult>, dated: Boolean): ReviewRow =
            when (val read = row.read) {
                is RowRead.Uncertain -> ReviewRow(row, RowState.UNCERTAIN, read.reason, toggles = false)
                is RowRead.NotImported -> ReviewRow(row, RowState.NOT_IMPORTED, read.reason, toggles = false)
                is RowRead.Ready -> {
                    val r = read.result
                    val earlier = saved.firstOrNull { it.marker == r.marker && sameUnit(it, r) }
                    when {
                        !dated -> ReviewRow(row, RowState.READY, read.caption, toggles = false)
                        saved.any { BloodworkRules.sameResult(it, r) } ->
                            ReviewRow(row, RowState.ALREADY_SAVED, null, toggles = false)
                        earlier != null -> ReviewRow(
                            row,
                            if (choice == Choice.KEEP) RowState.READY else RowState.SAME_DAY,
                            ImportMessages.savedEarlier(shown(earlier, read)),
                            toggles = true,
                        )
                        choice == Choice.LEAVE_OUT -> ReviewRow(row, RowState.LEFT_OUT, read.caption, toggles = true)
                        else -> ReviewRow(row, RowState.READY, read.caption, toggles = true)
                    }
                }
            }

        /** Unlisted results match only in the same unit; known markers are stored in one unit. */
        private fun sameUnit(a: MarkerResult, b: MarkerResult): Boolean =
            BloodMarkers.find(a.marker) != null ||
                BloodworkRules.normalizeUnit(a.unit.orEmpty()) == BloodworkRules.normalizeUnit(b.unit.orEmpty())

        /** [earlier] in [row]'s unit: "9.31 mmol/l". */
        private fun shown(earlier: MarkerResult, row: RowRead.Ready): String {
            val v = earlier.value / row.factor
            val decimals = when {
                v >= 100 -> 0
                v >= 10 -> 1
                else -> 2
            }
            val number = earlier.qualifier.orEmpty() + formatNumber(v, decimals)
            return if (row.unit.isEmpty()) number else "$number ${row.unit}"
        }

        /** Import doc §3.6: one line per saved row with a note, and per row without a number. */
        private fun note(rows: List<ReviewRow>): String {
            val text = rows.filter { it.state == RowState.READY || !it.row.hasNumber }
                .mapNotNull { it.row.noteLine }
                .joinToString("\n")
            val max = JournalEntry.MAX_NOTE_LENGTH
            return if (text.length <= max) text else text.take(max - 1) + "…"
        }
    }
}
