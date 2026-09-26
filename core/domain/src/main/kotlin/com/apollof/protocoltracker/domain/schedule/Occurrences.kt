package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.dosePerOccurrence
import com.apollof.protocoltracker.domain.model.followsLastDose
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
 *
 * Interval schedules with `fromLastDose` restart from each taken dose in [anchors]: the grid runs from the plan's
 * anchor until the first taken dose, then from each taken dose's date (every N days) or time (every X hours)
 * until the next one. The occurrence each taken dose confirmed is always kept, so its log still matches.
 */
fun occurrences(
    phases: List<Phase>,
    items: List<PlanItem>,
    from: Instant,
    to: Instant,
    zone: ZoneId,
    anchors: IntervalAnchors,
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
    fun emitInterval(item: PlanItem, at: Instant) {
        val date = at.atZone(zone).toLocalDate()
        if (activeOn(item, date)) emit(Occurrence(occurrenceKey(item.id, at), item, at, date, null, at.takeIf { item.remind }, false))
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
                val n = s.n.toLong()
                // Grid days origin + k·n (k ≥ 0) that fall in range and before endExclusive.
                fun grid(origin: LocalDate, endExclusive: LocalDate?) {
                    val start = if (firstDate <= origin) origin else {
                        val offset = Math.floorMod(ChronoUnit.DAYS.between(origin, firstDate), n)
                        firstDate.plusDays((n - offset) % n)
                    }
                    val end = if (endExclusive == null) lastDate else minOf(lastDate, endExclusive.minusDays(1))
                    forEachDate(start, end, n) { emitDay(item, it, s.timings) }
                }
                val restarts = if (s.followsLastDose) dayRestarts(anchors.forItem(item.id), item.id, s.anchor, zone) else null
                if (restarts == null || restarts.breaks.isEmpty()) {
                    grid(s.anchor, null)
                } else {
                    grid(s.anchor, restarts.breaks.first())
                    restarts.breaks.forEachIndexed { i, day -> grid(day.plusDays(n), restarts.breaks.getOrNull(i + 1)) }
                    for (day in restarts.pinned) if (day in firstDate..lastDate) emitDay(item, day, s.timings)
                }
            }
            is Schedule.EveryHours -> {
                val intervalMs = (s.hours * 3_600_000.0).roundToLong()
                // Doses at origin + k·interval (k ≥ 0) before endExclusive.
                fun grid(origin: Instant, endExclusive: Instant?) {
                    val end = if (endExclusive == null) to else minOf(to, endExclusive)
                    val sinceOrigin = Duration.between(origin, from).toMillis()
                    var k = if (sinceOrigin <= 0) 0L else ceil(sinceOrigin.toDouble() / intervalMs).toLong()
                    while (true) {
                        val at = origin.plusMillis(k * intervalMs)
                        if (at >= end) break
                        emitInterval(item, at)
                        k++
                    }
                }
                val restarts = if (s.followsLastDose) intervalRestarts(anchors.forItem(item.id), item.id, s.anchor) else null
                if (restarts == null || restarts.breaks.isEmpty()) {
                    grid(s.anchor, null)
                } else {
                    grid(s.anchor, restarts.breaks.first())
                    restarts.breaks.forEachIndexed { i, at -> grid(at.plusMillis(intervalMs), restarts.breaks.getOrNull(i + 1)) }
                    for (at in restarts.pinned) emitInterval(item, at)
                }
            }
            Schedule.AsNeeded -> Unit
        }
    }
    result.sortWith(compareBy<Occurrence>({ it.at }, { it.item.sortOrder }, { it.item.id }))
    return result
}

/** Where an interval grid restarts ([breaks], ascending) and the occurrences taken doses confirmed ([pinned]). */
private class Restarts<T>(val breaks: List<T>, val pinned: Collection<T>)

/**
 * Every-N-days restarts: each scheduled day with a taken dose restarts the grid on the day its first dose was
 * taken. Doses keyed before the plan's anchor are ignored, so moving the anchor later starts afresh.
 */
private fun dayRestarts(doses: List<TakenDose>, itemId: String, anchor: LocalDate, zone: ZoneId): Restarts<LocalDate> {
    val firstTakenByDay = HashMap<LocalDate, Instant>()
    for (dose in doses) {
        val day = when (val ref = parseOccurrenceKey(dose.occurrenceKey)?.takeIf { it.itemId == itemId }) {
            is OccurrenceRef.Slotted -> ref.date
            is OccurrenceRef.Timed -> ref.at.atZone(zone).toLocalDate()
            null -> continue
        }
        if (day < anchor) continue
        firstTakenByDay.merge(day, dose.takenAt) { a, b -> minOf(a, b) }
    }
    val breaks = firstTakenByDay.values.map { it.atZone(zone).toLocalDate() }.distinct().sorted()
    return Restarts(breaks, firstTakenByDay.keys)
}

/** Every-X-hours restarts: each taken dose restarts the grid at the minute it was taken. */
private fun intervalRestarts(doses: List<TakenDose>, itemId: String, anchor: Instant): Restarts<Instant> {
    val pinned = HashSet<Instant>()
    val breaks = HashSet<Instant>()
    for (dose in doses) {
        val ref = parseOccurrenceKey(dose.occurrenceKey) as? OccurrenceRef.Timed ?: continue
        if (ref.itemId != itemId || ref.at < anchor) continue
        pinned += ref.at
        breaks += dose.takenAt.truncatedTo(ChronoUnit.MINUTES)
    }
    return Restarts(breaks.sorted(), pinned)
}

private inline fun forEachDate(start: LocalDate, endInclusive: LocalDate, step: Long, block: (LocalDate) -> Unit) {
    var date = start
    while (date <= endInclusive) {
        block(date)
        date = date.plusDays(step)
    }
}
