package com.apollof.protocoltracker.domain.entitlement

import com.apollof.protocoltracker.domain.model.PlanItem
import java.time.Instant

/** How many days a feature reaches: n for `Days(n)`, 0 when off, null when no day limit applies. */
val Resolved.maxDays: Int?
    get() = when (val l = limit) {
        is Limit.Days -> l.days
        Limit.Off -> 0
        is Limit.Count, null -> null
    }

/** How many a feature allows: k for `Count(k)`, 0 when off, null when no count limit applies. */
val Resolved.maxCount: Int?
    get() = when (val l = limit) {
        is Limit.Count -> l.max
        Limit.Off -> 0
        is Limit.Days, null -> null
    }

/** Whether an on-or-off feature is available: unlocked or in its trial. */
val Resolved.available: Boolean get() = this !is Resolved.Limited

/** When the first of [resolved] trials ends; null when none is running. */
fun trialEndsAt(resolved: Iterable<Resolved>): Instant? =
    resolved.filterIsInstance<Resolved.TrialActive>().minOfOrNull { it.endsAt }

/**
 * The cap on plan items that are switched on ([Feature.ACTIVE_PLAN_ITEMS]).
 *
 * An item is active when it is switched on (`enabled`), whatever its phase or dates: it is in the plan and gets
 * reminders. A paused item is not active. The cap only stops switching one more item on; items already on stay on,
 * and a restore or an import is never capped (grandfathering).
 */
object ActivePlanItems {
    /** Items switched on, leaving out [excludingId] (the item being saved). */
    fun count(items: Collection<PlanItem>, excludingId: String? = null): Int = items.count { it.enabled && it.id != excludingId }

    /** Whether saving [saved] over [before] (null for a new item) switches an item on. */
    fun switchesOn(before: PlanItem?, saved: PlanItem): Boolean = saved.enabled && before?.enabled != true

    /** Whether [saved] may be saved into the plan [items] under [resolved]: only switching one more item on is limited. */
    fun maySave(items: Collection<PlanItem>, saved: PlanItem, resolved: Resolved): Boolean {
        val before = items.firstOrNull { it.id == saved.id }
        return !switchesOn(before, saved) || canAddActiveItem(count(items, excludingId = saved.id), resolved)
    }
}
