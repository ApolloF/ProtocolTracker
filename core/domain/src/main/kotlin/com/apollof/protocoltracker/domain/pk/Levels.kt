package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

enum class LevelMode { RECORDED, PLANNED, COMBINED }

data class DoseEvent(val dose: PkDose, val planned: Boolean)

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

/** Plotted curve for one analyte group (e.g. all testosterone esters) over a window. */
class GroupSeries(
    val group: String,
    val unit: BaseUnit,
    val colorArgb: Long,
    val series: LevelSeries,
    val events: List<DoseEvent>,
)

object Levels {
    /** Doses older than this many slowest half-lives before the window contribute < 0.1% and are dropped. */
    private const val HISTORY_HALF_LIVES = 10.0
    private val washoutSearch: Duration = Duration.ofDays(730)

    /**
     * Dose events for [group]. Recorded = taken logs. Planned = scheduled occurrences.
     * Combined = logs up to now + planned occurrences after now that are not yet logged.
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
    ): List<DoseEvent> {
        val events = ArrayList<DoseEvent>()
        val groupItems = items.filter { compounds[it.compoundId]?.group == group }
        val maxHalfLife = groupItems.mapNotNull { compounds[it.compoundId]?.pk?.eliminationHalfLifeH }
            .plus(logs.filter { it.snapshot.group == group }.map { it.snapshot.pk.eliminationHalfLifeH })
            .maxOrNull() ?: return events
        val lookback = from.minus(Duration.ofMinutes((maxHalfLife * HISTORY_HALF_LIVES * 60).toLong()))

        if (mode != LevelMode.PLANNED) {
            for (log in logs) {
                if (log.status != LogStatus.TAKEN || log.snapshot.group != group) continue
                if (log.takenAt < lookback || log.takenAt > to) continue
                val base = toBaseOrNull(log.amount, log.snapshot.baseUnit, log.snapshot.formulation) ?: continue
                events += DoseEvent(PkDose(log.takenAt.toEpochMilli(), base, log.snapshot.pk), planned = false)
            }
        }
        if (mode != LevelMode.RECORDED) {
            val loggedKeys = logs.mapNotNullTo(HashSet()) { it.occurrenceKey }
            val start = if (mode == LevelMode.COMBINED) maxOf(now, lookback) else lookback
            if (to > start) for (occ in occurrences(phases, groupItems, start, to, zone)) {
                if (mode == LevelMode.COMBINED && occ.key in loggedKeys) continue
                val compound = compounds[occ.item.compoundId] ?: continue
                val base = toBaseOrNull(occ.item.dose, compound.baseUnit, occ.item.formulation) ?: continue
                events += DoseEvent(PkDose(occ.at.toEpochMilli(), base, compound.pk), planned = true)
            }
        }
        events.sortBy { it.dose.atMs }
        return events
    }

    /** Uniform grid plus each dose time and its single-dose peak, so narrow oral peaks are not missed. */
    fun sample(events: List<DoseEvent>, from: Instant, to: Instant, points: Int = 600): LevelSeries {
        val start = from.toEpochMilli()
        val end = to.toEpochMilli()
        val grid = sortedSetOf<Long>()
        for (i in 0..points) grid += start + (end - start) * i / points
        for (e in events) {
            val pk = e.dose.pk
            val peak = e.dose.atMs + (PkEngine.tmaxHours(pk.ka, pk.ke) * 3_600_000).toLong()
            if (e.dose.atMs in start..end) grid += e.dose.atMs
            if (peak in start..end) grid += peak
        }
        val times = grid.toLongArray()
        return LevelSeries(times, PkEngine.simulate(events.map { it.dose }, times))
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
        points: Int = 600,
    ): GroupSeries? {
        val first = compounds.values.firstOrNull { it.group == group } ?: return null
        val events = doseEvents(group, compounds, logs, phases, items, mode, from, to, now, zone)
        return GroupSeries(group, first.baseUnit, first.colorArgb, sample(events, from, to, points), events)
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
    ): LevelMetrics? {
        val members = compounds.values.filter { it.group == group }
        if (members.isEmpty()) return null
        val doses = doseEvents(group, compounds, logs, phases, items, mode, now, now, now, zone).map { it.dose }
        val activeNow = activeItems(phases, items, now, zone).filter { compounds[it.compoundId]?.group == group }
        // Only kinetics actually in use; unused presets of the same group (e.g. other esters) must not count.
        val inUse = activeNow.mapNotNull { compounds[it.compoundId]?.pk } + doses.map { it.pk }
        val slowestRate = inUse.ifEmpty { members.map { it.pk } }.minOf { min(it.ka, it.ke) }
        return LevelMetrics(
            current = PkEngine.levelAt(doses, now.toEpochMilli()),
            steadyState = steadyState(activeNow, compounds, now, zone),
            timeTo90 = Duration.ofMinutes((ln(10.0) / slowestRate * 60).toLong()),
            clearsAt = clearance(group, compounds, logs, phases, items, mode, now, zone),
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
     * (≥ 10 slowest half-lives), then measure the last 7 days, which spans every supported schedule period.
     */
    fun steadyState(items: List<PlanItem>, compounds: Map<String, Compound>, now: Instant, zone: ZoneId): SteadyState? {
        val repeating = items.mapNotNull { item ->
            val compound = compounds[item.compoundId] ?: return@mapNotNull null
            if (item.schedule is Schedule.AsNeeded) return@mapNotNull null
            item.copy(phaseId = null, startDate = null, endDate = null) to compound
        }
        if (repeating.isEmpty()) return null
        val slowestHalfLife = repeating.maxOf { (_, c) -> max(c.pk.eliminationHalfLifeH, c.pk.absorptionHalfLifeH) }
        val settle = Duration.ofHours(max(24.0 * 28, slowestHalfLife * 10).toLong())
        val measureStart = now.plus(settle)
        val measureEnd = measureStart.plus(Duration.ofDays(7))
        val doses = occurrences(emptyList(), repeating.map { it.first }, now, measureEnd, zone).mapNotNull { occ ->
            val compound = compounds[occ.item.compoundId] ?: return@mapNotNull null
            val base = toBaseOrNull(occ.item.dose, compound.baseUnit, occ.item.formulation) ?: return@mapNotNull null
            DoseEvent(PkDose(occ.at.toEpochMilli(), base, compound.pk), planned = true)
        }
        if (doses.isEmpty()) return null
        val series = sample(doses, measureStart, measureEnd, points = 1000)
        return SteadyState(series.values.max(), series.values.min(), average(series))
    }

    /** First time after the last dose when the level drops below 10% of the post-dose peak; null if dosing continues. */
    private fun clearance(
        group: String,
        compounds: Map<String, Compound>,
        logs: List<DoseLog>,
        phases: List<Phase>,
        items: List<PlanItem>,
        mode: LevelMode,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        val horizon = now.plus(Duration.ofDays(365))
        val events = doseEvents(group, compounds, logs, phases, items, mode, now.minus(Duration.ofDays(365)), horizon, now, zone)
        val last = events.lastOrNull() ?: return null
        // Doses still scheduled near the horizon mean the plan has no end yet.
        if (last.planned && last.dose.atMs > horizon.minus(Duration.ofDays(30)).toEpochMilli()) return null
        val doses = events.map { it.dose }
        val stepMs = 3_600_000L
        val times = LongArray((washoutSearch.toHours()).toInt()) { last.dose.atMs + it * stepMs }
        val values = PkEngine.simulate(doses, times)
        val peakIndex = values.indices.maxByOrNull { values[it] } ?: return null
        val threshold = values[peakIndex] * 0.1
        for (i in peakIndex until values.size) if (values[i] < threshold) return Instant.ofEpochMilli(times[i])
        return null
    }
}
