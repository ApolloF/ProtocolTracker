package com.apollof.protocoltracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.describeSchedule
import com.apollof.protocoltracker.domain.schedule.dosesPerWeek
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import com.apollof.protocoltracker.ui.components.Formats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ItemRow(val item: PlanItem, val name: String, val color: Long, val dose: String, val schedule: String, val weekly: String?)

enum class PhaseStatus(val label: String) { ACTIVE("Active"), UPCOMING("Upcoming"), ENDED("Ended") }

data class PhaseCardState(val phase: Phase, val range: String, val status: PhaseStatus, val items: List<ItemRow>)

data class PlanState(
    val loading: Boolean = true,
    val always: List<ItemRow> = emptyList(),
    val phases: List<PhaseCardState> = emptyList(),
)

class PlanViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<PlanState> = c.repository.protocol.map { protocol ->
        val today = LocalDate.now(c.zone())
        val timeline = PhaseTimeline(protocol.phases)
        val active = timeline.phaseOn(today)
        fun rows(items: List<PlanItem>) = items.sortedBy { it.sortOrder }.map { item -> itemRow(item, protocol.compounds[item.compoundId]) }
        PlanState(
            loading = false,
            always = rows(protocol.items.filter { it.phaseId == null }),
            phases = timeline.phases.map { phase ->
                val end = timeline.effectiveEnd(phase)
                val status = when {
                    phase.id == active?.id -> PhaseStatus.ACTIVE
                    phase.startDate > today -> PhaseStatus.UPCOMING
                    else -> PhaseStatus.ENDED
                }
                PhaseCardState(phase, rangeText(phase.startDate, end), status, rows(protocol.items.filter { it.phaseId == phase.id }))
            }.sortedBy { it.status != PhaseStatus.ACTIVE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun savePhase(phase: Phase) = viewModelScope.launch { c.repository.savePhase(phase) }

    fun deletePhase(id: String) = viewModelScope.launch { c.repository.deletePhase(id) }

    fun newPhase(existing: List<Phase>): Phase {
        val lastEnd = existing.maxByOrNull { it.startDate }
        val start = lastEnd?.let { (it.endDate ?: it.startDate.plusWeeks(8)).plusDays(1) } ?: LocalDate.now(c.zone())
        return Phase(
            id = TrackerRepository.newId(), name = "", startDate = start, endDate = null,
            colorArgb = CompoundColors.palette[existing.size % CompoundColors.palette.size],
        )
    }

    companion object {
        fun itemRow(item: PlanItem, compound: Compound?): ItemRow {
            val base = compound?.let { toBaseOrNull(item.dose, it.baseUnit, item.formulation) }
            val weekly = if (base != null) dosesPerWeek(item.schedule)?.let { "${formatNumber(base * it, 1)} ${compound.baseUnit.label}/week" } else null
            return ItemRow(
                item = item, name = compound?.name ?: "Missing compound", color = compound?.colorArgb ?: 0xFF6B7280,
                dose = compound?.let { describeDose(item.dose, it.baseUnit, item.formulation) } ?: "",
                schedule = describeSchedule(item.schedule), weekly = weekly,
            )
        }

        fun rangeText(start: LocalDate, end: LocalDate?): String {
            if (end == null) return "From ${start.format(Formats.date)}"
            val days = ChronoUnit.DAYS.between(start, end) + 1
            val length = if (days % 7 == 0L) "${days / 7} wk" else "$days d"
            return "${start.format(Formats.date)} – ${end.format(Formats.date)} · $length"
        }
    }
}
