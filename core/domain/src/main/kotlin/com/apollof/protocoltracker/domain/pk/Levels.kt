package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.SupportKind
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToLong

enum class LevelMode { RECORDED, PLANNED, COMBINED }

/** A dose on a group's curve. [amount] is in the compound's base unit (mg or IU) as dosed. */
data class DoseEvent(val atMs: Long, val amount: Double, val pk: PkParams, val planned: Boolean)

/**
 * How a group is plotted. Absolute when every compound in use has a study peak (same display unit);
 * otherwise relative, as active amount in the body.
 */
data class LevelScale(val relative: Boolean, val unit: LevelUnit, val baseUnit: BaseUnit) {
    val label: String get() = if (relative) "${baseUnit.label} active (relative)" else unit.label

    fun curve(event: DoseEvent): CurveDose {
        val pk = event.pk
        val peak = if (relative) event.amount * pk.activeFraction else event.amount * pk.peakPerUnit!! * unit.perNgDl
        return CurveDose(event.atMs, peak, pk.tmaxH, pk.halfLifeH)
    }
}

class LevelSeries(val times: LongArray, val values: DoubleArray) {
    val size: Int get() = times.size
}

data class SteadyState(val peak: Double, val trough: Double, val average: Double)

/** Window-independent figures for one group. */
data class LevelMetrics(
    val current: Double,
    val steadyState: SteadyState?,
    /** Time to reach ~90% of steady state with the slowest kinetics in the group. */
    val timeTo90: Duration,
    /** When the level falls below 10% of its peak after the last dose, if the plan ends. */
    val clearsAt: Instant?,
)

/** A group listed on the Levels screen; [current] = in use now (see [Levels.groups]). */
data class LevelGroup(val name: String, val colorArgb: Long, val current: Boolean)

/** Plotted curve for one analyte group (e.g. all testosterone esters) over a window. */
class GroupSeries(
    val group: String,
    val scale: LevelScale,
    val colorArgb: Long,
    val series: LevelSeries,
    val events: List<DoseEvent>,
)

object Levels {
    /** Doses older than this many half-lives (after their peak) contribute < 0.1% and are dropped. */
    private const val HISTORY_HALF_LIVES = 10.0
    private val washoutSearch: Duration = Duration.ofDays(730)
    private const val MAX_PERIOD_MINUTES = 365L * 1_440
    /** About 1% of the peak remains after this many half-lives (2^-6.64). */
    private const val CURRENT_HALF_LIVES = 6.64
    private const val DEFAULT_COLOR = 0xFF6B7280

    /**
     * Plottable groups for the Levels screen, in plan section order (injectables, orals, support, peptides).
     * [LevelGroup.current] groups are taken by a plan item active now, or were dosed recently enough that the level
     * is still above about 1% of its peak; the rest (other phases, paused items, old doses) are listed separately.
     * Skipped doses never count. The colour comes from the compound actually in use.
     */
    fun groups(
        compounds: Map<String, Compound>,
        logs: List<DoseLog>,
        phases: List<Phase>,
        items: List<PlanItem>,
        now: Instant,
        zone: ZoneId,
    ): List<LevelGroup> {
        data class Candidate(val current: Boolean, val compound: Compound?, val category: CompoundCategory, val kind: SupportKind?)
        val found = LinkedHashMap<String, Candidate>()
        // Earlier sources win the colour; any current source marks the group current.
        fun add(group: String, current: Boolean, compound: Compound?, category: CompoundCategory, kind: SupportKind?) {
            val existing = found[group]
            found[group] = when {
                existing == null -> Candidate(current, compound, category, kind)
                else -> existing.copy(current = existing.current || current, compound = existing.compound ?: compound)
            }
        }
        val taken = logs.filter { it.status == LogStatus.TAKEN && it.snapshot.pk != null }.sortedByDescending { it.takenAt }
        for (item in activeItems(phases, items, now, zone)) {
            val c = compounds[item.compoundId]?.takeIf { it.pk != null } ?: continue
            add(c.group, true, c, c.category, c.supportKind)
        }
        for (log in taken) {
            val pk = log.snapshot.pk!!
            val stillThere = log.takenAt.plusSeconds(((pk.tmaxH + pk.halfLifeH * CURRENT_HALF_LIVES) * 3600).toLong()) >= now
            if (stillThere) add(log.snapshot.group, true, compounds[log.compoundId], log.snapshot.category, compounds[log.compoundId]?.supportKind)
        }
        for (item in items) {
            val c = compounds[item.compoundId]?.takeIf { it.pk != null } ?: continue
            add(c.group, false, c, c.category, c.supportKind)
        }
        for (log in taken) add(log.snapshot.group, false, compounds[log.compoundId], log.snapshot.category, compounds[log.compoundId]?.supportKind)

        return found.entries
            .sortedWith(compareBy({ it.value.category.ordinal }, { it.value.kind?.ordinal ?: -1 }, { it.key.lowercase() }))
            .map { (group, cand) ->
                val color = cand.compound?.colorArgb ?: compounds.values.firstOrNull { it.group == group }?.colorArgb ?: DEFAULT_COLOR
                LevelGroup(group, color, cand.current)
            }
    }

