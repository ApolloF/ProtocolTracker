package com.apollof.protocoltracker.domain.entitlement

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementTest {
    private val start: Instant = Instant.parse("2026-10-01T08:00:00Z")
    private val trialEnd: Instant = start.plus(Duration.ofDays(14))

    private fun play(feature: Feature, isPro: Boolean = false, started: Instant? = start, now: Instant = start.plusSeconds(60)) =
        resolve(feature, PlayPolicy, isPro, started, now)

    // --- resolve, every Access type ---

    @Test
    fun freeIsAlwaysUnlocked() {
        val policy = TablePolicy(PlayPolicy.table + (Feature.CUSTOM_REPORTS to Access.Free))
        assertEquals(Resolved.Unlocked, resolve(Feature.CUSTOM_REPORTS, policy, isPro = false, trialStartedAt = null, now = start))
    }

    @Test
    fun proUnlocksAndOthersGetTheFreeLimit() {
        assertEquals(Resolved.Unlocked, play(Feature.ACTIVE_PLAN_ITEMS, isPro = true))
        assertEquals(Resolved.Limited(Limit.Count(5)), play(Feature.ACTIVE_PLAN_ITEMS))
        assertEquals(Resolved.Limited(Limit.Off), play(Feature.CUSTOM_REPORTS))
    }

    @Test
    fun trialActiveUntilItsEnd() {
        assertEquals(Resolved.TrialActive(trialEnd), play(Feature.LEVELS_RANGE))
        assertEquals(Resolved.TrialActive(trialEnd), play(Feature.LEVELS_RANGE, now = trialEnd.minusMillis(1)))
    }

    @Test
    fun trialNotStartedCountsFromNow() {
        val now = Instant.parse("2026-11-01T00:00:00Z")
        assertEquals(Resolved.TrialActive(now.plus(Duration.ofDays(14))), play(Feature.LEVELS_RANGE, started = null, now = now))
    }

    @Test
    fun trialEndsExactlyAtTheBoundary() {
        assertEquals(Resolved.Limited(Limit.Days(14)), play(Feature.LEVELS_RANGE, now = trialEnd))
    }

    @Test
    fun expiredTrialFallsBackToEachLimit() {
        val later = trialEnd.plus(Duration.ofDays(30))
        assertEquals(Resolved.Limited(Limit.Days(14)), play(Feature.LEVELS_RANGE, now = later))
        assertEquals(Resolved.Limited(Limit.Count(1)), play(Feature.LEVELS_MULTI_COMPOUND, now = later))
        assertEquals(Resolved.Limited(Limit.Off), play(Feature.LEVELS_PLANNED_VS_LOGGED, now = later))
        assertEquals(Resolved.Limited(Limit.Off), play(Feature.LEVELS_LAB_OVERLAY, now = later))
    }

    @Test
    fun expiredTrialFallingBackToFreeUnlocks() {
        val policy = TablePolicy(PlayPolicy.table + (Feature.LEVELS_RANGE to Access.Trial(7, Access.Free)))
        assertEquals(Resolved.Unlocked, resolve(Feature.LEVELS_RANGE, policy, isPro = false, trialStartedAt = start, now = trialEnd))
    }

    @Test
    fun proSkipsTheTrial() {
        assertEquals(Resolved.Unlocked, play(Feature.LEVELS_RANGE, isPro = true))
        assertEquals(Resolved.Unlocked, play(Feature.LEVELS_RANGE, isPro = true, now = trialEnd.plus(Duration.ofDays(400))))
    }

    // --- Policies ---

    @Test
    fun playPolicySnapshot() {
        assertEquals(
            mapOf(
                Feature.ACTIVE_PLAN_ITEMS to Access.Pro(Limit.Count(5)),
                Feature.LEVELS_RANGE to Access.Trial(14, Access.Pro(Limit.Days(14))),
                Feature.LEVELS_MULTI_COMPOUND to Access.Trial(14, Access.Pro(Limit.Count(1))),
                Feature.LEVELS_PLANNED_VS_LOGGED to Access.Trial(14, Access.Pro(Limit.Off)),
                Feature.LEVELS_LAB_OVERLAY to Access.Trial(14, Access.Pro(Limit.Off)),
                Feature.CUSTOM_REPORTS to Access.Pro(Limit.Off),
            ),
            PlayPolicy.table,
        )
        assertEquals(Feature.entries, PlayPolicy.proFeatures)
    }

    @Test
    fun fossPolicyUnlocksEverything() {
        Feature.entries.forEach { f ->
            assertEquals(Resolved.Unlocked, resolve(f, FossPolicy, isPro = false, trialStartedAt = null, now = start), f.name)
            assertEquals(Resolved.Unlocked, resolve(f, FossPolicy, isPro = false, trialStartedAt = start, now = trialEnd.plus(Duration.ofDays(999))), f.name)
        }
        assertEquals(emptyList(), FossPolicy.proFeatures)
    }

    @Test
    fun aPolicyMustCoverEveryFeature() {
        assertFailsWith<IllegalArgumentException> { TablePolicy(mapOf(Feature.CUSTOM_REPORTS to Access.Free)) }
    }

    // --- Alternatives are one-line changes ---

    @Test
    fun levelsWithoutTrialIsLimitedFromTheStart() {
        val policy = TablePolicy(PlayPolicy.table + (Feature.LEVELS_RANGE to Access.Pro(Limit.Days(14))))
        assertEquals(Resolved.Limited(Limit.Days(14)), resolve(Feature.LEVELS_RANGE, policy, isPro = false, trialStartedAt = null, now = start))
    }

    @Test
    fun levelsFullyProAfterTheTrial() {
        val policy = TablePolicy(PlayPolicy.table + (Feature.LEVELS_RANGE to Access.Trial(14, Access.Pro())))
        assertEquals(Resolved.TrialActive(trialEnd), resolve(Feature.LEVELS_RANGE, policy, isPro = false, trialStartedAt = start, now = start))
        assertEquals(Resolved.Limited(Limit.Off), resolve(Feature.LEVELS_RANGE, policy, isPro = false, trialStartedAt = start, now = trialEnd))
    }

    @Test
    fun differentNumbers() {
        val policy = TablePolicy(
            PlayPolicy.table +
                (Feature.ACTIVE_PLAN_ITEMS to Access.Pro(Limit.Count(3))) +
                (Feature.LEVELS_RANGE to Access.Trial(30, Access.Pro(Limit.Days(7)))),
        )
        assertEquals(Resolved.Limited(Limit.Count(3)), resolve(Feature.ACTIVE_PLAN_ITEMS, policy, false, null, start))
        assertEquals(Resolved.TrialActive(start.plus(Duration.ofDays(30))), resolve(Feature.LEVELS_RANGE, policy, false, start, trialEnd))
        assertEquals(Resolved.Limited(Limit.Days(7)), resolve(Feature.LEVELS_RANGE, policy, false, start, start.plus(Duration.ofDays(30))))
    }

    // --- Plan item cap ---

    @Test
    fun capBlocksOnlyTheNextItem() {
        val limited = Resolved.Limited(Limit.Count(5))
        assertTrue(canAddActiveItem(4, limited))
        assertFalse(canAddActiveItem(5, limited))
        // Grandfathered: 8 items from a restore stay on; only a ninth is blocked.
        assertFalse(canAddActiveItem(8, limited))
        assertTrue(canAddActiveItem(50, Resolved.Unlocked))
        assertTrue(canAddActiveItem(50, Resolved.TrialActive(trialEnd)))
        assertFalse(canAddActiveItem(0, Resolved.Limited(Limit.Off)))
    }
}
