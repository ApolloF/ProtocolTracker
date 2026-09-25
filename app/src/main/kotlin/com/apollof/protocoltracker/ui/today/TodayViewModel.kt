package com.apollof.protocoltracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.pk.Levels
import com.apollof.protocoltracker.domain.schedule.AgendaEntry
import com.apollof.protocoltracker.domain.schedule.AgendaStatus
import com.apollof.protocoltracker.domain.schedule.AgendaWindows
import com.apollof.protocoltracker.domain.schedule.buildAgenda
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.ui.UiMessage
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.LocalDate

data class DoseRow(
    val entry: AgendaEntry,
    val name: String,
    val dose: String,
    val time: String,
    val color: Long,
    val note: String,
)

data class AsNeededItem(val item: PlanItem, val compound: Compound, val dose: String)

data class DayPreview(val label: String, val rows: List<DoseRow>)

data class TodayState(
    val loading: Boolean = true,
    val dateLabel: String = "",
    val phaseLabel: String? = null,
    val phaseProgress: Float? = null,
    val overdue: List<DoseRow> = emptyList(),
    val due: List<DoseRow> = emptyList(),
    val upcoming: List<DoseRow> = emptyList(),
    val done: List<DoseRow> = emptyList(),
    val asNeeded: List<AsNeededItem> = emptyList(),
    val nextDays: List<DayPreview> = emptyList(),
    val hasPlan: Boolean = false,
    val compounds: List<Compound> = emptyList(),
) {
    val pendingCount: Int get() = overdue.size + due.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private val ticker = minuteTicker(c.clock).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val logs = ticker
        .map { it.atZone(c.zone()).toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { day -> c.repository.logsSince(day.atStartOfDay(c.zone()).toInstant().minus(AgendaWindows.overdueLookback)) }

    val state: StateFlow<TodayState> = combine(c.repository.protocol, logs, ticker) { protocol, logs, now ->
        val zone = c.zone()
        val agenda = buildAgenda(protocol.phases, protocol.items, logs, now, zone)
        fun row(e: AgendaEntry, withDay: Boolean = true): DoseRow {
            val log = e.log
            val occ = e.occurrence
            val compound = protocol.compounds[log?.compoundId ?: occ!!.item.compoundId]
            val dose = when {
                log != null -> describeDose(log.amount, log.snapshot.baseUnit, log.snapshot.formulation)
                compound != null -> describeDose(occ!!.item.dose, compound.baseUnit, occ.item.formulation)
                else -> ""
            }
            val timeText = when {
                log == null -> Formats.time(occ!!.at, zone)
                log.status == LogStatus.SKIPPED -> "Skipped"
                else -> Formats.time(log.takenAt, zone)
            }
            val dayPrefix = if (withDay && occ != null && log == null && occ.localDate != agenda.date) Formats.relativeDay(occ.localDate, agenda.date) + " " else ""
            return DoseRow(
                entry = e, name = log?.snapshot?.compoundName ?: compound?.name ?: "Unknown", dose = dose,
                time = dayPrefix + timeText, color = compound?.colorArgb ?: 0xFF6B7280, note = log?.note.orEmpty(),
            )
        }
        val tomorrow = agenda.date.plusDays(1)
        val preview = occurrences(
            protocol.phases, protocol.items, tomorrow.atStartOfDay(zone).toInstant(), tomorrow.plusDays(PREVIEW_DAYS).atStartOfDay(zone).toInstant(), zone,
        ).groupBy { it.localDate }.map { (date, occs) ->
            DayPreview(Formats.relativeDay(date, agenda.date), occs.map { row(AgendaEntry(it, null, AgendaStatus.UPCOMING), withDay = false) })
        }
        val active = Levels.activeItems(protocol.phases, protocol.items, now, zone)
        TodayState(
            loading = false,
            dateLabel = agenda.date.format(Formats.dayLong),
            phaseLabel = agenda.phase?.let { p ->
                val total = p.totalDays?.let { " of ${(it + 6) / 7}" }.orEmpty()
                "${p.phase.name} · week ${p.week}$total"
            },
            phaseProgress = agenda.phase?.totalDays?.let { total -> (agenda.phase!!.day.toFloat() / total).coerceIn(0f, 1f) },
            overdue = agenda.overdue.map(::row),
            due = agenda.due.map(::row),
            upcoming = agenda.upcoming.map(::row),
            done = agenda.done.map(::row),
            asNeeded = active.filter { it.schedule is Schedule.AsNeeded }.mapNotNull { item ->
                val compound = protocol.compounds[item.compoundId] ?: return@mapNotNull null
                AsNeededItem(item, compound, describeDose(item.dose, compound.baseUnit, item.formulation))
            },
            nextDays = preview,
            hasPlan = protocol.items.isNotEmpty(),
            compounds = protocol.compounds.values.filter { !it.archived }.sortedBy { it.name },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun check(row: DoseRow) = launchLogged("${row.name} taken") { listOfNotNull(row.entry.occurrence?.let { c.doseActions.take(it) }) }

    fun checkAt(row: DoseRow, at: Instant) = launchLogged("${row.name} taken") { listOfNotNull(row.entry.occurrence?.let { c.doseActions.take(it, at) }) }

    fun checkNow(row: DoseRow) = checkAt(row, c.clock())

    fun skip(row: DoseRow) = launchLogged("${row.name} skipped") { listOfNotNull(row.entry.occurrence?.let { c.doseActions.skip(it) }) }

    fun checkAll() {
        val rows = state.value.overdue + state.value.due
        launchLogged("${rows.size} doses taken") { rows.mapNotNull { r -> r.entry.occurrence?.let { c.doseActions.take(it) } } }
    }

    /** Removes a done entry (tap on a checked row); undo restores it. */
    fun uncheck(row: DoseRow) {
        val log = row.entry.log ?: return
        viewModelScope.launch {
            val removed = c.repository.deleteLog(log.id) ?: return@launch
            _messages.emit(UiMessage("${row.name} unchecked") { c.repository.restoreLog(removed) })
        }
    }

    fun logAsNeeded(item: AsNeededItem) = launchLogged("${item.compound.name} logged") {
        listOf(c.repository.logUnscheduled(item.compound, item.item.dose, item.item.formulation, c.clock()))
    }

    fun logExtra(compound: Compound, amount: Amount, takenAt: Instant, note: String) = launchLogged("${compound.name} logged") {
        listOf(c.repository.logUnscheduled(compound, amount, Formulation(), takenAt, note))
    }

    fun today(): LocalDate = c.clock().atZone(c.zone()).toLocalDate()

    companion object {
        const val PREVIEW_DAYS = 3L
    }

    private fun launchLogged(message: String, block: suspend () -> List<DoseLog>) {
        viewModelScope.launch {
            val logs = block()
            if (logs.isNotEmpty()) _messages.emit(UiMessage(message) { c.doseActions.undo(logs) })
        }
    }
}
