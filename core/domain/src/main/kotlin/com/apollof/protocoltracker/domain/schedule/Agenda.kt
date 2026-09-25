package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class AgendaStatus { OVERDUE, DUE, UPCOMING, TAKEN, SKIPPED }

data class AgendaEntry(
    val occurrence: Occurrence?,
    val log: DoseLog?,
    val status: AgendaStatus,
) {
    val at: Instant get() = log?.takenAt ?: occurrence!!.at
    val id: String get() = occurrence?.key ?: log!!.id
}

data class PhaseProgress(val phase: Phase, val day: Int, val totalDays: Int?) {
    val week: Int get() = (day - 1) / 7 + 1
}

data class Agenda(
    val date: LocalDate,
    val overdue: List<AgendaEntry>,
    val due: List<AgendaEntry>,
    val upcoming: List<AgendaEntry>,
    val done: List<AgendaEntry>,
    val phase: PhaseProgress?,
) {
    /** Everything a "check all" action would confirm. */
    val pending: List<AgendaEntry> get() = overdue + due
}

object AgendaWindows {
    val overdueLookback: Duration = Duration.ofHours(48)
    /** Items scheduled within this window count as due, so they can be checked slightly early. */
    val dueAhead: Duration = Duration.ofMinutes(60)
}

/**
 * Builds today's agenda. [logs] must cover at least [now] − overdueLookback to the end of today.
 * Scheduled occurrences are matched to logs by occurrence key.
 */
fun buildAgenda(
    phases: List<Phase>,
    items: List<PlanItem>,
    logs: List<DoseLog>,
    now: Instant,
    zone: ZoneId,
): Agenda {
    val today = now.atZone(zone).toLocalDate()
    val startOfToday = today.atStartOfDay(zone).toInstant()
    val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
    val windowStart = minOf(startOfToday, now.minus(AgendaWindows.overdueLookback))
    val logsByKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }

    val overdue = ArrayList<AgendaEntry>()
    val due = ArrayList<AgendaEntry>()
    val upcoming = ArrayList<AgendaEntry>()
    val done = LinkedHashMap<String, AgendaEntry>()
    val dueLimit = now.plus(AgendaWindows.dueAhead)

    for (occ in occurrences(phases, items, windowStart, startOfTomorrow, zone)) {
        val log = logsByKey[occ.key]
        when {
            log != null -> if (occ.at >= startOfToday || log.takenAt >= startOfToday) {
                done[log.id] = AgendaEntry(occ, log, log.status.toAgenda())
            }
            occ.at < startOfToday -> if (occ.at >= now.minus(AgendaWindows.overdueLookback)) {
                overdue += AgendaEntry(occ, null, AgendaStatus.OVERDUE)
            }
            occ.at <= dueLimit -> due += AgendaEntry(occ, null, AgendaStatus.DUE)
            else -> upcoming += AgendaEntry(occ, null, AgendaStatus.UPCOMING)
        }
    }
    // Logs taken today with no matching occurrence: as-needed, extra, or from an edited/removed plan item.
    for (log in logs) {
        if (log.id !in done && log.takenAt >= startOfToday && log.takenAt < startOfTomorrow) {
            done[log.id] = AgendaEntry(null, log, log.status.toAgenda())
        }
    }

    return Agenda(
        date = today,
        overdue = overdue,
        due = due,
        upcoming = upcoming,
        done = done.values.sortedBy { it.at },
        phase = phaseProgress(phases, today),
    )
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

/** Next reminder time slot after [after]: the earliest unconfirmed occurrence instant and everything due then. */
fun nextReminderSlot(
    phases: List<Phase>,
    items: List<PlanItem>,
    confirmedKeys: Set<String>,
    after: Instant,
    zone: ZoneId,
    horizon: Duration = Duration.ofDays(8),
): Pair<Instant, List<Occurrence>>? {
    val pending = occurrences(phases, items, after, after.plus(horizon), zone)
        .filter { it.at > after && it.key !in confirmedKeys }
    val first = pending.firstOrNull() ?: return null
    return first.at to pending.filter { it.at == first.at }
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
): List<Adherence> {
    val end = minOf(to, now)
    if (!end.isAfter(from)) return emptyList()
    val byKey = logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
    return occurrences(phases, items, from, end, zone)
        .groupBy { it.item.id }
        .map { (itemId, occs) ->
            val statuses = occs.mapNotNull { byKey[it.key]?.status }
            Adherence(itemId, occs.size, statuses.count { it == LogStatus.TAKEN }, statuses.count { it == LogStatus.SKIPPED })
        }
}
