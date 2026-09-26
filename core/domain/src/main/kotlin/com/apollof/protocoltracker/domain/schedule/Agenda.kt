package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class AgendaStatus { MISSED, PENDING, TAKEN, SKIPPED }

data class AgendaEntry(
    val occurrence: Occurrence?,
    val log: DoseLog?,
    val status: AgendaStatus,
) {
    val at: Instant get() = log?.takenAt ?: occurrence!!.at
    val id: String get() = occurrence?.key ?: log!!.id
    val done: Boolean get() = status == AgendaStatus.TAKEN || status == AgendaStatus.SKIPPED
}

/** Doses of one part of the day (or one exact time), pending and done together in plan order. */
data class TimingGroup(
    val key: String,
    val label: String,
    val slot: DaySlot?,
    val time: LocalTime?,
    val entries: List<AgendaEntry>,
) {
    val pending: List<AgendaEntry> get() = entries.filter { it.status == AgendaStatus.PENDING }
}

data class PhaseProgress(val phase: Phase, val day: Int, val totalDays: Int?) {
    val week: Int get() = (day - 1) / 7 + 1
    val totalWeeks: Int? get() = totalDays?.let { (it + 6) / 7 }
    val fraction: Float? get() = totalDays?.let { (day.toFloat() / it).coerceIn(0f, 1f) }
}

data class Agenda(
    val date: LocalDate,
    /** Unlogged doses from earlier days within [AgendaWindows.missedLookback]. */
    val missed: List<AgendaEntry>,
    val groups: List<TimingGroup>,
    /** Doses from earlier days that were logged today (caught up late), shown as done. */
    val caughtUp: List<AgendaEntry>,
    /** Logs taken today that belong to no scheduled dose: unscheduled doses or doses of an edited/removed plan item. */
    val extras: List<AgendaEntry>,
    val phase: PhaseProgress?,
) {
    /** Everything a "log all" action could confirm. */
    val pending: List<AgendaEntry> get() = missed + groups.flatMap { it.pending }
    val scheduledToday: Int get() = groups.sumOf { it.entries.size }
    val doneToday: Int get() = groups.sumOf { g -> g.entries.count { it.done } }
}

object AgendaWindows {
    val missedLookback: Duration = Duration.ofHours(48)
}

/**
 * Builds today's agenda grouped by part of the day. Slot doses are due all day; only earlier days count
 * as missed. [logs] must cover at least the start of today minus [AgendaWindows.missedLookback].
 */
fun buildAgenda(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    now: Instant,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
): Agenda {
    val today = now.atZone(zone).toLocalDate()
    val startOfToday = today.atStartOfDay(zone).toInstant()
    val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
    val missedFrom = now.minus(AgendaWindows.missedLookback)
    val windowStart = minOf(startOfToday, missedFrom)
    val logsByKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }

    val missed = ArrayList<AgendaEntry>()
    val todays = ArrayList<AgendaEntry>()
    val caughtUp = ArrayList<AgendaEntry>()
    for (occ in occurrences(phases, items, windowStart, startOfTomorrow, zone, anchors, slotTimes)) {
        val log = logsByKey[occ.key]
        when {
            occ.localDate == today -> todays += AgendaEntry(occ, log, log?.status?.toAgenda() ?: AgendaStatus.PENDING)
            log != null -> if (log.takenAt >= startOfToday) caughtUp += AgendaEntry(occ, log, log.status.toAgenda())
            occ.at >= missedFrom -> missed += AgendaEntry(occ, null, AgendaStatus.MISSED)
        }
    }
    val matched = (todays + caughtUp).mapNotNullTo(HashSet()) { it.log?.id }
    // Logs taken today with no matching occurrence: unscheduled, or from an edited/removed plan item.
    val extras = logs.filter { log -> log.takenAt >= startOfToday && log.takenAt < startOfTomorrow && log.id !in matched }
        .map { AgendaEntry(null, it, it.status.toAgenda()) }

    return Agenda(
        date = today,
        missed = missed,
        groups = groupByTiming(todays, zone),
        caughtUp = caughtUp.sortedBy { it.occurrence!!.at },
        extras = extras.sortedBy { it.at },
        phase = phaseProgress(phases, today),
    )
}

/** One chosen day, for checking off or backfilling doses from the week strip or a date picker. */
data class DayAgenda(
    val date: LocalDate,
    val groups: List<TimingGroup>,
    /** Logs taken that day that belong to no scheduled dose of the day. */
    val extras: List<AgendaEntry>,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    val scheduled: Int get() = groups.sumOf { it.entries.size }
    val done: Int get() = groups.sumOf { g -> g.entries.count { it.done } }
}

/**
 * The doses scheduled on [date] with their logs. Unlogged doses of earlier days are [AgendaStatus.MISSED], of today
 * and later [AgendaStatus.PENDING]. [logs] must include every log keyed to [date] (late logs are taken after it).
 */
fun buildDay(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    date: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
): DayAgenda {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val logsByKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
    val entries = occurrences(phases, items, start, end, zone, anchors, slotTimes).filter { it.localDate == date }.map { occ ->
        val log = logsByKey[occ.key]
        AgendaEntry(occ, log, log?.status?.toAgenda() ?: if (date < today) AgendaStatus.MISSED else AgendaStatus.PENDING)
    }
    val matched = entries.mapNotNullTo(HashSet()) { it.log?.id }
    val extras = logs.filter { log ->
        if (log.takenAt < start || log.takenAt >= end || log.id in matched) return@filter false
        // A late log of another day's dose belongs to that day, not this one.
        when (val ref = log.occurrenceKey?.let(::parseOccurrenceKey)) {
            null -> true
            is OccurrenceRef.Timed -> ref.at.atZone(zone).toLocalDate() == date
            is OccurrenceRef.Slotted -> ref.date == date
        }
    }.map { AgendaEntry(null, it, it.status.toAgenda()) }
    return DayAgenda(date, groupByTiming(entries, zone), extras.sortedBy { it.at }, date == today, date > today)
}

