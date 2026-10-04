package com.apollof.protocoltracker.billing

import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.domain.entitlement.Access
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.Resolved
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.domain.entitlement.resolve
import com.apollof.protocoltracker.ui.minuteTicker
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The one place screens ask what a feature gives the user now: [policy] (per flavour, Distribution) resolved with the
 * Pro state and the trial starts. Screens never check Pro themselves.
 */
class FeatureGate(
    val policy: TablePolicy,
    val entitlements: Entitlements,
    private val settings: SettingsStore,
    private val clock: () -> Instant,
) {
    /** Whether this build sells Pro at all; false in foss and in a Play build without the unlock. */
    val sellsPro: Boolean get() = entitlements !== AllUnlocked && policy.proFeatures.isNotEmpty()

    /** Every feature, resolved; re-resolved each minute so a trial ends on time. */
    val all: Flow<Map<Feature, Resolved>> = combine(entitlements.isPro, settings.trialStarts, minuteTicker(clock)) { pro, trials, now ->
        Feature.entries.associateWith { resolve(it, policy, pro, trials[it], now) }
    }.distinctUntilChanged()

    fun resolved(feature: Feature): Flow<Resolved> = all.map { it.getValue(feature) }.distinctUntilChanged()

    suspend fun resolveNow(feature: Feature): Resolved =
        resolve(feature, policy, entitlements.isPro.value, settings.trialStarts.first()[feature], clock())

    /** The user opened [features]: their trials start now, unless already started. */
    suspend fun startTrials(features: Set<Feature>) {
        val trials = features.filterTo(HashSet()) { policy.access(it) is Access.Trial }
        if (trials.isNotEmpty()) settings.startTrials(trials, clock())
    }
}
