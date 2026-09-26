package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Interval schedules restart from the last taken dose ("count from last dose"). */
class ReanchorTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val any = listOf(Timing.Slot(DaySlot.ANY_TIME))

    private fun day(d: String) = LocalDate.parse(d)
    private fun at(date: String, time: String = "00:00") = ZonedDateTime.of(day(date), LocalTime.parse(time), zone).toInstant()
    private fun item(schedule: Schedule, id: String = "hcg") = PlanItem(id, null, "c", Amount(500.0, DoseUnit.IU), schedule = schedule)
    private fun every3(fromLastDose: Boolean = true) = item(Schedule.EveryNDays(3, day("2026-09-01"), any, fromLastDose))
    private fun slotKey(date: String, slot: DaySlot = DaySlot.ANY_TIME, id: String = "hcg") = slotOccurrenceKey(id, day(date), slot)
    private fun log(key: String, takenAt: Instant, status: LogStatus = LogStatus.TAKEN, itemId: String? = "hcg") = DoseLog(
        key, itemId, "c", key, null, takenAt, Amount(500.0, DoseUnit.IU), null, status,
        snapshot = DoseSnapshot("hCG", "hCG", CompoundCategory.SUPPORT, BaseUnit.IU, null), createdAt = takenAt,
    )
    private fun dates(item: PlanItem, logs: List<DoseLog>, from: String = "2026-09-01", to: String = "2026-09-20") =
        occurrences(emptyList(), listOf(item), at(from), at(to), zone, IntervalAnchors.from(logs)).map { it.localDate.toString() }

    @Test
    fun lateDoseMovesTheNextDose() {
        val logs = listOf(log(slotKey("2026-09-01"), at("2026-09-01", "10:00")), log(slotKey("2026-09-04"), at("2026-09-05", "10:00")))
        assertEquals(listOf("2026-09-01", "2026-09-04", "2026-09-08", "2026-09-11", "2026-09-14", "2026-09-17"), dates(every3(), logs))
    }

    @Test
    fun switchedOffKeepsThePlanGrid() {
        val logs = listOf(log(slotKey("2026-09-04"), at("2026-09-05", "10:00")))
        assertEquals(listOf("2026-09-01", "2026-09-04", "2026-09-07", "2026-09-10", "2026-09-13", "2026-09-16", "2026-09-19"), dates(every3(false), logs))
    }

    @Test
    fun skippedAndUnscheduledDosesDoNotMoveThePlan() {
        val logs = listOf(
            log(slotKey("2026-09-04"), at("2026-09-05", "10:00"), LogStatus.SKIPPED),
            log(slotKey("2026-09-07"), at("2026-09-08", "10:00"), itemId = null),
        )
        assertEquals(dates(every3(false), emptyList()), dates(every3(), logs))
    }

    @Test
    fun earlyDoseKeepsItsOwnDoseAndCountsFromTheDayTaken() {
        val logs = listOf(log(slotKey("2026-09-04"), at("2026-09-03", "20:00")))
        assertEquals(listOf("2026-09-01", "2026-09-04", "2026-09-06", "2026-09-09"), dates(every3(), logs, to = "2026-09-10"))
    }

    @Test
    fun dosesKeyedBeforeTheAnchorAreIgnored() {
        val moved = item(Schedule.EveryNDays(3, day("2026-09-10"), any))
        val logs = listOf(log(slotKey("2026-09-04"), at("2026-09-05", "10:00")))
        assertEquals(listOf("2026-09-10", "2026-09-13", "2026-09-16", "2026-09-19"), dates(moved, logs))
    }

    @Test
    fun everyDayIgnoresTheSwitch() {
        val daily = item(Schedule.EveryNDays(1, day("2026-09-01"), any))
        val logs = listOf(log(slotKey("2026-09-03"), at("2026-09-04", "10:00")))
        assertEquals(19, dates(daily, logs).size)
    }

    @Test
    fun onlyTheFirstDoseOfADayCounts() {
        val twice = item(Schedule.EveryNDays(3, day("2026-09-01"), listOf(Timing.Slot(DaySlot.MORNING), Timing.Slot(DaySlot.BEDTIME))))
        // Morning on time, bedtime dose logged after midnight: the grid stays.
        val logs = listOf(
            log(slotKey("2026-09-04", DaySlot.MORNING), at("2026-09-04", "08:00")),
            log(slotKey("2026-09-04", DaySlot.BEDTIME), at("2026-09-05", "00:30")),
        )
        val occ = occurrences(emptyList(), listOf(twice), at("2026-09-01"), at("2026-09-11"), zone, IntervalAnchors.from(logs))
        assertEquals(listOf("2026-09-01", "2026-09-04", "2026-09-07", "2026-09-10"), occ.map { it.localDate.toString() }.distinct())
        assertEquals(8, occ.size)
    }

    @Test
    fun narrowRangeMatchesTheSliceOfAWideRange() {
        val logs = listOf(log(slotKey("2026-09-04"), at("2026-09-05", "10:00")), log(slotKey("2026-09-11"), at("2026-09-12", "09:00")))
        val wide = dates(every3(), logs, "2026-08-01", "2026-10-01")
        val narrow = dates(every3(), logs, "2026-09-09", "2026-09-19")
        assertEquals(wide.filter { it >= "2026-09-09" && it < "2026-09-19" }, narrow)
    }

    @Test
    fun everyHoursCountsFromTheMinuteTaken() {
        val anchor = at("2026-09-01", "10:00")
        val s = item(Schedule.EveryHours(84.0, anchor))
        val second = anchor.plus(Duration.ofHours(84))
        val takenAt = second.plus(Duration.ofMinutes(125)).plusSeconds(42)
        val logs = listOf(log(occurrenceKey("hcg", second), takenAt))
        val occ = occurrences(emptyList(), listOf(s), at("2026-09-01"), at("2026-09-13"), zone, IntervalAnchors.from(logs)).map { it.at }
        val restart = takenAt.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        assertEquals(listOf(anchor, second, restart.plus(Duration.ofHours(84)), restart.plus(Duration.ofHours(168))), occ)
    }

    @Test
    fun everyHoursAcrossDstStaysElapsedTime() {
        val anchor = at("2026-10-20", "09:00")
        val s = item(Schedule.EveryHours(84.0, anchor))
        val second = anchor.plus(Duration.ofHours(84))
        val logs = listOf(log(occurrenceKey("hcg", second), second.plus(Duration.ofHours(3))))
        val occ = occurrences(emptyList(), listOf(s), at("2026-10-20"), at("2026-11-10"), zone, IntervalAnchors.from(logs)).map { it.at }
        assertTrue(occ.drop(1).zipWithNext().drop(1).all { (a, b) -> Duration.between(a, b).toHours() == 84L })
        assertEquals(second.plus(Duration.ofHours(87)), occ[2])
    }

    @Test
    fun lateLogIsCaughtUpNotMissedAndTodayFollowsTheNewGrid() {
        val logs = listOf(log(slotKey("2026-09-04"), at("2026-09-05", "10:00")))
        val agenda = buildAgenda(emptyList(), listOf(every3()), logs, at("2026-09-05", "12:00"), zone, IntervalAnchors.from(logs))
        assertEquals(listOf(slotKey("2026-09-04")), agenda.caughtUp.map { it.occurrence!!.key })
        assertEquals(AgendaStatus.TAKEN, agenda.caughtUp.single().status)
        assertTrue(agenda.missed.isEmpty())
        assertTrue(agenda.extras.isEmpty())
        assertTrue(agenda.groups.isEmpty())

        val next = nextReminderSlot(emptyList(), listOf(every3()), setOf(slotKey("2026-09-04")), at("2026-09-05", "12:00"), zone, IntervalAnchors.from(logs))!!
        assertEquals(day("2026-09-08"), next.second.single().localDate)
    }

    @Test
    fun weekSummaryDropsTheOldDate() {
        val logs = listOf(log(slotKey("2026-09-04"), at("2026-09-05", "10:00")))
        val week = weekSummary(emptyList(), listOf(every3()), logs, day("2026-08-31"), day("2026-09-06"), zone, IntervalAnchors.from(logs))
        assertEquals(listOf(0, 1, 0, 0, 1, 0, 0), week.map { it.scheduled })
        assertFalse(week.any { it.missed > 0 && it.date == day("2026-09-04") })
    }

    @Test
    fun oldScheduleJsonDefaultsToCountingFromLastDose() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"type":"every_n_days","n":3,"anchor":"2026-09-01","timings":[{"type":"slot","slot":"ANY_TIME"}]}"""
        val decoded = json.decodeFromString(Schedule.serializer(), old) as Schedule.EveryNDays
        assertTrue(decoded.fromLastDose)
        val hours = json.decodeFromString(Schedule.serializer(), """{"type":"every_hours","hours":84.0,"anchor":"2026-09-01T08:00:00Z"}""")
        assertTrue((hours as Schedule.EveryHours).fromLastDose)
    }
}
