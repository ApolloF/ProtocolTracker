package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Duration
import java.time.ZoneId

/** The longest normal gap between two doses of a schedule, so the previous one is always in reach; null for as-needed. */
fun Schedule.maxGap(): Duration? = when (this) {
    is Schedule.Daily -> Duration.ofDays(2)
    is Schedule.Weekdays -> Duration.ofDays(8)
    // Counting from the last dose can stretch a gap up to twice the interval.
    is Schedule.EveryNDays -> Duration.ofDays(if (fromLastDose) 2L * n + 1 else n + 1L)
    is Schedule.EveryHours -> Duration.ofMinutes((hours * 60 * (if (fromLastDose) 2 else 1)).toLong()).plusDays(1)
    Schedule.AsNeeded -> null
}

/**
 * The dose of the same plan item planned on an earlier day than [occ], or null when there is none within reach. With
 * several doses a day it is the one at the same timing (yesterday's Morning dose for today's Morning dose), so a
 * skipped Evening dose never speaks for the Morning one.
 */
fun previousOccurrence(
    phases: List<Phase>,
    occ: Occurrence,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
): Occurrence? {
    val gap = occ.item.schedule.maxGap() ?: return null
    val earlier = occurrences(phases, listOf(occ.item), occ.at.minus(gap), occ.at, zone, anchors, slotTimes)
        .filter { it.localDate < occ.localDate }
    return earlier.lastOrNull { occ.timing != null && it.timing == occ.timing } ?: earlier.lastOrNull()
}

/** The previous dose of a pending one went untaken: [skipped] or simply not logged. [lastTaken] is the compound's newest taken dose. */
data class LastTime(val previous: Occurrence, val skipped: Boolean, val lastTaken: DoseLog?)

/**
 * Pending doses whose previous dose was not taken, keyed by occurrence key. [previous] maps a pending occurrence's key
 * to its previous dose; [logsByKey] holds the logs of those previous doses; [latestTaken] is each compound's newest
 * taken dose. A dose already listed as missed ([shownAsMissed]) gets no hint, so it is never said twice. An unlogged
 * previous dose counts only when the compound was taken before it and not since: a new plan item, or a dose caught
 * up as an extra, says nothing.
 */
fun lastTimes(
    pending: List<Occurrence>,
    previous: Map<String, Occurrence>,
    logsByKey: Map<String, DoseLog>,
    latestTaken: Map<String, DoseLog>,
    shownAsMissed: Set<String>,
): Map<String, LastTime> = pending.mapNotNull { occ ->
    val prev = previous[occ.key] ?: return@mapNotNull null
    if (prev.key in shownAsMissed) return@mapNotNull null
    val log = logsByKey[prev.key]
    val last = latestTaken[occ.item.compoundId]
    val hint = when (log?.status) {
        LogStatus.TAKEN -> null
        LogStatus.SKIPPED -> LastTime(prev, skipped = true, lastTaken = last)
        null -> if (last != null && last.takenAt < prev.at) LastTime(prev, skipped = false, lastTaken = last) else null
    }
    hint?.let { occ.key to it }
}.toMap()
