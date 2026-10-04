package com.apollof.protocoltracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.SiteRotation
import com.apollof.protocoltracker.domain.schedule.AgendaWindows
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.dueReminders
import com.apollof.protocoltracker.domain.schedule.nextReminderSlot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps exactly one pending alarm for the next dose time slot and one for the daily summary.
 * Idempotent: call [resync] after any data/settings change, boot, time change or alarm fire.
 *
 * Alarms are lost when the phone is off and skipped when the clock jumps forward, so a mark records up to when
 * reminders were handled: after boot or a clock change, [resync] with `catchUp` posts the ones due since the mark (at
 * most [CATCH_UP] back), and an alarm never posts a reminder at or before the mark again.
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: TrackerRepository,
    private val settings: SettingsStore,
    private val clock: () -> Instant,
    private val zone: () -> ZoneId,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val marks = context.getSharedPreferences(MARKS_FILE, Context.MODE_PRIVATE)
    // Receivers, the app-wide observer and the worker can resync concurrently; the last read must win.
    private val lock = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** [catchUp]: after boot or a clock or zone change, first post the reminders that fell due since the mark. */
    suspend fun resync(catchUp: Boolean = false) = lock.withLock {
        val prefs = settings.current()
        val now = clock()
        if (prefs.doseReminders) {
            val protocol = repository.protocolNow()
            val confirmed = confirmedKeys(now)
            val mark = postedThrough
            if (mark == null) {
                postedThrough = now
            } else if (catchUp) {
                // A clock set back leaves the mark ahead of now; it moves back so later reminders still post.
                val from = maxOf(minOf(mark, now), now.minus(CATCH_UP))
                val due = dueReminders(protocol.phases, protocol.items, confirmed, from, now, zone(), repository.anchorsNow(), prefs.slotTimes)
                show(due, due.firstOrNull()?.remindAt ?: now)
                postedThrough = now
            }
            val slot = nextReminderSlot(protocol.phases, protocol.items, confirmed, now, zone(), repository.anchorsNow(), prefs.slotTimes)
            if (slot != null) set(slot.first, doseIntent(slot.first)) else cancel(doseIntent(Instant.EPOCH))
        } else {
            cancel(doseIntent(Instant.EPOCH))
            postedThrough = now
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

    /**
     * The alarm for [slot] fired, possibly late (doze): posts every unconfirmed reminder from [slot] to now that is
     * after the mark, then moves the mark to now.
     */
    suspend fun postDue(slot: Instant) = lock.withLock {
        val prefs = settings.current()
        if (!prefs.doseReminders) return@withLock
        val now = clock()
        val protocol = repository.protocolNow()
        val from = maxOf(slot.minusNanos(1), postedThrough ?: Instant.MIN)
        val due = dueReminders(protocol.phases, protocol.items, confirmedKeys(now), from, maxOf(slot, now), zone(), repository.anchorsNow(), prefs.slotTimes)
        show(due, slot)
        postedThrough = maxOf(postedThrough ?: now, now)
    }

    /** Posts [due] as one notification for the reminder time [postedAt], each injectable with its suggested site. */
    suspend fun show(due: List<Occurrence>, postedAt: Instant) {
        if (due.isEmpty()) return
        val protocol = repository.protocolNow()
        // Each injectable's suggested site on its latest line; a snoozed reminder recomputes it.
        val sites = SiteRotation.latestDoses(
            due.sortedBy { it.at }.mapNotNull { o -> protocol.compounds[o.item.compoundId]?.takeIf { it.route == Route.INJECTION }?.let { o.key to it.id } },
            repository.allLogsNow(),
        )
        Notifications.showDoses(context, postedAt, due, protocol.compounds, zone(), sites)
    }

    private suspend fun confirmedKeys(now: Instant): Set<String> =
        repository.logsSinceNow(now.minus(AgendaWindows.missedLookback)).mapNotNullTo(HashSet()) { it.occurrenceKey }

    /** Reminder times up to this instant are handled; null until the first resync. */
    private var postedThrough: Instant?
        get() = marks.getLong(KEY_POSTED_THROUGH, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(Instant::ofEpochMilli)
        set(value) {
            // Committed at once: a receiver's process can end right after the broadcast.
            marks.edit().apply { if (value == null) remove(KEY_POSTED_THROUGH) else putLong(KEY_POSTED_THROUGH, value.toEpochMilli()) }.commit()
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

    companion object {
        /** How far back a catch-up looks: older reminders are only listed as missed on Today. */
        val CATCH_UP: Duration = Duration.ofHours(12)

        private const val MARKS_FILE = "reminders"
        private const val KEY_POSTED_THROUGH = "posted_through_ms"
        private const val REQUEST_DOSE = 1
        private const val REQUEST_SUMMARY = 2
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
