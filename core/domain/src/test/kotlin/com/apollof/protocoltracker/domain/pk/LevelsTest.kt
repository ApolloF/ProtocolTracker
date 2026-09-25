package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.schedule.occurrenceKey
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelsTest {
    private val zone = ZoneId.of("UTC")
    private val te = Presets.byId("preset:test-enan")!!
    private val compounds = mapOf(te.id to te)
    private val anchor = Instant.parse("2026-01-01T09:00:00Z")
    private val item = PlanItem("i", null, te.id, Amount(0.5, DoseUnit.ML), Formulation(perMl = 250.0), Schedule.EveryHours(84.0, anchor))

    private fun log(at: Instant, key: String? = null) = DoseLog(
        "l$at", "i", te.id, key, at, at, Amount(125.0, DoseUnit.MG), LogStatus.TAKEN,
        snapshot = DoseSnapshot(te.name, te.group, te.baseUnit, te.pk), createdAt = at,
    )

    @Test
    fun combinedUsesLogsBeforeNowAndPlanAfterWithoutDoubleCounting() {
        val now = anchor.plus(Duration.ofHours(200))
        val logs = listOf(log(anchor, occurrenceKey("i", anchor)), log(anchor.plus(Duration.ofHours(90))))
        val events = Levels.doseEvents(te.group, compounds, logs, emptyList(), listOf(item), LevelMode.COMBINED, anchor, now.plus(Duration.ofDays(7)), now, zone)
        assertEquals(2, events.count { !it.planned })
        assertTrue(events.filter { it.planned }.all { it.dose.atMs > now.toEpochMilli() })
        assertEquals(125.0, events.first { it.planned }.dose.amount, 1e-9) // 0.5 mL × 250 mg/mL
    }

    @Test
    fun steadyStateMatchesAnalyticForE3_5D() {
        val ss = assertNotNull(Levels.steadyState(listOf(item), compounds, anchor, zone))
        val pk = te.pk
        val d = 125.0 * pk.activeFraction
        val tau = 84.0
        fun css(t: Double) = d * pk.ka / (pk.ka - pk.ke) *
            (kotlin.math.exp(-pk.ke * t) / (1 - kotlin.math.exp(-pk.ke * tau)) - kotlin.math.exp(-pk.ka * t) / (1 - kotlin.math.exp(-pk.ka * tau)))
        assertTrue(abs(ss.trough - css(0.0)) / css(0.0) < 0.01, "trough ${ss.trough} vs ${css(0.0)}")
        assertTrue(abs(ss.average - d / (pk.ke * tau)) / (d / (pk.ke * tau)) < 0.01)
    }

    @Test
    fun clearanceOnlyWhenPlanEnds() {
        val now = anchor
        val open = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(item), LevelMode.PLANNED, now, zone)!!
        assertNull(open.clearsAt)
        val ending = item.copy(endDate = java.time.LocalDate.parse("2026-02-01"))
        val closed = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(ending), LevelMode.PLANNED, now, zone)!!
        val clears = assertNotNull(closed.clearsAt)
        // Apparent half-life 189 h → ~3.3 half-lives (~26 days) after the last dose, give or take accumulation.
        assertTrue(clears > Instant.parse("2026-02-15T00:00:00Z") && clears < Instant.parse("2026-03-20T00:00:00Z"), "$clears")
    }

    @Test
    fun samplingIncludesPeaksOfShortOrals() {
        val anastrozole = Presets.byId("preset:anastrozole")!!
        val dose = PkDose(anchor.toEpochMilli(), 1.0, anastrozole.pk)
        val series = Levels.sample(listOf(DoseEvent(dose, false)), anchor.minus(Duration.ofDays(1)), anchor.plus(Duration.ofDays(90)), points = 50)
        val peakExact = PkEngine.levelAt(listOf(dose), anchor.toEpochMilli() + (PkEngine.tmaxHours(anastrozole.pk.ka, anastrozole.pk.ke) * 3_600_000).toLong())
        assertEquals(peakExact, series.values.max(), 1e-12)
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
    }

    @Test
    fun timeToSteadyUsesOnlyCompoundsInUse() {
        val all = Presets.all.associateBy { it.id } // includes slow testosterone undecanoate
        val m = Levels.metrics(te.group, all, emptyList(), emptyList(), listOf(item), LevelMode.PLANNED, anchor, zone)!!
        val expectedH = kotlin.math.ln(10.0) / te.pk.ke
        assertEquals(expectedH, m.timeTo90.toMinutes() / 60.0, 0.1)
    }

    @Test
    fun lookbackKeepsDosesStillAbsorbing() {
        val slowRelease = te.copy(id = "slow", pk = com.apollof.protocoltracker.domain.model.PkParams(100.0, 1.0))
        val dose = log(anchor).copy(compoundId = "slow", snapshot = DoseSnapshot("S", te.group, te.baseUnit, slowRelease.pk))
        val now = anchor.plus(Duration.ofHours(20))
        val events = Levels.doseEvents(te.group, mapOf("slow" to slowRelease), listOf(dose), emptyList(), emptyList(), LevelMode.RECORDED, now, now, now, zone)
        assertEquals(1, events.size)
    }

    @Test
    fun steadyStateCoversLongDosingIntervals() {
        // Every 14 days with short kinetics: a 7-day window would miss half the cycle.
        val short = te.copy(id = "short", pk = com.apollof.protocoltracker.domain.model.PkParams(1.0, 12.0))
        val fortnightly = item.copy(compoundId = "short", schedule = Schedule.EveryHours(336.0, anchor))
        val ss = assertNotNull(Levels.steadyState(listOf(fortnightly), mapOf("short" to short), anchor, zone))
        val expectedAvg = 125.0 / (short.pk.ke * 336.0)
        assertTrue(abs(ss.average - expectedAvg) / expectedAvg < 0.02, "avg ${ss.average} vs $expectedAvg")
    }

    @Test
    fun sparseOpenEndedScheduleHasNoClearance() {
        val every100Days = item.copy(schedule = Schedule.EveryHours(2400.0, anchor))
        val m = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(every100Days), LevelMode.PLANNED, anchor, zone)!!
        assertNull(m.clearsAt)
    }

    @Test
    fun planEndingAfterOneYearStillReportsClearance() {
        val daily = item.copy(schedule = Schedule.EveryHours(24.0, anchor), endDate = java.time.LocalDate.parse("2026-12-20"))
        val m = Levels.metrics(te.group, compounds, emptyList(), emptyList(), listOf(daily), LevelMode.PLANNED, anchor, zone)!!
        assertTrue(assertNotNull(m.clearsAt) > Instant.parse("2026-12-20T00:00:00Z"))
    }

    @Test
    fun presetsAreValid() {
        assertTrue(Presets.all.size >= 20)
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
    }
}
