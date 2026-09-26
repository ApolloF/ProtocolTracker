package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.dosePerOccurrence
import com.apollof.protocoltracker.domain.model.dosesPerWeek
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import java.time.Instant
import java.time.ZoneId

/** What 100% means on the compare chart. */
enum class CompareBaseline(val label: String) {
    /** Each compound's own steady-state peak at its planned dose. */
    PLAN("Plan"),
    /** The anchor compound's steady-state peak at its weekly dose; other mg compounds as if taken at that same weekly dose. */
    SHARED_DOSE("Shared dose"),
}

/** The reference a line was actually normalised to (a compound can fall back when its baseline cannot apply). */
enum class CompareReference { PLAN_STEADY, SHARED_DOSE, WINDOW_PEAK }

/** One compound on the compare chart: its level as a percentage of its reference, plus the estimate itself. */
class CompareSeries(
    val group: String,
    val colorArgb: Long,
    val times: LongArray,
    val percent: DoubleArray,
    val raw: DoubleArray,
    val unit: String,
    val reference: CompareReference,
    /** Weekly planned amount in the base unit, when the group is planned. */
    val weekly: Double?,
    /** Can be the shared-dose anchor: planned in mg with a steady state. */
    val canAnchor: Boolean,
)

class CompareResult(val series: List<CompareSeries>, val anchor: String?)

/**
 * What each group is normalised to. These depend only on the plan and logs, not on the visible window,
 * so they are computed once per data change and reused while panning.
 */
class CompareReferences(val entries: List<Entry>, val anchor: String?) {
    class Entry(
        val group: LevelGroup,
        /** Level that counts as 100%; null = use the peak in view. */
        val reference: Double?,
        val kind: CompareReference,
        val weekly: Double?,
        val canAnchor: Boolean,
    )
}

/**
 * Normalises several groups onto one percentage axis so their trends can be compared (experimental).
 * Percentages are relative to each group's own curve, so absolute and relative scales can be mixed.
 */
object Compare {
    private const val PREFERRED_ANCHOR = "Testosterone"

    fun references(
        groups: List<LevelGroup>,
        baseline: CompareBaseline,
        anchor: String?,
        compounds: Map<String, Compound>,
        logs: List<DoseLog>,
        phases: List<Phase>,
        items: List<PlanItem>,
        now: Instant,
        zone: ZoneId,
        slotTimes: SlotTimes = SlotTimes.DEFAULT,
    ): CompareReferences {
        val active = Levels.activeItems(phases, items, now, zone)
        class Prepared(val group: LevelGroup, val steadyPeak: Double?, val weekly: Double?, val baseUnit: BaseUnit)

        val prepared = groups.mapNotNull { g ->
            val scale = Levels.scale(g.name, compounds, logs, items) ?: return@mapNotNull null
            val groupItems = active.filter { compounds[it.compoundId]?.let { c -> c.group == g.name && c.pk != null } == true }
            val steady = Levels.steadyState(groupItems, compounds, scale, now, zone, slotTimes)?.peak?.takeIf { it > 0 }
            Prepared(g, steady, weeklyBase(groupItems, compounds), scale.baseUnit)
        }
        // Shared dose: the chosen anchor, else testosterone, else the highest-dosed mg group.
        val eligible = prepared.filter { it.baseUnit == BaseUnit.MG && it.weekly != null && it.steadyPeak != null }
        val anchorGroup = if (baseline != CompareBaseline.SHARED_DOSE) null else
            eligible.firstOrNull { it.group.name == anchor } ?: eligible.firstOrNull { it.group.name == PREFERRED_ANCHOR }
                ?: eligible.maxByOrNull { it.weekly!! }
        val anchorWeekly = anchorGroup?.weekly
        val entries = prepared.map { p ->
            val (reference, kind) = when {
                anchorWeekly != null && p in eligible -> p.steadyPeak!! * anchorWeekly / p.weekly!! to CompareReference.SHARED_DOSE
                p.steadyPeak != null -> p.steadyPeak to CompareReference.PLAN_STEADY
                else -> null to CompareReference.WINDOW_PEAK
            }
            CompareReferences.Entry(p.group, reference, kind, p.weekly, p in eligible)
        }
        return CompareReferences(entries, anchorGroup?.group?.name)
    }

    /** Samples each group over [from, to] and divides by its reference. Cheap enough to run while panning. */
    fun series(
        references: CompareReferences,
        compounds: Map<String, Compound>,
        logs: List<DoseLog>,
        phases: List<Phase>,
        items: List<PlanItem>,
        mode: LevelMode,
        from: Instant,
        to: Instant,
        now: Instant,
        zone: ZoneId,
        slotTimes: SlotTimes = SlotTimes.DEFAULT,
    ): CompareResult {
        val series = references.entries.mapNotNull { e ->
            val curve = Levels.series(e.group.name, compounds, logs, phases, items, mode, from, to, now, zone, slotTimes, colorArgb = e.group.colorArgb)
                ?: return@mapNotNull null
            val raw = curve.series.values
            val reference = e.reference ?: raw.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            CompareSeries(
                group = e.group.name, colorArgb = e.group.colorArgb, times = curve.series.times,
                percent = DoubleArray(raw.size) { raw[it] / reference * 100 }, raw = raw,
                unit = curve.scale.label, reference = e.kind, weekly = e.weekly, canAnchor = e.canAnchor,
            )
        }
        return CompareResult(series, references.anchor)
    }

    /** Planned amount per week in the base unit, or null when nothing repeating is planned or a dose cannot be converted. */
    fun weeklyBase(items: List<PlanItem>, compounds: Map<String, Compound>): Double? {
        var total = 0.0
        for (item in items) {
            val compound = compounds[item.compoundId] ?: return null
            val perWeek = item.schedule.dosesPerWeek() ?: continue
            val formulation = Formulation(
                perMl = item.formulation.perMl ?: compound.defaultFormulation.perMl,
                perTablet = item.formulation.perTablet ?: compound.defaultFormulation.perTablet,
            )
            total += (toBaseOrNull(item.dosePerOccurrence(), compound.baseUnit, formulation) ?: return null) * perWeek
        }
        return total.takeIf { it > 0 }
    }
}
