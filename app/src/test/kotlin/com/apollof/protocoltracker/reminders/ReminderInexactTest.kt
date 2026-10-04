package com.apollof.protocoltracker.reminders

import android.app.AlarmManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The play build has no USE_EXACT_ALARM, so a user may not grant exact alarms. Reminders are then still set, as a
 * 10-minute window from the dose time, never dropped.
 */
@RunWith(AndroidJUnit4::class)
class ReminderInexactTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
    private val container get() = app.container
    private val zone = ZoneId.systemDefault()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before
    fun seed(): Unit = runBlocking {
        container.repository.seedPresets()
        val time = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES).atZone(zone).toLocalTime()
        container.repository.saveItem(
            PlanItem(
                "anavar", null, "preset:oxandrolone", Amount(20.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perTablet = 10.0),
                Schedule.Daily(listOf(Timing.At(time))), startDate = LocalDate.now(zone).minusDays(1),
            ),
        )
    }

    @After
    fun reset() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @Test
    fun withoutExactAlarmsRemindersUseAWindow(): Unit = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(container.reminders.canScheduleExact())
        container.reminders.resync()
        val set = alarms.scheduledAlarms
        assertTrue(set.isNotEmpty(), "no reminder alarm set")
        set.forEach { assertEquals(Duration.ofMinutes(10).toMillis(), it.windowLengthMs) }
    }

    @Test
    fun withExactAlarmsRemindersAreExact(): Unit = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        container.reminders.resync()
        val set = alarms.scheduledAlarms
        assertTrue(set.isNotEmpty(), "no reminder alarm set")
        set.forEach { assertEquals(ShadowAlarmManager.WINDOW_EXACT, it.windowLengthMs) }
    }
}
