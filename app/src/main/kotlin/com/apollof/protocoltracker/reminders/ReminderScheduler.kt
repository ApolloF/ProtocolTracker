package com.apollof.protocoltracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.schedule.AgendaWindows
import com.apollof.protocoltracker.domain.schedule.nextReminderSlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps exactly one pending alarm for the next dose time slot and one for the daily summary.
 * Idempotent: call [resync] after any data/settings change, boot, time change or alarm fire.
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: TrackerRepository,
    private val settings: SettingsStore,
    private val clock: () -> Instant,
    private val zone: () -> ZoneId,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    // Receivers, the app-wide observer and the worker can resync concurrently; the last read must win.
    private val lock = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    suspend fun resync() = lock.withLock {
        val prefs = settings.current()
        val now = clock()
        if (prefs.doseReminders) {
            val protocol = repository.protocolNow()
            val confirmed = repository.logsSinceNow(now.minus(AgendaWindows.missedLookback))
                .mapNotNullTo(HashSet()) { it.occurrenceKey }
            val slot = nextReminderSlot(protocol.phases, protocol.items, confirmed, now, zone(), repository.anchorsNow(), prefs.slotTimes)
            if (slot != null) set(slot.first, doseIntent(slot.first)) else cancel(doseIntent(Instant.EPOCH))
        } else {
            cancel(doseIntent(Instant.EPOCH))
        }
        if (prefs.dailySummary) {
            val today = LocalDate.now(zone())
            var next = today.atTime(prefs.dailySummaryTime).atZone(zone()).toInstant()
            if (!next.isAfter(now)) next = today.plusDays(1).atTime(prefs.dailySummaryTime).atZone(zone()).toInstant()
            set(next, summaryIntent())
        } else {
            cancel(summaryIntent())
        }
    }

    fun snooze(keys: List<String>, minutes: Int) {
        val at = clock().plusSeconds(minutes * 60L)
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_SNOOZED)
            .putExtra(AlarmReceiver.EXTRA_KEYS, keys.toTypedArray())
            .putExtra(AlarmReceiver.EXTRA_SLOT, at.epochSecond)
        set(at, PendingIntent.getBroadcast(context, keys.hashCode(), intent, FLAGS))
    }

    private fun doseIntent(slot: Instant): PendingIntent {
        // One request code: setting a new slot replaces the previous alarm.
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_DOSE)
            .putExtra(AlarmReceiver.EXTRA_SLOT, slot.epochSecond)
        return PendingIntent.getBroadcast(context, REQUEST_DOSE, intent, FLAGS)
    }

    private fun summaryIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SUMMARY)
        return PendingIntent.getBroadcast(context, REQUEST_SUMMARY, intent, FLAGS)
    }

    private fun set(at: Instant, operation: PendingIntent) {
        val ms = at.toEpochMilli()
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, operation)
        } else {
            // Without exact-alarm access, deliver within 10 minutes of the dose time.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60_000L, operation)
        }
    }

    private fun cancel(operation: PendingIntent) = alarms.cancel(operation)

    private companion object {
        const val REQUEST_DOSE = 1
        const val REQUEST_SUMMARY = 2
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
