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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LevelAdjustmentsTest {
    @Test
    fun factorIsOnePlusPercent() {
        val a = LevelAdjustments.of("Testosterone" to 25, "Estradiol" to -40)
        assertEquals(1.25, a.factor("Testosterone"), 1e-12)
        assertEquals(0.6, a.factor("Estradiol"), 1e-12)
        assertEquals(1.0, a.factor("Nandrolone"), 1e-12)
        assertEquals(0.0, LevelAdjustments.factorOf(-100), 1e-12)
        assertEquals(2.0, LevelAdjustments.factorOf(100), 1e-12)
    }

    @Test
    fun withClampsAndZeroRemoves() {
        val a = LevelAdjustments.NONE.with("Testosterone", 250).with("Estradiol", -300)
        assertEquals(100, a.percent("Testosterone"))
        assertEquals(-100, a.percent("Estradiol"))
        assertEquals(mapOf("Estradiol" to -100), a.with("Testosterone", 0).byGroup)
        assertEquals(LevelAdjustments.NONE, a.with("Testosterone", 0).with("Estradiol", 0))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val a = LevelAdjustments.of("Testosterone" to 15, "S-23" to -40, "Name with \"quotes\"" to 3)
        assertEquals(a, LevelAdjustments.decode(a.encode()))
        assertEquals("", LevelAdjustments.NONE.encode())
        assertEquals(LevelAdjustments.NONE, LevelAdjustments.decode(""))
        assertEquals(LevelAdjustments.NONE, LevelAdjustments.decode(null))
    }

    @Test
    fun badStoredValuesNeverThrow() {
        assertEquals(LevelAdjustments.NONE, LevelAdjustments.decode("not json"))
        assertEquals(LevelAdjustments.NONE, LevelAdjustments.decode("[1, 2]"))
        assertEquals(LevelAdjustments.NONE, LevelAdjustments.decode("12"))
        val read = LevelAdjustments.decode("""{"A": 500, "B": -1e9, "C": "15", "D": null, "E": 12.6, "": 10, "F": {"x": 1}, "G": 0, "H": true}""")
        assertEquals(mapOf("A" to 100, "B" to -100, "E" to 13), read.byGroup)
    }

    @Test
    fun labelShowsTheSign() {
        assertEquals("+15%", LevelAdjustments.label(15))
        assertEquals("−40%", LevelAdjustments.label(-40))
        assertNull(LevelAdjustments.label(0))
    }

    private val zone = ZoneId.of("UTC")
    private val te = Presets.byId("preset:test-enan")!!
    private val compounds = mapOf(te.id to te)
    private val anchor = Instant.parse("2026-01-01T09:00:00Z")
    private val item = PlanItem(
        "i", null, te.id, Amount(250.0, DoseUnit.MG), DoseBasis.PER_DOSE, Formulation(perMl = 250.0), Schedule.EveryHours(168.0, anchor),
    )

    private fun log(at: Instant) = DoseLog(
        "l$at", null, te.id, null, at, at, Amount(250.0, DoseUnit.MG), Amount(250.0, DoseUnit.MG), LogStatus.TAKEN,
        snapshot = DoseSnapshot(te.displayName, te.group, te.category, te.baseUnit, te.pk), createdAt = at,
    )

    @Test
    fun curveNowSteadyStateAndClearanceUseTheAdjustedScale() {
        val now = anchor.plus(Duration.ofDays(30))
        val logs = listOf(log(anchor.plus(Duration.ofDays(28))))
        val adjust = LevelAdjustments.of(te.group to -40)
        val plain = assertNotNull(Levels.metrics(te.group, compounds, logs, emptyList(), listOf(item), LevelMode.COMBINED, now, zone))
        val adjusted = assertNotNull(Levels.metrics(te.group, compounds, logs, emptyList(), listOf(item), LevelMode.COMBINED, now, zone, adjustments = adjust))
        assertEquals(plain.current * 0.6, adjusted.current, 1e-9 * plain.current)
        val ss = assertNotNull(plain.steadyState)
        val ssAdjusted = assertNotNull(adjusted.steadyState)
        assertEquals(ss.peak * 0.6, ssAdjusted.peak, 1e-9 * ss.peak)
        assertEquals(ss.trough * 0.6, ssAdjusted.trough, 1e-9 * ss.trough)
        assertEquals(ss.average * 0.6, ssAdjusted.average, 1e-9 * ss.average)
        // Relative figures do not move.
        assertEquals(plain.timeTo90, adjusted.timeTo90)
        assertEquals(plain.clearsAt, adjusted.clearsAt)

        val from = anchor
        val to = now.plus(Duration.ofDays(14))
        val a = assertNotNull(Levels.series(te.group, compounds, logs, emptyList(), listOf(item), LevelMode.COMBINED, from, to, now, zone))
        val b = assertNotNull(Levels.series(te.group, compounds, logs, emptyList(), listOf(item), LevelMode.COMBINED, from, to, now, zone, adjustments = adjust))
        assertEquals(a.series.size, b.series.size)
        for (i in 0 until a.series.size) assertEquals(a.series.values[i] * 0.6, b.series.values[i], 1e-9 * (1 + a.series.values[i]))
        // Logged doses keep their snapshot: the adjustment is applied at plotting time only.
        assertEquals(te.pk, logs.single().snapshot.pk)
    }

    @Test
    fun otherGroupsAreNotAdjusted() {
        val scale = assertNotNull(Levels.scale(te.group, compounds, emptyList(), listOf(item), LevelAdjustments.of("Estradiol" to 50)))
        assertEquals(1.0, scale.factor)
    }
}
