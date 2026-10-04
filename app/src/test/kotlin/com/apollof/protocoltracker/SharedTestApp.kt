package com.apollof.protocoltracker

import com.apollof.protocoltracker.billing.AllUnlocked
import com.apollof.protocoltracker.domain.entitlement.FossPolicy
import com.apollof.protocoltracker.domain.pk.PresetChannel

/**
 * The app for tests shared by both flavours: every preset with its foss name and nothing behind Pro, so a test reads
 * the same in foss and play. Set as the default in robolectric.properties.
 */
open class SharedTestApp : ProtocolTrackerApp() {
    override fun createContainer() = AppContainer(this, presetChannel = PresetChannel.FOSS, policy = FossPolicy, entitlements = { AllUnlocked })
}
