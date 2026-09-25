package com.apollof.protocoltracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.validate
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.units.ConversionException
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.domain.units.toBase
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.toDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleKind(val label: String) {
    DAILY("Daily"), WEEKDAYS("Weekdays"), EVERY_N_DAYS("Every N days"), EVERY_HOURS("Every X hours"), AS_NEEDED("As needed")
}

data class ItemDraft(
    val id: String,
    val phaseId: String?,
    val isNew: Boolean,
    val compoundId: String? = null,
    val doseText: String = "",
    val doseUnit: DoseUnit = DoseUnit.MG,
    val perMlText: String = "",
    val perTabletText: String = "",
    val kind: ScheduleKind = ScheduleKind.WEEKDAYS,
    val times: List<LocalTime> = listOf(LocalTime.of(9, 0)),
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
    val everyNText: String = "2",
    val hoursText: String = "84",
    val anchorDate: LocalDate = LocalDate.now(),
    val anchorTime: LocalTime = LocalTime.of(9, 0),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val notes: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

data class ItemPreview(val summary: String?, val weekly: String?, val next: List<String>, val errors: List<String>)

class ItemEditorViewModel(private val c: AppContainer, itemId: String?, phaseId: String?) : ViewModel() {
    private val _draft = MutableStateFlow(ItemDraft(id = itemId ?: TrackerRepository.newId(), phaseId = phaseId, isNew = itemId == null))
    val draft: StateFlow<ItemDraft> = _draft.asStateFlow()
    val compounds: StateFlow<List<Compound>> = c.repository.compounds.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var phases: List<Phase> = emptyList()
    private val _loaded = MutableStateFlow(itemId == null)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        viewModelScope.launch {
            val protocol = c.repository.protocolNow()
            phases = protocol.phases
            val item = protocol.items.firstOrNull { it.id == itemId }
            if (item != null) _draft.value = fromItem(item)
            else if (phaseId != null) {
                val phase = protocol.phases.firstOrNull { it.id == phaseId }
                if (phase != null && phase.startDate > LocalDate.now(c.zone())) _draft.update { it.copy(anchorDate = phase.startDate) }
            }
            _loaded.value = true
        }
    }

    fun edit(transform: (ItemDraft) -> ItemDraft) = _draft.update(transform)

    /** Picks sensible units and formulation defaults for a newly selected compound. */
    fun selectCompound(compound: Compound) = _draft.update { d ->
        val f = compound.defaultFormulation
        val unit = when {
            compound.baseUnit == BaseUnit.IU -> DoseUnit.IU
            compound.category == CompoundCategory.INJECTABLE && f.perMl != null -> DoseUnit.ML
            f.perTablet != null -> DoseUnit.TABLET
            else -> DoseUnit.MG
        }
        d.copy(
            compoundId = compound.id, doseUnit = unit,
            perMlText = f.perMl?.let { formatNumber(it) } ?: "",
            perTabletText = f.perTablet?.let { formatNumber(it) } ?: "",
            kind = if (compound.category == CompoundCategory.INJECTABLE || compound.category == CompoundCategory.HCG) d.kind else ScheduleKind.DAILY,
        )
    }

    fun preview(d: ItemDraft, compounds: List<Compound>): ItemPreview {
        val result = build(d, compounds)
        val item = result.getOrNull() ?: return ItemPreview(null, null, emptyList(), result.exceptionOrNull()?.message?.split("\n").orEmpty())
        val compound = compounds.first { it.id == item.compoundId }
        val row = PlanViewModel.itemRow(item, compound)
        val now = c.clock()
        val zone = c.zone()
        val next = if (item.schedule is Schedule.AsNeeded) emptyList() else
            occurrences(phases, listOf(item.copy(enabled = true)), now, now.plus(Duration.ofDays(400)), zone, limit = 5_000)
                .take(5).map { it.at.atZone(zone).format(Formats.dateTime) }
        return ItemPreview("${row.dose} · ${row.schedule}", row.weekly, next, emptyList())
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        build(_draft.value, compounds.value).onSuccess {
            c.repository.saveItem(it)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        c.repository.deleteItem(_draft.value.id)
        onDone()
    }

    private fun fromItem(item: PlanItem): ItemDraft {
        val base = ItemDraft(
            id = item.id, phaseId = item.phaseId, isNew = false, compoundId = item.compoundId,
            doseText = formatNumber(item.dose.value, 4), doseUnit = item.dose.unit,
            perMlText = item.formulation.perMl?.let { formatNumber(it) } ?: "",
            perTabletText = item.formulation.perTablet?.let { formatNumber(it) } ?: "",
            startDate = item.startDate, endDate = item.endDate, notes = item.notes, enabled = item.enabled, sortOrder = item.sortOrder,
        )
        return when (val s = item.schedule) {
            is Schedule.Daily -> base.copy(kind = ScheduleKind.DAILY, times = s.times)
            is Schedule.Weekdays -> base.copy(kind = ScheduleKind.WEEKDAYS, times = s.times, weekdays = s.days)
            is Schedule.EveryNDays -> base.copy(kind = ScheduleKind.EVERY_N_DAYS, times = s.times, everyNText = s.n.toString(), anchorDate = s.anchor)
            is Schedule.EveryHours -> {
                val local = s.anchor.atZone(c.zone())
                base.copy(kind = ScheduleKind.EVERY_HOURS, hoursText = formatNumber(s.hours, 2), anchorDate = local.toLocalDate(), anchorTime = local.toLocalTime())
            }
            Schedule.AsNeeded -> base.copy(kind = ScheduleKind.AS_NEEDED)
        }
    }

    private fun build(d: ItemDraft, compounds: List<Compound>): Result<PlanItem> = runCatching {
        val errors = mutableListOf<String>()
        val compound = compounds.firstOrNull { it.id == d.compoundId }
        if (compound == null) errors += "Choose a compound"
        val dose = d.doseText.toDecimal()?.takeIf { it > 0 }
        if (dose == null) errors += "Enter a dose"
        val perMl = d.perMlText.toDecimal()?.takeIf { it > 0 }
        val perTablet = d.perTabletText.toDecimal()?.takeIf { it > 0 }
        val schedule = when (d.kind) {
            ScheduleKind.DAILY -> Schedule.Daily(d.times.sorted())
            ScheduleKind.WEEKDAYS -> Schedule.Weekdays(d.weekdays, d.times.sorted())
            ScheduleKind.EVERY_N_DAYS -> Schedule.EveryNDays(d.everyNText.toIntOrNull() ?: 0, d.anchorDate, d.times.sorted())
            ScheduleKind.EVERY_HOURS -> Schedule.EveryHours(d.hoursText.toDecimal() ?: 0.0, d.anchorDate.atTime(d.anchorTime).atZone(c.zone()).toInstant())
            ScheduleKind.AS_NEEDED -> Schedule.AsNeeded
        }
        errors += schedule.validate()
        if (d.startDate != null && d.endDate != null && d.endDate < d.startDate) errors += "End date is before start date"
        val formulation = Formulation(perMl, perTablet)
        if (compound != null && dose != null) {
            try { toBase(Amount(dose, d.doseUnit), compound.baseUnit, formulation) } catch (e: ConversionException) { errors += e.message.orEmpty() }
        }
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.joinToString("\n"))
        PlanItem(
            id = d.id, phaseId = d.phaseId, compoundId = compound!!.id, dose = Amount(dose!!, d.doseUnit),
            formulation = formulation, schedule = schedule, startDate = d.startDate, endDate = d.endDate,
            notes = d.notes.trim(), enabled = d.enabled, sortOrder = d.sortOrder,
        )
    }
}
