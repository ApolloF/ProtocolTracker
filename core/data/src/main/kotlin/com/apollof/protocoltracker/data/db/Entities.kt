package com.apollof.protocoltracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "compounds")
data class CompoundEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupName: String,
    val category: String,
    val baseUnit: String,
    val colorArgb: Long,
    val absorptionHalfLifeH: Double,
    val eliminationHalfLifeH: Double,
    val activeFraction: Double,
    val bioavailability: Double,
    val perMl: Double?,
    val perTablet: Double?,
    val sourceNote: String,
    val isPreset: Boolean,
    val archived: Boolean,
)

@Entity(tableName = "phases")
data class PhaseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ISO dates sort correctly as text. */
    val startDate: String,
    val endDate: String?,
    val colorArgb: Long,
    val notes: String,
)

@Entity(tableName = "plan_items", indices = [Index("phaseId"), Index("compoundId")])
data class PlanItemEntity(
    @PrimaryKey val id: String,
    val phaseId: String?,
    val compoundId: String,
    val doseValue: Double,
    val doseUnit: String,
    val perMl: Double?,
    val perTablet: Double?,
    val scheduleJson: String,
    val startDate: String?,
    val endDate: String?,
    val notes: String,
    val enabled: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "dose_logs",
    indices = [Index(value = ["occurrenceKey"], unique = true), Index("takenAtMs"), Index("compoundId")],
)
data class DoseLogEntity(
    @PrimaryKey val id: String,
    val planItemId: String?,
    val compoundId: String,
    /** Unique so one scheduled occurrence can never be logged twice; NULLs (unscheduled doses) don't collide. */
    val occurrenceKey: String?,
    val scheduledAtMs: Long?,
    val takenAtMs: Long,
    val amountValue: Double,
    val amountUnit: String,
    val status: String,
    val note: String,
    val snapshotJson: String,
    val createdAtMs: Long,
)
