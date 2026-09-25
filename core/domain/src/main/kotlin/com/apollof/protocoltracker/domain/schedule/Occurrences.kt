package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

data class Occurrence(
    val key: String,
    val item: PlanItem,
    val at: Instant,
    val localDate: LocalDate,
    /** Wall-clock time fell into a DST gap and was moved forward. */
    val shifted: Boolean,
)

fun occurrenceKey(itemId: String, at: Instant): String = "$itemId@${at.epochSecond}"

/**
 * Resolves which phase is active on a date. Phases are ordered by start date; the latest phase that has
 * started wins. A phase without an end date runs until the next one starts; with an end date it stops after it.
 */
class PhaseTimeline(phases: List<Phase>) {
    private val sorted = phases.sortedWith(compareBy<Phase>({ it.startDate }, { it.id }))
    private val starts = sorted.map { it.startDate }

    fun phaseOn(date: LocalDate): Phase? {
        var lo = 0
        var hi = starts.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= date) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (found < 0) return null
        val phase = sorted[found]
        return if (phase.endDate == null || date <= phase.endDate) phase else null
    }

    /** Effective last day of [phase] (inclusive), or null when open-ended. */
    fun effectiveEnd(phase: Phase): LocalDate? {
        val index = sorted.indexOfFirst { it.id == phase.id }
        if (index < 0) return phase.endDate
        val nextStartMinusOne = sorted.getOrNull(index + 1)?.startDate?.minusDays(1)
        return listOfNotNull(phase.endDate, nextStartMinusOne).minOrNull()
    }

    val phases: List<Phase> get() = sorted
}

class OccurrenceLimitException : IllegalStateException("Too many scheduled doses in range; shorten the range")

/**
 * All scheduled occurrences with instant in [from, to), sorted by time.
 * Cost is proportional to days in range (date-based kinds) or to output size (interval kinds).
 */
fun occurrences(
    phases: List<Phase>,
    items: List<PlanItem>,
    from: Instant,
    to: Instant,
    zone: ZoneId,
    limit: Int = 200_000,
): List<Occurrence> {
    require(!to.isBefore(from)) { "Range end precedes start" }
    val timeline = PhaseTimeline(phases)
    val phaseCache = HashMap<LocalDate, Phase?>()
    fun activeOn(item: PlanItem, date: LocalDate): Boolean {
        if (item.startDate != null && date < item.startDate) return false
        if (item.endDate != null && date > item.endDate) return false
        if (item.phaseId == null) return true
        return phaseCache.getOrPut(date) { timeline.phaseOn(date) }?.id == item.phaseId
    }

    val firstDate = from.atZone(zone).toLocalDate()
    val lastDate = to.atZone(zone).toLocalDate()
    val result = ArrayList<Occurrence>()
    fun emit(item: PlanItem, at: Instant, date: LocalDate, shifted: Boolean) {
        if (at < from || at >= to) return
        result += Occurrence(occurrenceKey(item.id, at), item, at, date, shifted)
        if (result.size > limit) throw OccurrenceLimitException()
    }
    fun emitDay(item: PlanItem, date: LocalDate, times: List<LocalTime>) {
        if (!activeOn(item, date)) return
        for (time in times) {
            // ZonedDateTime.of moves times in a DST gap forward and picks the earlier offset in an overlap.
            val zoned = ZonedDateTime.of(date, time, zone)
            emit(item, zoned.toInstant(), date, zoned.toLocalTime() != time)
        }
    }

    for (item in items) {
        if (!item.enabled) continue
        when (val s = item.schedule) {
            is Schedule.Daily -> forEachDate(firstDate, lastDate, 1) { emitDay(item, it, s.times) }
            is Schedule.Weekdays -> forEachDate(firstDate, lastDate, 1) {
                if (it.dayOfWeek in s.days) emitDay(item, it, s.times)
            }
            is Schedule.EveryNDays -> {
                val start = if (firstDate <= s.anchor) s.anchor else {
                    val offset = Math.floorMod(ChronoUnit.DAYS.between(s.anchor, firstDate), s.n.toLong())
                    firstDate.plusDays((s.n - offset) % s.n)
                }
                forEachDate(start, lastDate, s.n.toLong()) { emitDay(item, it, s.times) }
            }
            is Schedule.EveryHours -> {
                val intervalMs = (s.hours * 3_600_000.0).roundToLong()
                val sinceAnchor = Duration.between(s.anchor, from).toMillis()
                var k = if (sinceAnchor <= 0) 0L else ceil(sinceAnchor.toDouble() / intervalMs).toLong()
                while (true) {
                    val at = s.anchor.plusMillis(k * intervalMs)
                    if (at >= to) break
                    val date = at.atZone(zone).toLocalDate()
                    if (activeOn(item, date)) emit(item, at, date, false)
                    k++
                }
            }
            Schedule.AsNeeded -> Unit
        }
    }
    result.sortWith(compareBy<Occurrence>({ it.at }, { it.item.sortOrder }, { it.item.id }))
    return result
}

private inline fun forEachDate(start: LocalDate, endInclusive: LocalDate, step: Long, block: (LocalDate) -> Unit) {
    var date = start
    while (date <= endInclusive) {
        block(date)
        date = date.plusDays(step)
    }
}
