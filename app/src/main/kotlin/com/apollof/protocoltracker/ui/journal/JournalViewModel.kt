package com.apollof.protocoltracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.BpWeek
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.MarkerSheetData
import com.apollof.protocoltracker.domain.model.MarkerTrend
import com.apollof.protocoltracker.domain.model.MoodPoint
import com.apollof.protocoltracker.domain.model.UnlistedTrend
import com.apollof.protocoltracker.domain.model.bloodPressureWeeks
import com.apollof.protocoltracker.domain.model.lastDrawAge
import com.apollof.protocoltracker.domain.model.markerSheetData
import com.apollof.protocoltracker.domain.model.markerTrends
import com.apollof.protocoltracker.domain.model.moodTrend
import com.apollof.protocoltracker.domain.model.measuredMarkers
import com.apollof.protocoltracker.domain.model.unlistedTrends
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.schedule.Adherence
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.adherence
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.UiMessage
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.health.BloodworkInput
import com.apollof.protocoltracker.ui.health.SymptomInput
import com.apollof.protocoltracker.ui.health.toEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Journal filters; [dev] ones exist only in dev builds (symptom logging and bloodwork). */
enum class JournalFilter(val label: String, val dev: Boolean = false) {
    ALL("All"), DOSES("Doses"), BLOOD_PRESSURE("Blood pressure"), NOTES("Notes"), SYMPTOMS("Symptoms", dev = true), BLOODWORK("Bloodwork", dev = true);

    companion object {
        val available: List<JournalFilter> get() = entries.filter { !it.dev || BuildConfig.DEV_FEATURES }
    }
}

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

/** [header] is the day's section label: stable "Today · 2026-09-24"; dev "Today", "Thu, Sep 24", or with the year when not this year. */
data class JournalDay(val date: LocalDate, val header: String, val rows: List<JournalRow>)

internal fun journalDayHeader(date: LocalDate, today: LocalDate): String = when {
    !BuildConfig.DEV_FEATURES -> "${Formats.relativeDay(date, today)} · $date"
    date.year != today.year -> date.format(Formats.dayYear)
    else -> Formats.relativeDay(date, today)
}
data class AdherenceRow(val name: String, val week: String, val month: String)
data class CompoundFilter(val id: String, val name: String)
data class BpSummary(val latest: String, val latestWhen: String, val average7: String?, val readings7: Int)

data class DoseEditorData(val compounds: List<Compound> = emptyList(), val logs: List<DoseLog> = emptyList())

