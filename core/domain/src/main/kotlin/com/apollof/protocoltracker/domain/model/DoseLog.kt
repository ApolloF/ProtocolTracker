package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogStatus { TAKEN, SKIPPED }

/** Frozen copy of what was taken, so edits to the plan or compound never rewrite history. */
@Serializable
data class DoseSnapshot(
    val compoundName: String,
    val group: String,
    val baseUnit: BaseUnit,
    val pk: PkParams,
    val formulation: Formulation = Formulation(),
)

@Serializable
data class DoseLog(
    val id: String,
    val planItemId: String?,
    val compoundId: String,
    /** `itemId@epochSecond` of the scheduled occurrence; null for unscheduled doses. */
    val occurrenceKey: String?,
    val scheduledAt: InstantS?,
    val takenAt: InstantS,
    val amount: Amount,
    val status: LogStatus,
    val note: String = "",
    val snapshot: DoseSnapshot,
    val createdAt: InstantS,
)
