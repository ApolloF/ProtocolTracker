package com.apollof.protocoltracker

import com.apollof.protocoltracker.billing.AllUnlocked
import com.apollof.protocoltracker.billing.Entitlements
import com.apollof.protocoltracker.domain.entitlement.Access
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.FossPolicy
import com.apollof.protocoltracker.domain.entitlement.PlayPolicy
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.domain.pk.PresetChannel
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.shadows.ShadowLooper

// Apps with a given entitlement policy, so tests of either flavour check the Play tiers and the foss build
// (`@Config(application = ...)`); the flavour's own Distribution is not used. All seed every preset, as SharedTestApp.

/** Pro as a test sets it. */
class TestEntitlements(pro: Boolean = false) : Entitlements {
    override val isPro = MutableStateFlow(pro)
}

/** The Play tiers, without Pro. */
class PlayGatedApp : ProtocolTrackerApp() {
    override fun createContainer() = AppContainer(this, presetChannel = PresetChannel.FOSS, policy = PlayPolicy, entitlements = { TestEntitlements(pro = false) })
}

/** The foss build: everything unlocked. */
class FossGatedApp : ProtocolTrackerApp() {
    override fun createContainer() = AppContainer(this, presetChannel = PresetChannel.FOSS, policy = FossPolicy, entitlements = { AllUnlocked })
}

/** A policy where Levels is Pro only, with no free days (LEVELS_RANGE `Pro(Off)`), without Pro. */
class LevelsOffApp : ProtocolTrackerApp() {
    override fun createContainer() = AppContainer(
        this,
        presetChannel = PresetChannel.FOSS,
        policy = TablePolicy(PlayPolicy.table + (Feature.LEVELS_RANGE to Access.Pro())),
        entitlements = { TestEntitlements(pro = false) },
    )
}

/**
 * Waits until [condition] holds, running the main looper in between: view models launch on Robolectric's paused
 * main looper, which a plain wait never lets run.
 */
fun awaitMain(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        ShadowLooper.idleMainLooper()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
        Thread.sleep(20)
    }
}
