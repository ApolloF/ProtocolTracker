package com.apollof.protocoltracker.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatestTakenTest {
    private val snapshot = DoseSnapshot("Anastrozole", "anastrozole", CompoundCategory.SUPPORT, BaseUnit.MG, null)

    private fun log(id: String, compound: String, at: String, status: LogStatus = LogStatus.TAKEN) = DoseLog(
        id, null, compound, null, null, Instant.parse(at), Amount(0.5, DoseUnit.MG), status = status, snapshot = snapshot, createdAt = Instant.parse(at),
    )

    @Test
    fun theNewestTakenDoseOfThatCompound() {
        val logs = listOf(
            log("a", "ai", "2026-09-20T08:00:00Z"),
            log("b", "ai", "2026-09-28T08:00:00Z", LogStatus.SKIPPED),
            log("c", "ai", "2026-09-25T08:00:00Z"),
            log("d", "other", "2026-09-30T08:00:00Z"),
        )
        assertEquals("c", logs.latestTaken("ai")?.id)
        assertNull(logs.latestTaken("none"))
    }

    @Test
    fun theShortNameDropsTheScientificPart() {
        assertEquals("Anavar", snapshot.copy(displayName = displayName("Anavar", "oxandrolone")).shortName("Anavar"))
        assertEquals("Tirzepatide", snapshot.copy(displayName = displayName("", "tirzepatide")).shortName(""))
        // A scientific name with brackets, or a compound renamed since, keeps the logged name.
        assertEquals("Semaglutide (oral)", snapshot.copy(displayName = displayName("", "semaglutide (oral)")).shortName(""))
        assertEquals("Anavar (oxandrolone)", snapshot.copy(displayName = "Anavar (oxandrolone)").shortName("Var"))
    }

    @Test
    fun aSkipSitsAtItsPlannedTimeAndKeepsNoSite() {
        val planned = Instant.parse("2026-09-24T08:00:00Z")
        val skip = log("s", "ai", "2026-09-25T09:00:00Z", LogStatus.SKIPPED).copy(scheduledAt = planned, site = "R_DELT")
        assertEquals(planned, skip.shownAt)
        val stored = skip.normalizedForWrite()
        assertEquals(planned, stored.takenAt)
        assertNull(stored.site)
        val taken = log("t", "ai", "2026-09-25T09:00:00Z").copy(scheduledAt = planned, site = "R_DELT")
        assertEquals(taken, taken.normalizedForWrite())
        assertEquals(Instant.parse("2026-09-25T09:00:00Z"), taken.shownAt)
        // An unscheduled skip has no planned time: it stays where it was.
        assertEquals(Instant.parse("2026-09-25T09:00:00Z"), log("u", "ai", "2026-09-25T09:00:00Z", LogStatus.SKIPPED).shownAt)
    }
}
