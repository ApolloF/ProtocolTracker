package com.apollof.protocoltracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.compoundOrder
import com.apollof.protocoltracker.domain.model.validate
import com.apollof.protocoltracker.domain.schedule.PlanFigures
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.schedule.planFigures
import com.apollof.protocoltracker.domain.units.ConversionException
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.domain.units.toBase
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.toDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleKind(val label: String) {
    WEEKDAYS("Days of week"), DAILY("Daily"), EVERY_N_DAYS("Every N days"), EVERY_HOURS("Every X hours"), AS_NEEDED("As needed")
}

data class ItemDraft(
    val id: String,
    val phaseId: String?,
    val isNew: Boolean,
    val compoundId: String? = null,
    val basis: DoseBasis = DoseBasis.PER_DOSE,
    val doseText: String = "",
    val doseUnit: DoseUnit = DoseUnit.MG,
    val perMlText: String = "",
    val perTabletText: String = "",
    val kind: ScheduleKind = ScheduleKind.DAILY,
    val slots: Set<DaySlot> = setOf(DaySlot.ANY_TIME),
    val times: List<LocalTime> = emptyList(),
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
    val everyNText: String = "2",
    val hoursText: String = "84",
    val anchorDate: LocalDate = LocalDate.now(),
    val anchorTime: LocalTime = LocalTime.of(9, 0),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val notes: String = "",
    val enabled: Boolean = true,
    val remind: Boolean = true,
    val sortOrder: Int = 0,
)

data class ItemPreview(val figures: PlanFigures?, val next: List<String>, val errors: List<String>)

class ItemEditorViewModel(private val c: AppContainer, itemId: String?, phaseId: String?) : ViewModel() {
    private val _draft = MutableStateFlow(ItemDraft(id = itemId ?: TrackerRepository.newId(), phaseId = phaseId, isNew = itemId == null))
    val draft: StateFlow<ItemDraft> = _draft.asStateFlow()
    val compounds: StateFlow<List<Compound>> = c.repository.compounds.map { it.sortedWith(compoundOrder) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val slotTimes: StateFlow<SlotTimes> = c.settings.settings.map { it.slotTimes }.stateIn(viewModelScope, SharingStarted.Eagerly, SlotTimes.DEFAULT)
    private val _phases = MutableStateFlow<List<Phase>>(emptyList())
    val phases: StateFlow<List<Phase>> = _phases
    private val _loaded = MutableStateFlow(itemId == null)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        viewModelScope.launch {
            val protocol = c.repository.protocolNow()
            _phases.value = protocol.phases.sortedBy { it.startDate }
            val item = protocol.items.firstOrNull { it.id == itemId }
            if (item != null) _draft.value = fromItem(item)
            else {
                val phase = protocol.phases.firstOrNull { it.id == phaseId }
                val order = (protocol.items.maxOfOrNull { it.sortOrder } ?: 0) + 10
                _draft.update { d ->
                    d.copy(sortOrder = order, anchorDate = phase?.startDate?.takeIf { it > LocalDate.now(c.zone()) } ?: LocalDate.now(c.zone()))
                }
            }
            _loaded.value = true
        }
    }

    fun edit(transform: (ItemDraft) -> ItemDraft) = _draft.update(transform)

    /** Sensible defaults for a newly chosen compound: injections weekly on Mon + Thu, everything else daily. */
    fun selectCompound(compound: Compound) = _draft.update { d ->
        val f = compound.defaultFormulation
        val injected = compound.route == Route.INJECTION
        d.copy(
            compoundId = compound.id,
            basis = if (injected) DoseBasis.PER_WEEK else DoseBasis.PER_DOSE,
            doseUnit = if (compound.baseUnit == BaseUnit.IU) DoseUnit.IU else DoseUnit.MG,
            perMlText = f.perMl?.let { formatNumber(it) } ?: "",
            perTabletText = f.perTablet?.let { formatNumber(it) } ?: "",
            kind = if (d.isNew) (if (injected) ScheduleKind.WEEKDAYS else ScheduleKind.DAILY) else d.kind,
        )
    }

