package com.apollof.protocoltracker.domain.model

import java.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
enum class LogStatus { TAKEN, SKIPPED }

/** Frozen copy of what was taken, so edits to the plan or compound never rewrite history. */
@Serializable
data class DoseSnapshot(
    val displayName: String,
    val group: String,
    val category: CompoundCategory,
    val baseUnit: BaseUnit,
    /** Null when the compound had no level data. */
    val pk: PkParams?,
    val formulation: Formulation = Formulation(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DoseLog(
    val id: String,
    val planItemId: String?,
    val compoundId: String,
    /** Scheduled occurrence key (see `occurrenceKey`); null for unscheduled doses. */
    val occurrenceKey: String?,
    val scheduledAt: InstantS?,
    val takenAt: InstantS,
    val amount: Amount,
    /** The plan's amount for this dose when it was logged; differs from [amount] after a one-off adjustment. */
    val plannedAmount: Amount? = null,
    val status: LogStatus,
    val note: String = "",
    val snapshot: DoseSnapshot,
    val createdAt: InstantS,
    /** Injection site, an [InjectionSites] key (unknown keys are kept as they are); null when none was recorded. Left out of backups when null. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val site: String? = null,
) {
    /** True when the logged amount differs from the plan's amount for this dose. */
    val adjusted: Boolean
        get() = plannedAmount != null && (plannedAmount.unit != amount.unit || kotlin.math.abs(plannedAmount.value - amount.value) > 1e-9)
}

/**
 * Where the log sits in time: a skip belongs to its planned time, a taken dose to when it was taken. Skips written
 * before 0.5.0-dev.15 stored the moment of skipping as [DoseLog.takenAt]; this places them on their day too.
 */
val DoseLog.shownAt: Instant get() = if (status == LogStatus.SKIPPED) scheduledAt ?: takenAt else takenAt

/** The log as it is stored: a skip records no site and its planned time. */
fun DoseLog.normalizedForWrite(): DoseLog =
    if (status != LogStatus.SKIPPED) this else copy(site = null, takenAt = scheduledAt ?: takenAt)

/**
 * The name a dose was logged under without its scientific part: "Anavar" for "Anavar (oxandrolone)" when [commonName]
 * (its compound's) is "Anavar". Any other name stays whole, so "Semaglutide (oral)" never reads as "Semaglutide".
 */
fun DoseSnapshot.shortName(commonName: String?): String =
    commonName?.takeIf { it.isNotBlank() && displayName.startsWith("$it (") } ?: displayName

/** The newest taken dose of [compoundId] (skipped doses never count), or null. */
fun List<DoseLog>.latestTaken(compoundId: String): DoseLog? =
    filter { it.compoundId == compoundId && it.status == LogStatus.TAKEN }.maxByOrNull { it.takenAt }
