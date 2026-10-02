package com.apollof.protocoltracker.domain.model

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

/** The newest taken dose of [compoundId] (skipped doses never count), or null. */
fun List<DoseLog>.latestTaken(compoundId: String): DoseLog? =
    filter { it.compoundId == compoundId && it.status == LogStatus.TAKEN }.maxByOrNull { it.takenAt }
