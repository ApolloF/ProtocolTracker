package com.apollof.protocoltracker.domain.io

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
import com.apollof.protocoltracker.domain.model.validate
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.domain.pk.Presets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ImportResult(
    val compounds: List<Compound>,
    val phases: List<Phase>,
    val items: List<PlanItem>,
    val logs: List<DoseLog>,
    val skippedHealthRecords: Int,
    val warnings: List<String>,
)

class ImportFormatException(message: String) : IllegalArgumentException(message)

/**
 * Maps an export from the previous CycleTracker web app (`format: "cycletracker-1"`).
 * IDs are derived from source IDs, so importing the same file twice updates rather than duplicates.
 */
object LegacyImport {
    private const val PREFIX = "ct1:"

    private val formulationToPreset = mapOf(
        "testosterone-cypionate-im" to "preset:test-cyp",
        "testosterone-enanthate-im" to "preset:test-enan",
        "testosterone-enanthate-sc" to "preset:test-enan",
        "nandrolone-decanoate-im" to "preset:deca",
        "nandrolone-phenylpropionate-im" to "preset:npp",
        "hcg-urinary-sc" to "preset:hcg",
        "hcg-urinary-im" to "preset:hcg",
        "hcg-recombinant-sc" to "preset:hcg",
        "anastrozole-oral" to "preset:anastrozole",
        "exemestane-oral" to "preset:exemestane",
        "oxandrolone-oral" to "preset:oxandrolone",
        "stanozolol-oral" to "preset:stanozolol",
        "stanozolol-im" to "preset:stanozolol",
        "oxymetholone-oral" to "preset:oxymetholone",
    )

    fun parse(text: String, zone: ZoneId, existingCompounds: Collection<Compound>): ImportResult {
        val root = try { Json.parseToJsonElement(text).jsonObject } catch (e: Exception) {
            throw ImportFormatException("Not a JSON export file")
        }
        if (root.str("format") != "cycletracker-1") throw ImportFormatException("Unsupported export format; expected cycletracker-1")
        val warnings = ArrayList<String>()
        val exportedAt = root.str("exportedAt")?.let(::instantOrNull) ?: Instant.now()
        val compounds = LinkedHashMap<String, Compound>()
        existingCompounds.forEach { compounds[it.id] = it }
        val newCompounds = LinkedHashMap<String, Compound>()

        fun compoundFor(entry: JsonObject): Compound {
            val presetId = formulationToPreset[entry.str("formulationId")]
            if (presetId != null) return compounds[presetId] ?: Presets.byId(presetId)!!
            val name = entry.str("name")?.trim().orEmpty().ifEmpty { "Imported item" }
            val id = "${PREFIX}compound:${name.lowercase()}"
            return compounds[id] ?: Compound(
                id = id, name = name, group = name, category = CompoundCategory.OTHER,
                baseUnit = if (entry.obj("amount")?.str("unit") == "IU") BaseUnit.IU else BaseUnit.MG,
                colorArgb = CompoundColors.palette[newCompounds.size % CompoundColors.palette.size],
                pk = PkParams(absorptionHalfLifeH = 1.0, eliminationHalfLifeH = 24.0),
                sourceNote = "Imported; half-lives are placeholders — edit before using levels",
            ).also { compounds[id] = it; newCompounds[id] = it; warnings += "Created \"$name\" with placeholder kinetics" }
        }

        val protocol = root.obj("workspace")?.obj("protocol")
        val activations = protocol?.arr("activations")?.mapNotNull { it as? JsonObject }.orEmpty()
        val phases = ArrayList<Phase>()
        val items = ArrayList<PlanItem>()
        protocol?.arr("phases")?.forEachIndexed { index, element ->
            val p = element as? JsonObject ?: return@forEachIndexed
            val sourceId = p.str("id") ?: return@forEachIndexed
            val own = activations.filter { it.str("phaseId") == sourceId }
            val start = p.str("plannedStart")?.let(::dateOrNull)
                ?: own.mapNotNull { it.str("at")?.let(::instantOrNull) }.minOrNull()?.atZone(zone)?.toLocalDate()
                ?: exportedAt.atZone(zone).toLocalDate()
            val end = p.str("plannedEnd")?.let(::dateOrNull)
                ?: own.mapNotNull { it.str("endedAt")?.let(::instantOrNull) }.maxOrNull()?.atZone(zone)?.toLocalDate()
            val phaseId = "${PREFIX}phase:$sourceId"
            phases += Phase(
                id = phaseId, name = p.str("name") ?: "Phase ${index + 1}", startDate = start,
                endDate = end?.takeIf { it >= start },
                colorArgb = CompoundColors.palette[index % CompoundColors.palette.size], notes = p.str("notes").orEmpty(),
            )
            p.arr("entries")?.forEachIndexed { order, e ->
                val entry = e as? JsonObject ?: return@forEachIndexed
                val item = mapEntry(entry, phaseId, order, compoundFor(entry), warnings) ?: return@forEachIndexed
                items += item
            }
        }

        val logs = ArrayList<DoseLog>()
        var skippedHealth = 0
        root.arr("records")?.forEach { element ->
            val record = element as? JsonObject ?: return@forEach
            when (record.str("kind")) {
                "health" -> skippedHealth++
                "administration" -> {
                    val data = record.obj("data") ?: return@forEach
                    val status = when (data.str("status")) { "taken" -> LogStatus.TAKEN; "skipped" -> LogStatus.SKIPPED; else -> return@forEach }
                    val at = data.str("at")?.let(::instantOrNull) ?: return@forEach
                    val snapshotEntry = data.obj("snapshot") ?: return@forEach
                    val compound = compoundFor(snapshotEntry)
                    val amount = data.obj("amount")?.let(::amountOf) ?: snapshotEntry.obj("amount")?.let(::amountOf)
                    if (amount == null) { warnings += "Skipped a dose without a valid amount"; return@forEach }
                    val note = listOfNotNull(
                        data.str("note")?.takeIf(String::isNotBlank),
                        data.str("site")?.takeIf(String::isNotBlank)?.let { "Site: $it" },
                    ).joinToString("\n")
                    logs += DoseLog(
                        id = "${PREFIX}log:${record.str("id")}", planItemId = null, compoundId = compound.id,
                        occurrenceKey = null, scheduledAt = null, takenAt = at, amount = amount, status = status, note = note,
                        snapshot = DoseSnapshot(compound.name, compound.group, compound.baseUnit, compound.pk, formulationOf(snapshotEntry, compound.baseUnit)),
                        createdAt = at,
                    )
                }
            }
        }
        if (skippedHealth > 0) warnings += "$skippedHealth health records not imported (no health module)"
        return ImportResult(newCompounds.values.toList(), phases, items, logs, skippedHealth, warnings)
    }

