package com.apollof.protocoltracker.ui.journal

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.occurrences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MISS-1 in Journal: dev adherence counts from the first dose log; stable from 30 days back. */
@RunWith(AndroidJUnit4::class)
class JournalAdherenceTest {
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Test
    fun monthAdherenceStartsAtTheFirstLog(): Unit = runBlocking {
        val zone = ZoneId.systemDefault()
        container.repository.seedPresets()
        val item = PlanItem(
            "av", null, "preset:oxandrolone", Amount(20.0, DoseUnit.MG),
            schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING))), startDate = LocalDate.now().minusDays(20),
        )
        container.repository.saveItem(item)
        // The plan ran 20 days; the first log is three days ago.
        val day = LocalDate.now().minusDays(3)
        val occ = occurrences(emptyList(), listOf(item), day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(), zone, IntervalAnchors.NONE).single()
        container.repository.logOccurrence(occ, LogStatus.TAKEN, occ.at)
        val vm = JournalViewModel(container)
        val state = withTimeout(30_000) { vm.state.first { !it.loading && it.adherence.isNotEmpty() } }
        val month = state.adherence.single().month
        val scheduled = Regex("""/(\d+)\)""").find(month)!!.groupValues[1].toInt()
        if (BuildConfig.DEV_FEATURES) assertTrue(scheduled in 3..4, "dev counts from three days ago: $month") else assertTrue(scheduled >= 19, "stable counts the month: $month")
        assertEquals(true, month.contains("(1/"))
    }
}
