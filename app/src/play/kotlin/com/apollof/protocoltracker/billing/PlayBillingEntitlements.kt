package com.apollof.protocoltracker.billing

import android.app.Activity
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pro from Google Play Billing (one-time product [productId]). Starts with the cached state and never waits on Play:
 * every query that succeeds replaces the state (a refund or revoke shows up as the purchase missing), a query that
 * fails keeps it. A pending purchase grants nothing until Play reports it purchased.
 */
class PlayBillingEntitlements(
    private val gateway: BillingGateway,
    private val cache: ProCache,
    private val scope: CoroutineScope,
    private val productId: String = PRODUCT_ID,
) : Entitlements {
    private val _isPro = MutableStateFlow(cache.isPro)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _pending = MutableStateFlow(false)
    /** A purchase waits for payment (e.g. cash at a shop); Pro is not unlocked yet. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    /** The price from Google Play, or null before it has loaded (show [FALLBACK_PRICE]). */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-shot results for the user, e.g. "Pro unlocked". */
    val messages: Flow<String> = _messages

    private val refreshLock = Mutex()
    private val acknowledging = HashSet<String>()

    init {
        scope.launch { gateway.updates.collect(::onUpdate) }
    }

    /** Re-reads what the user owns. False when Google Play could not be reached (the state is kept). */
    suspend fun refresh(): Boolean = refreshLock.withLock {
        val owned = gateway.queryPurchases() ?: return@withLock false
        apply(owned, complete = true)
        true
    }

    /** "Restore purchase": a refresh that tells the user its result. */
    suspend fun restore() {
        val message = when {
            !refresh() -> UNREACHABLE
            _isPro.value -> UNLOCKED
            _pending.value -> PENDING
            else -> "No purchase found"
        }
        _messages.emit(message)
    }

    suspend fun loadPrice() {
        if (_price.value == null) gateway.price(productId)?.let { _price.value = it }
    }

    suspend fun buy(activity: Activity) {
        when (gateway.launchPurchase(activity, productId)) {
            LaunchResult.STARTED -> Unit
            LaunchResult.ALREADY_OWNED -> restore()
            LaunchResult.FAILED -> _messages.emit(UNREACHABLE)
        }
    }

    private suspend fun onUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchases -> {
                refreshLock.withLock { apply(update.purchases, complete = false) }
                refresh()
            }
            PurchaseUpdate.AlreadyOwned -> refresh()
            PurchaseUpdate.Canceled -> Unit
            PurchaseUpdate.Failed -> _messages.emit("Purchase not completed")
        }
    }

    /**
     * [complete] purchases are everything the user owns (a query), so a missing Pro purchase revokes Pro. A purchase
     * update only lists what changed, so it may unlock but never revoke.
     */
    private suspend fun apply(purchases: List<OwnedPurchase>, complete: Boolean) {
        val ours = purchases.filter { productId in it.products }
        val purchased = ours.firstOrNull { it.state == OwnedPurchase.State.PURCHASED }
        val waiting = ours.any { it.state == OwnedPurchase.State.PENDING }
        val wasPro = _isPro.value
        val wasPending = _pending.value
        when {
            purchased != null -> setPro(true)
            complete -> setPro(false)
        }
        if (complete || purchased != null || waiting) _pending.value = purchased == null && waiting
        when {
            !wasPro && _isPro.value -> _messages.emit(UNLOCKED)
            !wasPending && _pending.value -> _messages.emit(PENDING)
        }
        if (purchased != null && !purchased.acknowledged) acknowledge(purchased.token)
    }

    private fun setPro(pro: Boolean) {
        if (cache.isPro != pro) cache.isPro = pro
        _isPro.value = pro
    }

    /** Play refunds a purchase not acknowledged within three days; a failed one is retried on the next query. */
    private suspend fun acknowledge(token: String) {
        if (!synchronized(acknowledging) { acknowledging.add(token) }) return
        try {
            gateway.acknowledge(token)
        } finally {
            synchronized(acknowledging) { acknowledging.remove(token) }
        }
    }

    companion object {
        const val PRODUCT_ID = "pro_unlock"
        /** Shown until Google Play has sent the price in the user's currency. */
        const val FALLBACK_PRICE = "€9.99"
        const val UNLOCKED = "Pro unlocked"
        const val PENDING = "Purchase pending"
        const val UNREACHABLE = "Could not reach Google Play"

        /** The app's instance: re-reads purchases each time the app comes to the foreground (and so on start). */
        fun create(context: Context): PlayBillingEntitlements {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val entitlements = PlayBillingEntitlements(PlayBillingGateway(context), PrefsProCache(context), scope)
            scope.launch(Dispatchers.Main) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        scope.launch { entitlements.refresh() }
                    }
                })
            }
            return entitlements
        }
    }
}
