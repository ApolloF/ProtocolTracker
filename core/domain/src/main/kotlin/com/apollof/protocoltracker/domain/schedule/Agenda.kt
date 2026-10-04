package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.shownAt
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
    val at: Instant get() = log?.shownAt ?: occurrence!!.at
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
    /** Doses from earlier days taken today (caught up late), shown as done. Skips are never caught up: they stay on their day. */
    val caughtUp: List<AgendaEntry>,
    /** Logs of today (by [shownAt]) that belong to no scheduled dose: unscheduled doses or doses of an edited/removed plan item. */
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
 * Builds today's agenda grouped by part of the day. Today is the logical day ([SlotTimes.dayStart]): at 01:00 with a
 * 4:00 day start, yesterday's doses are still today's. Slot doses are due all day; only earlier days count as missed.
 * [logs] must cover at least [agendaLogsFrom].
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
    val today = slotTimes.dateOf(now, zone)
    val startOfToday = today.atStartOfDay(zone).toInstant()
    val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
    val dayFrom = slotTimes.dayStartOf(today, zone)
    val missedFrom = now.minus(AgendaWindows.missedLookback)
    val windowStart = minOf(startOfToday, missedFrom)
    val logsByKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }

    val missed = ArrayList<AgendaEntry>()
    val todays = ArrayList<AgendaEntry>()
    val caughtUp = ArrayList<AgendaEntry>()
    // Logs of any dose in the window belong to that dose, never to today's extras.
    val matched = HashSet<String>()
    for (occ in occurrences(phases, items, windowStart, startOfTomorrow, zone, anchors, slotTimes)) {
        val log = logsByKey[occ.key]
        if (log != null) matched += log.id
        when {
            occ.localDate == today -> todays += AgendaEntry(occ, log, log?.status?.toAgenda() ?: AgendaStatus.PENDING)
            log != null -> if (log.status == LogStatus.TAKEN && log.takenAt >= dayFrom) caughtUp += AgendaEntry(occ, log, AgendaStatus.TAKEN)
            occ.at >= missedFrom -> missed += AgendaEntry(occ, null, AgendaStatus.MISSED)
        }
    }
    // Today's logs with no matching occurrence: unscheduled, or from an edited/removed plan item.
    val extras = logs.filter { log -> log.id !in matched && slotTimes.isOn(log.shownAt, today, zone) && log.keyedTo(today, zone) }
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
    val taken: Int get() = count(AgendaStatus.TAKEN)
    val skipped: Int get() = count(AgendaStatus.SKIPPED)
    val missed: Int get() = count(AgendaStatus.MISSED)

    /** Unlogged doses of an earlier day before the history starts ([buildDay]'s `countFrom`); not counted as missed. */
    val notLogged: Int get() = if (isToday || isFuture) 0 else count(AgendaStatus.PENDING)

    private fun count(status: AgendaStatus) = groups.sumOf { g -> g.entries.count { it.status == status } }

    /**
     * "2 of 3 done · 1 skipped" today, "2 taken · 1 skipped · 1 missed" on an earlier day ("2 not logged" before the
     * history starts), "3 planned" on a later one.
     */
    val summary: String
        get() = when {
            scheduled == 0 -> "Nothing scheduled"
            isFuture -> "$scheduled planned"
            isToday -> "$done of $scheduled done" + if (skipped > 0) " · $skipped skipped" else ""
            else -> listOfNotNull(
                "$taken taken".takeIf { taken > 0 || (skipped == 0 && missed == 0 && notLogged == 0) },
                "$skipped skipped".takeIf { skipped > 0 },
                "$missed missed".takeIf { missed > 0 },
                "$notLogged not logged".takeIf { notLogged > 0 },
            ).joinToString(" · ")
        }
}

/**
 * The doses scheduled on [date] with their logs. Unlogged doses of earlier days are [AgendaStatus.MISSED], of today
 * and later [AgendaStatus.PENDING]. Before [countFrom] (the history's start, [trackedFrom]) nothing counts as missed:
 * unlogged doses stay [AgendaStatus.PENDING]. [logs] must include every log keyed to [date] (late logs are taken after it).
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
    countFrom: LocalDate? = null,
): DayAgenda {
    val counted = countFrom == null || date >= countFrom
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val logsByKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
    val entries = occurrences(phases, items, start, end, zone, anchors, slotTimes).filter { it.localDate == date }.map { occ ->
        val log = logsByKey[occ.key]
        AgendaEntry(occ, log, log?.status?.toAgenda() ?: if (date < today && counted) AgendaStatus.MISSED else AgendaStatus.PENDING)
    }
    val matched = entries.mapNotNullTo(HashSet()) { it.log?.id }
    val extras = logs.filter { log -> log.id !in matched && slotTimes.isOn(log.shownAt, date, zone) && log.keyedTo(date, zone) }
        .map { AgendaEntry(null, it, it.status.toAgenda()) }
    return DayAgenda(date, groupByTiming(entries, zone), extras.sortedBy { it.at }, date == today, date > today)
}

/**
 * Whether a log may be listed as an extra of [date]: a log of a planned dose belongs to that dose's own date, so a
 * late log (or one written before the day start for a dose after midnight) is never another day's extra.
 */
