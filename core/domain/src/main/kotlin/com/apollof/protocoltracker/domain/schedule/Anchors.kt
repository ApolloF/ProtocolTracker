package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import java.time.Instant

/** A taken dose of a plan item: which occurrence it confirmed and when it was actually taken. */
data class TakenDose(val planItemId: String, val occurrenceKey: String, val takenAt: Instant)

/**
 * Taken doses that re-anchor interval schedules with "count from last dose" on (see [occurrences]).
 * Must hold every taken plan dose, not a window: the deciding dose can be older than any view's range.
 */
class IntervalAnchors(doses: Collection<TakenDose>) {
    private val byItem: Map<String, List<TakenDose>> = doses.groupBy { it.planItemId }

    fun forItem(itemId: String): List<TakenDose> = byItem[itemId].orEmpty()

    val isEmpty: Boolean get() = byItem.isEmpty()

    override fun equals(other: Any?): Boolean = other is IntervalAnchors && other.byItem == byItem
    override fun hashCode(): Int = byItem.hashCode()

    companion object {
        /** Plain plan grid, ignoring logs. */
        val NONE = IntervalAnchors(emptyList())

        /** Anchors from logs; only taken scheduled doses count (skipped and unscheduled doses never move the plan). */
        fun from(logs: Collection<DoseLog>) = IntervalAnchors(
            logs.mapNotNull { log ->
                if (log.status != LogStatus.TAKEN) return@mapNotNull null
                TakenDose(log.planItemId ?: return@mapNotNull null, log.occurrenceKey ?: return@mapNotNull null, log.takenAt)
            },
        )
    }
}
