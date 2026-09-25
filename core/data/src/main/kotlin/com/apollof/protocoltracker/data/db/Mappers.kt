package com.apollof.protocoltracker.data.db

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun CompoundEntity.toDomain() = Compound(
    id = id, name = name, group = groupName, category = CompoundCategory.valueOf(category),
    baseUnit = BaseUnit.valueOf(baseUnit), colorArgb = colorArgb,
    pk = PkParams(absorptionHalfLifeH, eliminationHalfLifeH, activeFraction, bioavailability),
    defaultFormulation = Formulation(perMl, perTablet), sourceNote = sourceNote, isPreset = isPreset, archived = archived,
)

fun Compound.toEntity() = CompoundEntity(
    id = id, name = name, groupName = group, category = category.name, baseUnit = baseUnit.name, colorArgb = colorArgb,
    absorptionHalfLifeH = pk.absorptionHalfLifeH, eliminationHalfLifeH = pk.eliminationHalfLifeH,
    activeFraction = pk.activeFraction, bioavailability = pk.bioavailability,
    perMl = defaultFormulation.perMl, perTablet = defaultFormulation.perTablet,
    sourceNote = sourceNote, isPreset = isPreset, archived = archived,
)

fun PhaseEntity.toDomain() = Phase(id, name, LocalDate.parse(startDate), endDate?.let(LocalDate::parse), colorArgb, notes)
fun Phase.toEntity() = PhaseEntity(id, name, startDate.toString(), endDate?.toString(), colorArgb, notes)

fun PlanItemEntity.toDomain() = PlanItem(
    id = id, phaseId = phaseId, compoundId = compoundId, dose = Amount(doseValue, DoseUnit.valueOf(doseUnit)),
    formulation = Formulation(perMl, perTablet), schedule = json.decodeFromString(Schedule.serializer(), scheduleJson),
    startDate = startDate?.let(LocalDate::parse), endDate = endDate?.let(LocalDate::parse),
    notes = notes, enabled = enabled, sortOrder = sortOrder,
)

fun PlanItem.toEntity() = PlanItemEntity(
    id = id, phaseId = phaseId, compoundId = compoundId, doseValue = dose.value, doseUnit = dose.unit.name,
    perMl = formulation.perMl, perTablet = formulation.perTablet,
    scheduleJson = json.encodeToString(Schedule.serializer(), schedule),
    startDate = startDate?.toString(), endDate = endDate?.toString(), notes = notes, enabled = enabled, sortOrder = sortOrder,
)

fun DoseLogEntity.toDomain() = DoseLog(
    id = id, planItemId = planItemId, compoundId = compoundId, occurrenceKey = occurrenceKey,
    scheduledAt = scheduledAtMs?.let(Instant::ofEpochMilli), takenAt = Instant.ofEpochMilli(takenAtMs),
    amount = Amount(amountValue, DoseUnit.valueOf(amountUnit)), status = LogStatus.valueOf(status), note = note,
    snapshot = json.decodeFromString(DoseSnapshot.serializer(), snapshotJson), createdAt = Instant.ofEpochMilli(createdAtMs),
)

fun DoseLog.toEntity() = DoseLogEntity(
    id = id, planItemId = planItemId, compoundId = compoundId, occurrenceKey = occurrenceKey,
    scheduledAtMs = scheduledAt?.toEpochMilli(), takenAtMs = takenAt.toEpochMilli(),
    amountValue = amount.value, amountUnit = amount.unit.name, status = status.name, note = note,
    snapshotJson = json.encodeToString(DoseSnapshot.serializer(), snapshot), createdAtMs = createdAt.toEpochMilli(),
)
