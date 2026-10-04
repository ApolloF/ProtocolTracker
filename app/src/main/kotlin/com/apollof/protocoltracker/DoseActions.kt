package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.data.CheckTime
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.OccurrenceRef
import com.apollof.protocoltracker.domain.schedule.dateOf
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.schedule.parseOccurrenceKey
import com.apollof.protocoltracker.reminders.Notifications
import java.time.Instant
import java.time.LocalDate
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
    /**
     * Re-derives an occurrence from its key; null if the plan no longer schedules it. An instant key from a reminder
     * posted by an earlier version finds the exact-time dose planned at that instant.
     */
    suspend fun findOccurrence(key: String): Occurrence? {
        val ref = parseOccurrenceKey(key) ?: return null
        val protocol = repository.protocolNow()
        val item = protocol.items.firstOrNull { it.id == ref.itemId } ?: return null
        val slotTimes = settings.current().slotTimes
        fun day(date: LocalDate) = date.atStartOfDay(zone()).toInstant() to date.plusDays(1).atStartOfDay(zone()).toInstant()
        val (from, to) = when (ref) {
            is OccurrenceRef.Timed -> ref.at to ref.at.plusSeconds(1)
            is OccurrenceRef.Slotted -> day(ref.date)
            is OccurrenceRef.AtTime -> day(ref.date)
        }
        return occurrences(protocol.phases, listOf(item), from, to, zone(), repository.anchorsNow(), slotTimes)
            .firstOrNull { it.key == key || (ref is OccurrenceRef.Timed && it.at == ref.at && it.timing is Timing.At) }
    }

    /**
     * Time recorded by a one-tap check: part-of-day doses taken today (by the day start, so 1:00 still counts for
     * yesterday's evening dose) log the current time, earlier ones their nominal time; exact-time doses follow the
     * "check records" setting.
     */
    suspend fun defaultTakenAt(occurrence: Occurrence): Instant {
        val now = clock()
        val settings = settings.current()
        if (occurrence.timing is Timing.Slot) {
            return if (occurrence.localDate == settings.slotTimes.dateOf(now, zone())) now else occurrence.at
        }
        return when (settings.checkTime) {
            CheckTime.SCHEDULED -> occurrence.at
            CheckTime.NOW -> now
        }
    }

    suspend fun take(
        occurrence: Occurrence,
        takenAt: Instant? = null,
        amount: Amount? = null,
        note: String = "",
        site: SiteWrite = SiteWrite.Keep,
    ): DoseLog {
        val time = takenAt ?: defaultTakenAt(occurrence)
        return repository.logOccurrence(occurrence, LogStatus.TAKEN, takenAt = time, amount = amount ?: occurrence.dose, note = note, site = site)
            .also { clearNotification(occurrence) }
    }

    /** A skip sits at the dose's planned time, so it stays on the day it was planned. */
    suspend fun skip(occurrence: Occurrence, note: String = ""): DoseLog =
        repository.logOccurrence(occurrence, LogStatus.SKIPPED, takenAt = occurrence.at, note = note).also { clearNotification(occurrence) }

    /** Dismisses the reminder for this reminder time once nothing in it is left unconfirmed. */
    private suspend fun clearNotification(occurrence: Occurrence) {
        val remindAt = occurrence.remindAt ?: return
        val protocol = repository.protocolNow()
        val slot = occurrences(protocol.phases, protocol.items, remindAt.minusSeconds(86_400), remindAt.plusSeconds(1), zone(), repository.anchorsNow(), settings.current().slotTimes)
            .filter { it.remindAt == remindAt }
        val confirmed = repository.logsSinceNow(remindAt.minusSeconds(2 * 86_400)).mapNotNullTo(HashSet()) { it.occurrenceKey }
        if (slot.all { it.key in confirmed }) Notifications.cancel(context, Notifications.slotId(remindAt))
    }

    /**
     * Notification/widget actions: log each key that still resolves and is not yet confirmed.
     * Keys confirmed elsewhere in the meantime are left untouched. [sites] maps a key to the site the action showed.
     */
    suspend fun takeKeys(keys: Collection<String>, sites: Map<String, String> = emptyMap()): List<DoseLog> = keys.mapNotNull { key ->
        val occ = findOccurrence(key) ?: return@mapNotNull null
        repository.logOccurrenceIfAbsent(occ, LogStatus.TAKEN, defaultTakenAt(occ), sites[key])?.also { clearNotification(occ) }
    }

    suspend fun skipKeys(keys: Collection<String>): List<DoseLog> = keys.mapNotNull { key ->
        val occ = findOccurrence(key) ?: return@mapNotNull null
        repository.logOccurrenceIfAbsent(occ, LogStatus.SKIPPED, occ.at)?.also { clearNotification(occ) }
    }

    suspend fun undo(logs: Collection<DoseLog>) = logs.forEach { repository.deleteLog(it.id) }
}
