package com.apollof.protocoltracker.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.occurrences
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** A reminder's Skip: "Skip all" when it holds several doses; skips sit at their planned time and a short note confirms them. */
@RunWith(AndroidJUnit4::class)
class ReminderSkipTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
    private val container get() = app.container
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    @Before
    fun seed(): Unit = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.repository.seedPresets()
    }

    /** Test C every morning since yesterday: yesterday's and today's dose. */
    private suspend fun plan(): List<Occurrence> {
        val start = today.minusDays(1)
        val item = PlanItem(
            "test-item", null, "preset:test-cyp", Amount(700.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0),
            Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING))), startDate = start,
        )
        container.repository.saveItem(item)
        val protocol = container.repository.protocolNow()
        return occurrences(
            protocol.phases, listOf(item), start.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant(), zone,
            IntervalAnchors.NONE, container.settings.current().slotTimes,
        )
    }

    private val manager get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private suspend fun remind(vararg due: Occurrence): Notification {
        val intent = Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DOSE)
            .putExtra(AlarmReceiver.EXTRA_SLOT, due.last().at.epochSecond)
            .putExtra(AlarmReceiver.EXTRA_KEYS, due.map { it.key }.toTypedArray())
        AlarmReceiver.handle(app, intent)
        return manager.allNotifications.single()
    }

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.skip() = actions.single { it.title.startsWith("Skip") }

    @Test
    fun oneDoseSaysSkip(): Unit = runBlocking {
        assertEquals("Skip", remind(plan().last()).skip().title)
    }

    @Test
    fun skipAllSkipsEachDoseAtItsPlannedTimeAndConfirms(): Unit = runBlocking {
        val due = plan()
        val skip = remind(*due.toTypedArray()).skip()
        assertEquals("Skip all", skip.title)

        NotificationActionReceiver.handle(app, shadowOf(skip.actionIntent).savedIntent)
        val logs = container.repository.allLogsNow().sortedBy { it.takenAt }
        assertEquals(due.map { it.key }, logs.map { it.occurrenceKey })
        logs.forEach { log ->
            assertEquals(LogStatus.SKIPPED, log.status)
            assertEquals(log.scheduledAt, log.takenAt)
            assertNull(log.site)
        }
        assertEquals(listOf("2 doses skipped"), manager.allNotifications.map { it.title })
    }
}
