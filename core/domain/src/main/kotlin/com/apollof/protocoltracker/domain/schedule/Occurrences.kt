package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.dosePerOccurrence
import com.apollof.protocoltracker.domain.model.validate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Clock times for the parts of the day, from settings. */
data class SlotTimes(
    val times: Map<DaySlot, LocalTime> = emptyMap(),
    /** When an unlogged any-time dose is reminded. */
    val anyTimeReminder: LocalTime = LocalTime.of(19, 0),
) {
    fun timeOf(slot: DaySlot): LocalTime = times[slot] ?: slot.defaultTime

    companion object {
        val DEFAULT = SlotTimes()
    }
}

data class Occurrence(
    val key: String,
    val item: PlanItem,
    /** Nominal time: the exact time, or the slot's clock time. Used for curves, sorting and "scheduled" logging. */
    val at: Instant,
    val localDate: LocalDate,
    /** Null for interval schedules (every X hours). */
    val timing: Timing?,
    /** When to remind; null when the item has reminders off. */
    val remindAt: Instant?,
    /** Wall-clock time fell into a DST gap and was moved forward. */
    val shifted: Boolean,
) {
    /** Amount of this dose (a weekly plan is split over its doses). */
    val dose: Amount get() = item.dosePerOccurrence()

    val slot: DaySlot? get() = (timing as? Timing.Slot)?.slot
}

/** Key of an exact-time occurrence: `itemId@epochSecond`. */
fun occurrenceKey(itemId: String, at: Instant): String = "$itemId@${at.epochSecond}"

/** Key of a day-slot occurrence: `itemId@yyyy-MM-dd/SLOT`. Stays stable when slot clock times change. */
fun slotOccurrenceKey(itemId: String, date: LocalDate, slot: DaySlot): String = "$itemId@$date/${slot.name}"

sealed interface OccurrenceRef {
    val itemId: String
    data class Timed(override val itemId: String, val at: Instant) : OccurrenceRef
    data class Slotted(override val itemId: String, val date: LocalDate, val slot: DaySlot) : OccurrenceRef
}

/** Parses either key format; null for anything else. */
fun parseOccurrenceKey(key: String): OccurrenceRef? {
    val itemId = key.substringBeforeLast('@', "").ifEmpty { return null }
    val rest = key.substringAfterLast('@')
    if ('/' in rest) {
        val date = runCatching { LocalDate.parse(rest.substringBefore('/')) }.getOrNull() ?: return null
        val slot = DaySlot.entries.firstOrNull { it.name == rest.substringAfter('/') } ?: return null
        return OccurrenceRef.Slotted(itemId, date, slot)
    }
    return rest.toLongOrNull()?.let { OccurrenceRef.Timed(itemId, Instant.ofEpochSecond(it)) }
}

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
 * All scheduled occurrences with nominal time in [from, to), sorted by time.
 * Cost is proportional to days in range (date-based kinds) or to output size (interval kinds).
 */
fun occurrences(
    phases: List<Phase>,
    items: List<PlanItem>,
    from: Instant,
    to: Instant,
    zone: ZoneId,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
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
    val keys = HashSet<String>()
    fun emit(occurrence: Occurrence) {
        if (occurrence.at < from || occurrence.at >= to) return
        // Two wall-clock times can collapse onto one instant in a DST gap (02:30 and 03:30 → 03:30).
        if (!keys.add(occurrence.key)) return
        result += occurrence
        if (result.size > limit) throw OccurrenceLimitException()
    }
    fun emitDay(item: PlanItem, date: LocalDate, timings: List<Timing>) {
        if (!activeOn(item, date)) return
        for (timing in timings) {
            val time = when (timing) {
                is Timing.At -> timing.time
                is Timing.Slot -> slotTimes.timeOf(timing.slot)
            }
            // ZonedDateTime.of moves times in a DST gap forward and picks the earlier offset in an overlap.
            val zoned = ZonedDateTime.of(date, time, zone)
            val at = zoned.toInstant()
            val key = when (timing) {
                is Timing.At -> occurrenceKey(item.id, at)
                is Timing.Slot -> slotOccurrenceKey(item.id, date, timing.slot)
            }
            val remindAt = when {
                !item.remind -> null
                timing is Timing.Slot && timing.slot == DaySlot.ANY_TIME ->
                    ZonedDateTime.of(date, slotTimes.anyTimeReminder, zone).toInstant()
                else -> at
            }
            emit(Occurrence(key, item, at, date, timing, remindAt, zoned.toLocalTime() != time))
        }
    }

    for (item in items) {
        // Invalid schedules (e.g. from a hand-edited file) would divide by zero or never advance.
        if (!item.enabled || item.schedule.validate().isNotEmpty()) continue
        when (val s = item.schedule) {
            is Schedule.Daily -> forEachDate(firstDate, lastDate, 1) { emitDay(item, it, s.timings) }
            is Schedule.Weekdays -> forEachDate(firstDate, lastDate, 1) {
                if (it.dayOfWeek in s.days) emitDay(item, it, s.timings)
            }
            is Schedule.EveryNDays -> {
                val start = if (firstDate <= s.anchor) s.anchor else {
                    val offset = Math.floorMod(ChronoUnit.DAYS.between(s.anchor, firstDate), s.n.toLong())
                    firstDate.plusDays((s.n - offset) % s.n)
                }
                forEachDate(start, lastDate, s.n.toLong()) { emitDay(item, it, s.timings) }
            }
            is Schedule.EveryHours -> {
                val intervalMs = (s.hours * 3_600_000.0).roundToLong()
                val sinceAnchor = Duration.between(s.anchor, from).toMillis()
                var k = if (sinceAnchor <= 0) 0L else ceil(sinceAnchor.toDouble() / intervalMs).toLong()
                while (true) {
                    val at = s.anchor.plusMillis(k * intervalMs)
                    if (at >= to) break
                    val date = at.atZone(zone).toLocalDate()
                    if (activeOn(item, date)) {
                        emit(Occurrence(occurrenceKey(item.id, at), item, at, date, null, at.takeIf { item.remind }, false))
                    }
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
