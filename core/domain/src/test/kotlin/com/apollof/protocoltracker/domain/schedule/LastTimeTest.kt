package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LastTimeTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val weekly = PlanItem("test", null, "te", Amount(250.0, DoseUnit.MG), schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY), listOf(Timing.Slot(DaySlot.MORNING))))
    private fun at(date: String, time: String = "08:00") = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()
    private fun occ(date: String) = occurrences(emptyList(), listOf(weekly), at(date, "00:00"), at(date, "23:59"), zone, IntervalAnchors.NONE).single()
    private fun log(key: String?, takenAt: Instant, status: LogStatus = LogStatus.TAKEN) = DoseLog(
        key ?: "x", "test", "te", key, null, takenAt, Amount(250.0, DoseUnit.MG), null, status,
        snapshot = DoseSnapshot("Test E", "Test E", CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null), createdAt = takenAt,
    )

    private val today = occ("2026-09-28")
    private val previous = occ("2026-09-21")

    private fun hint(logs: List<DoseLog>, latest: DoseLog?, missed: Set<String> = emptySet()) = lastTimes(
        listOf(today), mapOf(today.key to previous), logs.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! },
        latest?.let { mapOf("te" to it) }.orEmpty(), missed,
    )[today.key]

    @Test
    fun findsTheDoseOfTheWeekBefore() {
        assertEquals(previous.key, previousOccurrence(emptyList(), today, zone, IntervalAnchors.NONE)!!.key)
    }

    @Test
    fun aWeeklyDoseMissedLastWeekSaysSoWithTheLastTakenDose() {
        val before = log(occ("2026-09-14").key, at("2026-09-14"))
        val h = hint(listOf(before), before)!!
        assertEquals(false, h.skipped)
        assertEquals(previous.key, h.previous.key)
        assertEquals(before, h.lastTaken)
    }

    @Test
    fun aSkippedDoseSaysSkipped() {
        val before = log(occ("2026-09-14").key, at("2026-09-14"))
        val h = hint(listOf(before, log(previous.key, at("2026-09-21"), LogStatus.SKIPPED)), before)!!
        assertTrue(h.skipped)
    }

    @Test
    fun noHintWhenTheDoseWasTakenOrCaughtUpOrAlreadyListedOrNeverTaken() {
        val taken = log(previous.key, at("2026-09-21"))
        assertNull(hint(listOf(taken), taken))
        // Taken late as an extra dose after the planned time.
        assertNull(hint(emptyList(), log(null, at("2026-09-22"))))
        // Already in the Missed card.
        val before = log(occ("2026-09-14").key, at("2026-09-14"))
        assertNull(hint(listOf(before), before, missed = setOf(previous.key)))
        // A new item that was never taken.
        assertNull(hint(emptyList(), null))
    }

    @Test
    fun noPreviousDoseBeforeThePhaseStarted() {
        val phases = listOf(Phase("p", "Blast", LocalDate.parse("2026-09-28"), null, 0))
        val inPhase = weekly.copy(phaseId = "p")
        val first = occurrences(phases, listOf(inPhase), at("2026-09-28", "00:00"), at("2026-09-29", "00:00"), zone, IntervalAnchors.NONE).single()
        assertNull(previousOccurrence(phases, first, zone, IntervalAnchors.NONE))
        assertNull(Schedule.AsNeeded.maxGap())
    }
}