    /** Compounds in use that have no reliable level data; shown as a note instead of a curve. */
    fun unplottable(compounds: Map<String, Compound>, logs: List<DoseLog>, items: List<PlanItem>): List<String> =
        (items.mapNotNull { compounds[it.compoundId]?.takeIf { c -> c.pk == null }?.displayName } +
            logs.filter { it.snapshot.pk == null }.map { it.snapshot.displayName }).distinct().sorted()

    /** Scale for [group] from every kinetic in use (plan items and history), so all views of a group agree. */
    fun scale(group: String, compounds: Map<String, Compound>, logs: List<DoseLog>, items: List<PlanItem>): LevelScale? {
        val used = items.mapNotNull { compounds[it.compoundId] }.filter { it.group == group && it.pk != null }.map { it.pk!! to it.baseUnit } +
            logs.filter { it.snapshot.group == group && it.snapshot.pk != null }.map { it.snapshot.pk!! to it.snapshot.baseUnit }
        val members = used.ifEmpty {
            compounds.values.filter { it.group == group && it.pk != null }.map { it.pk!! to it.baseUnit }
        }
        if (members.isEmpty()) return null
        val units = members.map { it.first.levelUnit }.distinct()
        val relative = members.any { it.first.peakPerUnit == null } || units.size > 1
        return LevelScale(relative, units.first(), members.first().second)
    }

    /**
     * Dose events for [group]. Recorded = taken logs. Planned = scheduled occurrences.
     * Combined = logs up to now + planned occurrences after now that are not yet logged.
     * [logs] must be all logs: planned doses of interval schedules restart from the last taken dose.
     */
    fun doseEvents(
        group: String,
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
    ): List<DoseEvent> {
        val events = ArrayList<DoseEvent>()
        val groupItems = items.filter { compounds[it.compoundId]?.let { c -> c.group == group && c.pk != null } == true }
        val horizons = groupItems.mapNotNull { compounds[it.compoundId]?.pk }.map { it.tmaxH + it.halfLifeH * HISTORY_HALF_LIVES } +
            logs.filter { it.snapshot.group == group }.mapNotNull { it.snapshot.pk }.map { it.tmaxH + it.halfLifeH * HISTORY_HALF_LIVES }
        val maxHorizonH = horizons.maxOrNull() ?: return events
        val lookback = from.minus(Duration.ofMinutes((maxHorizonH * 60).toLong()))

        if (mode != LevelMode.PLANNED) {
            for (log in logs) {
                val pk = log.snapshot.pk ?: continue
                if (log.status != LogStatus.TAKEN || log.snapshot.group != group) continue
                if (log.takenAt < lookback || log.takenAt > to) continue
                val base = toBaseOrNull(log.amount, log.snapshot.baseUnit, log.snapshot.formulation) ?: continue
                events += DoseEvent(log.takenAt.toEpochMilli(), base, pk, planned = false)
            }
        }
        if (mode != LevelMode.RECORDED) {
            val loggedKeys = logs.mapNotNullTo(HashSet()) { it.occurrenceKey }
            val start = if (mode == LevelMode.COMBINED) maxOf(now, lookback) else lookback
            if (to > start) for (occ in occurrences(phases, groupItems, start, to, zone, IntervalAnchors.from(logs), slotTimes)) {
                if (mode == LevelMode.COMBINED && occ.key in loggedKeys) continue
                val compound = compounds[occ.item.compoundId] ?: continue
                val pk = compound.pk ?: continue
                val base = toBaseOrNull(occ.dose, compound.baseUnit, occ.item.formulation) ?: continue
                events += DoseEvent(occ.at.toEpochMilli(), base, pk, planned = true)
            }
        }
        events.sortBy { it.atMs }
        return events
    }

