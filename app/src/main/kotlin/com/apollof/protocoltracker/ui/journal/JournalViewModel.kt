package com.apollof.protocoltracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.schedule.Adherence
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.adherence
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.UiMessage
import com.apollof.protocoltracker.ui.components.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class JournalFilter(val label: String) { ALL("All"), DOSES("Doses"), BLOOD_PRESSURE("Blood pressure"), NOTES("Notes") }

sealed interface JournalRow {
    val at: Instant
    val key: String

    data class Dose(val log: DoseLog, val detail: String, val time: String) : JournalRow {
        override val at: Instant get() = log.takenAt
        override val key: String get() = "d-${log.id}"
    }

    data class Entry(val entry: JournalEntry, val time: String) : JournalRow {
        override val at: Instant get() = entry.at
        override val key: String get() = "j-${entry.id}"
    }
}

data class JournalDay(val date: LocalDate, val label: String, val rows: List<JournalRow>)
data class AdherenceRow(val name: String, val week: String, val month: String)
data class CompoundFilter(val id: String, val name: String)
data class BpSummary(val latest: String, val latestWhen: String, val average7: String?, val readings7: Int)

data class JournalState(
    val loading: Boolean = true,
    val days: List<JournalDay> = emptyList(),
    val adherence: List<AdherenceRow> = emptyList(),
    val compounds: List<CompoundFilter> = emptyList(),
    val filter: JournalFilter = JournalFilter.ALL,
    val compound: String? = null,
    val bloodPressure: BpSummary? = null,
    val empty: Boolean = false,
)

class JournalViewModel(private val c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(JournalFilter.ALL)
    private val compound = MutableStateFlow<String?>(null)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private data class Selection(val filter: JournalFilter, val compound: String?)

    val state: StateFlow<JournalState> = combine(
        c.repository.allLogs, c.repository.journal, c.repository.protocol, combine(filter, compound, ::Selection), c.settings.settings,
    ) { logs, journal, protocol, sel, settings ->
        val zone = c.zone()
        val now = c.clock()
        val today = now.atZone(zone).toLocalDate()
        val doseRows = logs.filter { sel.compound == null || it.compoundId == sel.compound }.map { log ->
            val detail = when (log.status) {
                LogStatus.SKIPPED -> "Skipped"
                LogStatus.TAKEN -> describeDose(log.amount, log.snapshot.baseUnit, log.snapshot.formulation) +
                    (log.plannedAmount?.takeIf { log.adjusted }?.let { " (plan ${formatNumber(it.value, 2)} ${it.unit.label})" } ?: "")
            }
            JournalRow.Dose(log, detail, Formats.time(log.takenAt, zone))
        }
        val entryRows = journal.map { JournalRow.Entry(it, Formats.time(it.at, zone)) }
        val rows: List<JournalRow> = when (sel.filter) {
            JournalFilter.ALL -> if (sel.compound != null) doseRows else doseRows + entryRows
            JournalFilter.DOSES -> doseRows
            JournalFilter.BLOOD_PRESSURE -> entryRows.filter { it.entry is JournalEntry.BloodPressure }
            JournalFilter.NOTES -> entryRows.filter { it.entry is JournalEntry.Note }
        }
        val days = rows.sortedByDescending { it.at }.groupBy { it.at.atZone(zone).toLocalDate() }
            .map { (date, list) -> JournalDay(date, Formats.relativeDay(date, today), list) }

        fun ratio(a: Adherence?) = a?.ratio?.let { "${(it * 100).toInt()}% (${a.taken}/${a.scheduled})" } ?: "–"
        val anchors = IntervalAnchors.from(logs)
        fun adherenceSince(from: Instant) =
            adherence(protocol.phases, protocol.items, logs, from, now, now, zone, anchors, settings.slotTimes).associateBy { it.itemId }
        val week = adherenceSince(now.minus(Duration.ofDays(7)))
        val month = adherenceSince(now.minus(Duration.ofDays(30)))
        val adherenceRows = protocol.items.filter { it.id in month }.mapNotNull { item ->
            val compound = protocol.compounds[item.compoundId] ?: return@mapNotNull null
            AdherenceRow(compound.displayName, ratio(week[item.id]), ratio(month[item.id]))
        }

        val readings = journal.filterIsInstance<JournalEntry.BloodPressure>().sortedByDescending { it.at }
        val recent = readings.filter { it.at >= now.minus(Duration.ofDays(7)) }
        JournalState(
            loading = false,
            days = days,
            adherence = adherenceRows,
            compounds = logs.map { it.compoundId }.distinct().mapNotNull { id ->
                logs.first { it.compoundId == id }.snapshot.displayName.let { CompoundFilter(id, it) }
            }.sortedBy { it.name },
            filter = sel.filter,
            compound = sel.compound,
            bloodPressure = readings.firstOrNull()?.let { latest ->
                BpSummary(
                    latest = "${latest.systolic}/${latest.diastolic}",
                    latestWhen = "${Formats.relativeDay(latest.at.atZone(zone).toLocalDate(), today)} ${Formats.time(latest.at, zone)}",
                    average7 = recent.takeIf { it.size >= 2 }?.let { r -> "${Math.round(r.map { it.systolic }.average())}/${Math.round(r.map { it.diastolic }.average())}" },
                    readings7 = recent.size,
                )
            },
            empty = logs.isEmpty() && journal.isEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

    fun setFilter(f: JournalFilter) { filter.value = f; if (f != JournalFilter.ALL && f != JournalFilter.DOSES) compound.value = null }
    fun setCompound(id: String?) { compound.value = id; if (id != null) filter.value = JournalFilter.DOSES }

    fun update(log: DoseLog) = viewModelScope.launch { c.repository.updateLog(log) }

    fun deleteLog(log: DoseLog) = viewModelScope.launch {
        val removed = c.repository.deleteLog(log.id) ?: return@launch
        _messages.emit(UiMessage("Entry deleted") { c.repository.restoreLog(removed) })
    }

    fun saveEntry(entry: JournalEntry) = viewModelScope.launch { c.repository.saveJournal(entry) }

    fun newBloodPressure(systolic: Int, diastolic: Int, pulse: Int?, at: Instant, note: String, existing: JournalEntry.BloodPressure?) = saveEntry(
        JournalEntry.BloodPressure(existing?.id ?: TrackerRepository.newId(), at, systolic, diastolic, pulse, note.trim(), existing?.createdAt ?: c.clock()),
    )

    fun newNote(text: String, at: Instant, existing: JournalEntry.Note?) = saveEntry(
        JournalEntry.Note(existing?.id ?: TrackerRepository.newId(), at, text.trim(), existing?.createdAt ?: c.clock()),
    )

    fun deleteEntry(entry: JournalEntry) = viewModelScope.launch {
        val removed = c.repository.deleteJournal(entry.id) ?: return@launch
        _messages.emit(UiMessage("Entry deleted") { c.repository.saveJournal(removed) })
    }

    fun now(): Instant = c.clock()
    fun zone(): ZoneId = c.zone()
}
