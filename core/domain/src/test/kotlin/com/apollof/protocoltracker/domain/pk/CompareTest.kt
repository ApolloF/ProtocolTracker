package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class CompareTest {
    private val zone = ZoneId.of("UTC")
    private val anchor = Instant.parse("2026-01-01T09:00:00Z")
    private val te = Presets.byId("preset:test-enan")!!
    private val anavar = Presets.byId("preset:oxandrolone")!!
    private val hcg = Presets.byId("preset:hcg")!!
    private val compounds = listOf(te, anavar, hcg).associateBy { it.id }
    private val testE = PlanItem(
        "t", null, te.id, Amount(500.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 250.0), Schedule.EveryHours(84.0, anchor),
    )
    private val var20 = PlanItem("v", null, anavar.id, Amount(20.0, DoseUnit.MG), schedule = Schedule.EveryHours(24.0, anchor))
    private val hcg500 = PlanItem("h", null, hcg.id, Amount(500.0, DoseUnit.IU), schedule = Schedule.EveryHours(84.0, anchor))

    // Far enough ahead that every curve has reached steady state.
    private val from = anchor.plus(Duration.ofDays(150))
    private val to = from.plus(Duration.ofDays(14))

    private fun run(items: List<PlanItem>, baseline: CompareBaseline, logs: List<DoseLog> = emptyList(), anchorGroup: String? = null): CompareResult {
        val groups = Levels.groups(compounds, logs, emptyList(), items, anchor, zone)
        val refs = Compare.references(groups, baseline, anchorGroup, compounds, logs, emptyList(), items, anchor, zone)
        return Compare.series(refs, compounds, logs, emptyList(), items, LevelMode.PLANNED, from, to, anchor, zone)
    }

    private fun CompareResult.peak(group: String) = series.first { it.group == group }.percent.max()

    @Test
    fun planBaselinePutsEachCompoundAtAHundredPercentAtSteadyState() {
        val r = run(listOf(testE, var20, hcg500), CompareBaseline.PLAN)
        for (s in r.series) {
            assertEquals(CompareReference.PLAN_STEADY, s.reference)
            assertEquals(100.0, s.percent.max(), 3.0, s.group)
        }
    }

    @Test
    fun sharedDoseScalesByWeeklyDoseAgainstTestosterone() {
        val r = run(listOf(testE, var20), CompareBaseline.SHARED_DOSE)
        assertEquals(te.group, r.anchor)
        assertEquals(100.0, r.peak(te.group), 3.0)
        // 140 mg of oxandrolone a week against 500 mg of testosterone: 28%.
        assertEquals(28.0, r.peak(anavar.group), 2.0)
        assertEquals(CompareReference.SHARED_DOSE, r.series.first { it.group == anavar.group }.reference)
    }

    @Test
    fun sharedDoseKeepsOtherUnitsOnTheirOwnPlan() {
        val r = run(listOf(testE, hcg500), CompareBaseline.SHARED_DOSE)
        val h = r.series.first { it.group == hcg.group }
        assertEquals(CompareReference.PLAN_STEADY, h.reference)
        assertEquals(100.0, h.percent.max(), 3.0)
    }

    @Test
    fun chosenAnchorIsUsed() {
        val r = run(listOf(testE, var20), CompareBaseline.SHARED_DOSE, anchorGroup = anavar.group)
        assertEquals(anavar.group, r.anchor)
        assertEquals(100.0, r.peak(anavar.group), 3.0)
        // 500 mg against 140 mg.
        assertEquals(357.0, r.peak(te.group), 12.0)
    }

    @Test
    fun unplannedCompoundsUseTheirPeakInView() {
        val at = from.plus(Duration.ofDays(2))
        val log = DoseLog(
            "l", "x", hcg.id, null, null, at, Amount(1000.0, DoseUnit.IU), null, LogStatus.TAKEN,
            snapshot = DoseSnapshot(hcg.displayName, hcg.group, hcg.category, hcg.baseUnit, hcg.pk), createdAt = at,
        )
        val groups = Levels.groups(compounds, listOf(log), emptyList(), emptyList(), at, zone)
        val refs = Compare.references(groups, CompareBaseline.PLAN, null, compounds, listOf(log), emptyList(), emptyList(), at, zone)
        val r = Compare.series(refs, compounds, listOf(log), emptyList(), emptyList(), LevelMode.RECORDED, from, to, at, zone)
        assertEquals(CompareReference.WINDOW_PEAK, r.series.single().reference)
        assertEquals(100.0, r.series.single().percent.max(), 1e-6)
    }
}
