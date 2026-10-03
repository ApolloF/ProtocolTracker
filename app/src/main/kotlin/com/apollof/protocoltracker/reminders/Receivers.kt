package com.apollof.protocoltracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.container
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.SiteRotation
import com.apollof.protocoltracker.domain.schedule.agendaLogsFrom
import com.apollof.protocoltracker.domain.schedule.AgendaWindows
import com.apollof.protocoltracker.domain.schedule.buildAgenda
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.runAsync(tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } catch (e: Exception) { Log.e(tag, "Broadcast handling failed", e) } finally { pending.finish() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync("AlarmReceiver") { handle(context, intent) }

    companion object {
        const val ACTION_DOSE = "com.apollof.protocoltracker.DOSE"
        const val ACTION_SNOOZED = "com.apollof.protocoltracker.SNOOZED"
        const val ACTION_SUMMARY = "com.apollof.protocoltracker.SUMMARY"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_KEYS = "keys"

        /** The work of [onReceive], run directly by tests. */
        internal suspend fun handle(context: Context, intent: Intent) {
            val c = context.container
            val protocol = c.repository.protocolNow()
            val now = c.clock()
            val prefs = c.settings.current()
            val confirmed = c.repository.logsSinceNow(now.minus(AgendaWindows.missedLookback)).mapNotNullTo(HashSet()) { it.occurrenceKey }
            when (intent.action) {
                ACTION_DOSE, ACTION_SNOOZED -> {
                    val slot = Instant.ofEpochSecond(intent.getLongExtra(EXTRA_SLOT, now.epochSecond))
                    val keys = intent.getStringArrayExtra(EXTRA_KEYS)?.toSet()
                    if (intent.action == ACTION_SNOOZED && !prefs.doseReminders) return
                    val due = if (keys != null) {
                        keys.mapNotNull { c.doseActions.findOccurrence(it) }
                    } else {
                        // Alarms can fire late (doze, reboot); include every reminder time missed since this one.
                        val until = maxOf(slot, now).plusSeconds(1)
                        occurrences(protocol.phases, protocol.items, slot.minusSeconds(86_400), until, c.zone(), c.repository.anchorsNow(), prefs.slotTimes)
                            .filter { o -> o.remindAt?.let { it >= slot && it < until } == true }
                    }.filter { it.key !in confirmed }
                    val postedAt = if (intent.action == ACTION_SNOOZED) due.mapNotNull { it.remindAt }.minOrNull() ?: slot else slot
                    // Dev: each injectable's suggested site on its latest line; a snoozed reminder recomputes it.
                    val sites = if (!BuildConfig.DEV_FEATURES || due.isEmpty()) emptyMap() else SiteRotation.latestDoses(
                        due.sortedBy { it.at }.mapNotNull { o -> protocol.compounds[o.item.compoundId]?.takeIf { it.route == Route.INJECTION }?.let { o.key to it.id } },
                        c.repository.allLogsNow(),
                    )
                    Notifications.showDoses(context, postedAt, due, protocol.compounds, c.zone(), sites)
                }
                ACTION_SUMMARY -> {
                    val logs = c.repository.logsSinceNow(agendaLogsFrom(now, c.zone(), prefs.slotTimes))
                    val agenda = buildAgenda(protocol.phases, protocol.items, logs, now, c.zone(), c.repository.anchorsNow(), prefs.slotTimes)
                    val lines = agenda.groups.flatMap { group ->
                        group.pending.mapNotNull { e ->
                            val occ = e.occurrence ?: return@mapNotNull null
                            val compound = protocol.compounds[occ.item.compoundId] ?: return@mapNotNull null
                            "${group.label}: ${compound.displayName} · ${describeDose(occ.dose, compound.baseUnit, occ.item.formulation)}"
                        }
                    }
                    Notifications.showSummary(context, lines)
                }
            }
            if (intent.action != ACTION_SNOOZED) c.reminders.resync()
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync("NotificationAction") { handle(context, intent) }

    companion object {
        const val ACTION_TAKE = "com.apollof.protocoltracker.TAKE"
        const val ACTION_SKIP = "com.apollof.protocoltracker.SKIP"
        const val ACTION_SNOOZE = "com.apollof.protocoltracker.SNOOZE"
        const val EXTRA_KEYS = "keys"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        /** Taken only: the site each line showed, aligned with [EXTRA_KEYS] (blank = none); absent when no line had one. */
        const val EXTRA_SITES = "sites"

        /** The work of [onReceive], run directly by tests. */
        internal suspend fun handle(context: Context, intent: Intent) {
            val c = context.container
            val keys = intent.getStringArrayExtra(EXTRA_KEYS)?.toList().orEmpty()
            Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            when (intent.action) {
                ACTION_TAKE -> {
                    val sites = keys.zip(intent.getStringArrayExtra(EXTRA_SITES).orEmpty()).filter { it.second.isNotBlank() }.toMap()
                    c.doseActions.takeKeys(keys, sites)
                }
                ACTION_SKIP -> c.doseActions.skipKeys(keys).takeIf { it.isNotEmpty() }?.let { Notifications.showSkipped(context, it.size) }
                ACTION_SNOOZE -> c.reminders.snooze(keys, c.settings.current().snoozeMinutes)
            }
            TodayWidget.refresh(context)
        }
    }
}

/** Boot, app update, clock/time-zone changes and exact-alarm permission changes all invalidate alarms. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        runAsync("SystemEvent") {
            context.container.reminders.resync()
            TodayWidget.refresh(context)
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
