package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val item = PlanItem("i", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))))
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()
    private fun log(key: String?, takenAt: Instant, status: LogStatus = LogStatus.TAKEN, id: String = key ?: "x") = DoseLog(
        id, "i", "c", key, null, takenAt, Amount(1.0, DoseUnit.MG), status,
        snapshot = DoseSnapshot("C", "C", BaseUnit.MG, PkParams(1.0, 10.0)), createdAt = takenAt,
    )

    @Test
    fun sortsIntoOverdueDueUpcomingAndDone() {
        val now = at("2026-09-25", "12:00")
        val logs = listOf(log(occurrenceKey("i", at("2026-09-25", "08:00")), at("2026-09-25", "08:05")))
        val agenda = buildAgenda(emptyList(), listOf(item), logs, now, zone)
        // Everything unconfirmed within the 48 h lookback (from 23 Sep 12:00).
        assertEquals(listOf(at("2026-09-23", "20:00"), at("2026-09-24", "08:00"), at("2026-09-24", "20:00")), agenda.overdue.map { it.at })
        assertEquals(emptyList(), agenda.due)
        assertEquals(listOf(at("2026-09-25", "20:00")), agenda.upcoming.map { it.at })
        assertEquals(1, agenda.done.size)
        assertEquals(3, agenda.pending.size)
    }

    @Test
    fun itemsWithinAnHourCountAsDue() {
        val agenda = buildAgenda(emptyList(), listOf(item), emptyList(), at("2026-09-25", "19:15"), zone)
        assertEquals(listOf(at("2026-09-25", "08:00"), at("2026-09-25", "20:00")), agenda.due.map { it.at })
    }

    @Test
    fun unscheduledLogsTodayAreDoneAndSkipsAreResolved() {
        val now = at("2026-09-25", "21:00")
        val logs = listOf(
            log(null, at("2026-09-25", "10:00"), id = "extra"),
            log(occurrenceKey("i", at("2026-09-24", "20:00")), at("2026-09-25", "07:00"), LogStatus.SKIPPED),
        )
        val agenda = buildAgenda(emptyList(), listOf(item), logs, now, zone)
        assertEquals(setOf("extra", occurrenceKey("i", at("2026-09-24", "20:00"))), agenda.done.map { it.log!!.id }.toSet())
        assertEquals(1, agenda.overdue.size) // 24 Sep 08:00; today's unconfirmed items are due, not overdue
        assertEquals(listOf(at("2026-09-25", "08:00"), at("2026-09-25", "20:00")), agenda.due.map { it.at })
    }

    @Test
    fun phaseProgressCountsDaysAndWeeks() {
        val phases = listOf(
            Phase("a", "Blast", LocalDate.parse("2026-09-01"), null, 0),
            Phase("b", "PCT", LocalDate.parse("2026-10-13"), null, 0),
        )
        val p = phaseProgress(phases, LocalDate.parse("2026-09-15"))!!
        assertEquals(15, p.day)
        assertEquals(3, p.week)
        assertEquals(42, p.totalDays)
    }

    @Test
    fun nextReminderSlotGroupsSameTimeAndSkipsConfirmed() {
        val other = item.copy(id = "j")
        val after = at("2026-09-25", "07:00")
        val first = occurrenceKey("i", at("2026-09-25", "08:00"))
        val (time, due) = nextReminderSlot(emptyList(), listOf(item, other), emptySet(), after, zone)!!
        assertEquals(at("2026-09-25", "08:00"), time)
        assertEquals(2, due.size)
        val (_, remaining) = nextReminderSlot(emptyList(), listOf(item, other), setOf(first), after, zone)!!
        assertEquals(listOf("j"), remaining.map { it.item.id })
    }

    @Test
    fun adherenceCountsOnlyPastOccurrences() {
        val now = at("2026-09-25", "12:00")
        val logs = listOf(
            log(occurrenceKey("i", at("2026-09-24", "08:00")), at("2026-09-24", "08:00")),
            log(occurrenceKey("i", at("2026-09-24", "20:00")), at("2026-09-24", "20:00"), LogStatus.SKIPPED),
        )
        val a = adherence(emptyList(), listOf(item), logs, at("2026-09-24", "00:00"), at("2026-09-26", "00:00"), now, zone).single()
        assertEquals(3, a.scheduled)
        assertEquals(1, a.taken)
        assertEquals(1, a.skipped)
    }
}
