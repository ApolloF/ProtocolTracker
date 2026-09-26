package com.apollof.protocoltracker.domain.units

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How times, dates and injection volumes are shown. The app sets [current] from its settings whenever they are
 * read, so every formatter (screens, notifications, reports) follows the same choice.
 */
data class DisplayFormat(
    val use24Hour: Boolean = true,
    /** "26 Sep" when true, "Sep 26" when false. */
    val dayFirst: Boolean = true,
    /** Volumes as units on a U-100 insulin syringe (100 units = 1 mL) instead of mL. */
    val syringeUnits: Boolean = false,
) {
    val time: DateTimeFormatter by lazy { pattern(if (use24Hour) "HH:mm" else "h:mm a") }
    /** "26 Sep" / "Sep 26". */
    val dayMonth: DateTimeFormatter by lazy { pattern(if (dayFirst) "d MMM" else "MMM d") }
    /** "Sat 26 Sep" / "Sat, Sep 26". */
    val dayShort: DateTimeFormatter by lazy { pattern(if (dayFirst) "EEE d MMM" else "EEE, MMM d") }
    /** "Saturday 26 September" / "Saturday, September 26". */
    val dayLong: DateTimeFormatter by lazy { pattern(if (dayFirst) "EEEE d MMMM" else "EEEE, MMMM d") }
    /** "Sat 26 Sep 2026" / "Sat, Sep 26 2026". */
    val dayYear: DateTimeFormatter by lazy { pattern(if (dayFirst) "EEE d MMM yyyy" else "EEE, MMM d yyyy") }
    /** "26 Sep 2026" / "Sep 26, 2026". */
    val date: DateTimeFormatter by lazy { pattern(if (dayFirst) "d MMM yyyy" else "MMM d, yyyy") }

    private fun pattern(p: String): DateTimeFormatter = DateTimeFormatter.ofPattern(p, Locale.getDefault())

    companion object {
        @Volatile
        var current: DisplayFormat = DisplayFormat()

        /** Whether the locale writes the day before the month in its short date pattern. */
        fun localeDayFirst(locale: Locale = Locale.getDefault()): Boolean {
            val pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                java.time.format.FormatStyle.SHORT, null, java.time.chrono.IsoChronology.INSTANCE, locale,
            )
            val d = pattern.indexOf('d')
            val m = pattern.indexOf('M')
            return d < 0 || m < 0 || d < m
        }
    }
}
