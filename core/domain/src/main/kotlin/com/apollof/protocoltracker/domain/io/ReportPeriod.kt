package com.apollof.protocoltracker.domain.io

import java.time.LocalDate

/** The days a report covers, both ends included; never past [today][custom]. */
data class ReportPeriod(val from: LocalDate, val to: LocalDate) {
    companion object {
        /** From the first entry ([earliest], null when there is none) to today. */
        fun all(earliest: LocalDate?, today: LocalDate) = ReportPeriod(minOf(earliest ?: today, today), today)

        /** The last [days] days, today included. */
        fun lastDays(days: Int, today: LocalDate) = ReportPeriod(today.minusDays(days - 1L), today)

        /** The current phase from its start ([phaseStart]); with no phase today, the last 30 days. */
        fun currentPhase(phaseStart: LocalDate?, today: LocalDate) = phaseStart?.let { ReportPeriod(minOf(it, today), today) } ?: lastDays(30, today)

        /** Dates the user picked; a date after [today] counts as today. Null when [from] is after [to]. */
        fun custom(from: LocalDate, to: LocalDate, today: LocalDate): ReportPeriod? {
            val start = minOf(from, today)
            val end = minOf(to, today)
            return if (start > end) null else ReportPeriod(start, end)
        }

        /** What is wrong with a custom period, or null when it is fine. */
        fun customProblem(from: LocalDate, to: LocalDate, today: LocalDate): String? =
            if (custom(from, to, today) == null) "The start date is after the end date." else null
    }
}
