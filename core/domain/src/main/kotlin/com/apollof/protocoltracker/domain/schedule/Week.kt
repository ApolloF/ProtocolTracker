package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.shownAt
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The weeks the week strip can swipe through (their Mondays, ascending): back to the week of the first log or the
 * plan's start, at most [maxBack] weeks; forward [ahead] weeks, or less when the plan ends sooner. This week is always
 * there, and so is the week of [include] (a day picked from the calendar) when it lies within [maxBack] weeks of today.
 */
fun stripWeeks(
    phases: List<Phase>,
    items: List<PlanItem>,
    firstLog: LocalDate?,
    today: LocalDate,
    include: LocalDate? = null,
    ahead: Int = 4,
    maxBack: Int = 104,
): List<LocalDate> {
    val thisWeek = weekStartOf(today)
    val planStart = (phases.map { it.startDate } + items.mapNotNull { it.startDate }).minOrNull()
    val earliest = listOfNotNull(firstLog, planStart, today).min()
    val first = maxOf(weekStartOf(earliest), thisWeek.minusWeeks(maxBack.toLong()))
    val planEnd = planEnd(phases, items)
    val aheadWeek = thisWeek.plusWeeks(ahead.toLong())
    val last = if (planEnd == null) aheadWeek else minOf(aheadWeek, maxOf(weekStartOf(planEnd), thisWeek))
    val picked = include?.let(::weekStartOf)?.takeIf { it in thisWeek.minusWeeks(maxBack.toLong())..thisWeek.plusWeeks(maxBack.toLong()) }
    val from = picked?.let { minOf(first, it) } ?: first
    val to = picked?.let { maxOf(last, it) } ?: last
    return (0..ChronoUnit.WEEKS.between(from, to)).map { from.plusWeeks(it) }
}

/**
 * The first day whose unlogged doses count as missed: the day of the first dose log, or [today] before any. Earlier
 * days predate the history kept in the app.
 */
fun trackedFrom(logs: List<DoseLog>, today: LocalDate, zone: ZoneId, slotTimes: SlotTimes): LocalDate =
    logs.minOfOrNull { it.shownAt }?.let { minOf(slotTimes.dateOf(it, zone), today) } ?: today

/** The plan's last planned day, or null while any part of it is open-ended. */
private fun planEnd(phases: List<Phase>, items: List<PlanItem>): LocalDate? {
    val active = items.filter { it.enabled }
    if (active.isEmpty()) return null
    val timeline = PhaseTimeline(phases)
    val ends = active.map { item ->
        val phaseEnd = item.phaseId?.let { id -> phases.firstOrNull { it.id == id }?.let(timeline::effectiveEnd) }
        listOfNotNull(item.endDate, phaseEnd).minOrNull()
    }
    return if (ends.any { it == null }) null else ends.filterNotNull().maxOrNull()
}
