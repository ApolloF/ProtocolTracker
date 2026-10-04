package com.apollof.protocoltracker.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pro from Google Play Billing (one-time product [PRODUCT_ID]). */
class PlayBillingEntitlements private constructor() : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)

    companion object {
        const val PRODUCT_ID = "pro_unlock"

        @Suppress("UNUSED_PARAMETER")
        fun create(context: Context): PlayBillingEntitlements = PlayBillingEntitlements()
    }
}