/** Groups in day order by clock time; "Any time" last. Exact times form their own groups. */
private fun groupByTiming(entries: List<AgendaEntry>, zone: ZoneId): List<TimingGroup> {
    data class GroupKey(val slot: DaySlot?, val time: LocalTime?)
    val byKey = LinkedHashMap<GroupKey, MutableList<AgendaEntry>>()
    for (e in entries) {
        val occ = e.occurrence!!
        val key = when (val t = occ.timing) {
            is Timing.Slot -> GroupKey(t.slot, null)
            is Timing.At -> GroupKey(null, t.time)
            null -> GroupKey(null, occ.at.atZone(zone).toLocalTime().withSecond(0).withNano(0))
        }
        byKey.getOrPut(key) { ArrayList() } += e
    }
    return byKey.map { (key, list) ->
        val first = list.first().occurrence!!
        val clock = key.time ?: first.at.atZone(zone).toLocalTime()
        Triple(key, list, clock)
    }.sortedWith(compareBy({ it.first.slot == DaySlot.ANY_TIME }, { it.third }, { it.first.slot?.ordinal ?: -1 })).map { (key, list, _) ->
        TimingGroup(
            key = key.slot?.name ?: "at-${key.time}",
            label = key.slot?.label ?: key.time!!.format(DisplayFormat.current.time),
            slot = key.slot,
            time = key.time,
            entries = list.sortedWith(compareBy({ it.occurrence!!.item.sortOrder }, { it.occurrence!!.item.id })),
        )
    }
}


fun phaseProgress(phases: List<Phase>, date: LocalDate): PhaseProgress? {
    val timeline = PhaseTimeline(phases)
    val phase = timeline.phaseOn(date) ?: return null
    val end = timeline.effectiveEnd(phase)
    return PhaseProgress(
        phase = phase,
        day = ChronoUnit.DAYS.between(phase.startDate, date).toInt() + 1,
        totalDays = end?.let { ChronoUnit.DAYS.between(phase.startDate, it).toInt() + 1 },
    )
}

private fun LogStatus.toAgenda() = if (this == LogStatus.TAKEN) AgendaStatus.TAKEN else AgendaStatus.SKIPPED

/** One day of the week strip. */
data class DayStatus(
    val date: LocalDate,
    val scheduled: Int,
    val taken: Int,
    val skipped: Int,
    /** Scheduled, unlogged, and the day is over. */
    val missed: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    /** Short status text; never colour alone. */
    val summary: String
        get() = when {
            scheduled == 0 -> "–"
            isToday -> "${taken + skipped} of $scheduled"
            isFuture -> "$scheduled due"
            missed > 0 -> "$missed missed"
            else -> "all taken"
        }
}

/** Status of each day in the 7 days starting [weekStart]. [logs] must cover that range. */
fun weekSummary(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    weekStart: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
): List<DayStatus> {
    val from = weekStart.atStartOfDay(zone).toInstant()
    val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
    val byKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
    val byDate = occurrences(phases, items, from, to, zone, anchors, slotTimes).groupBy { it.localDate }
    return (0L until 7L).map { offset ->
        val date = weekStart.plusDays(offset)
        val occs = byDate[date].orEmpty()
        val statuses = occs.map { byKey[it.key]?.status }
        val taken = statuses.count { it == LogStatus.TAKEN }
        val skipped = statuses.count { it == LogStatus.SKIPPED }
        DayStatus(
            date = date,
            scheduled = occs.size,
            taken = taken,
            skipped = skipped,
            missed = if (date < today) statuses.count { it == null } else 0,
            isToday = date == today,
            isFuture = date > today,
        )
    }
}

/**
 * Next reminder after [after]: the earliest reminder time of an unconfirmed occurrence, and every occurrence
 * reminded then. Occurrences with reminders off are ignored.
 */
fun nextReminderSlot(
    phases: List<Phase>,
    items: List<PlanItem>,
    confirmedKeys: Set<String>,
    after: Instant,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
    horizon: Duration = Duration.ofDays(8),
): Pair<Instant, List<Occurrence>>? {
    // Any-time doses are reminded later the same day than their nominal time, so look back one day.
    val pending = occurrences(phases, items, after.minus(Duration.ofDays(1)), after.plus(horizon), zone, anchors, slotTimes)
        .filter { it.remindAt != null && it.remindAt > after && it.key !in confirmedKeys }
    val first = pending.minByOrNull { it.remindAt!! } ?: return null
    return first.remindAt!! to pending.filter { it.remindAt == first.remindAt }
}

data class Adherence(val itemId: String, val scheduled: Int, val taken: Int, val skipped: Int) {
    val ratio: Double? get() = if (scheduled == 0) null else taken.toDouble() / scheduled
}

/** Per-item adherence for occurrences in [from, min(to, now)). */
fun adherence(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    from: Instant,
    to: Instant,
    now: Instant,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
): List<Adherence> {
    val end = minOf(to, now)
    if (!end.isAfter(from)) return emptyList()
    val byKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
    return occurrences(phases, items, from, end, zone, anchors, slotTimes)
        .groupBy { it.item.id }
        .map { (itemId, occs) ->
            val statuses = occs.mapNotNull { byKey[it.key]?.status }
            Adherence(itemId, occs.size, statuses.count { it == LogStatus.TAKEN }, statuses.count { it == LogStatus.SKIPPED })
        }
}
