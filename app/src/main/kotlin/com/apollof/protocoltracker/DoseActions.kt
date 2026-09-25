package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.data.CheckTime
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.OccurrenceRef
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.schedule.parseOccurrenceKey
import com.apollof.protocoltracker.reminders.Notifications
import java.time.Instant
import java.time.ZoneId

/**
 * Logging actions shared by the Today screen, notification buttons and the widget,
 * so a check means the same thing everywhere.
 */
class DoseActions(
    private val context: Context,
    private val repository: TrackerRepository,
    private val settings: SettingsStore,
    private val clock: () -> Instant,
    private val zone: () -> ZoneId,
) {
    /** Re-derives an occurrence from its key; null if the plan no longer schedules it. */
    suspend fun findOccurrence(key: String): Occurrence? {
        val ref = parseOccurrenceKey(key) ?: return null
        val protocol = repository.protocolNow()
        val item = protocol.items.firstOrNull { it.id == ref.itemId } ?: return null
        val slotTimes = settings.current().slotTimes
        val (from, to) = when (ref) {
            is OccurrenceRef.Timed -> ref.at to ref.at.plusSeconds(1)
            is OccurrenceRef.Slotted -> ref.date.atStartOfDay(zone()).toInstant() to ref.date.plusDays(1).atStartOfDay(zone()).toInstant()
        }
        return occurrences(protocol.phases, listOf(item), from, to, zone(), slotTimes).firstOrNull { it.key == key }
    }

    /**
     * Time recorded by a one-tap check: part-of-day doses taken today log the current time, earlier ones their
     * nominal time; exact-time doses follow the "check records" setting.
     */
    suspend fun defaultTakenAt(occurrence: Occurrence): Instant {
        val now = clock()
        if (occurrence.timing is Timing.Slot) {
            return if (occurrence.localDate == now.atZone(zone()).toLocalDate()) now else occurrence.at
        }
        return when (settings.current().checkTime) {
            CheckTime.SCHEDULED -> occurrence.at
            CheckTime.NOW -> now
        }
    }

    suspend fun take(occurrence: Occurrence, takenAt: Instant? = null, amount: Amount? = null, note: String = ""): DoseLog {
        val time = takenAt ?: defaultTakenAt(occurrence)
        return repository.logOccurrence(occurrence, LogStatus.TAKEN, takenAt = time, amount = amount ?: occurrence.dose, note = note)
            .also { clearNotification(occurrence) }
    }

    suspend fun skip(occurrence: Occurrence, note: String = ""): DoseLog =
        repository.logOccurrence(occurrence, LogStatus.SKIPPED, takenAt = clock(), note = note).also { clearNotification(occurrence) }

    /** Dismisses the reminder for this reminder time once nothing in it is left unconfirmed. */
    private suspend fun clearNotification(occurrence: Occurrence) {
        val remindAt = occurrence.remindAt ?: return
        val protocol = repository.protocolNow()
        val slot = occurrences(protocol.phases, protocol.items, remindAt.minusSeconds(86_400), remindAt.plusSeconds(1), zone(), settings.current().slotTimes)
            .filter { it.remindAt == remindAt }
        val confirmed = repository.logsSinceNow(remindAt.minusSeconds(2 * 86_400)).mapNotNullTo(HashSet()) { it.occurrenceKey }
        if (slot.all { it.key in confirmed }) Notifications.cancel(context, Notifications.slotId(remindAt))
    }

    /**
     * Notification/widget actions: log each key that still resolves and is not yet confirmed.
     * Keys confirmed elsewhere in the meantime are left untouched.
     */
    suspend fun takeKeys(keys: Collection<String>): List<DoseLog> = keys.mapNotNull { key ->
        val occ = findOccurrence(key) ?: return@mapNotNull null
        repository.logOccurrenceIfAbsent(occ, LogStatus.TAKEN, defaultTakenAt(occ))?.also { clearNotification(occ) }
    }

    suspend fun skipKeys(keys: Collection<String>): List<DoseLog> = keys.mapNotNull { key ->
        val occ = findOccurrence(key) ?: return@mapNotNull null
        repository.logOccurrenceIfAbsent(occ, LogStatus.SKIPPED, clock())?.also { clearNotification(occ) }
    }

    suspend fun undo(logs: Collection<DoseLog>) = logs.forEach { repository.deleteLog(it.id) }
}
