package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.billing.AllUnlocked
import com.apollof.protocoltracker.billing.Entitlements
import com.apollof.protocoltracker.domain.entitlement.FossPolicy
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.domain.pk.PresetChannel

/** The foss build (GitHub, F-Droid, Obtainium): every preset, everything unlocked, no proprietary code. */
object Distribution {
    val presetChannel: PresetChannel = PresetChannel.FOSS
    val policy: TablePolicy = FossPolicy

    @Suppress("UNUSED_PARAMETER")
    fun entitlements(context: Context): Entitlements = AllUnlocked
}
