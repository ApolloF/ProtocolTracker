package com.apollof.protocoltracker.domain.io

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReportPeriodTest {
    private val today = LocalDate.of(2026, 10, 4)

    @Test
    fun fixedPeriodsEndToday() {
        assertEquals(ReportPeriod(LocalDate.of(2026, 9, 5), today), ReportPeriod.lastDays(30, today))
        assertEquals(ReportPeriod(LocalDate.of(2026, 7, 7), today), ReportPeriod.lastDays(90, today))
        assertEquals(ReportPeriod(LocalDate.of(2025, 1, 1), today), ReportPeriod.all(LocalDate.of(2025, 1, 1), today))
        assertEquals(ReportPeriod(today, today), ReportPeriod.all(null, today))
        assertEquals(ReportPeriod(today, today), ReportPeriod.all(today.plusDays(3), today))
        assertEquals(ReportPeriod(LocalDate.of(2026, 9, 1), today), ReportPeriod.currentPhase(LocalDate.of(2026, 9, 1), today))
        assertEquals(ReportPeriod.lastDays(30, today), ReportPeriod.currentPhase(null, today))
    }

    @Test
    fun customKeepsThePickedDays() {
        val p = ReportPeriod.custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 15), today)
        assertEquals(ReportPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 15)), p)
        assertEquals(ReportPeriod(today, today), ReportPeriod.custom(today, today, today))
    }

    @Test
    fun customClampsToTodayAndRejectsAReversedPeriod() {
        assertEquals(ReportPeriod(LocalDate.of(2026, 9, 1), today), ReportPeriod.custom(LocalDate.of(2026, 9, 1), today.plusDays(10), today))
        assertEquals(ReportPeriod(today, today), ReportPeriod.custom(today.plusDays(2), today.plusDays(5), today))
        assertNull(ReportPeriod.custom(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), today))
        assertNotNull(ReportPeriod.customProblem(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), today))
        assertNull(ReportPeriod.customProblem(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1), today))
    }
}
