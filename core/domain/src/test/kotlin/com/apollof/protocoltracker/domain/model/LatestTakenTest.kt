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
}
