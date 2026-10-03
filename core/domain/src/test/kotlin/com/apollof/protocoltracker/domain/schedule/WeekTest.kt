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
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val daily = PlanItem("i", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING))))
    private val today = d("2026-10-01") // a Thursday; its week starts 28 Sep

    @Test
    fun runsFromTheFirstLogToFourWeeksAhead() {
        val weeks = stripWeeks(emptyList(), listOf(daily), firstLog = d("2026-09-16"), today = today)
        assertEquals(d("2026-09-14"), weeks.first())
        assertEquals(d("2026-10-26"), weeks.last())
        assertEquals(7, weeks.size)
    }

    @Test
    fun stopsAtThePlanEndAndKeepsThisWeek() {
        val ending = daily.copy(endDate = d("2026-10-07"))
        assertEquals(listOf(d("2026-09-28"), d("2026-10-05")), stripWeeks(emptyList(), listOf(ending), null, today))
        val ended = daily.copy(endDate = d("2026-09-01"))
        assertEquals(d("2026-09-28"), stripWeeks(emptyList(), listOf(ended), null, today).last())
    }

    @Test
    fun startsAtThePlanStartAndHoldsAPickedDay() {
        val phases = listOf(Phase("p", "Blast", d("2026-09-01"), null, 0))
        val weeks = stripWeeks(phases, listOf(daily.copy(phaseId = "p")), null, today, include = d("2027-01-04"))
        assertEquals(d("2026-08-31"), weeks.first())
        assertEquals(d("2027-01-04"), weeks.last())
    }

    @Test
    fun aPickedDayFarAwayDoesNotStretchTheStrip() {
        val plain = stripWeeks(emptyList(), listOf(daily), null, today)
        assertEquals(plain, stripWeeks(emptyList(), listOf(daily), null, today, include = d("1950-06-01")))
        assertEquals(plain, stripWeeks(emptyList(), listOf(daily), null, today, include = d("2100-01-01")))
    }

    @Test
    fun goesBackAtMostTwoYears() {
        val weeks = stripWeeks(emptyList(), listOf(daily), firstLog = d("2020-01-01"), today = today)
        assertEquals(d("2026-09-28").minusWeeks(104), weeks.first())
    }

    @Test
    fun summariesOfManyWeeksMatchOneWeekAtATime() {
        val daily = this.daily.copy(startDate = d("2026-09-01"))
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val today = LocalDate.parse("2026-09-24")
        val weeks = listOf("2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28").map(LocalDate::parse)
        val all = weekSummaries(emptyList(), listOf(daily), emptyList(), weeks, today, zone, IntervalAnchors.NONE)
        assertEquals(weeks, all.keys.toList())
        for (w in weeks) assertEquals(weekSummary(emptyList(), listOf(daily), emptyList(), w, today, zone, IntervalAnchors.NONE), all[w])
        assertEquals(DayMark.MISSED, all.getValue(weeks[0])[1].mark)
        assertEquals(DayMark.NONE, all.getValue(weeks[0])[0].mark)
        assertEquals(DayMark.FUTURE, all.getValue(weeks[4])[0].mark)
    }

    @Test
    fun daysBeforeTheHistoryStartsAreNotMissed() {
        val daily = this.daily.copy(startDate = d("2026-09-01"))
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val today = d("2026-09-24")
        val week = weekSummary(emptyList(), listOf(daily), emptyList(), d("2026-09-14"), today, zone, IntervalAnchors.NONE, countFrom = d("2026-09-17"))
        assertEquals(DayMark.UNTRACKED, week[0].mark) // Mon 14th
        assertEquals(0, week[0].missed)
        assertEquals("–", week[0].cell)
        assertEquals("before your first log", week[0].summary)
        assertEquals(DayMark.MISSED, week[3].mark) // Thu 17th, the first day of the history
    }

    @Test
    fun theHistoryStartsAtTheFirstLogOrToday() {
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val night = SlotTimes(dayStart = java.time.LocalTime.of(4, 0))
        val at = java.time.ZonedDateTime.of(d("2026-09-20"), java.time.LocalTime.of(1, 30), zone).toInstant()
        val log = DoseLog(
            "x", null, "c", null, null, at, Amount(1.0, DoseUnit.MG), null, LogStatus.TAKEN,
            snapshot = DoseSnapshot("C", "C", CompoundCategory.SUPPORT, BaseUnit.MG, null), createdAt = at,
        )
        assertEquals(d("2026-09-19"), trackedFrom(listOf(log), today, zone, night))
        assertEquals(today, trackedFrom(emptyList(), today, zone, night))
    }
}