    private fun mapEntry(entry: JsonObject, phaseId: String, order: Int, compound: Compound, warnings: MutableList<String>): PlanItem? {
        val sourceId = entry.str("id") ?: return null
        val s = entry.obj("schedule") ?: return null
        val slots = s.arr("slots")?.mapNotNull { it as? JsonObject }.orEmpty()
        val times = slots.mapNotNull { it.str("time")?.let(::timeOrNull) }.distinct()
        val startDate = s.str("startDate")?.let(::dateOrNull)
        val schedule = when (s.str("kind")) {
            "daily" -> Schedule.Daily(times)
            "weekdays" -> Schedule.Weekdays(
                s.arr("weekdays")?.mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.orEmpty().toSet(),
                times,
            )
            "interval" -> Schedule.EveryNDays(s.num("intervalDays")?.toInt() ?: 2, startDate ?: LocalDate.now(), times)
            "elapsed" -> Schedule.EveryHours(
                s.num("intervalHours") ?: 48.0,
                s.str("anchorInstant")?.let(::instantOrNull) ?: return null,
            )
            else -> Schedule.AsNeeded
        }
        if (schedule.validate().isNotEmpty()) {
            warnings += "\"${entry.str("name")}\": schedule could not be mapped; imported as as-needed"
        }
        val slotAmounts = slots.mapNotNull { it.obj("amount")?.let(::amountOf) }
        if (slotAmounts.distinct().size > 1) warnings += "\"${entry.str("name")}\": different per-time amounts; first amount used"
        val dose = entry.obj("amount")?.let(::amountOf) ?: slotAmounts.firstOrNull() ?: return null
        return PlanItem(
            id = "${PREFIX}item:$sourceId", phaseId = phaseId, compoundId = compound.id, dose = dose,
            formulation = formulationOf(entry, compound.baseUnit),
            schedule = if (schedule.validate().isEmpty()) schedule else Schedule.AsNeeded,
            startDate = startDate, endDate = s.str("endDate")?.let(::dateOrNull),
            notes = entry.str("notes").orEmpty(), enabled = entry.bool("enabled") ?: true, sortOrder = order,
        )
    }

    /** Grams are converted to mg; unknown units are rejected. */
    private fun amountOf(o: JsonObject): Amount? {
        val value = o.num("value")?.takeIf { it > 0 } ?: return null
        val unit = o.str("unit") ?: return null
        return when (unit) {
            "mg" -> Amount(value, DoseUnit.MG)
            "mcg" -> Amount(value, DoseUnit.MCG)
            "g" -> Amount(value * 1000, DoseUnit.MG)
            "IU" -> Amount(value, DoseUnit.IU)
            "mL" -> Amount(value, DoseUnit.ML)
            else -> null
        }
    }

    private fun formulationOf(entry: JsonObject, base: BaseUnit): Formulation {
        fun toBase(o: JsonObject?): Double? {
            val value = o?.num("value")?.takeIf { it > 0 } ?: return null
            return when (o.str("unit")) {
                "mg" -> value.takeIf { base == BaseUnit.MG }
                "mcg" -> (value / 1000).takeIf { base == BaseUnit.MG }
                "g" -> (value * 1000).takeIf { base == BaseUnit.MG }
                "IU" -> value.takeIf { base == BaseUnit.IU }
                else -> null
            }
        }
        return Formulation(perMl = toBase(entry.obj("concentration")), perTablet = toBase(entry.obj("pillStrength")))
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): List<JsonElement>? = this[key] as? JsonArray

    private fun instantOrNull(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant() }.getOrNull()
    private fun dateOrNull(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
    private fun timeOrNull(s: String): LocalTime? = runCatching { LocalTime.parse(s) }.getOrNull()
}
