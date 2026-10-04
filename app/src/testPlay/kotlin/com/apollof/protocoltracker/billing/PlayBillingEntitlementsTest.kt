package com.apollof.protocoltracker.billing

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.Limit
import com.apollof.protocoltracker.domain.entitlement.PlayPolicy
import com.apollof.protocoltracker.domain.entitlement.Resolved
import com.apollof.protocoltracker.domain.entitlement.resolve
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Pro state of the Play build over a fake Google Play: purchases, pending, refunds, failures and the cache. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlayBillingEntitlementsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val trialOver = now.minus(Duration.ofDays(30))

    private class FakeGateway(var owned: List<OwnedPurchase>? = emptyList()) : BillingGateway {
        val acknowledged = mutableListOf<String>()
        val flow = MutableSharedFlow<PurchaseUpdate>()
        var launch = LaunchResult.STARTED
        override val updates = flow
        override suspend fun connect() = owned != null
        override suspend fun queryPurchases() = owned
        override suspend fun price(productId: String) = owned?.let { "9,99 €" }
        override suspend fun launchPurchase(activity: Activity, productId: String) = launch
        override suspend fun acknowledge(token: String): Boolean {
            acknowledged += token
            owned = owned?.map { if (it.token == token) it.copy(acknowledged = true) else it }
            return true
        }
    }

    private class MemoryCache(override var isPro: Boolean = false) : ProCache

    private fun purchase(state: OwnedPurchase.State, acknowledged: Boolean = false, token: String = "t1") =
        OwnedPurchase(token, listOf(PlayBillingEntitlements.PRODUCT_ID), state, acknowledged)

    private fun TestScope.entitlements(gateway: BillingGateway, cache: ProCache = MemoryCache()): PlayBillingEntitlements =
        PlayBillingEntitlements(gateway, cache, backgroundScope).also { runCurrent() }

    private fun PlayBillingEntitlements.resolved(feature: Feature) = resolve(feature, PlayPolicy, isPro.value, trialOver, now)

    @Before
    fun clearPrefs() {
        app.getSharedPreferences("billing", 0).edit().clear().commit()
    }

    @Test
    fun noPurchaseGivesTheFreeLimits() = runTest {
        val pro = entitlements(FakeGateway())
        assertTrue(pro.refresh())
        assertFalse(pro.isPro.value)
        assertEquals(Resolved.Limited(Limit.Count(5)), pro.resolved(Feature.ACTIVE_PLAN_ITEMS))
        assertEquals(Resolved.Limited(Limit.Days(14)), pro.resolved(Feature.LEVELS_RANGE))
        assertEquals(Resolved.Limited(Limit.Off), pro.resolved(Feature.CUSTOM_REPORTS))
    }

    @Test
    fun purchaseUnlocksEverythingAndIsAcknowledgedOnce() = runTest {
        val gateway = FakeGateway(listOf(purchase(OwnedPurchase.State.PURCHASED)))
        val cache = MemoryCache()
        val pro = entitlements(gateway, cache)
        pro.refresh()
        pro.refresh()
        gateway.flow.emit(PurchaseUpdate.Purchases(gateway.owned!!))
        runCurrent()
        assertTrue(pro.isPro.value)
        assertTrue(cache.isPro)
        assertEquals(listOf("t1"), gateway.acknowledged)
        Feature.entries.forEach { assertEquals(Resolved.Unlocked, pro.resolved(it), "$it") }
    }

    @Test
    fun otherProductsGrantNothing() = runTest {
        val gateway = FakeGateway(listOf(OwnedPurchase("x", listOf("something_else"), OwnedPurchase.State.PURCHASED, true)))
        val pro = entitlements(gateway)
        pro.refresh()
        assertFalse(pro.isPro.value)
    }

    @Test
    fun pendingGrantsNothingUntilItCompletes() = runTest {
        val gateway = FakeGateway(listOf(purchase(OwnedPurchase.State.PENDING)))
        val pro = entitlements(gateway)
        val messages = mutableListOf<String>()
        backgroundScope.launch { pro.messages.toList(messages) }
        runCurrent()
        pro.refresh()
        assertFalse(pro.isPro.value)
        assertTrue(pro.pending.value)
        assertTrue(gateway.acknowledged.isEmpty())

        gateway.owned = listOf(purchase(OwnedPurchase.State.PURCHASED))
        gateway.flow.emit(PurchaseUpdate.Purchases(gateway.owned!!))
        runCurrent()
        assertTrue(pro.isPro.value)
        assertFalse(pro.pending.value)
        assertEquals(listOf("t1"), gateway.acknowledged)
        assertEquals(listOf(PlayBillingEntitlements.PENDING, PlayBillingEntitlements.UNLOCKED), messages)
    }

    @Test
    fun refundRevokesPro() = runTest {
        val gateway = FakeGateway(listOf(purchase(OwnedPurchase.State.PURCHASED, acknowledged = true)))
        val cache = MemoryCache()
        val pro = entitlements(gateway, cache)
        pro.refresh()
        assertTrue(pro.isPro.value)

        gateway.owned = emptyList()
        pro.refresh()
        assertFalse(pro.isPro.value)
        assertFalse(cache.isPro)
    }

    @Test
    fun anUpdateNeverRevokes() = runTest {
        val gateway = FakeGateway(null)
        val pro = entitlements(gateway, MemoryCache(isPro = true))
        gateway.flow.emit(PurchaseUpdate.Purchases(emptyList()))
        runCurrent()
        assertTrue(pro.isPro.value)
    }

    @Test
    fun failedQueryKeepsCachedPro() = runTest {
        val cache = MemoryCache(isPro = true)
        val pro = entitlements(FakeGateway(null), cache)
        assertTrue(pro.isPro.value)
        assertFalse(pro.refresh())
        assertTrue(pro.isPro.value)
        assertTrue(cache.isPro)
    }

    @Test
    fun failedQueryKeepsCachedFree() = runTest {
        val cache = MemoryCache(isPro = false)
        val pro = entitlements(FakeGateway(null), cache)
        assertFalse(pro.refresh())
        assertFalse(pro.isPro.value)
        assertFalse(cache.isPro)
    }

    @Test
    fun failedRestoreSaysPlayIsUnreachable() = runTest {
        val pro = entitlements(FakeGateway(null), MemoryCache(isPro = true))
        val messages = mutableListOf<String>()
        backgroundScope.launch { pro.messages.toList(messages) }
        runCurrent()
        pro.restore()
        runCurrent()
        assertEquals(listOf(PlayBillingEntitlements.UNREACHABLE), messages)
        assertTrue(pro.isPro.value)
    }

    @Test
    fun cacheSurvivesANewInstance() = runTest {
        val first = entitlements(FakeGateway(listOf(purchase(OwnedPurchase.State.PURCHASED, acknowledged = true))), PrefsProCache(app))
        first.refresh()
        assertTrue(first.isPro.value)

        val second = entitlements(FakeGateway(null), PrefsProCache(app))
        assertTrue(second.isPro.value)
    }

    @Test
    fun priceFallsBackUntilPlayAnswers() = runTest {
        val offline = entitlements(FakeGateway(null))
        offline.loadPrice()
        assertEquals(null, offline.price.value)

        val online = entitlements(FakeGateway())
        online.loadPrice()
        assertEquals("9,99 €", online.price.value)
    }

    @Test
    fun alreadyOwnedRestores() = runTest {
        val gateway = FakeGateway(listOf(purchase(OwnedPurchase.State.PURCHASED, acknowledged = true))).apply { launch = LaunchResult.ALREADY_OWNED }
        val pro = entitlements(gateway)
        pro.buy(Robolectric.buildActivity(Activity::class.java).get())
        assertTrue(pro.isPro.value)
    }
}
