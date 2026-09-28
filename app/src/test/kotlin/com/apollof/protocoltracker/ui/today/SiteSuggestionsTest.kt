package com.apollof.protocoltracker.ui.today

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Today's rows wait for the site history: before every dose log has loaded there are no suggestions, not empty ones. */
class SiteSuggestionsTest {
    private val snapshot = DoseSnapshot("Test C (testosterone cypionate)", "testosterone", CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null)
    private val at = Instant.parse("2026-09-25T08:00:00Z")
    private val pin = DoseLog("a", null, "tc", null, null, at, Amount(100.0, DoseUnit.MG), null, LogStatus.TAKEN, "", snapshot, at, site = "delt_l")

    @Test
    fun nothingUntilEveryLogHasLoaded(): Unit = runBlocking {
        val logs = MutableStateFlow<List<DoseLog>?>(null)
        val first = async { siteSuggestions(logs).first() }
        repeat(3) { yield() }
        assertFalse(first.isCompleted, "an empty map before loading would build rows without their site")
        logs.value = listOf(pin)
        assertEquals(mapOf("tc" to "delt_r"), first.await())
    }
}
