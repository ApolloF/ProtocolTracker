package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.schedule.occurrenceKey
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelsTest {
    private val zone = ZoneId.of("UTC")
    private val te = Presets.byId("preset:test-enan")!!
    private val compounds = mapOf(te.id to te)
    private val anchor = Instant.parse("2026-01-01T09:00:00Z")
    private val item = PlanItem(
        "i", null, te.id, Amount(500.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 250.0), Schedule.EveryHours(84.0, anchor),
    )

    private fun log(at: Instant, key: String? = null) = DoseLog(
        "l$at", "i", te.id, key, at, at, Amount(250.0, DoseUnit.MG), Amount(250.0, DoseUnit.MG), LogStatus.TAKEN,
        snapshot = DoseSnapshot(te.displayName, te.group, te.category, te.baseUnit, te.pk), createdAt = at,
    )

    @Test
    fun singleTestEDoseMatchesSheetPeak() {
        val one = item.copy(schedule = Schedule.EveryHours(24.0 * 365, anchor), doseBasis = DoseBasis.PER_DOSE, dose = Amount(250.0, DoseUnit.MG))
        val scale = Levels.scale(te.group, compounds, emptyList(), listOf(one))!!
        assertFalse(scale.relative)
        val events = Levels.doseEvents(te.group, compounds, emptyList(), emptyList(), listOf(one), LevelMode.PLANNED, anchor, anchor.plus(Duration.ofDays(10)), anchor, zone)
        val peakAt = anchor.plusMillis(Math.round(te.pk!!.tmaxH * 3_600_000))
        val peak = CurveEngine.levelAt(events.map(scale::curve), peakAt.toEpochMilli())
        assertEquals(250 * 11.3095, peak, 1e-6) // ≈2 827 ng/dL at ~33 h
    }

    @Test
    fun combinedUsesLogsBeforeNowAndPlanAfterWithoutDoubleCounting() {
        val now = anchor.plus(Duration.ofHours(200))
        val logs = listOf(log(anchor, occurrenceKey("i", anchor)), log(anchor.plus(Duration.ofHours(90))))
        val events = Levels.doseEvents(te.group, compounds, logs, emptyList(), listOf(item), LevelMode.COMBINED, anchor, now.plus(Duration.ofDays(7)), now, zone)
        assertEquals(2, events.count { !it.planned })
        assertTrue(events.filter { it.planned }.all { it.atMs > now.toEpochMilli() })
        assertEquals(250.0, events.first { it.planned }.amount, 1e-9) // 500 mg/week every 84 h
    }

    @Test
    fun steadyStateAverageMatchesDoseAreaPerInterval() {
        val scale = Levels.scale(te.group, compounds, emptyList(), listOf(item))!!
        val ss = assertNotNull(Levels.steadyState(listOf(item), compounds, scale, anchor, zone))
        val pk = te.pk!!
        val expected = 250 * pk.peakPerUnit!! * pk.areaPerPeakH / 84.0
        assertTrue(abs(ss.average - expected) / expected < 0.01, "avg ${ss.average} vs $expected")
        assertTrue(ss.trough < ss.average && ss.average < ss.peak)
    }

    @Test
    fun clearanceOnlyWhenPlanEnds() {
        val open = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(item), LevelMode.PLANNED, anchor, zone)!!
        assertNull(open.clearsAt)
        val ending = item.copy(endDate = java.time.LocalDate.parse("2026-02-01"))
        val closed = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(ending), LevelMode.PLANNED, anchor, zone)!!
        val clears = assertNotNull(closed.clearsAt)
        // t½ 7.2 d after a 1.4 d peak: ~3.3 half-lives, about 25 days after the last dose.
        assertTrue(clears > Instant.parse("2026-02-15T00:00:00Z") && clears < Instant.parse("2026-03-20T00:00:00Z"), "$clears")
    }

    @Test
    fun samplingIncludesPeaksOfShortOrals() {
        val anavar = Presets.byId("preset:oxandrolone")!!
        val dose = CurveDose(anchor.toEpochMilli(), 7720.0, anavar.pk!!.tmaxH, anavar.pk.halfLifeH)
        val series = Levels.sample(listOf(dose), anchor.minus(Duration.ofDays(1)), anchor.plus(Duration.ofDays(90)), points = 50)
        assertEquals(7720.0, series.values.max(), 1e-9)
    }

    @Test
    fun seriesAddsEstersOfOneGroup() {
        val cyp = Presets.byId("preset:test-cyp")!!
        val both = compounds + (cyp.id to cyp)
        val items = listOf(item, item.copy(id = "j", compoundId = cyp.id, formulation = Formulation(perMl = 200.0)))
        val to = anchor.plus(Duration.ofDays(20))
        val combined = Levels.series(te.group, both, emptyList(), emptyList(), items, LevelMode.PLANNED, anchor, to, anchor, zone)!!
        val single = Levels.series(te.group, compounds, emptyList(), emptyList(), listOf(item), LevelMode.PLANNED, anchor, to, anchor, zone)!!
        assertTrue(combined.series.values.last() > single.series.values.last())
        assertEquals(2 * single.events.size, combined.events.size)
        assertEquals("ng/dL", combined.scale.label)
    }

    @Test
    fun mixingAPeaklessCompoundMakesTheGroupRelative() {
        val custom = te.copy(id = "custom", pk = PkParams(100.0, 10.0, null, 0.7))
        val all = compounds + (custom.id to custom)
        val items = listOf(item, item.copy(id = "k", compoundId = custom.id))
        val scale = Levels.scale(te.group, all, emptyList(), items)!!
        assertTrue(scale.relative)
        assertEquals("mg active (relative)", scale.label)
    }

    @Test
    fun logOnlyCompoundsAreNotPlotted() {
        val bpc = Presets.byId("preset:bpc-157")!!
        val items = listOf(item, PlanItem("b", null, bpc.id, Amount(250.0, DoseUnit.MCG), schedule = Schedule.EveryHours(24.0, anchor)))
        val all = compounds + (bpc.id to bpc)
        assertEquals(listOf(te.group), Levels.plottableGroups(all, emptyList(), items))
        assertEquals(listOf("BPC-157"), Levels.unplottable(all, emptyList(), items))
    }

    @Test
    fun timeToSteadyUsesOnlyCompoundsInUse() {
        val all = Presets.all.associateBy { it.id } // includes slow testosterone undecanoate
        val m = Levels.metrics(te.group, all, emptyList(), emptyList(), listOf(item), LevelMode.PLANNED, anchor, zone)!!
        val expectedH = te.pk!!.tmaxH + te.pk.halfLifeH * kotlin.math.ln(10.0) / 0.6931471805599453
        assertEquals(expectedH, m.timeTo90.toMinutes() / 60.0, 0.1)
    }

    @Test
    fun lookbackKeepsDosesStillRising() {
        val slow = te.copy(id = "slow", pk = PkParams(10.0, 100.0, 1.0))
        val dose = log(anchor).copy(compoundId = "slow", snapshot = DoseSnapshot("S", te.group, te.category, te.baseUnit, slow.pk))
        val now = anchor.plus(Duration.ofHours(200))
        val events = Levels.doseEvents(te.group, mapOf("slow" to slow), listOf(dose), emptyList(), emptyList(), LevelMode.RECORDED, now, now, now, zone)
        assertEquals(1, events.size)
    }

    @Test
    fun sparseOpenEndedScheduleHasNoClearance() {
        val every100Days = item.copy(schedule = Schedule.EveryHours(2400.0, anchor))
        val m = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(every100Days), LevelMode.PLANNED, anchor, zone)!!
        assertNull(m.clearsAt)
    }
}
