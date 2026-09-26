package com.apollof.protocoltracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "compounds")
data class CompoundEntity(
    @PrimaryKey val id: String,
    val name: String,
    val commonName: String,
    val groupName: String,
    val category: String,
    val supportKind: String?,
    val route: String,
    val baseUnit: String,
    val colorArgb: Long,
    /** Level parameters as JSON; null when the compound has no level data. */
    val pkJson: String?,
    val perMl: Double?,
    val perTablet: Double?,
    val sourceNote: String,
    val isPreset: Boolean,
    val edited: Boolean,
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
    val doseBasis: String,
    val perMl: Double?,
    val perTablet: Double?,
    val scheduleJson: String,
    val startDate: String?,
    val endDate: String?,
    val notes: String,
    val enabled: Boolean,
    val remind: Boolean,
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
    val plannedValue: Double?,
    val plannedUnit: String?,
    val status: String,
    val note: String,
    val snapshotJson: String,
    val createdAtMs: Long,
)

/**
 * Blood pressure readings, notes, symptom logs and bloodwork. [kind] is BLOOD_PRESSURE, NOTE, SYMPTOMS or BLOODWORK;
 * unused columns stay null. [text] holds the note; [dataJson] the extra fields of symptom logs and bloodwork.
 */
@Entity(tableName = "journal", indices = [Index("atMs")])
data class JournalEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val atMs: Long,
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
    val text: String,
    val createdAtMs: Long,
    val dataJson: String? = null,
)
