package com.apollof.protocoltracker.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** [BillingGateway] over the Play Billing Library. The client is built on first use, never at app start. */
class PlayBillingGateway(context: Context) : BillingGateway {
    private val appContext = context.applicationContext
    private val connectLock = Mutex()
    private val details = HashMap<String, ProductDetails>()
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseUpdate> = _updates

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .setListener { result, purchases -> _updates.tryEmit(update(result, purchases)) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    override suspend fun connect(): Boolean = guarded(false) {
        connectLock.withLock {
            if (client.isReady) return@withLock true
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        }
    }

    override suspend fun queryPurchases(): List<OwnedPurchase>? = guarded(null) {
        if (!connect()) return@guarded null
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) purchases.map(::owned) else null)
            }
        }
    }

    override suspend fun price(productId: String): String? =
        guarded(null) { productDetails(productId)?.oneTimePurchaseOfferDetails?.formattedPrice }

    override suspend fun acknowledge(token: String): Boolean = guarded(false) {
        if (!connect()) return@guarded false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result -> if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK) }
        }
    }

    override suspend fun launchPurchase(activity: Activity, productId: String): LaunchResult = guarded(LaunchResult.FAILED) {
        val product = productDetails(productId) ?: return@guarded LaunchResult.FAILED
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        product.oneTimePurchaseOfferDetails?.offerToken?.let(productParams::setOfferToken)
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build()
        when (client.launchBillingFlow(activity, params).responseCode) {
            BillingResponseCode.OK -> LaunchResult.STARTED
            BillingResponseCode.ITEM_ALREADY_OWNED -> LaunchResult.ALREADY_OWNED
            else -> LaunchResult.FAILED
        }
    }

    private suspend fun productDetails(productId: String): ProductDetails? {
        synchronized(details) { details[productId] }?.let { return it }
        if (!connect()) return null
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId).setProductType(BillingClient.ProductType.INAPP).build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        val found = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, query ->
                val match = query.productDetailsList.firstOrNull { it.productId == productId }
                if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) match else null)
            }
        }
        if (found != null) synchronized(details) { details[productId] = found }
        return found
    }

    /** Runs [block] with a time limit; a Play Store that never answers or throws gives [fallback]. */
    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() } ?: fallback
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L

        fun update(result: BillingResult, purchases: List<Purchase>?): PurchaseUpdate = when (result.responseCode) {
            BillingResponseCode.OK -> PurchaseUpdate.Purchases(purchases.orEmpty().map(::owned))
            BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Canceled
            BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.AlreadyOwned
            else -> PurchaseUpdate.Failed
        }

        fun owned(p: Purchase) = OwnedPurchase(
            token = p.purchaseToken,
            products = p.products,
            state = when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> OwnedPurchase.State.PURCHASED
                Purchase.PurchaseState.PENDING -> OwnedPurchase.State.PENDING
                else -> OwnedPurchase.State.OTHER
            },
            acknowledged = p.isAcknowledged,
        )
    }
}
