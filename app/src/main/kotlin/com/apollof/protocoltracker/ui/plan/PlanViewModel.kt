package com.apollof.protocoltracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.dosePerOccurrence
import com.apollof.protocoltracker.domain.model.dosesPerWeek
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.PlanFigures
import com.apollof.protocoltracker.domain.schedule.planFigures
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import com.apollof.protocoltracker.ui.components.Formats
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PhaseStatus(val label: String) { ACTIVE("Active"), UPCOMING("Upcoming"), ENDED("Ended") }

data class PlanItemUi(val item: PlanItem, val compound: Compound, val figures: PlanFigures, val allPhases: Boolean)

data class PlanSection(val category: CompoundCategory, val total: String?, val items: List<PlanItemUi>)

data class PhaseChip(val phase: Phase, val status: PhaseStatus)

data class CycleUi(val name: String, val range: String, val totalWeeks: Int?, val currentWeek: Int?, val note: String)

data class PlanState(
    val loading: Boolean = true,
    val phases: List<PhaseChip> = emptyList(),
    val selected: Phase? = null,
    val cycle: CycleUi? = null,
    val sections: List<PlanSection> = emptyList(),
    /** Items whose compound is missing (e.g. after a partial import). */
    val orphans: Int = 0,
)

class PlanViewModel(private val c: AppContainer) : ViewModel() {
    /** Chosen phase id; null follows the active (or next) phase. */
    private val chosen = MutableStateFlow<String?>(null)

    val state: StateFlow<PlanState> = combine(c.repository.protocol, chosen) { protocol, chosenId ->
        val today = LocalDate.now(c.zone())
        val timeline = PhaseTimeline(protocol.phases)
        val active = timeline.phaseOn(today)
        val chips = timeline.phases.map { phase ->
            PhaseChip(phase, when {
                phase.id == active?.id -> PhaseStatus.ACTIVE
                phase.startDate > today -> PhaseStatus.UPCOMING
                else -> PhaseStatus.ENDED
            })
        }
        val selected = chips.firstOrNull { it.phase.id == chosenId }?.phase
            ?: active ?: chips.firstOrNull { it.status == PhaseStatus.UPCOMING }?.phase ?: chips.lastOrNull()?.phase
        val visible = protocol.items.filter { it.phaseId == null || it.phaseId == selected?.id }
        val rows = visible.mapNotNull { item ->
            val compound = protocol.compounds[item.compoundId] ?: return@mapNotNull null
            PlanItemUi(item, compound, planFigures(item, compound), allPhases = item.phaseId == null && selected != null)
        }
        PlanState(
            loading = false,
            phases = chips,
            selected = selected,
            cycle = selected?.let { cycleUi(it, timeline, today) },
            sections = CompoundCategory.entries.mapNotNull { category ->
                val items = rows.filter { it.compound.category == category }
                    .sortedWith(compareBy({ it.compound.supportKind?.ordinal ?: -1 }, { it.item.sortOrder }, { it.compound.displayName }))
                if (items.isEmpty()) null else PlanSection(category, weeklyTotal(category, items), items)
            },
            orphans = visible.count { protocol.compounds[it.compoundId] == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun select(phaseId: String) { chosen.value = phaseId }

    fun savePhase(phase: Phase) = viewModelScope.launch { c.repository.savePhase(phase); chosen.value = phase.id }

    fun deletePhase(id: String) = viewModelScope.launch { c.repository.deletePhase(id); chosen.value = null }

    fun newPhase(existing: List<Phase>): Phase {
        val last = existing.maxByOrNull { it.startDate }
        val start = last?.let { (it.endDate ?: it.startDate.plusWeeks(8)).plusDays(1) } ?: LocalDate.now(c.zone())
        return Phase(
            id = TrackerRepository.newId(), name = "", startDate = start, endDate = null,
            colorArgb = CompoundColors.palette[existing.size % CompoundColors.palette.size],
        )
    }

    private fun cycleUi(phase: Phase, timeline: PhaseTimeline, today: LocalDate): CycleUi {
        val end = timeline.effectiveEnd(phase)
        val format = Formats.dayMonth
        val days = end?.let { ChronoUnit.DAYS.between(phase.startDate, it).toInt() + 1 }
        val totalWeeks = days?.let { (it + 6) / 7 }
        val current = if (today < phase.startDate || (end != null && today > end)) null else ChronoUnit.DAYS.between(phase.startDate, today).toInt() / 7 + 1
        val note = when {
            current != null -> "Week $current" + (totalWeeks?.let { " of $it" } ?: "")
            today < phase.startDate -> "Starts ${phase.startDate.format(format)}"
            else -> "Ended"
        }
        return CycleUi(
            name = phase.name.ifBlank { "Unnamed phase" },
            range = "${phase.startDate.format(format)} – ${end?.format(format) ?: "open"}",
            totalWeeks = totalWeeks, currentWeek = current, note = note,
        )
    }

    /** Weekly mg of injectable steroids, shown next to the section title. */
    private fun weeklyTotal(category: CompoundCategory, items: List<PlanItemUi>): String? {
        if (category != CompoundCategory.INJECTABLE_STEROID) return null
        val total = items.filter { it.item.enabled && it.compound.baseUnit == BaseUnit.MG }.sumOf { ui ->
            val perWeek = ui.item.schedule.dosesPerWeek() ?: return@sumOf 0.0
            val base = toBaseOrNull(ui.item.dosePerOccurrence(), ui.compound.baseUnit, ui.item.formulation.takeIf { it.perMl != null } ?: ui.compound.defaultFormulation) ?: 0.0
            base * perWeek
        }
        return if (total > 0) "${formatNumber(total, 0)} mg/wk" else null
    }
}
