package com.apollof.protocoltracker.domain.entitlement

import java.time.Duration
import java.time.Instant

/**
 * Features a policy can limit. Logging, reminders, sites, the journal, bloodwork, symptoms, backup and restore and the
 * basic report are always free and never appear here. Add a new paid feature here and give it a row in every policy.
 */
enum class Feature {
    /** Plan items that are switched on. */
    ACTIVE_PLAN_ITEMS,
    /** How far Levels reaches back and how wide its window gets. */
    LEVELS_RANGE,
    /** Several compound charts on the Levels overview at once. */
    LEVELS_MULTI_COMPOUND,
    /** Levels detail: Logged and Plan only next to Logged + plan. */
    LEVELS_PLANNED_VS_LOGGED,
    /** Lab results drawn on the level curves. */
    LEVELS_LAB_OVERLAY,
    /** Reports over a date range of the user's choice. */
    CUSTOM_REPORTS,
}

/** The free amount of a limited feature. */
sealed interface Limit {
    /** Not available. */
    data object Off : Limit

    /** At most [max] (items, charts). */
    data class Count(val max: Int) : Limit

    /** At most [days] days. */
    data class Days(val days: Int) : Limit
}

/** Who gets a feature. */
sealed interface Access {
    /** Everyone, without limit. */
    data object Free : Access

    /** Pro unlocks it; everyone else gets [freeLimit]. */
    data class Pro(val freeLimit: Limit = Limit.Off) : Access

    /** Everything for [days] days after the user first opens the feature, then [then]. */
    data class Trial(val days: Int, val then: Access) : Access
}

/** What the user gets now. */
sealed interface Resolved {
    data object Unlocked : Resolved

    data class Limited(val limit: Limit) : Resolved

    /** Unlocked until [endsAt]; then the trial's fallback. */
    data class TrialActive(val endsAt: Instant) : Resolved
}

/** Maps every [Feature] to its [Access]. */
interface Policy {
    fun access(feature: Feature): Access
}

/** A policy from a table; every feature must have a row. */
open class TablePolicy(val table: Map<Feature, Access>) : Policy {
    init {
        require(table.keys == Feature.entries.toSet()) { "Policy misses ${Feature.entries - table.keys}" }
    }

    override fun access(feature: Feature): Access = table.getValue(feature)

    /** Features Pro adds something to, in [Feature] order (the paywall lists these). */
    val proFeatures: List<Feature> get() = Feature.entries.filter { table.getValue(it) != Access.Free }
}

/** The FOSS build (GitHub, F-Droid): everything free. */
object FossPolicy : TablePolicy(Feature.entries.associateWith { Access.Free })

/** Levels' free trial: the full Levels for this many days after Levels is first opened. */
private const val LEVELS_TRIAL_DAYS = 14

/**
 * The Play build (owner decision 2026-10-04: free with a one-time Pro unlock). This table is the single place that
 * decides tiers, limits and trial lengths; EntitlementTest pins it.
 */
object PlayPolicy : TablePolicy(
    mapOf(
        Feature.ACTIVE_PLAN_ITEMS to Access.Pro(Limit.Count(5)),
        Feature.LEVELS_RANGE to Access.Trial(LEVELS_TRIAL_DAYS, Access.Pro(Limit.Days(14))),
        Feature.LEVELS_MULTI_COMPOUND to Access.Trial(LEVELS_TRIAL_DAYS, Access.Pro(Limit.Count(1))),
        Feature.LEVELS_PLANNED_VS_LOGGED to Access.Trial(LEVELS_TRIAL_DAYS, Access.Pro()),
        Feature.LEVELS_LAB_OVERLAY to Access.Trial(LEVELS_TRIAL_DAYS, Access.Pro()),
        Feature.CUSTOM_REPORTS to Access.Pro(),
    ),
)

/** Features whose trial starts when Levels is first opened. */
val LEVELS_FEATURES: Set<Feature> = setOf(
    Feature.LEVELS_RANGE, Feature.LEVELS_MULTI_COMPOUND, Feature.LEVELS_PLANNED_VS_LOGGED, Feature.LEVELS_LAB_OVERLAY,
)

/**
 * What [feature] gives the user at [now]. A trial not started yet ([trialStartedAt] null) counts as starting now, so
 * the first look at a feature shows it whole; the caller records the start. A trial ends exactly `days` days after its
 * start: at that instant the fallback applies.
 */
fun resolve(feature: Feature, policy: Policy, isPro: Boolean, trialStartedAt: Instant?, now: Instant): Resolved =
    resolve(policy.access(feature), isPro, trialStartedAt, now)

private fun resolve(access: Access, isPro: Boolean, trialStartedAt: Instant?, now: Instant): Resolved = when (access) {
    Access.Free -> Resolved.Unlocked
    is Access.Pro -> if (isPro) Resolved.Unlocked else Resolved.Limited(access.freeLimit)
    is Access.Trial -> {
        val endsAt = (trialStartedAt ?: now).plus(Duration.ofDays(access.days.toLong()))
        when {
            isPro -> Resolved.Unlocked
            now.isBefore(endsAt) -> Resolved.TrialActive(endsAt)
            else -> resolve(access.then, isPro = false, trialStartedAt = trialStartedAt, now = now)
        }
    }
}

/** Whether one more plan item may be switched on while [activeCount] are on. Items already on are never touched. */
fun canAddActiveItem(activeCount: Int, resolved: Resolved): Boolean = when (resolved) {
    Resolved.Unlocked, is Resolved.TrialActive -> true
    is Resolved.Limited -> when (val limit = resolved.limit) {
        is Limit.Count -> activeCount < limit.max
        Limit.Off -> false
        is Limit.Days -> true
    }
}

/** The limit in force now; null when the feature is unlocked or in its trial. */
val Resolved.limit: Limit? get() = (this as? Resolved.Limited)?.limit