private fun DoseLog.keyedTo(date: LocalDate, zone: ZoneId): Boolean = when (val ref = occurrenceKey?.let(::parseOccurrenceKey)) {
    null -> true
    is OccurrenceRef.Timed -> ref.at.atZone(zone).toLocalDate() == date
    is OccurrenceRef.Slotted -> ref.date == date
    is OccurrenceRef.AtTime -> ref.date == date
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

/** How a day of the week strip reads at a glance; the strip says it in words too, never by colour alone. */
enum class DayMark { NONE, FUTURE, TODAY, MISSED, SKIPPED, ALL_TAKEN, UNTRACKED }

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
    /** An earlier day before the history starts: unlogged doses are not counted as [missed]. */
    val untracked: Boolean = false,
) {
    /** Missed outranks skipped, skipped outranks all taken. A day before the history with doses unlogged is untracked. */
    val mark: DayMark
        get() = when {
            scheduled == 0 -> DayMark.NONE
            isToday -> DayMark.TODAY
            isFuture -> DayMark.FUTURE
            untracked && taken + skipped < scheduled -> DayMark.UNTRACKED
            missed > 0 -> DayMark.MISSED
            skipped > 0 -> DayMark.SKIPPED
            else -> DayMark.ALL_TAKEN
        }

    /** Text for a narrow cell: "–", "1/3", "2 due", "1 miss", "1 skip" or "all". */
    val cell: String
        get() = when (mark) {
            DayMark.NONE, DayMark.UNTRACKED -> "–"
            DayMark.TODAY -> "${taken + skipped}/$scheduled"
            DayMark.FUTURE -> "$scheduled due"
            DayMark.MISSED -> "$missed miss"
            DayMark.SKIPPED -> "$skipped skip"
            DayMark.ALL_TAKEN -> "all"
        }

    /** Status in words, for screen readers: "2 taken, 1 skipped, 1 missed", "1 of 3 done" or "3 planned". */
    val summary: String
        get() = when (mark) {
            DayMark.NONE -> "nothing planned"
            DayMark.UNTRACKED -> "before your first log"
            DayMark.TODAY -> "${taken + skipped} of $scheduled done" + if (skipped > 0) ", $skipped skipped" else ""
            DayMark.FUTURE -> "$scheduled planned"
            DayMark.ALL_TAKEN -> "all taken"
            DayMark.MISSED, DayMark.SKIPPED -> listOfNotNull(
                "$taken taken".takeIf { taken > 0 },
                "$skipped skipped".takeIf { skipped > 0 },
                "$missed missed".takeIf { missed > 0 },
            ).joinToString(", ")
        }
}

/** Status of each day in the 7 days starting [weekStart]. [logs] must cover that range; see [buildDay] for [countFrom]. */
fun weekSummary(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    weekStart: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
    countFrom: LocalDate? = null,
): List<DayStatus> = weekSummaries(phases, items, logs, listOf(weekStart), today, zone, anchors, slotTimes, countFrom).getValue(weekStart)

/** [weekSummary] of each of [weeks] (their Mondays, ascending), with one pass over the plan. [logs] must cover them. */
fun weekSummaries(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    weeks: List<LocalDate>,
    today: LocalDate,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
    countFrom: LocalDate? = null,
): Map<LocalDate, List<DayStatus>> {
    if (weeks.isEmpty()) return emptyMap()
    val from = weeks.min().atStartOfDay(zone).toInstant()
    val to = weeks.max().plusDays(7).atStartOfDay(zone).toInstant()
    val byKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
    val byDate = occurrences(phases, items, from, to, zone, anchors, slotTimes).groupBy { it.localDate }
    return weeks.associateWith { weekStart -> daysOf(weekStart, byKey, byDate, today, countFrom) }
}

private fun daysOf(
    weekStart: LocalDate,
    byKey: Map<String, DoseLog>,
    byDate: Map<LocalDate, List<Occurrence>>,
    today: LocalDate,
    countFrom: LocalDate?,
): List<DayStatus> =
    (0L until 7L).map { offset ->
        val date = weekStart.plusDays(offset)
        val occs = byDate[date].orEmpty()
        val statuses = occs.map { byKey[it.key]?.status }
        val taken = statuses.count { it == LogStatus.TAKEN }
        val skipped = statuses.count { it == LogStatus.SKIPPED }
        val untracked = date < today && countFrom != null && date < countFrom
        DayStatus(
            date = date,
            scheduled = occs.size,
            taken = taken,
            skipped = skipped,
            missed = if (date < today && !untracked) statuses.count { it == null } else 0,
            isToday = date == today,
            isFuture = date > today,
            untracked = untracked,
        )
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

/**
 * Unconfirmed occurrences reminded in (after, until], earliest first: the reminders a late alarm, a phone that was off
 * or a clock jump left unposted. Occurrences with reminders off are ignored.
 */
fun dueReminders(
    phases: List<Phase>,
    items: List<PlanItem>,
    confirmedKeys: Set<String>,
    after: Instant,
    until: Instant,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
): List<Occurrence> {
    if (!until.isAfter(after)) return emptyList()
    // Any-time doses are reminded later the same day than their nominal time, so look back one day.
    return occurrences(phases, items, after.minus(Duration.ofDays(1)), until.plusSeconds(1), zone, anchors, slotTimes)
        .filter { o -> o.remindAt?.let { it > after && it <= until } == true && o.key !in confirmedKeys }
        .sortedBy { it.remindAt }
}

data class Adherence(val itemId: String, val scheduled: Int, val taken: Int, val skipped: Int) {
    val ratio: Double? get() = if (scheduled == 0) null else taken.toDouble() / scheduled

    /** "86% taken (6/7) · 1 skipped"; skips are named, never folded into the missed ones. */
    val text: String
        get() = ratio?.let { r -> "${Math.round(r * 100)}% taken ($taken/$scheduled)" + if (skipped > 0) " · $skipped skipped" else "" } ?: "–"
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