    /** Uniform grid plus each dose time and its peak, so narrow oral peaks are not missed. */
    fun sample(curves: List<CurveDose>, from: Instant, to: Instant, points: Int = 600): LevelSeries {
        val start = from.toEpochMilli()
        val end = to.toEpochMilli()
        val grid = sortedSetOf<Long>()
        for (i in 0..points) grid += start + (end - start) * i / points
        for (c in curves) {
            val peak = c.atMs + Math.round(c.tmaxH * 3_600_000)
            if (c.atMs in start..end) grid += c.atMs
            if (peak in start..end) grid += peak
        }
        val times = grid.toLongArray()
        return LevelSeries(times, CurveEngine.simulate(curves, times))
    }

    /** Curve for [group] over [from, to]; cheap enough to recompute while panning. */
    fun series(
        group: String,
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
        points: Int = 600,
        colorArgb: Long? = null,
    ): GroupSeries? {
        val scale = scale(group, compounds, logs, items) ?: return null
        val color = colorArgb ?: compounds.values.firstOrNull { it.group == group }?.colorArgb ?: DEFAULT_COLOR
        val events = doseEvents(group, compounds, logs, phases, items, mode, from, to, now, zone, slotTimes)
        return GroupSeries(group, scale, color, sample(events.map(scale::curve), from, to, points), events)
    }

    fun metrics(
        group: String,
        compounds: Map<String, Compound>,
        logs: List<DoseLog>,
        phases: List<Phase>,
        items: List<PlanItem>,
        mode: LevelMode,
        now: Instant,
        zone: ZoneId,
        slotTimes: SlotTimes = SlotTimes.DEFAULT,
    ): LevelMetrics? {
        val scale = scale(group, compounds, logs, items) ?: return null
        val events = doseEvents(group, compounds, logs, phases, items, mode, now, now, now, zone, slotTimes)
        val activeNow = activeItems(phases, items, now, zone).filter { compounds[it.compoundId]?.let { c -> c.group == group && c.pk != null } == true }
        // Only kinetics actually in use; unused presets of the same group (e.g. other esters) must not count.
        val inUse = activeNow.mapNotNull { compounds[it.compoundId]?.pk } + events.map { it.pk }
        val slowest = inUse.ifEmpty { compounds.values.filter { it.group == group }.mapNotNull { it.pk } }
            .maxOf { CurveEngine.hoursUntilBelow(it.tmaxH, it.halfLifeH, 0.1) }
        return LevelMetrics(
            current = CurveEngine.levelAt(events.map(scale::curve), now.toEpochMilli()),
            steadyState = steadyState(activeNow, compounds, scale, now, zone, slotTimes),
            timeTo90 = Duration.ofMinutes((slowest * 60).toLong()),
            clearsAt = clearance(group, compounds, logs, phases, items, mode, scale, now, zone, slotTimes),
        )
    }

    /** Trapezoidal time-average of a series. */
    fun average(series: LevelSeries): Double {
        if (series.size < 2) return series.values.firstOrNull() ?: 0.0
        var area = 0.0
        for (i in 1 until series.size) {
            area += (series.times[i] - series.times[i - 1]) * (series.values[i] + series.values[i - 1]) / 2
        }
        return area / (series.times.last() - series.times.first())
    }

    /** Plan items that would fire on the current date (current phase plus Always group). */
    fun activeItems(phases: List<Phase>, items: List<PlanItem>, now: Instant, zone: ZoneId): List<PlanItem> {
        val today = now.atZone(zone).toLocalDate()
        val phase = PhaseTimeline(phases).phaseOn(today)
        return items.filter {
            it.enabled && (it.phaseId == null || it.phaseId == phase?.id) &&
                (it.startDate == null || it.startDate <= today) && (it.endDate == null || it.endDate >= today)
        }
    }