data class JournalState(
    val loading: Boolean = true,
    val days: List<JournalDay> = emptyList(),
    val adherence: List<AdherenceRow> = emptyList(),
    val compounds: List<CompoundFilter> = emptyList(),
    val filter: JournalFilter = JournalFilter.ALL,
    val compound: String? = null,
    val bloodPressure: BpSummary? = null,
    /** 7-day blood pressure averages for the card's chart (dev builds, under the Blood pressure chip only). */
    val bpWeeks: List<BpWeek> = emptyList(),
    /** Mood ratings for the chart (dev builds, under the Symptoms chip only; empty below 2 days). */
    val mood: List<MoodPoint> = emptyList(),
    val empty: Boolean = false,
    /** Latest result per marker (dev builds). */
    val bloodwork: List<MarkerTrend> = emptyList(),
    /** Latest result per unlisted test (dev builds). */
    val unlisted: List<UnlistedTrend> = emptyList(),
    /** Marker keys with a result in any draw, for the Bloodwork sheet (dev builds). */
    val measured: Set<String> = emptySet(),
    /** "3 days ago" for the dev Bloodwork card; null in stable and without a past draw. */
    val lastDraw: String? = null,
    val labUnits: LabUnits = LabUnits.CONVENTIONAL,
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
                    (log.plannedAmount?.takeIf { log.adjusted }?.let { " (plan ${formatNumber(it.value, 2)} ${it.unit.label})" } ?: "") +
                    // Dev: where it went ("125 mg · 0.63 mL · R VG").
                    (log.site?.takeIf { BuildConfig.DEV_FEATURES && it.isNotBlank() }?.let { " · ${InjectionSites.label(it)}" } ?: "")
            }
            JournalRow.Dose(log, detail, Formats.time(log.takenAt, zone))
        }
        val entryRows = journal.map { JournalRow.Entry(it, Formats.time(it.at, zone)) }
        val rows: List<JournalRow> = when (sel.filter) {
            JournalFilter.ALL -> if (sel.compound != null) doseRows else doseRows + entryRows
            JournalFilter.DOSES -> doseRows
            JournalFilter.BLOOD_PRESSURE -> entryRows.filter { it.entry is JournalEntry.BloodPressure }
            JournalFilter.NOTES -> entryRows.filter { it.entry is JournalEntry.Note }
            JournalFilter.SYMPTOMS -> entryRows.filter { it.entry is JournalEntry.Symptoms }
            JournalFilter.BLOODWORK -> entryRows.filter { it.entry is JournalEntry.Bloodwork }
        }
        val days = rows.sortedByDescending { it.at }.groupBy { it.at.atZone(zone).toLocalDate() }
            .map { (date, list) -> JournalDay(date, journalDayHeader(date, today), list) }

        fun ratio(a: Adherence?) = a?.ratio?.let { "${(it * 100).toInt()}% (${a.taken}/${a.scheduled})" } ?: "–"
        val anchors = IntervalAnchors.from(logs)
        // Dev: plan days before the first dose log never count (MISS-1); with no log there is nothing to count.
        val firstLog = logs.minOfOrNull { it.takenAt }?.atZone(zone)?.toLocalDate()?.atStartOfDay(zone)?.toInstant()
        fun adherenceSince(from: Instant) = when {
            !BuildConfig.DEV_FEATURES -> adherence(protocol.phases, protocol.items, logs, from, now, now, zone, anchors, settings.slotTimes)
            firstLog == null -> emptyList()
            else -> adherence(protocol.phases, protocol.items, logs, maxOf(from, firstLog), now, now, zone, anchors, settings.slotTimes)
        }.associateBy { it.itemId }
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
            bpWeeks = if (BuildConfig.DEV_FEATURES && sel.filter == JournalFilter.BLOOD_PRESSURE) bloodPressureWeeks(journal, now) else emptyList(),
            mood = if (BuildConfig.DEV_FEATURES && sel.filter == JournalFilter.SYMPTOMS) moodTrend(journal, zone) else emptyList(),
            empty = logs.isEmpty() && journal.isEmpty(),
            bloodwork = if (BuildConfig.DEV_FEATURES) markerTrends(journal) else emptyList(),
            unlisted = if (BuildConfig.DEV_FEATURES) unlistedTrends(journal) else emptyList(),
            measured = if (BuildConfig.DEV_FEATURES) measuredMarkers(journal) else emptySet(),
            lastDraw = if (BuildConfig.DEV_FEATURES) lastDrawAge(journal.filterIsInstance<JournalEntry.Bloodwork>().map { it.at }, now, zone) else null,
            labUnits = settings.labUnits,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalState())

    /** Dev: what the dose sheet's edit mode needs, every compound and every log (for its Site row); empty in stable. */
    val doseEditor: StateFlow<DoseEditorData> =
        if (!BuildConfig.DEV_FEATURES) MutableStateFlow(DoseEditorData())
        else combine(c.repository.compounds, c.repository.allLogs, ::DoseEditorData)
            .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoseEditorData())

    private val markerKey = MutableStateFlow<String?>(null)

    /** The marker sheet's content for the tapped Bloodwork card row (dev builds), computed only for that key. */
    val markerSheet: StateFlow<MarkerSheetData?> = combine(c.repository.journal, markerKey) { journal, key ->
        key?.takeIf { BuildConfig.DEV_FEATURES }?.let { markerSheetData(journal, it) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showMarker(key: String?) { markerKey.value = key }

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

    fun saveSymptoms(input: SymptomInput, existing: JournalEntry.Symptoms?) =
        saveEntry(input.toEntry(existing?.id ?: TrackerRepository.newId(), existing?.createdAt ?: c.clock()))

    fun saveBloodwork(input: BloodworkInput, existing: JournalEntry.Bloodwork?) =
        saveEntry(input.toEntry(existing?.id ?: TrackerRepository.newId(), existing?.createdAt ?: c.clock()))

    fun deleteEntry(entry: JournalEntry) = viewModelScope.launch {
        val removed = c.repository.deleteJournal(entry.id) ?: return@launch
        _messages.emit(UiMessage("Entry deleted") { c.repository.saveJournal(removed) })
    }

    fun now(): Instant = c.clock()
    fun zone(): ZoneId = c.zone()
}
