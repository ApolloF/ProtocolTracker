package com.apollof.protocoltracker.billing

import com.apollof.protocoltracker.domain.entitlement.Access
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.Limit
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import java.time.Duration
import java.time.Instant

// Pro wording of the Play build, from the policy table so the paywall never disagrees with the limits.

/** What the user gets without Pro once any trial is over; null when the feature is free. */
internal fun freeLimit(access: Access): Limit? = when (access) {
    Access.Free -> null
    is Access.Pro -> access.freeLimit
    is Access.Trial -> freeLimit(access.then)
}

/** One line of the paywall's list: what Pro adds to [feature]. */
internal fun proFeatureLine(feature: Feature, policy: TablePolicy): String {
    val limit = freeLimit(policy.access(feature))
    return when (feature) {
        Feature.ACTIVE_PLAN_ITEMS ->
            if (limit is Limit.Count) "More than ${limit.max} active plan items" else "Any number of active plan items"
        Feature.LEVELS_RANGE ->
            if (limit is Limit.Days) "Levels history beyond ${limit.days} days, up to 1 year" else "Levels history up to 1 year"
        Feature.LEVELS_MULTI_COMPOUND -> "Several compounds on the Levels overview"
        Feature.LEVELS_PLANNED_VS_LOGGED -> "Logged and Plan only views on Levels"
        Feature.LEVELS_LAB_OVERLAY -> "Lab results on the level curves"
        Feature.CUSTOM_REPORTS -> "Reports over any date range"
    }
}

/** Why the paywall opened for [feature]. */
internal fun paywallReason(feature: Feature, policy: TablePolicy): String {
    val access = policy.access(feature)
    val limit = freeLimit(access)
    val reason = when (feature) {
        Feature.ACTIVE_PLAN_ITEMS ->
            if (limit is Limit.Count) "Without Pro, up to ${limit.max} plan items can be active at once." else "Active plan items need Pro."
        Feature.LEVELS_RANGE ->
            if (limit is Limit.Days) "Without Pro, Levels reaches back ${limit.days} days." else "The Levels history needs Pro."
        Feature.LEVELS_MULTI_COMPOUND ->
            if (limit is Limit.Count && limit.max == 1) "Without Pro, the Levels overview shows one compound at a time."
            else if (limit is Limit.Count) "Without Pro, the Levels overview shows up to ${limit.max} compounds at a time."
            else "Several compounds on the Levels overview need Pro."
        Feature.LEVELS_PLANNED_VS_LOGGED -> "The Logged and Plan only views need Pro."
        Feature.LEVELS_LAB_OVERLAY -> "Lab results on the level curves need Pro."
        Feature.CUSTOM_REPORTS -> "Reports over a chosen date range need Pro."
    }
    return if (access is Access.Trial) "The Levels trial has ended. $reason" else reason
}

/** "€9.99 once, no subscription". */
internal fun priceLine(price: String): String = "$price once, no subscription"

/** When the Levels trial ends or ended; null when it has not started or Levels has no trial. */
internal fun levelsTrialEnd(policy: TablePolicy, trialStarts: Map<Feature, Instant>): Instant? {
    val trial = policy.access(Feature.LEVELS_RANGE) as? Access.Trial ?: return null
    val start = trialStarts[Feature.LEVELS_RANGE] ?: return null
    return start.plus(Duration.ofDays(trial.days.toLong()))
}
