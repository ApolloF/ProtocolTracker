package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.Timing.At
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

class OccurrencesTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val nine = LocalTime.of(9, 0)

    private fun item(schedule: Schedule, phaseId: String? = null, id: String = "i", start: LocalDate? = null, end: LocalDate? = null) =
        PlanItem(id, phaseId, "c", Amount(100.0, DoseUnit.MG), schedule = schedule, startDate = start, endDate = end)

    private fun at(date: String, time: String = "00:00") = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()
    private fun phase(id: String, start: String, end: String? = null) =
        Phase(id, id, LocalDate.parse(start), end?.let(LocalDate::parse), 0xFF000000)

    @Test
    fun dailyMultipleTimes() {
        val occ = occurrences(emptyList(), listOf(item(Schedule.Daily(listOf(At(LocalTime.of(20, 0)), At(nine))))), at("2026-03-01"), at("2026-03-04"), zone, IntervalAnchors.NONE)
        assertEquals(6, occ.size)
        assertEquals(at("2026-03-01", "09:00"), occ.first().at)
        assertTrue(occ.zipWithNext().all { (a, b) -> a.at < b.at })
    }

    @Test
    fun weekdaysOnly() {
        val occ = occurrences(emptyList(), listOf(item(Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(At(nine))))),
            at("2026-09-01"), at("2026-10-01"), zone, IntervalAnchors.NONE)
        assertTrue(occ.all { it.localDate.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY) })
        assertEquals(8, occ.size) // Sept 2026: 4 Mondays + 4 Thursdays
    }

    @Test
    fun everyNDaysJumpsFromAnchorWithoutScanning() {
        val s = Schedule.EveryNDays(3, LocalDate.parse("2020-01-01"), listOf(At(nine)))
        val occ = occurrences(emptyList(), listOf(item(s)), at("2026-09-01"), at("2026-09-15"), zone, IntervalAnchors.NONE)
        assertTrue(occ.all { java.time.temporal.ChronoUnit.DAYS.between(s.anchor, it.localDate) % 3 == 0L })
        assertTrue(occ.size in 4..5)
    }

    @Test
    fun everyNDaysBeforeAnchorStartsAtAnchor() {
        val s = Schedule.EveryNDays(2, LocalDate.parse("2026-09-10"), listOf(At(nine)))
        val occ = occurrences(emptyList(), listOf(item(s)), at("2026-09-01"), at("2026-09-15"), zone, IntervalAnchors.NONE)
        assertEquals(listOf("2026-09-10", "2026-09-12", "2026-09-14"), occ.map { it.localDate.toString() })
    }

    @Test
    fun every84HoursIsInstantBasedAcrossDst() {
        val anchor = at("2026-10-20", "09:00")
        val occ = occurrences(emptyList(), listOf(item(Schedule.EveryHours(84.0, anchor))), at("2026-10-19"), at("2026-11-05"), zone, IntervalAnchors.NONE)
        assertTrue(occ.zipWithNext().all { (a, b) -> java.time.Duration.between(a.at, b.at).toHours() == 84L })
        assertEquals(anchor, occ.first().at)
        // DST ends 25 Oct: wall-clock time moves by an hour, elapsed interval does not.
        assertEquals(LocalTime.of(8, 0), occ[2].at.atZone(zone).toLocalTime())
    }

    @Test
    fun springForwardGapShiftsAndFlags() {
        val occ = occurrences(emptyList(), listOf(item(Schedule.Daily(listOf(At(LocalTime.of(2, 30)))))), at("2026-03-28"), at("2026-03-31"), zone, IntervalAnchors.NONE)
        val gapDay = occ.single { it.localDate == LocalDate.parse("2026-03-29") }
        assertTrue(gapDay.shifted)
        assertEquals(LocalTime.of(3, 30), gapDay.at.atZone(zone).toLocalTime())
        assertEquals(1, occ.count { it.shifted })
    }

    @Test
    fun springForwardGapNeverDuplicatesAnOccurrence() {
        val times = listOf(At(LocalTime.of(2, 30)), At(LocalTime.of(3, 30)))
        val occ = occurrences(emptyList(), listOf(item(Schedule.Daily(times))), at("2026-03-29"), at("2026-03-30"), zone, IntervalAnchors.NONE)
        assertEquals(1, occ.size) // 02:30 moves to 03:30 and collapses onto the 03:30 dose
        assertEquals(occ.map { it.key }.toSet().size, occ.size)
    }

    @Test
    fun invalidSchedulesAreSkippedNotLooped() {
        val bad = listOf(item(Schedule.EveryNDays(0, LocalDate.parse("2026-01-01"), listOf(At(nine)))), item(Schedule.EveryHours(0.0, Instant.EPOCH), id = "h"))
        assertEquals(emptyList(), occurrences(emptyList(), bad, at("2026-09-01"), at("2026-09-10"), zone, IntervalAnchors.NONE))
    }

    @Test
    fun fallBackOverlapUsesEarlierOffsetOnce() {
        val occ = occurrences(emptyList(), listOf(item(Schedule.Daily(listOf(At(LocalTime.of(2, 30)))))), at("2026-10-25"), at("2026-10-26"), zone, IntervalAnchors.NONE)
        assertEquals(1, occ.size)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), occ.single().at)
    }

    @Test
    fun phasesSwitchByDateAndAlwaysItemsContinue() {
        val phases = listOf(phase("cruise", "2026-09-01"), phase("blast", "2026-09-08", "2026-09-10"))
        val items = listOf(
            item(Schedule.Daily(listOf(At(nine))), "cruise", "c1"),
            item(Schedule.Daily(listOf(At(nine))), "blast", "b1"),
            item(Schedule.Daily(listOf(At(nine))), null, "always"),
        )
        val occ = occurrences(phases, items, at("2026-09-06"), at("2026-09-13"), zone, IntervalAnchors.NONE)
        fun on(id: String) = occ.filter { it.item.id == id }.map { it.localDate.dayOfMonth }
        assertEquals(listOf(6, 7), on("c1"))          // cruise ends when blast starts
        assertEquals(listOf(8, 9, 10), on("b1"))      // blast has an explicit end
        assertEquals((6..12).toList(), on("always"))  // 11–12: no phase active, always items still fire
    }

    @Test
    fun timelineResolvesGapsAndEnds() {
        val t = PhaseTimeline(listOf(phase("a", "2026-01-01", "2026-01-10"), phase("b", "2026-02-01")))
        assertEquals("a", t.phaseOn(LocalDate.parse("2026-01-10"))?.id)
        assertNull(t.phaseOn(LocalDate.parse("2026-01-11")))
        assertEquals("b", t.phaseOn(LocalDate.parse("2027-01-01"))?.id)
        assertNull(t.phaseOn(LocalDate.parse("2025-12-31")))
    }

    @Test
    fun itemDateBoundsAndDisabledAndAsNeeded() {
        val items = listOf(
            item(Schedule.Daily(listOf(At(nine))), id = "bounded", start = LocalDate.parse("2026-09-03"), end = LocalDate.parse("2026-09-04")),
            item(Schedule.Daily(listOf(At(nine))), id = "off").copy(enabled = false),
            item(Schedule.AsNeeded, id = "prn"),
        )
        val occ = occurrences(emptyList(), items, at("2026-09-01"), at("2026-09-10"), zone, IntervalAnchors.NONE)
        assertEquals(listOf("bounded", "bounded"), occ.map { it.item.id })
    }

    @Test
    fun keysAreStable() {
        val i = item(Schedule.Daily(listOf(At(nine))))
        val a = occurrences(emptyList(), listOf(i), at("2026-09-01"), at("2026-09-03"), zone, IntervalAnchors.NONE)
        val b = occurrences(emptyList(), listOf(i), at("2026-08-30"), at("2026-09-05"), zone, IntervalAnchors.NONE)
        assertTrue(b.map { it.key }.containsAll(a.map { it.key }))
    }

    @Test
    fun describesSchedules() {
        assertEquals("Every 3.5 days", describeSchedule(Schedule.EveryHours(84.0, Instant.EPOCH)))
        assertEquals("Every other day · 09:00", describeSchedule(Schedule.EveryNDays(2, LocalDate.EPOCH, listOf(At(nine)))))
        assertEquals("Mon, Thu · 09:00", describeSchedule(Schedule.Weekdays(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY), listOf(At(nine))), java.util.Locale.ENGLISH))
    }

    @Test
    fun slotKeysAreDateBasedAndSurviveSlotTimeChanges() {
        val i = item(Schedule.Daily(listOf(Timing.Slot(DaySlot.PRE_WORKOUT), Timing.Slot(DaySlot.ANY_TIME))))
        val early = occurrences(emptyList(), listOf(i), at("2026-09-25"), at("2026-09-26"), zone, IntervalAnchors.NONE)
        val moved = SlotTimes(mapOf(DaySlot.PRE_WORKOUT to LocalTime.of(6, 30)))
        val late = occurrences(emptyList(), listOf(i), at("2026-09-25"), at("2026-09-26"), zone, IntervalAnchors.NONE, moved)
        assertEquals(setOf("i@2026-09-25/PRE_WORKOUT", "i@2026-09-25/ANY_TIME"), early.map { it.key }.toSet())
        assertEquals(early.map { it.key }.toSet(), late.map { it.key }.toSet())
        assertEquals(at("2026-09-25", "17:00"), early.single { it.slot == DaySlot.PRE_WORKOUT }.at)
        assertEquals(at("2026-09-25", "06:30"), late.single { it.slot == DaySlot.PRE_WORKOUT }.at)
    }

    @Test
    fun anyTimeRemindsInTheEveningAndRemindOffHasNoReminder() {
        val i = item(Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))))
        val occ = occurrences(emptyList(), listOf(i), at("2026-09-25"), at("2026-09-26"), zone, IntervalAnchors.NONE).single()
        assertEquals(at("2026-09-25", "12:00"), occ.at)
        assertEquals(at("2026-09-25", "19:00"), occ.remindAt)
        val quiet = occurrences(emptyList(), listOf(i.copy(remind = false)), at("2026-09-25"), at("2026-09-26"), zone, IntervalAnchors.NONE).single()
        assertNull(quiet.remindAt)
    }

    @Test
    fun parsesBothKeyFormats() {
        val t = Instant.parse("2026-09-25T07:00:00Z")
        assertEquals(OccurrenceRef.Timed("a-b", t), parseOccurrenceKey(occurrenceKey("a-b", t)))
        assertEquals(
            OccurrenceRef.Slotted("ct1:item:x", LocalDate.parse("2026-09-25"), DaySlot.EVENING),
            parseOccurrenceKey(slotOccurrenceKey("ct1:item:x", LocalDate.parse("2026-09-25"), DaySlot.EVENING)),
        )
        assertNull(parseOccurrenceKey("nonsense"))
        assertNull(parseOccurrenceKey("i@2026-09-25/NOPE"))
    }

    @Test
    fun weeklyDoseIsSplitOverScheduledDoses() {
        val twice = item(Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(Timing.Slot(DaySlot.ANY_TIME))))
            .copy(dose = Amount(500.0, DoseUnit.MG), doseBasis = DoseBasis.PER_WEEK)
        val occ = occurrences(emptyList(), listOf(twice), at("2026-09-21"), at("2026-09-28"), zone, IntervalAnchors.NONE)
        assertEquals(2, occ.size)
        assertTrue(occ.all { it.dose == Amount(250.0, DoseUnit.MG) })
        val e35d = item(Schedule.EveryHours(84.0, at("2026-09-21", "09:00"))).copy(dose = Amount(500.0, DoseUnit.MG), doseBasis = DoseBasis.PER_WEEK)
        assertEquals(Amount(250.0, DoseUnit.MG), occurrences(emptyList(), listOf(e35d), at("2026-09-21"), at("2026-09-28"), zone, IntervalAnchors.NONE).first().dose)
    }

    @Test
    fun describesTimings() {
        val s = Schedule.Weekdays(setOf(DayOfWeek.MONDAY), listOf(Timing.Slot(DaySlot.ANY_TIME), Timing.Slot(DaySlot.MORNING), At(LocalTime.of(13, 0))))
        assertEquals("Mon · Morning, 13:00, Any time", describeSchedule(s, java.util.Locale.ENGLISH))
    }
}