    /**
     * Steady state if [items] continued indefinitely: simulate until transients have decayed
     * (≥ 10 slowest half-lives after the peak), then measure one full combined schedule period (LCM of item periods).
     */
    fun steadyState(
        items: List<PlanItem>,
        compounds: Map<String, Compound>,
        scale: LevelScale,
        now: Instant,
        zone: ZoneId,
        slotTimes: SlotTimes = SlotTimes.DEFAULT,
    ): SteadyState? {
        val repeating = items.mapNotNull { item ->
            val compound = compounds[item.compoundId]?.takeIf { it.pk != null } ?: return@mapNotNull null
            if (item.schedule is Schedule.AsNeeded) return@mapNotNull null
            item.copy(phaseId = null, startDate = null, endDate = null) to compound
        }
        if (repeating.isEmpty()) return null
        val slowest = repeating.maxOf { (_, c) -> c.pk!!.tmaxH + c.pk.halfLifeH * 10 }
        val settle = Duration.ofHours(max(24.0 * 28, slowest).toLong())
        val period = repeating.map { (item, _) -> periodMinutes(item.schedule) }
            .reduce { a, b -> minOf(lcm(a, b), MAX_PERIOD_MINUTES) }
        val measureStart = now.plus(settle)
        val measureEnd = measureStart.plus(Duration.ofMinutes(period))
        val curves = occurrences(emptyList(), repeating.map { it.first }, now, measureEnd, zone, IntervalAnchors.NONE, slotTimes).mapNotNull { occ ->
            val compound = compounds[occ.item.compoundId] ?: return@mapNotNull null
            val base = toBaseOrNull(occ.dose, compound.baseUnit, occ.item.formulation) ?: return@mapNotNull null
            scale.curve(DoseEvent(occ.at.toEpochMilli(), base, compound.pk!!, planned = true))
        }
        if (curves.isEmpty()) return null
        val series = sample(curves, measureStart, measureEnd, points = 1000)
        return SteadyState(series.values.max(), series.values.min(), average(series))
    }

    /** Repeat period of a schedule in minutes (weekday patterns repeat weekly). */
    private fun periodMinutes(schedule: Schedule): Long = when (schedule) {
        is Schedule.Daily -> 1_440
        is Schedule.Weekdays -> 10_080
        is Schedule.EveryNDays -> schedule.n * 1_440L
        is Schedule.EveryHours -> max(1L, (schedule.hours * 60).roundToLong())
        Schedule.AsNeeded -> 1_440
    }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    private fun lcm(a: Long, b: Long): Long = a / gcd(a, b) * b

    /**
     * First time after the last dose when the level drops below 10% of its post-dose peak.
     * Null while dosing continues: some plan item of the group has no end (no end date and an open-ended phase).
     */
    private fun clearance(
        group: String,
        compounds: Map<String, Compound>,
        logs: List<DoseLog>,
        phases: List<Phase>,
        items: List<PlanItem>,
        mode: LevelMode,
        scale: LevelScale,
        now: Instant,
        zone: ZoneId,
        slotTimes: SlotTimes,
    ): Instant? {
        val end: Instant = if (mode == LevelMode.RECORDED) now else {
            val timeline = PhaseTimeline(phases)
            val phaseById = phases.associateBy { it.id }
            var planEnd: LocalDate? = null
            for (item in items) {
                val compound = compounds[item.compoundId] ?: continue
                if (!item.enabled || item.schedule is Schedule.AsNeeded || compound.group != group || compound.pk == null) continue
                val phaseEnd: LocalDate? = if (item.phaseId == null) null else {
                    val phase = phaseById[item.phaseId] ?: continue // orphaned item never fires
                    timeline.effectiveEnd(phase)
                }
                // Neither the item nor its phase ends: dosing continues indefinitely.
                val itemEnd = listOfNotNull(item.endDate, phaseEnd).minOrNull() ?: return null
                planEnd = if (planEnd == null || itemEnd > planEnd) itemEnd else planEnd
            }
            planEnd?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.let { maxOf(it, now) } ?: now
        }
        val events = doseEvents(group, compounds, logs, phases, items, mode, end, end, now, zone, slotTimes)
        val last = events.lastOrNull() ?: return null
        val curves = events.map(scale::curve)
        val stepMs = 3_600_000L
        val times = LongArray(washoutSearch.toHours().toInt()) { last.atMs + it * stepMs }
        val values = CurveEngine.simulate(curves, times)
        val peakIndex = values.indices.maxByOrNull { values[it] } ?: return null
        val threshold = values[peakIndex] * 0.1
        for (i in peakIndex until values.size) if (values[i] < threshold) return Instant.ofEpochMilli(times[i])
        return null
    }
}
