package com.apollof.protocoltracker.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A reminder that fell due while the phone was off (alarms do not survive a reboot) or that a clock jump skipped is
 * posted once on boot or the time change, and never again (review 2026-10, M1).
 */
@RunWith(AndroidJUnit4::class)
class ReminderCatchUpTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
    private val container get() = app.container
    private val zone = ZoneId.systemDefault()
    private val manager get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val now = Instant.now()
    /** The dose's reminder time: an hour ago, to the minute. */
    private val due = now.minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES)

    @Before
    fun seed(): Unit = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.repository.seedPresets()
        val time = due.atZone(zone).toLocalTime()
        container.repository.saveItem(
            PlanItem(
                "anavar", null, "preset:oxandrolone", Amount(20.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perTablet = 10.0),
                Schedule.Daily(listOf(Timing.At(time))), startDate = LocalDate.now(zone).minusDays(1),
            ),
        )
    }

    /** Reminders were last handled two hours ago; then the phone went off. */
    private fun handledUntil(at: Instant) {
        app.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().putLong("posted_through_ms", at.toEpochMilli()).commit()
    }

    private suspend fun system(action: String) = SystemEventReceiver.handle(app, Intent(action))

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test
    fun aReminderMissedWhileThePhoneWasOffIsPostedOnceOnBoot(): Unit = runBlocking {
        handledUntil(now.minus(Duration.ofHours(2)))
        system(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(1, manager.allNotifications.size)
        assertTrue(manager.allNotifications.single().title.contains("Anavar"))

        // Nothing posts it again: not a second boot, a clock change or the old alarm firing late.
        app.getSystemService(NotificationManager::class.java).cancelAll()
        system(Intent.ACTION_BOOT_COMPLETED)
        system(Intent.ACTION_TIME_CHANGED)
        container.reminders.postDue(due)
        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun aClockJumpPastTheReminderPostsIt(): Unit = runBlocking {
        handledUntil(now.minus(Duration.ofHours(2)))
        system(Intent.ACTION_TIME_CHANGED)
        assertEquals(1, manager.allNotifications.size)
    }

    @Test
    fun aReminderAtOrBeforeTheMarkIsNotPostedAgain(): Unit = runBlocking {
        handledUntil(due)
        system(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(0, manager.allNotifications.size)
    }

    @Test
    fun anAppUpdateDoesNotCatchUp(): Unit = runBlocking {
        handledUntil(now.minus(Duration.ofHours(2)))
        system(Intent.ACTION_MY_PACKAGE_REPLACED)
        assertEquals(0, manager.allNotifications.size)
    }
}
