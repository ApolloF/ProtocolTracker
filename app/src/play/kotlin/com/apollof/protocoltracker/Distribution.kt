package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.billing.AllUnlocked
import com.apollof.protocoltracker.billing.Entitlements
import com.apollof.protocoltracker.billing.PlayBillingEntitlements
import com.apollof.protocoltracker.domain.entitlement.FossPolicy
import com.apollof.protocoltracker.domain.entitlement.PlayPolicy
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.domain.pk.PresetChannel

/**
 * The Google Play build: the reviewed preset allowlist, and with `-Pplay.monetization=unlock` (the default) the
 * one-time Pro unlock. With none or paid_listing everything is unlocked, as in foss.
 */
object Distribution {
    val presetChannel: PresetChannel = PresetChannel.PLAY
    private val sellsUnlock: Boolean = BuildConfig.MONETIZATION == "unlock"
    val policy: TablePolicy = if (sellsUnlock) PlayPolicy else FossPolicy

    fun entitlements(context: Context): Entitlements = if (sellsUnlock) PlayBillingEntitlements.create(context) else AllUnlocked
}
