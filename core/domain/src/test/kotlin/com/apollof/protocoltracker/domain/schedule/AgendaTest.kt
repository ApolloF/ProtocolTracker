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

class AgendaTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun slots(vararg s: DaySlot) = s.map { Timing.Slot(it) }
    private val morning = PlanItem("m", null, "c", Amount(40.0, DoseUnit.MG), schedule = Schedule.Daily(slots(DaySlot.MORNING)), sortOrder = 1)
    private val pre = PlanItem("p", null, "c", Amount(50.0, DoseUnit.MG), schedule = Schedule.Daily(slots(DaySlot.PRE_WORKOUT)), sortOrder = 2)
    private val any = PlanItem("a", null, "c", Amount(5.0, DoseUnit.MG), schedule = Schedule.Daily(slots(DaySlot.ANY_TIME)), sortOrder = 3)
    private val timed = PlanItem("t", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.At(LocalTime.of(13, 0)))), sortOrder = 4)
    private val items = listOf(any, timed, pre, morning)

    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()
    private fun key(item: PlanItem, date: String, slot: DaySlot) = slotOccurrenceKey(item.id, LocalDate.parse(date), slot)
    private fun log(key: String?, takenAt: Instant, status: LogStatus = LogStatus.TAKEN, id: String = key ?: "x") = DoseLog(
        id, "i", "c", key, null, takenAt, Amount(1.0, DoseUnit.MG), null, status,
        snapshot = DoseSnapshot("C", "C", CompoundCategory.SUPPORT, BaseUnit.MG, null), createdAt = takenAt,
    )

    @Test
    fun groupsByPartOfDayWithAnyTimeLastAndDoneInPlace() {
        val now = at("2026-09-25", "09:00")
        val logs = listOf(log(key(morning, "2026-09-25", DaySlot.MORNING), at("2026-09-25", "08:12")))
        val agenda = buildAgenda(emptyList(), items, logs, now, zone, IntervalAnchors.NONE)
        assertEquals(listOf("Morning", "13:00", "Pre-workout", "Any time"), agenda.groups.map { it.label })
        val morningGroup = agenda.groups.first()
        assertEquals(AgendaStatus.TAKEN, morningGroup.entries.single().status)
        assertEquals(emptyList(), morningGroup.pending)
        // Slot doses are due all day: nothing today is missed, even after its nominal time.
        assertEquals(AgendaStatus.PENDING, agenda.groups[2].entries.single().status)
        assertEquals(4, agenda.scheduledToday)
        assertEquals(1, agenda.doneToday)
    }

    @Test
    fun earlierDaysWithoutLogsAreMissed() {
        val now = at("2026-09-25", "09:00")
        val agenda = buildAgenda(emptyList(), listOf(pre), emptyList(), now, zone, IntervalAnchors.NONE)
        // 48 h back from now: 23 Sep 17:00 and 24 Sep 17:00.
        assertEquals(listOf(at("2026-09-23", "17:00"), at("2026-09-24", "17:00")), agenda.missed.map { it.at })
        assertEquals(3, agenda.pending.size)
    }

    @Test
    fun unscheduledLogsAreExtrasAndLateLogsAreCaughtUp() {
        val now = at("2026-09-25", "21:00")
        val logs = listOf(
            log(null, at("2026-09-25", "10:00"), id = "extra"),
            log(key(pre, "2026-09-24", DaySlot.PRE_WORKOUT), at("2026-09-25", "07:00"), LogStatus.SKIPPED),
        )
        val agenda = buildAgenda(emptyList(), listOf(pre), logs, now, zone, IntervalAnchors.NONE)
        assertEquals(listOf("extra"), agenda.extras.map { it.log!!.id })
        assertEquals(listOf(key(pre, "2026-09-24", DaySlot.PRE_WORKOUT)), agenda.caughtUp.map { it.occurrence!!.key })
        assertEquals(AgendaStatus.SKIPPED, agenda.caughtUp.single().status)
        assertEquals(0, agenda.missed.size) // 23 Sep is outside 48 h; 24 Sep was skipped late
    }

    @Test
    fun entriesFollowPlanOrderWithinAGroup() {
        val a = any.copy(id = "a2", sortOrder = 0)
        val agenda = buildAgenda(emptyList(), listOf(any, a), emptyList(), at("2026-09-25", "09:00"), zone, IntervalAnchors.NONE)
        assertEquals(listOf("a2", "a"), agenda.groups.single().entries.map { it.occurrence!!.item.id })
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
        assertEquals(6, p.totalWeeks)
    }

    @Test
    fun weekSummaryStatesEachDay() {
        val twice = PlanItem("w", null, "c", Amount(500.0, DoseUnit.MG), schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), slots(DaySlot.ANY_TIME)))
        val daily = morning
        val logs = listOf(
            log(key(twice, "2026-09-21", DaySlot.ANY_TIME), at("2026-09-21", "10:00")),
            log(key(daily, "2026-09-21", DaySlot.MORNING), at("2026-09-21", "08:00")),
            log(key(daily, "2026-09-22", DaySlot.MORNING), at("2026-09-22", "08:00")),
            log(key(daily, "2026-09-25", DaySlot.MORNING), at("2026-09-25", "08:00")),
        )
        val week = weekSummary(emptyList(), listOf(twice, daily), logs, LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-25"), zone, IntervalAnchors.NONE)
        // Mon 21 … Sun 27; today is Friday 25.
        assertEquals(listOf("all taken", "all taken", "1 missed", "2 missed", "1 of 1", "1 due", "1 due"), week.map { it.summary })
    }

    @Test
    fun remindersUseReminderTimeAndSkipConfirmedAndQuietItems() {
        val other = pre.copy(id = "p2")
        val quiet = morning.copy(remind = false)
        val after = at("2026-09-25", "07:00")
        val (time, due) = nextReminderSlot(emptyList(), listOf(pre, other, quiet), emptySet(), after, zone, IntervalAnchors.NONE)!!
        assertEquals(at("2026-09-25", "17:00"), time)
        assertEquals(2, due.size)
        val (_, remaining) = nextReminderSlot(emptyList(), listOf(pre, other), setOf(key(pre, "2026-09-25", DaySlot.PRE_WORKOUT)), after, zone, IntervalAnchors.NONE)!!
        assertEquals(listOf("p2"), remaining.map { it.item.id })
        // Any-time doses are reminded at the evening reminder time, after their nominal noon time.
        val (anyTime, _) = nextReminderSlot(emptyList(), listOf(any), emptySet(), at("2026-09-25", "13:00"), zone, IntervalAnchors.NONE)!!
        assertEquals(at("2026-09-25", "19:00"), anyTime)
    }

    @Test
    fun adherenceCountsOnlyPastOccurrences() {
        val now = at("2026-09-25", "12:00")
        val logs = listOf(
            log(key(morning, "2026-09-24", DaySlot.MORNING), at("2026-09-24", "08:00")),
            log(key(pre, "2026-09-24", DaySlot.PRE_WORKOUT), at("2026-09-24", "20:00"), LogStatus.SKIPPED),
        )
        val a = adherence(emptyList(), listOf(morning), logs, at("2026-09-24", "00:00"), at("2026-09-26", "00:00"), now, zone, IntervalAnchors.NONE).single()
        assertEquals(2, a.scheduled) // 24 Sep and 25 Sep 08:00
        assertEquals(1, a.taken)
    }
}
