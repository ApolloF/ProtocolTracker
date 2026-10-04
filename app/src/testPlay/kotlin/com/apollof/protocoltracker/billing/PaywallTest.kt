package com.apollof.protocoltracker.billing

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.FossPolicy
import com.apollof.protocoltracker.domain.entitlement.PlayPolicy
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The paywall lists what Pro adds straight from the Play policy, with the one-time price. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PaywallTest {
    @get:Rule
    val compose = createComposeRule()

    private val lines = PlayPolicy.proFeatures.map { proFeatureLine(it, PlayPolicy) }

    private fun show(isPro: Boolean = false, pending: Boolean = false) = compose.setContent {
        ProtocolTrackerTheme {
            PaywallContent(
                title = "App Pro",
                reason = paywallReason(Feature.ACTIVE_PLAN_ITEMS, PlayPolicy),
                features = lines,
                price = PlayBillingEntitlements.FALLBACK_PRICE,
                isPro = isPro,
                pending = pending,
                message = null,
                onBuy = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun listsOneLinePerProFeatureAndThePrice() {
        show()
        assertEquals(PlayPolicy.proFeatures.size, lines.toSet().size)
        lines.forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }
        compose.onNodeWithText("€9.99 once, no subscription").performScrollTo()
        compose.onNodeWithText("Buy").performScrollTo()
        compose.onNodeWithText("Not now").performScrollTo()
    }

    @Test
    fun pendingShowsNoBuyButton() {
        show(pending = true)
        compose.onNodeWithText(PlayBillingEntitlements.PENDING).performScrollTo()
        compose.onAllNodesWithText("Buy").assertCountEquals(0)
    }

    @Test
    fun unlockedOffersClose() {
        show(isPro = true)
        compose.onNodeWithText(PlayBillingEntitlements.UNLOCKED).performScrollTo()
        compose.onNodeWithText("Close").performScrollTo()
        compose.onAllNodesWithText("Buy").assertCountEquals(0)
    }

    @Test
    fun wordingTakesNumbersFromThePolicy() {
        assertEquals("More than 5 active plan items", proFeatureLine(Feature.ACTIVE_PLAN_ITEMS, PlayPolicy))
        assertEquals("Levels history beyond 14 days, up to 1 year", proFeatureLine(Feature.LEVELS_RANGE, PlayPolicy))
        assertTrue(paywallReason(Feature.LEVELS_MULTI_COMPOUND, PlayPolicy).startsWith("The Levels trial has ended."))
        assertTrue(FossPolicy.proFeatures.isEmpty())
    }

    @Test
    fun levelsTrialEndsAfterItsDays() {
        val start = Instant.parse("2026-10-01T08:00:00Z")
        assertEquals(Instant.parse("2026-10-15T08:00:00Z"), levelsTrialEnd(PlayPolicy, mapOf(Feature.LEVELS_RANGE to start)))
        assertNull(levelsTrialEnd(PlayPolicy, emptyMap()))
        assertNull(levelsTrialEnd(FossPolicy, mapOf(Feature.LEVELS_RANGE to start)))
    }
}
