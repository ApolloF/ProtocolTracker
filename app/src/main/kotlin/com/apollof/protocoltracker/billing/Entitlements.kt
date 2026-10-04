package com.apollof.protocoltracker.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the user owns Pro. A cached value: it never waits on a store, and nothing free depends on it. */
interface Entitlements {
    val isPro: StateFlow<Boolean>
}

/** Nothing to buy: the foss build always, and the Play build when it sells no unlock (monetisation none or paid_listing). */
object AllUnlocked : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
}
