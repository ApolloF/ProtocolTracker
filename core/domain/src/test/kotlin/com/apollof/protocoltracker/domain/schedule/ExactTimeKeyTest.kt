package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exact-time doses keep their identity when the time zone or the item's time changes (review 2026-10, H1). */
class ExactTimeKeyTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val london = ZoneId.of("Europe/London")
    private val day = LocalDate.parse("2026-10-05")

    private fun daily(vararg times: String, start: LocalDate = LocalDate.parse("2026-10-01")) = PlanItem(
        "i", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.Daily(times.map { Timing.At(LocalTime.parse(it)) }), startDate = start,
    )

    private fun at(date: LocalDate, time: String, zone: ZoneId) = ZonedDateTime.of(date, LocalTime.parse(time), zone).toInstant()

    private fun log(key: String, takenAt: Instant, id: String = key) = DoseLog(
        id, key.substringBefore('@'), "c", key, takenAt, takenAt, Amount(1.0, DoseUnit.MG), null, LogStatus.TAKEN,
        snapshot = DoseSnapshot("C", "C", CompoundCategory.SUPPORT, BaseUnit.MG, null), createdAt = takenAt,
    )

    private fun occurrenceOn(item: PlanItem, date: LocalDate, zone: ZoneId) = occurrences(
        emptyList(), listOf(item), date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant(), zone, IntervalAnchors.NONE,
    ).single()

    @Test
    fun aDoseTakenBeforeAZoneChangeStaysTakenAndIsNotRemindedAgain() {
        val item = daily("08:00", start = day)
        // Taken at 08:00 in Amsterdam; the phone then moves to London, where 08:00 is an hour later.
        val taken = log(occurrenceOn(item, day, amsterdam).key, at(day, "08:02", amsterdam))

        val agenda = buildAgenda(emptyList(), listOf(item), listOf(taken), at(day, "07:30", london), london, IntervalAnchors.NONE)
        assertEquals(AgendaStatus.TAKEN, agenda.groups.single().entries.single().status)
        assertEquals(emptyList(), agenda.extras)
        assertEquals(emptyList(), agenda.pending)

        val next = nextReminderSlot(emptyList(), listOf(item), setOf(taken.occurrenceKey!!), at(day, "07:30", london), london, IntervalAnchors.NONE)
        assertEquals(at(day.plusDays(1), "08:00", london), next?.first)

        val week = weekSummary(emptyList(), listOf(item), listOf(taken), weekStartOf(day), day.plusDays(1), london, IntervalAnchors.NONE)
        assertEquals(1, week.single { it.date == day }.taken)
    }

    @Test
    fun keysNameTheDateAndThePlannedTime() {
        val occ = occurrenceOn(daily("08:30"), day, amsterdam)
        assertEquals("i@2026-10-05/T0830", occ.key)
        assertEquals(OccurrenceRef.AtTime("i", day, LocalTime.of(8, 30)), parseOccurrenceKey(occ.key))
        // A time in the DST gap keeps its planned time in the key, though it is given an hour later.
        val gap = occurrenceOn(daily("02:30"), LocalDate.parse("2027-03-28"), amsterdam)
        assertEquals("i@2027-03-28/T0230", gap.key)
        assertTrue(gap.shifted)
        assertNull(parseOccurrenceKey("i@2026-10-05/T2460"))
        assertNull(parseOccurrenceKey("i@2026-10-05/T083"))
    }

    @Test
    fun instantKeysOfEarlierVersionsMoveToDateAndTime() {
        val item = daily("08:00", "20:00")
        val interval = PlanItem("h", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.EveryHours(84.0, at(day, "09:00", amsterdam)))
        val morning = log(occurrenceKey("i", at(day, "08:00", amsterdam)), at(day, "08:05", amsterdam))
        val elsewhere = log(occurrenceKey("i", at(day, "09:15", amsterdam)), at(day, "09:15", amsterdam))
        val hourly = log(occurrenceKey("h", at(day, "09:00", amsterdam)), at(day, "09:00", amsterdam))
        // The evening dose was already logged with the new key; its old twin must not take that key.
        val evening = log(timeOccurrenceKey("i", day, LocalTime.of(20, 0)), at(day, "20:01", amsterdam))
        val eveningOld = log(occurrenceKey("i", at(day, "20:00", amsterdam)), at(day, "20:00", amsterdam))

        val moved = rekeyInstantLogs(listOf(item, interval), listOf(morning, elsewhere, hourly, evening, eveningOld), amsterdam)
        assertEquals(listOf(morning.copy(occurrenceKey = "i@2026-10-05/T0800")), moved)
    }

    @Test
    fun aTimeEditMovesTheItemsLogsSoTakenDosesStayTaken() {
        val before = daily("08:00", "20:00")
        val after = daily("08:30", "20:00")
        val yesterday = day.minusDays(1)
        val logs = listOf(
            log(timeOccurrenceKey("i", yesterday, LocalTime.of(8, 0)), at(yesterday, "08:01", amsterdam)),
            log(timeOccurrenceKey("i", yesterday, LocalTime.of(20, 0)), at(yesterday, "20:01", amsterdam)),
        )
        val moved = rekeyForTimeEdit(before, after, logs)
        assertEquals(listOf("i@2026-10-04/T0830"), moved.map { it.occurrenceKey })
        val now = logs.map { log -> moved.firstOrNull { it.id == log.id } ?: log }

        val from = yesterday.atStartOfDay(amsterdam).toInstant()
        val adherence = adherence(emptyList(), listOf(after), now, from, day.atStartOfDay(amsterdam).toInstant(), at(day, "10:00", amsterdam), amsterdam, IntervalAnchors.NONE)
        assertEquals(2, adherence.single().taken)
    }

    @Test
    fun aTimeEditThatAddsOrDropsTimesMovesNothing() {
        val logs = listOf(log(timeOccurrenceKey("i", day, LocalTime.of(8, 0)), at(day, "08:01", amsterdam)))
        assertEquals(emptyList(), rekeyForTimeEdit(daily("08:00"), daily("08:30", "20:00"), logs))
        assertEquals(emptyList(), rekeyForTimeEdit(daily("08:00", "20:00"), daily("20:00"), logs))
        assertEquals(emptyList(), rekeyForTimeEdit(daily("08:00"), daily("08:00"), logs))
    }

    @Test
    fun aTimeEditNeverTakesAKeyAnotherLogHolds() {
        // An 08:30 log of the same day already exists (logged as an extra time earlier): the 08:00 log stays put.
        val logs = listOf(
            log(timeOccurrenceKey("i", day, LocalTime.of(8, 0)), at(day, "08:01", amsterdam)),
            log(timeOccurrenceKey("i", day, LocalTime.of(8, 30)), at(day, "08:31", amsterdam)),
        )
        assertEquals(emptyList(), rekeyForTimeEdit(daily("08:00"), daily("08:30"), logs))
    }

    @Test
    fun everyHoursKeysStayInstants() {
        val anchor = at(day, "09:00", amsterdam)
        val item = PlanItem("h", null, "c", Amount(1.0, DoseUnit.MG), schedule = Schedule.EveryHours(12.0, anchor))
        val occ = occurrences(emptyList(), listOf(item), anchor, anchor.plus(Duration.ofHours(1)), amsterdam, IntervalAnchors.NONE).single()
        assertEquals(occurrenceKey("h", anchor), occ.key)
    }
}
