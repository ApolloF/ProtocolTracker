package com.apollof.protocoltracker.domain.entitlement

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitsTest {
    private val fiveFree = Resolved.Limited(Limit.Count(5))

    private fun item(id: String, enabled: Boolean = true) =
        PlanItem(id, null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.AsNeeded, enabled = enabled)

    private fun plan(on: Int, off: Int = 0) = (1..on).map { item("on$it") } + (1..off).map { item("off$it", enabled = false) }

    @Test
    fun limitAccessors() {
        assertEquals(14, Resolved.Limited(Limit.Days(14)).maxDays)
        assertEquals(0, Resolved.Limited(Limit.Off).maxDays)
        assertNull(Resolved.Unlocked.maxDays)
        assertNull(Resolved.TrialActive(Instant.EPOCH).maxDays)
        assertNull(Resolved.Limited(Limit.Count(1)).maxDays)
        assertEquals(1, Resolved.Limited(Limit.Count(1)).maxCount)
        assertEquals(0, Resolved.Limited(Limit.Off).maxCount)
        assertNull(Resolved.Unlocked.maxCount)
        assertTrue(Resolved.Unlocked.available)
        assertTrue(Resolved.TrialActive(Instant.EPOCH).available)
        assertFalse(Resolved.Limited(Limit.Off).available)
    }

    @Test
    fun trialEndIsTheEarliestRunningTrial() {
        val a = Instant.parse("2026-10-10T00:00:00Z")
        val b = Instant.parse("2026-10-12T00:00:00Z")
        assertEquals(a, trialEndsAt(listOf(Resolved.Unlocked, Resolved.TrialActive(b), Resolved.TrialActive(a))))
        assertNull(trialEndsAt(listOf(Resolved.Unlocked, Resolved.Limited(Limit.Off))))
    }

    @Test
    fun activeMeansSwitchedOn() {
        assertEquals(3, ActivePlanItems.count(plan(on = 3, off = 2)))
        assertEquals(2, ActivePlanItems.count(plan(on = 3, off = 2), excludingId = "on1"))
        assertEquals(3, ActivePlanItems.count(plan(on = 3, off = 2), excludingId = "off1"))
    }

    @Test
    fun aSixthActiveItemIsBlockedButPausedOnesAreNot() {
        val five = plan(on = 5, off = 1)
        assertFalse(ActivePlanItems.maySave(five, item("new"), fiveFree))
        assertTrue(ActivePlanItems.maySave(five, item("new", enabled = false), fiveFree))
        // Switching the paused one on would be the sixth.
        assertFalse(ActivePlanItems.maySave(five, item("off1"), fiveFree))
        assertTrue(ActivePlanItems.maySave(plan(on = 4, off = 1), item("off1"), fiveFree))
        assertTrue(ActivePlanItems.maySave(five, item("new"), Resolved.Unlocked))
    }

    @Test
    fun itemsAlreadyOnAreNeverBlocked() {
        // A restored plan with more items on than the free amount: editing or pausing them is fine, a new one is not.
        val eight = plan(on = 8)
        assertTrue(ActivePlanItems.maySave(eight, item("on3"), fiveFree))
        assertTrue(ActivePlanItems.maySave(eight, item("on3", enabled = false), fiveFree))
        assertFalse(ActivePlanItems.maySave(eight, item("ninth"), fiveFree))
        assertTrue(ActivePlanItems.switchesOn(null, item("x")))
        assertTrue(ActivePlanItems.switchesOn(item("x", enabled = false), item("x")))
        assertFalse(ActivePlanItems.switchesOn(item("x"), item("x")))
    }
}
