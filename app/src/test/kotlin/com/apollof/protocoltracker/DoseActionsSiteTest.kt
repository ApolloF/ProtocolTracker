package com.apollof.protocoltracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.occurrences
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sites through the logging actions shared by Today, notifications and the widget. */
@RunWith(AndroidJUnit4::class)
class DoseActionsSiteTest {
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val zone = ZoneId.systemDefault()

    /** A daily injectable over the last three days; returns its three occurrences. */
    private suspend fun seedDaily(): List<Occurrence> {
        container.repository.seedPresets()
        val start = LocalDate.now(zone).minusDays(3)
        val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
        container.repository.saveItem(PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = start))
        val protocol = container.repository.protocolNow()
        return occurrences(
            protocol.phases, protocol.items, start.atStartOfDay(zone).toInstant(), start.plusDays(3).atStartOfDay(zone).toInstant(), zone,
            IntervalAnchors.NONE, container.settings.current().slotTimes,
        )
    }

    @Test
    fun takeKeysStoresTheShownSitesAndNeverOverwrites(): Unit = runBlocking {
        val (a, b, c) = seedDaily()
        val actions = container.doseActions
        assertEquals(listOf("vg_r", null), actions.takeKeys(listOf(a.key, b.key), mapOf(a.key to "vg_r")).map { it.site })
        assertTrue(actions.takeKeys(listOf(a.key), mapOf(a.key to "delt_l")).isEmpty())
        actions.takeKeys(listOf(c.key))
        val stored = container.repository.allLogsNow().associate { it.occurrenceKey to it.site }
        assertEquals(mapOf<String?, String?>(a.key to "vg_r", b.key to null, c.key to null), stored)
    }

    @Test
    fun takeKeepsTheSiteUnlessSet(): Unit = runBlocking {
        val occ = seedDaily().first()
        val actions = container.doseActions
        assertEquals("delt_l", actions.take(occ, site = SiteWrite.Set("delt_l")).site)
        assertEquals("delt_l", actions.take(occ, note = "later").site)
        assertNull(actions.take(occ, site = SiteWrite.Set(null)).site)
    }
}
