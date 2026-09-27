package com.apollof.protocoltracker.domain.model

import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BloodPressureTrendTest {
    private val ams = ZoneId.of("Europe/Amsterdam")
    private val now = LocalDateTime.of(2026, 9, 26, 10, 0).atZone(ams).toInstant()
    private val day = Duration.ofDays(1)
    private var n = 0

    private val locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        Locale.setDefault(Locale.ENGLISH)
        DisplayFormat.current = DisplayFormat()
    }

    @AfterTest
    fun reset() {
        Locale.setDefault(locale)
        DisplayFormat.current = DisplayFormat()
    }

    private fun bp(at: Instant, sys: Int, dia: Int) = JournalEntry.BloodPressure("bp${n++}", at, sys, dia, createdAt = at)

    private fun ago(d: Long): Instant = now.minus(day.multipliedBy(d))

    @Test
    fun noReadingsGiveNoWeeksAndOtherKindsAreIgnored() {
        assertEquals(emptyList(), bloodPressureWeeks(emptyList(), now))
        val other = listOf(
            JournalEntry.Note("n", ago(1), "BP felt high", ago(1)),
            JournalEntry.Bloodwork("b", ago(2), listOf(MarkerResult("hematocrit", 48.0)), createdAt = ago(2)),
        )
        assertEquals(emptyList(), bloodPressureWeeks(other, now))
        val one = bloodPressureWeeks(other + bp(ago(1), 125, 80), now)
        assertEquals(1, one.size)
        assertEquals(BpWeek(0, now, 125, 80, 1), one.single())
    }

    @Test
    fun weeksComeOldestFirstAndEmptyWeeksAreDropped() {
        val weeks = bloodPressureWeeks(listOf(bp(ago(1), 120, 80), bp(ago(9), 130, 85), bp(ago(30), 140, 90)), now)
        assertEquals(listOf(4, 1, 0), weeks.map { it.weeksAgo })
        assertEquals(listOf(140, 130, 120), weeks.map { it.systolic })
        assertEquals(listOf(ago(28), ago(7), now), weeks.map { it.end })
    }

    @Test
    fun edgesAreExact() {
        val start = now.minus(Duration.ofDays(7))
        assertEquals(0, bloodPressureWeeks(listOf(bp(start, 120, 80)), now).single().weeksAgo)
        assertEquals(1, bloodPressureWeeks(listOf(bp(start.minusMillis(1), 120, 80)), now).single().weeksAgo)
        assertEquals(1, bloodPressureWeeks(listOf(bp(ago(14), 120, 80)), now).single().weeksAgo)
        assertEquals(2, bloodPressureWeeks(listOf(bp(ago(14).minusMillis(1), 120, 80)), now).single().weeksAgo)
        assertEquals(0, bloodPressureWeeks(listOf(bp(now.plus(Duration.ofHours(6)), 120, 80)), now).single().weeksAgo)
        val oldest = ago(7L * BP_TREND_WEEKS)
        assertEquals(BP_TREND_WEEKS - 1, bloodPressureWeeks(listOf(bp(oldest, 120, 80)), now).single().weeksAgo)
        assertEquals(emptyList(), bloodPressureWeeks(listOf(bp(oldest.minusMillis(1), 120, 80)), now))
    }

    @Test
    fun theNewestWeekEqualsTheCardAverage() {
        val readings = listOf(
            bp(now.plus(Duration.ofHours(6)), 131, 84), bp(ago(1), 127, 81), bp(ago(2), 122, 79), bp(ago(3), 129, 83),
            bp(ago(4), 126, 80), bp(ago(5), 133, 86), bp(ago(6), 124, 78), bp(ago(7), 128, 82), bp(ago(10), 150, 95),
        )
        // The Blood pressure card: readings at or after now − 7 d, rounded means.
        val recent = readings.filter { it.at >= now.minus(Duration.ofDays(7)) }
        val newest = bloodPressureWeeks(readings, now).last()
        assertEquals(8, newest.readings)
        assertEquals(Math.round(recent.map { it.systolic }.average()).toInt(), newest.systolic)
        assertEquals(Math.round(recent.map { it.diastolic }.average()).toInt(), newest.diastolic)
        assertEquals(128, bloodPressureWeeks(listOf(bp(ago(1), 127, 80), bp(ago(2), 128, 80)), now).single().systolic)
    }

    @Test
    fun oldReadingsAreIgnored() {
        val old = listOf(bp(ago(7L * 30), 160, 100), bp(ago(7L * 30 + 2), 150, 95))
        assertEquals(emptyList(), bloodPressureWeeks(old, now))
        val weeks = bloodPressureWeeks(old + bp(ago(2), 120, 80) + bp(ago(12), 125, 82), now)
        assertEquals(listOf(1, 0), weeks.map { it.weeksAgo })
        assertEquals(2, weeks.sumOf { it.readings })
    }

    @Test
    fun systolicStaysAboveDiastolic() {
        val readings = (0 until 200).map { i -> bp(now.minus(Duration.ofHours(i * 13L)), 80 + i % 37, 79 + i % 37) }
        for (w in bloodPressureWeeks(readings, now)) assertTrue(w.systolic > w.diastolic, w.toString())
    }

    @Test
    fun aThousandReadingsCountRight() {
        val readings = (0 until 1_000).map { i -> bp(now.minus(Duration.ofMinutes(i * 250L)), 120 + i % 10, 80) }
        val weeks = bloodPressureWeeks(readings, now)
        val span = Duration.ofMinutes(999L * 250).toDays() / 7 + 1
        assertEquals(span.toInt(), weeks.size)
        assertEquals(1_000, weeks.sumOf { it.readings })
        val week0 = readings.count { it.at >= now.minus(Duration.ofDays(7)) }
        assertEquals(week0, weeks.last().readings)
    }

    @Test
    fun describe() {
        val weeks = bloodPressureWeeks(
            (0 until 9).map { bp(now.minus(Duration.ofHours(it * 18L)), 127, 81) } +
                (0 until 12).map { bp(ago(8).minusSeconds(it * 3_600L), 125, 80) } +
                bp(ago(15), 130, 85),
            now,
        )
        assertEquals("Last 7 days · 127/81 mmHg · 9 readings", weeks[2].describe(ams))
        assertEquals("7 days to 19 Sep · 125/80 mmHg · 12 readings", weeks[1].describe(ams))
        assertEquals("7 days to 12 Sep · 130/85 mmHg · 1 reading", weeks[0].describe(ams))
        // The same week ends on 18 Sep in UTC−10.
        assertEquals("7 days to 18 Sep · 125/80 mmHg · 12 readings", weeks[1].describe(ZoneOffset.ofHours(-10)))
        DisplayFormat.current = DisplayFormat(dayFirst = false)
        assertEquals("7 days to Sep 19 · 125/80 mmHg · 12 readings", weeks[1].describe(ams))
        // An end at midnight names the day before it.
        val midnight = LocalDateTime.of(2026, 9, 27, 0, 0).atZone(ams).toInstant()
        assertEquals("7 days to Sep 19 · 120/80 mmHg · 1 reading", BpWeek(1, midnight.minus(Duration.ofDays(7)), 120, 80, 1).describe(ams))
    }
}
