package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The first planned dose at or after [from] within [within], or null (as-needed items never count). */
fun nextOccurrence(
    phases: List<Phase>,
    items: List<PlanItem>,
    from: Instant,
    zone: ZoneId,
    anchors: IntervalAnchors,
    slotTimes: SlotTimes = SlotTimes.DEFAULT,
    within: Duration = Duration.ofDays(60),
): Occurrence? {
    val planned = items.filter { it.enabled && it.schedule !is Schedule.AsNeeded }
    if (planned.isEmpty()) return null
    return occurrences(phases, planned, from, from.plus(within), zone, anchors, slotTimes).minByOrNull { it.at }
}
