package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

typealias LocalTimeS = @Serializable(LocalTimeSerializer::class) LocalTime
typealias LocalDateS = @Serializable(LocalDateSerializer::class) LocalDate
typealias InstantS = @Serializable(InstantSerializer::class) Instant

/**
 * Part of the day a dose belongs to, in display order. Clock times come from settings
 * ([com.apollof.protocoltracker.domain.schedule.SlotTimes]); [defaultTime] is used until the user changes them.
 */
@Serializable
enum class DaySlot(val label: String, val defaultTime: LocalTime) {
    MORNING("Morning", LocalTime.of(8, 0)),
    MIDDAY("Midday", LocalTime.of(12, 30)),
    PRE_WORKOUT("Pre-workout", LocalTime.of(17, 0)),
    POST_WORKOUT("Post-workout", LocalTime.of(19, 0)),
    EVENING("Evening", LocalTime.of(20, 0)),
    BEDTIME("Bedtime", LocalTime.of(22, 30)),
    /** Any time that day. The clock time only places the dose on level curves. */
    ANY_TIME("Any time", LocalTime.of(12, 0)),
}

/** When on a scheduled day a dose is due: a part of the day or an exact clock time. */
@Serializable
sealed interface Timing {
    @Serializable @SerialName("slot")
    data class Slot(val slot: DaySlot) : Timing

    @Serializable @SerialName("at")
    data class At(val time: LocalTimeS) : Timing
}

@Serializable
sealed interface Schedule {
    /** Every day at each of [timings]. */
    @Serializable @SerialName("daily")
    data class Daily(val timings: List<Timing>) : Schedule

    /** On selected weekdays at each of [timings]. */
    @Serializable @SerialName("weekdays")
    data class Weekdays(val days: Set<DayOfWeek>, val timings: List<Timing>) : Schedule

    /** Every [n] days counted from [anchor], at each of [timings]. */
    @Serializable @SerialName("every_n_days")
    data class EveryNDays(val n: Int, val anchor: LocalDateS, val timings: List<Timing>) : Schedule

    /** Fixed elapsed interval from an exact instant, e.g. 84 h. Ignores wall-clock/DST shifts. */
    @Serializable @SerialName("every_hours")
    data class EveryHours(val hours: Double, val anchor: InstantS) : Schedule

    @Serializable @SerialName("as_needed")
    data object AsNeeded : Schedule
}

val Schedule.timings: List<Timing>
    get() = when (this) {
        is Schedule.Daily -> timings
        is Schedule.Weekdays -> timings
        is Schedule.EveryNDays -> timings
        is Schedule.EveryHours, Schedule.AsNeeded -> emptyList()
    }

fun Schedule.validate(): List<String> = buildList {
    fun checkTimings(timings: List<Timing>) {
        if (timings.isEmpty()) add("Choose when to take it")
        if (timings.toSet().size != timings.size) add("Each time of day can be used once")
        if (timings.size > 24) add("At most 24 doses per day")
    }
    when (val s = this@validate) {
        is Schedule.Daily -> checkTimings(s.timings)
        is Schedule.Weekdays -> { checkTimings(s.timings); if (s.days.isEmpty()) add("Choose at least one weekday") }
        is Schedule.EveryNDays -> { checkTimings(s.timings); if (s.n !in 1..3650) add("Interval must be 1–3650 days") }
        is Schedule.EveryHours -> if (!(s.hours.isFinite() && s.hours >= 1 && s.hours <= 24 * 365)) add("Interval must be 1 h – 1 year")
        Schedule.AsNeeded -> Unit
    }
}

/** Average doses per week, or null for as-needed. */
fun Schedule.dosesPerWeek(): Double? = when (this) {
    is Schedule.Daily -> 7.0 * timings.size
    is Schedule.Weekdays -> days.size.toDouble() * timings.size
    is Schedule.EveryNDays -> 7.0 / n * timings.size
    is Schedule.EveryHours -> 168.0 / hours
    Schedule.AsNeeded -> null
}

@Serializable
data class Phase(
    val id: String,
    val name: String,
    val startDate: LocalDateS,
    /** Inclusive last day. Null = runs until the next phase starts (or indefinitely). */
    val endDate: LocalDateS? = null,
    val colorArgb: Long,
    val notes: String = "",
)

/** What [PlanItem.dose] means: the amount of each dose, or the total per week split over the scheduled doses. */
@Serializable
enum class DoseBasis { PER_DOSE, PER_WEEK }

@Serializable
data class PlanItem(
    val id: String,
    /** Null = "Always" group, active regardless of phase. */
    val phaseId: String?,
    val compoundId: String,
    val dose: Amount,
    val doseBasis: DoseBasis = DoseBasis.PER_DOSE,
    val formulation: Formulation = Formulation(),
    val schedule: Schedule,
    val startDate: LocalDateS? = null,
    val endDate: LocalDateS? = null,
    val notes: String = "",
    val enabled: Boolean = true,
    /** Send a reminder when a dose is due and not yet logged. */
    val remind: Boolean = true,
    val sortOrder: Int = 0,
)

/** Amount of one scheduled dose. A weekly total is split evenly over the schedule's doses per week. */
fun PlanItem.dosePerOccurrence(): Amount = when (doseBasis) {
    DoseBasis.PER_DOSE -> dose
    DoseBasis.PER_WEEK -> {
        val perWeek = schedule.dosesPerWeek()?.takeIf { it > 0 } ?: return dose
        Amount(dose.value / perWeek, dose.unit)
    }
}

fun PlanItem.validate(): List<String> = buildList {
    addAll(schedule.validate())
    if (doseBasis == DoseBasis.PER_WEEK) {
        if (schedule is Schedule.AsNeeded) add("A weekly dose needs a schedule")
        if (dose.unit == DoseUnit.ML || dose.unit == DoseUnit.TABLET) add("Enter the weekly dose in mg, mcg or IU")
    }
    if (startDate != null && endDate != null && endDate < startDate) add("End date is before start date")
}

data class Protocol(
    val phases: List<Phase>,
    val items: List<PlanItem>,
    val compounds: Map<String, Compound>,
)
