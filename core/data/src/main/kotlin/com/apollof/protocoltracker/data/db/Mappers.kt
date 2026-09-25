package com.apollof.protocoltracker.data.db

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SupportKind
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun CompoundEntity.toDomain() = Compound(
    id = id, name = name, commonName = commonName, group = groupName, category = CompoundCategory.valueOf(category),
    supportKind = supportKind?.let(SupportKind::valueOf), route = Route.valueOf(route),
    baseUnit = BaseUnit.valueOf(baseUnit), colorArgb = colorArgb,
    pk = pkJson?.let { json.decodeFromString(PkParams.serializer(), it) },
    defaultFormulation = Formulation(perMl, perTablet), sourceNote = sourceNote, isPreset = isPreset, edited = edited, archived = archived,
)

fun Compound.toEntity() = CompoundEntity(
    id = id, name = name, commonName = commonName, groupName = group, category = category.name, supportKind = supportKind?.name,
    route = route.name, baseUnit = baseUnit.name, colorArgb = colorArgb,
    pkJson = pk?.let { json.encodeToString(PkParams.serializer(), it) },
    perMl = defaultFormulation.perMl, perTablet = defaultFormulation.perTablet,
    sourceNote = sourceNote, isPreset = isPreset, edited = edited, archived = archived,
)

fun PhaseEntity.toDomain() = Phase(id, name, LocalDate.parse(startDate), endDate?.let(LocalDate::parse), colorArgb, notes)
fun Phase.toEntity() = PhaseEntity(id, name, startDate.toString(), endDate?.toString(), colorArgb, notes)

fun PlanItemEntity.toDomain() = PlanItem(
    id = id, phaseId = phaseId, compoundId = compoundId, dose = Amount(doseValue, DoseUnit.valueOf(doseUnit)),
    doseBasis = DoseBasis.valueOf(doseBasis), formulation = Formulation(perMl, perTablet),
    schedule = json.decodeFromString(Schedule.serializer(), scheduleJson),
    startDate = startDate?.let(LocalDate::parse), endDate = endDate?.let(LocalDate::parse),
    notes = notes, enabled = enabled, remind = remind, sortOrder = sortOrder,
)

fun PlanItem.toEntity() = PlanItemEntity(
    id = id, phaseId = phaseId, compoundId = compoundId, doseValue = dose.value, doseUnit = dose.unit.name, doseBasis = doseBasis.name,
    perMl = formulation.perMl, perTablet = formulation.perTablet,
    scheduleJson = json.encodeToString(Schedule.serializer(), schedule),
    startDate = startDate?.toString(), endDate = endDate?.toString(), notes = notes, enabled = enabled, remind = remind, sortOrder = sortOrder,
)

fun DoseLogEntity.toDomain() = DoseLog(
    id = id, planItemId = planItemId, compoundId = compoundId, occurrenceKey = occurrenceKey,
    scheduledAt = scheduledAtMs?.let(Instant::ofEpochMilli), takenAt = Instant.ofEpochMilli(takenAtMs),
    amount = Amount(amountValue, DoseUnit.valueOf(amountUnit)),
    plannedAmount = if (plannedValue != null && plannedUnit != null) Amount(plannedValue, DoseUnit.valueOf(plannedUnit)) else null,
    status = LogStatus.valueOf(status), note = note,
    snapshot = json.decodeFromString(DoseSnapshot.serializer(), snapshotJson), createdAt = Instant.ofEpochMilli(createdAtMs),
)

fun DoseLog.toEntity() = DoseLogEntity(
    id = id, planItemId = planItemId, compoundId = compoundId, occurrenceKey = occurrenceKey,
    scheduledAtMs = scheduledAt?.toEpochMilli(), takenAtMs = takenAt.toEpochMilli(),
    amountValue = amount.value, amountUnit = amount.unit.name,
    plannedValue = plannedAmount?.value, plannedUnit = plannedAmount?.unit?.name, status = status.name, note = note,
    snapshotJson = json.encodeToString(DoseSnapshot.serializer(), snapshot), createdAtMs = createdAt.toEpochMilli(),
)

private const val KIND_BP = "BLOOD_PRESSURE"
private const val KIND_NOTE = "NOTE"

fun JournalEntity.toDomain(): JournalEntry = when (kind) {
    KIND_BP -> JournalEntry.BloodPressure(
        id, Instant.ofEpochMilli(atMs), systolic ?: 0, diastolic ?: 0, pulse, text, Instant.ofEpochMilli(createdAtMs),
    )
    else -> JournalEntry.Note(id, Instant.ofEpochMilli(atMs), text, Instant.ofEpochMilli(createdAtMs))
}

fun JournalEntry.toEntity(): JournalEntity = when (this) {
    is JournalEntry.BloodPressure -> JournalEntity(id, KIND_BP, at.toEpochMilli(), systolic, diastolic, pulse, note, createdAt.toEpochMilli())
    is JournalEntry.Note -> JournalEntity(id, KIND_NOTE, at.toEpochMilli(), null, null, null, text, createdAt.toEpochMilli())
}
