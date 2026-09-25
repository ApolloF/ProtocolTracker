package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.data.CheckTime
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.reminders.Notifications
import com.apollof.protocoltracker.domain.schedule.occurrences
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
    /** Re-derives an occurrence from its key (`itemId@epochSecond`); null if the plan no longer schedules it. */
    suspend fun findOccurrence(key: String): Occurrence? {
        val itemId = key.substringBeforeLast('@')
        val at = key.substringAfterLast('@').toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
        val protocol = repository.protocolNow()
        val item = protocol.items.firstOrNull { it.id == itemId } ?: return null
        return occurrences(protocol.phases, listOf(item), at, at.plusSeconds(1), zone()).firstOrNull { it.key == key }
    }

    suspend fun take(occurrence: Occurrence, takenAt: Instant? = null): DoseLog {
        val time = takenAt ?: when (settings.current().checkTime) {
            CheckTime.SCHEDULED -> occurrence.at
            CheckTime.NOW -> clock()
        }
        return repository.logOccurrence(occurrence, LogStatus.TAKEN, takenAt = time).also { clearNotification(occurrence) }
    }

    suspend fun skip(occurrence: Occurrence): DoseLog =
        repository.logOccurrence(occurrence, LogStatus.SKIPPED, takenAt = clock()).also { clearNotification(occurrence) }

    /** Dismisses the reminder for this time slot once nothing in it is left unconfirmed. */
    private suspend fun clearNotification(occurrence: Occurrence) {
        val protocol = repository.protocolNow()
        val slot = occurrences(protocol.phases, protocol.items, occurrence.at, occurrence.at.plusSeconds(1), zone())
        val confirmed = repository.logsSinceNow(occurrence.at.minusSeconds(1)).mapNotNullTo(HashSet()) { it.occurrenceKey }
        if (slot.all { it.key in confirmed }) Notifications.cancel(context, Notifications.slotId(occurrence.at))
    }

    /**
     * Notification/widget actions: log each key that still resolves and is not yet confirmed.
     * Keys confirmed elsewhere in the meantime are left untouched.
     */
    suspend fun takeKeys(keys: Collection<String>): List<DoseLog> {
        val checkTime = settings.current().checkTime
        return keys.mapNotNull { key ->
            val occ = findOccurrence(key) ?: return@mapNotNull null
            val time = if (checkTime == CheckTime.SCHEDULED) occ.at else clock()
            repository.logOccurrenceIfAbsent(occ, LogStatus.TAKEN, time)?.also { clearNotification(occ) }
        }
    }

    suspend fun skipKeys(keys: Collection<String>): List<DoseLog> = keys.mapNotNull { key ->
        val occ = findOccurrence(key) ?: return@mapNotNull null
        repository.logOccurrenceIfAbsent(occ, LogStatus.SKIPPED, clock())?.also { clearNotification(occ) }
    }

    suspend fun undo(logs: Collection<DoseLog>) = logs.forEach { repository.deleteLog(it.id) }
}
