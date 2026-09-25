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
import com.apollof.protocoltracker.domain.schedule.occurrenceKey
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
        val activePhaseId = protocol?.str("activePhaseId")
        val phases = ArrayList<Phase>()
        val items = ArrayList<PlanItem>()
        // (source entry id, source slot id) -> imported item id, to link logged occurrences.
        val slotItems = HashMap<Pair<String, String>, String>()
        protocol?.arr("phases")?.forEachIndexed { index, element ->
            val p = element as? JsonObject ?: return@forEachIndexed
            val sourceId = p.str("id") ?: return@forEachIndexed
            val name = p.str("name") ?: "Phase ${index + 1}"
            val own = activations.filter { it.str("phaseId") == sourceId }
            fun date(o: JsonObject, key: String) = o.str(key)?.let(::instantOrNull)?.atZone(zone)?.toLocalDate()
            val starts = own.mapNotNull { date(it, "at") }
            val plannedStart = p.str("plannedStart")?.let(::dateOrNull)
            val plannedEnd = p.str("plannedEnd")?.let(::dateOrNull)
            // Dates follow what actually happened: the active phase starts at its latest activation so it stays
            // current; other activated phases span their activations; never-activated phases need planned dates.
            val (start, end) = when {
                sourceId == activePhaseId -> (starts.maxOrNull() ?: plannedStart ?: exportedAt.atZone(zone).toLocalDate()) to plannedEnd
                starts.isNotEmpty() -> starts.min() to (own.mapNotNull { date(it, "endedAt") }.maxOrNull() ?: starts.max())
                plannedStart != null && p.bool("archived") != true -> plannedStart to plannedEnd
                else -> {
                    warnings += "\"$name\" was never activated and has no dates; not imported"
                    return@forEachIndexed
                }
            }
            val phaseId = "${PREFIX}phase:$sourceId"
            phases += Phase(
                id = phaseId, name = name, startDate = start,
                endDate = end?.takeIf { it >= start },
                colorArgb = CompoundColors.palette[index % CompoundColors.palette.size], notes = p.str("notes").orEmpty(),
            )
            p.arr("entries")?.forEachIndexed { order, e ->
                val entry = e as? JsonObject ?: return@forEachIndexed
                items += mapEntry(entry, phaseId, order * 10, compoundFor(entry), warnings, slotItems)
            }
        }

        val logs = ArrayList<DoseLog>()
        val usedKeys = HashSet<String>()
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
                    // Old occurrence ids are `entryId:version:slotId:ISO-instant`; link them so the dose counts as confirmed.
                    val link = data.str("occurrenceId")?.split(":", limit = 4)?.takeIf { it.size == 4 }?.let { parts ->
                        val itemId = slotItems[parts[0] to parts[2]] ?: return@let null
                        val scheduled = instantOrNull(parts[3]) ?: return@let null
                        Triple(itemId, scheduled, occurrenceKey(itemId, scheduled)).takeIf { usedKeys.add(it.third) }
                    }
                    logs += DoseLog(
                        id = "${PREFIX}log:${record.str("id")}", planItemId = link?.first, compoundId = compound.id,
                        occurrenceKey = link?.third, scheduledAt = link?.second, takenAt = at, amount = amount, status = status, note = note,
                        snapshot = DoseSnapshot(compound.name, compound.group, compound.baseUnit, compound.pk, formulationOf(snapshotEntry, compound.baseUnit)),
                        createdAt = at,
                    )
                }
            }
        }
        if (skippedHealth > 0) warnings += "$skippedHealth health records not imported (no health module)"
        return ImportResult(newCompounds.values.toList(), phases, items, logs, skippedHealth, warnings)
    }

    /**
     * Maps one old entry to plan items. Old slots can carry different amounts per time;
     * each distinct amount becomes its own item so every scheduled dose keeps its amount.
     */
    private fun mapEntry(
        entry: JsonObject,
        phaseId: String,
        order: Int,
        compound: Compound,
        warnings: MutableList<String>,
        slotItems: MutableMap<Pair<String, String>, String>,
    ): List<PlanItem> {
        val sourceId = entry.str("id") ?: return emptyList()
        val s = entry.obj("schedule") ?: return emptyList()
        val name = entry.str("name")
        val fallback = entry.obj("amount")?.let(::amountOf)
        val slots = s.arr("slots")?.mapNotNull { it as? JsonObject }.orEmpty().mapNotNull { slot ->
            val time = slot.str("time")?.let(::timeOrNull) ?: return@mapNotNull null
            Slot(slot.str("id").orEmpty(), time, slot.obj("amount")?.let(::amountOf) ?: fallback ?: return@mapNotNull null)
        }
        val startDate = s.str("startDate")?.let(::dateOrNull)
        // One group per distinct amount (the old app used slot amounts for scheduled doses).
        val groups: List<Pair<Amount, List<Slot>>> =
            if (slots.isEmpty()) listOf((fallback ?: return emptyList()) to emptyList())
            else slots.groupBy { it.amount }.toList()
        if (groups.size > 1) warnings += "\"$name\": different amounts per time; split into ${groups.size} plan items"

        return groups.mapIndexedNotNull { i, (dose, groupSlots) ->
            val times = groupSlots.map { it.time }.distinct()
            val schedule = when (s.str("kind")) {
                "daily" -> Schedule.Daily(times)
                "weekdays" -> Schedule.Weekdays(
                    s.arr("weekdays")?.mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.orEmpty().toSet(),
                    times,
                )
                "interval" -> Schedule.EveryNDays(s.num("intervalDays")?.toInt() ?: 2, startDate ?: LocalDate.now(), times)
                "elapsed" -> Schedule.EveryHours(
                    s.num("intervalHours") ?: 48.0,
                    s.str("anchorInstant")?.let(::instantOrNull) ?: return@mapIndexedNotNull null,
                )
                else -> Schedule.AsNeeded
            }
            val valid = schedule.validate().isEmpty()
            if (!valid) warnings += "\"$name\": schedule could not be mapped; imported as as-needed"
            val itemId = "${PREFIX}item:$sourceId" + if (i == 0) "" else ":$i"
            groupSlots.forEach { slotItems[sourceId to it.id] = itemId }
            PlanItem(
                id = itemId, phaseId = phaseId, compoundId = compound.id, dose = dose,
                formulation = formulationOf(entry, compound.baseUnit),
                schedule = if (valid) schedule else Schedule.AsNeeded,
                startDate = startDate, endDate = s.str("endDate")?.let(::dateOrNull),
                notes = entry.str("notes").orEmpty(), enabled = entry.bool("enabled") ?: true, sortOrder = order + i,
            )
        }
    }

    private data class Slot(val id: String, val time: LocalTime, val amount: Amount)

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