    fun preview(d: ItemDraft, compounds: List<Compound>, slotTimes: SlotTimes): ItemPreview {
        val result = build(d, compounds)
        val item = result.getOrNull() ?: return ItemPreview(null, emptyList(), result.exceptionOrNull()?.message?.split("\n").orEmpty())
        val compound = compounds.first { it.id == item.compoundId }
        val now = c.clock()
        val zone = c.zone()
        val next = if (item.schedule is Schedule.AsNeeded) emptyList() else
            occurrences(_phases.value, listOf(item.copy(enabled = true)), now, now.plus(Duration.ofDays(400)), zone, slotTimes, limit = 5_000)
                .take(5).map { occ ->
                    val day = occ.localDate.format(Formats.dayShort)
                    occ.slot?.let { "$day · ${it.label}" } ?: occ.at.atZone(zone).format(Formats.dateTime)
                }
        return ItemPreview(planFigures(item, compound), next, emptyList())
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
            id = item.id, phaseId = item.phaseId, isNew = false, compoundId = item.compoundId, basis = item.doseBasis,
            doseText = formatNumber(item.dose.value, 4), doseUnit = item.dose.unit,
            perMlText = item.formulation.perMl?.let { formatNumber(it) } ?: "",
            perTabletText = item.formulation.perTablet?.let { formatNumber(it) } ?: "",
            startDate = item.startDate, endDate = item.endDate, notes = item.notes, enabled = item.enabled, remind = item.remind,
            sortOrder = item.sortOrder,
        )
        fun ItemDraft.withTimings(timings: List<Timing>) = copy(
            slots = timings.filterIsInstance<Timing.Slot>().map { it.slot }.toSet(),
            times = timings.filterIsInstance<Timing.At>().map { it.time },
        )
        return when (val s = item.schedule) {
            is Schedule.Daily -> base.copy(kind = ScheduleKind.DAILY).withTimings(s.timings)
            is Schedule.Weekdays -> base.copy(kind = ScheduleKind.WEEKDAYS, weekdays = s.days).withTimings(s.timings)
            is Schedule.EveryNDays -> base.copy(kind = ScheduleKind.EVERY_N_DAYS, everyNText = s.n.toString(), anchorDate = s.anchor).withTimings(s.timings)
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
        if (dose == null) errors += if (d.basis == DoseBasis.PER_WEEK) "Enter the weekly dose" else "Enter a dose"
        val perMl = d.perMlText.toDecimal()?.takeIf { it > 0 }
        val perTablet = d.perTabletText.toDecimal()?.takeIf { it > 0 }
        val timings = DaySlot.entries.filter { it in d.slots }.map { Timing.Slot(it) } + d.times.sorted().map { Timing.At(it) }
        val schedule = when (d.kind) {
            ScheduleKind.DAILY -> Schedule.Daily(timings)
            ScheduleKind.WEEKDAYS -> Schedule.Weekdays(d.weekdays, timings)
            ScheduleKind.EVERY_N_DAYS -> Schedule.EveryNDays(d.everyNText.toIntOrNull() ?: 0, d.anchorDate, timings)
            ScheduleKind.EVERY_HOURS -> Schedule.EveryHours(d.hoursText.toDecimal() ?: 0.0, d.anchorDate.atTime(d.anchorTime).atZone(c.zone()).toInstant())
            ScheduleKind.AS_NEEDED -> Schedule.AsNeeded
        }
        val formulation = Formulation(perMl, perTablet)
        if (compound != null && dose != null) {
            try { toBase(Amount(dose, d.doseUnit), compound.baseUnit, formulation) } catch (e: ConversionException) { errors += e.message.orEmpty() }
        }
        val item = if (compound != null && dose != null) PlanItem(
            id = d.id, phaseId = d.phaseId, compoundId = compound.id, dose = Amount(dose, d.doseUnit), doseBasis = d.basis,
            formulation = formulation, schedule = schedule, startDate = d.startDate, endDate = d.endDate,
            notes = d.notes.trim(), enabled = d.enabled, remind = d.remind, sortOrder = d.sortOrder,
        ) else null
        errors += item?.validate() ?: schedule.validate()
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.distinct().joinToString("\n"))
        item!!
    }
}
