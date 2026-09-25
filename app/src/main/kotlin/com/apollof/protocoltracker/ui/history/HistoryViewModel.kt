package com.apollof.protocoltracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.schedule.adherence
import com.apollof.protocoltracker.domain.units.describeDose
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
import java.time.LocalDate

data class LogRow(val log: DoseLog, val name: String, val dose: String, val time: String, val color: Long)
data class DayGroup(val date: LocalDate, val label: String, val rows: List<LogRow>)
data class AdherenceRow(val name: String, val color: Long, val week: String, val month: String)
data class FilterOption(val id: String, val name: String, val color: Long)

data class HistoryState(
    val loading: Boolean = true,
    val days: List<DayGroup> = emptyList(),
    val adherence: List<AdherenceRow> = emptyList(),
    val filters: List<FilterOption> = emptyList(),
    val filter: String? = null,
)

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow<String?>(null)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    val state: StateFlow<HistoryState> = combine(c.repository.allLogs, c.repository.protocol, filter) { logs, protocol, selected ->
        val zone = c.zone()
        val now = c.clock()
        val today = now.atZone(zone).toLocalDate()
        val filtered = if (selected == null) logs else logs.filter { it.compoundId == selected }
        val days = filtered.sortedByDescending { it.takenAt }.groupBy { it.takenAt.atZone(zone).toLocalDate() }.map { (date, dayLogs) ->
            DayGroup(date, Formats.relativeDay(date, today), dayLogs.map { log ->
                LogRow(
                    log, log.snapshot.compoundName, describeDose(log.amount, log.snapshot.baseUnit, log.snapshot.formulation),
                    Formats.time(log.takenAt, zone), protocol.compounds[log.compoundId]?.colorArgb ?: 0xFF6B7280,
                )
            })
        }
        fun pct(from: java.time.Instant) = adherence(protocol.phases, protocol.items, logs, from, now, now, zone).associateBy { it.itemId }
        val week = pct(now.minus(Duration.ofDays(7)))
        val month = pct(now.minus(Duration.ofDays(30)))
        val adherenceRows = protocol.items.filter { it.id in month }.mapNotNull { item ->
            val compound = protocol.compounds[item.compoundId] ?: return@mapNotNull null
            fun fmt(a: com.apollof.protocoltracker.domain.schedule.Adherence?) =
                a?.ratio?.let { "${(it * 100).toInt()}% (${a.taken}/${a.scheduled})" } ?: "–"
            AdherenceRow(compound.name, compound.colorArgb, fmt(week[item.id]), fmt(month[item.id]))
        }
        HistoryState(
            loading = false, days = days, adherence = adherenceRows,
            filters = logs.map { it.compoundId }.distinct().mapNotNull { id ->
                val name = logs.first { it.compoundId == id }.snapshot.compoundName
                FilterOption(id, name, protocol.compounds[id]?.colorArgb ?: 0xFF6B7280)
            }.sortedBy { it.name },
            filter = selected,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun setFilter(id: String?) { filter.value = id }

    fun update(log: DoseLog) = viewModelScope.launch { c.repository.updateLog(log) }

    fun delete(log: DoseLog) = viewModelScope.launch {
        val removed = c.repository.deleteLog(log.id) ?: return@launch
        _messages.emit(UiMessage("Entry deleted") { c.repository.restoreLog(removed) })
    }
}
