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

@Serializable
sealed interface Schedule {
    /** Every day at each of [times]. */
    @Serializable @SerialName("daily")
    data class Daily(val times: List<LocalTimeS>) : Schedule

    /** On selected weekdays at each of [times]. */
    @Serializable @SerialName("weekdays")
    data class Weekdays(val days: Set<DayOfWeek>, val times: List<LocalTimeS>) : Schedule

    /** Every [n] days counted from [anchor], at each of [times]. */
    @Serializable @SerialName("every_n_days")
    data class EveryNDays(val n: Int, val anchor: LocalDateS, val times: List<LocalTimeS>) : Schedule

    /** Fixed elapsed interval from an exact instant, e.g. 84 h. Ignores wall-clock/DST shifts. */
    @Serializable @SerialName("every_hours")
    data class EveryHours(val hours: Double, val anchor: InstantS) : Schedule

    @Serializable @SerialName("as_needed")
    data object AsNeeded : Schedule
}

fun Schedule.validate(): List<String> = buildList {
    fun checkTimes(times: List<LocalTime>) {
        if (times.isEmpty()) add("Add at least one time")
        if (times.toSet().size != times.size) add("Times must be unique")
        if (times.size > 24) add("At most 24 times per day")
    }
    when (val s = this@validate) {
        is Schedule.Daily -> checkTimes(s.times)
        is Schedule.Weekdays -> { checkTimes(s.times); if (s.days.isEmpty()) add("Choose at least one weekday") }
        is Schedule.EveryNDays -> { checkTimes(s.times); if (s.n !in 1..3650) add("Interval must be 1–3650 days") }
        is Schedule.EveryHours -> if (!(s.hours.isFinite() && s.hours >= 1 && s.hours <= 24 * 365)) add("Interval must be 1 h – 1 year")
        Schedule.AsNeeded -> Unit
    }
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

@Serializable
data class PlanItem(
    val id: String,
    /** Null = "Always" group, active regardless of phase. */
    val phaseId: String?,
    val compoundId: String,
    val dose: Amount,
    val formulation: Formulation = Formulation(),
    val schedule: Schedule,
    val startDate: LocalDateS? = null,
    val endDate: LocalDateS? = null,
    val notes: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

data class Protocol(
    val phases: List<Phase>,
    val items: List<PlanItem>,
    val compounds: Map<String, Compound>,
)
