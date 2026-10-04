package com.apollof.protocoltracker.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/** A one-time purchase as Google Play reports it, reduced to what the Pro state needs. */
data class OwnedPurchase(
    val token: String,
    val products: List<String>,
    val state: State,
    val acknowledged: Boolean,
) {
    enum class State { PURCHASED, PENDING, OTHER }
}

/** What a purchase flow sent back through the purchases listener. */
sealed interface PurchaseUpdate {
    /** New or changed purchases; not the full list of what the user owns. */
    data class Purchases(val purchases: List<OwnedPurchase>) : PurchaseUpdate

    data object Canceled : PurchaseUpdate

    data object AlreadyOwned : PurchaseUpdate

    data object Failed : PurchaseUpdate
}

/** Whether the purchase screen of Google Play opened. */
enum class LaunchResult { STARTED, ALREADY_OWNED, FAILED }

/**
 * The calls Pro needs from Google Play Billing. Every call returns a failure value instead of throwing, so a missing
 * or broken Play Store never reaches the app. [PlayBillingGateway] is the real one; tests use a fake.
 */
interface BillingGateway {
    /** Connects if needed; false when Play Billing is not available now. */
    suspend fun connect(): Boolean

    /** Every one-time purchase the user owns, or null when the query failed (state unknown). */
    suspend fun queryPurchases(): List<OwnedPurchase>?

    /** The formatted price of [productId] in the user's currency, or null when unknown. */
    suspend fun price(productId: String): String?

    suspend fun acknowledge(token: String): Boolean

    suspend fun launchPurchase(activity: Activity, productId: String): LaunchResult

    /** Results of purchase flows, also of ones finished while the app was in the background. */
    val updates: Flow<PurchaseUpdate>
}
