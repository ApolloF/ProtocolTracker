package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.BloodworkRules
import com.apollof.protocoltracker.domain.model.HAIR_SHEDDING_LABELS
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.bloodPressureProblems
import com.apollof.protocoltracker.domain.model.labRange
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.domain.units.formatNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * What the web history import would add, before anything is saved. [entries] are sorted by time, then id.
 * [alreadyThere] counts records the app already holds: the same web id, or the same reading, note or symptom log
 * entered in the app within 15 minutes.
 */
data class WebImport(
    val entries: List<JournalEntry>,
    val alreadyThere: Int,
    val leftOut: WebLeftOut,
    val warnings: List<WebImportWarning>,
) {
    val bloodPressure: Int get() = entries.count { it is JournalEntry.BloodPressure }
    val notes: Int get() = entries.count { it is JournalEntry.Note }
    val symptoms: Int get() = entries.count { it is JournalEntry.Symptoms }
    val draws: Int get() = entries.count { it is JournalEntry.Bloodwork }
    val from: Instant? get() = entries.minOfOrNull { it.at }
    val to: Instant? get() = entries.maxOfOrNull { it.at }

    /**
     * The confirm dialog's text: the span and counts per kind, what is never imported, the skipped count, that there is
     * no undo, then one `•` line per warning reason. Dates in [zone] and [format]; kinds with no entries are left out.
     */
    fun text(zone: ZoneId, format: DisplayFormat = DisplayFormat.current): String {
        val counts = listOf(
            bloodPressure to "blood pressure reading", notes to "note", symptoms to "symptom log", draws to "blood draw",
        ).filter { it.first > 0 }.joinToString(", ") { (n, word) -> "$n ${if (n == 1) word else word + "s"}" }
        val first = from?.atZone(zone)?.toLocalDate()
        val last = to?.atZone(zone)?.toLocalDate()
        val span = when {
            first == null || last == null -> null
            first == last -> "On ${format.date.format(last)}"
            first.year == last.year -> "From ${format.dayMonth.format(first)} to ${format.date.format(last)}"
            else -> "From ${format.date.format(first)} to ${format.date.format(last)}"
        }
        val skipped = when (alreadyThere) {
            0 -> ""
            1 -> " 1 entry already in ProtocolTracker is skipped."
            else -> " $alreadyThere entries already in ProtocolTracker are skipped."
        }
        return listOfNotNull(
            if (span == null) "Nothing to import." else "$span: $counts.",
            "Doses, dose notes, weekly notes, tracker ticks and settings are not imported.$skipped",
            "There is no undo. Save a backup first if you want a way back.",
            warnings.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• ${it.text(format)}" },
        ).joinToString("\n\n")
    }
}

/**
 * Web records with no home in the app, counted: dose logs (every dose type, tracker ticks and old injection logs,
 * plus the AI and Dbol doses on symptom rows), checklist ticks, weekly notes, settings and bloodwork PDFs.
 */
data class WebLeftOut(
    val doses: Int = 0,
    val trackerTicks: Int = 0,
    val weeklyNotes: Int = 0,
    val settings: Int = 0,
    val pdfs: Int = 0,
) {
    val total: Int get() = doses + trackerTicks + weeklyNotes + settings + pdfs
}

/** One reason why records were left out or changed, with how often it happened and the first few examples. */
data class WebImportWarning(
    val reason: Reason,
    val count: Int,
    val examples: List<Example>,
    /** Distinct examples not in [examples]. */
    val more: Int,
) {
    enum class Reason {
        CAPPED, UNREADABLE, BLOOD_PRESSURE, SCALE, RESULT, NO_RESULTS, SAME_DAY_DRAW, OTHER_MARKER, SHORTENED,
    }

    /** "Hemoglobin 146 g/dL" on a date, a date alone, or a name alone. */
    data class Example(val text: String?, val date: LocalDate?) {
        fun text(format: DisplayFormat): String = listOfNotNull(text, date?.let(format.date::format)).joinToString(" on ")
    }

    /** One line for the confirm dialog, dates in [format]. */
    fun text(format: DisplayFormat = DisplayFormat.current): String {
        val n = count
        val head = when (reason) {
            Reason.CAPPED -> return "This file holds at most 5,000 logs and 1,000 symptom logs, so older entries may be " +
                "missing. The full export has everything."
            Reason.UNREADABLE -> "$n ${plural(n, "record")} without a readable date, id or value left out"
            Reason.BLOOD_PRESSURE -> "$n impossible blood pressure ${plural(n, "reading")} left out"
            Reason.SCALE -> "$n mood or hair shedding ${plural(n, "value")} outside the scale left out"
            Reason.RESULT -> "$n impossible lab ${plural(n, "result")} left out"
            Reason.NO_RESULTS -> "$n blood ${plural(n, "draw")} without results left out"
            Reason.SAME_DAY_DRAW -> "$n blood ${plural(n, "draw")} on a day that already has a draw in the app left out"
            Reason.OTHER_MARKER -> "$n ${plural(n, "result")} of tests the app does not list kept without a unit"
            Reason.SHORTENED -> "$n ${plural(n, "text")} over 5,000 characters shortened"
        }
        if (examples.isEmpty()) return "$head."
        val tail = if (more > 0) " and $more more" else ""
        return "$head: ${examples.joinToString(", ") { it.text(format) }}$tail."
    }

    private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
}

/**
 * Reads the CycleTracker web app's full export (`/api/export/full`) or its Logs page "Export AI Review" file into
 * journal entries: blood pressure, notes, symptom logs (with the old mood logs) and blood draws. Ids come from the web
 * ids (`web:log:<id>`, `web:symptom:<id>`, `web:bloodwork:<date>`), so saving the same file twice never duplicates.
 * Records the app already holds are skipped; records with no home are counted, never guessed (research: web-export).
 */
object WebExportImport {
    /** Id prefix of every imported entry; entries without it were made in the app. */
    const val ID_PREFIX = "web:"

    /** The web app's markers (backend/units.py): same key, unit, SI factor and default range as [BloodMarkers]. */
    val WEB_MARKERS: Set<String> = setOf(
        "total_testosterone", "free_testosterone", "estradiol", "shbg", "lh", "fsh", "tsh", "hemoglobin", "hematocrit",
        "cholesterol", "hdl", "ldl", "non_hdl", "triglycerides", "glucose", "creatinine", "albumin", "ast", "alt", "ggt",
        "ck", "psa",
    )

    private val DUPLICATE_WINDOW: Duration = Duration.ofMinutes(15)
    private const val LOG_CAP = 5_000
    private const val SYMPTOM_CAP = 1_000
    private const val MAX_EXAMPLES = 3
    private val ENTRY_LOG_TYPES = setOf("blood_pressure", "note", "mood")
    private val SYMPTOM_ROW_DOSES = listOf("aromasin_dose_mg", "anastrozole_dose_mg", "dbol_dose_mg")

    /** A JSON object without a `format` key that holds a `logs`, `symptoms` or `bloodwork` array. */
    fun matches(text: String): Boolean = (rootOf(text) as? JsonObject)?.let(::isWebExport) ?: false

    /**
     * The entries in [text], dated in [zone] where the web kept only a date. Entries with an id in [existing], and
     * readings, notes and symptom logs made in the app within 15 minutes with the same values, are skipped; a web draw
     * on a date that has a draw made in the app is left out with a warning.
     */
    fun parse(text: String, zone: ZoneId, existing: List<JournalEntry> = emptyList()): WebImport {
        val root = rootOf(text) ?: throw ImportFormatException("Not a JSON export file")
        if (root !is JsonObject || !isWebExport(root)) throw ImportFormatException("Not a web app export")
        return Reader(zone, existing).read(root)
    }

    private fun rootOf(text: String): JsonElement? = try { Json.parseToJsonElement(text) } catch (e: Exception) { null }

    private fun isWebExport(root: JsonObject): Boolean =
        "format" !in root && listOf("logs", "symptoms", "bloodwork").any { root[it] is JsonArray }

    private class Reader(private val zone: ZoneId, existing: List<JournalEntry>) {
        private val existingIds = existing.mapTo(HashSet()) { it.id }
        private val native = existing.filterNot { it.id.startsWith(ID_PREFIX) }
        private val nativeReadings = native.filterIsInstance<JournalEntry.BloodPressure>()
        private val nativeNotes = native.filterIsInstance<JournalEntry.Note>()
        private val nativeSymptoms = native.filterIsInstance<JournalEntry.Symptoms>()
        private val nativeDrawDates = native.filterIsInstance<JournalEntry.Bloodwork>().mapTo(HashSet()) { it.at.atZone(zone).toLocalDate() }

        private val entries = ArrayList<JournalEntry>()
        private val seenIds = HashSet<String>()
        private var alreadyThere = 0
        private var doses = 0
        private var pdfs = 0
        private val groups = sortedMapOf<WebImportWarning.Reason, Group>()

        /** Warnings of the record being read; kept only when the record is not already in the app. */
        private val pending = ArrayList<Pair<WebImportWarning.Reason, WebImportWarning.Example?>>()

        private class Group {
            var count = 0
            val examples = LinkedHashSet<WebImportWarning.Example>()
        }

        fun read(root: JsonObject): WebImport {
            val logs = root.array("logs")
            val symptoms = root.array("symptoms")
            logs.forEach { record { readLog(it) } }
            symptoms.forEach { record { readSymptoms(it) } }
            root.array("bloodwork").forEach { record { readDraw(it) } }
            if ("user" !in root && (logs.size >= LOG_CAP || symptoms.size >= SYMPTOM_CAP)) warn(WebImportWarning.Reason.CAPPED, null)
            val leftOut = WebLeftOut(
                doses = doses,
                trackerTicks = root.array("checklist").size,
                weeklyNotes = root.array("weekly_goals").count { (it as? JsonObject)?.get("custom_note").string()?.isNotBlank() == true },
                settings = (root["settings"] as? JsonObject)?.size ?: 0,
                pdfs = pdfs,
            )
            val warnings = groups.map { (reason, g) ->
                WebImportWarning(reason, g.count, g.examples.take(MAX_EXAMPLES), (g.examples.size - MAX_EXAMPLES).coerceAtLeast(0))
            }
            return WebImport(entries.sortedWith(compareBy({ it.at }, { it.id })), alreadyThere, leftOut, warnings)
        }

        /** Reads one record; its warnings count only if it is not already in the app. */
        private fun record(read: () -> Outcome) {
            pending.clear()
            val outcome = read()
            if (outcome is Outcome.Entry) {
                val e = outcome.entry
                when {
                    !seenIds.add(e.id) -> return
                    e.id in existingIds || nativeCopy(e) -> { alreadyThere++; return }
                    else -> entries += e
                }
            } else if (outcome == Outcome.AlreadyThere) {
                alreadyThere++
                return
            }
            pending.forEach { (reason, example) -> warn(reason, example) }
        }

        private sealed interface Outcome {
            data class Entry(val entry: JournalEntry) : Outcome
            data object Skipped : Outcome
            data object AlreadyThere : Outcome
        }

        private fun warn(reason: WebImportWarning.Reason, example: WebImportWarning.Example?) {
            val g = groups.getOrPut(reason) { Group() }
            g.count++
            if (example != null) g.examples += example
        }

        private fun later(reason: WebImportWarning.Reason, text: String?, date: LocalDate?): Outcome {
            pending += reason to (if (text == null && date == null) null else WebImportWarning.Example(text, date))
            return Outcome.Skipped
        }

        private fun unreadable() = later(WebImportWarning.Reason.UNREADABLE, null, null)

        private fun readLog(row: JsonElement): Outcome {
            val o = row as? JsonObject ?: return unreadable()
            val type = o["type"].string() ?: return unreadable()
            if (type !in ENTRY_LOG_TYPES) { doses++; return Outcome.Skipped }
            val id = o["id"].idText()?.let { "${ID_PREFIX}log:$it" } ?: return unreadable()
            val at = instantOf(o["created_at"].string()) ?: return unreadable()
            val data = o["data"] as? JsonObject ?: JsonObject(emptyMap())
            val date = at.atZone(zone).toLocalDate()
            return when (type) {
                "blood_pressure" -> {
                    val sys = data["sys"].number()?.roundToInt()
                    val dia = data["dia"].number()?.roundToInt()
                    val pulse = data["hr"].number()?.roundToInt()?.takeIf { it != 0 }
                    if (sys == null || dia == null) return unreadable()
                    if (bloodPressureProblems(sys, dia, pulse).isNotEmpty()) {
                        return later(WebImportWarning.Reason.BLOOD_PRESSURE, "$sys/$dia", date)
                    }
                    Outcome.Entry(JournalEntry.BloodPressure(id, at, sys, dia, pulse, text(data["notes"], date), at))
                }
                "note" -> {
                    val note = text(data["text"], date)
                    if (note.isBlank()) Outcome.Skipped else Outcome.Entry(JournalEntry.Note(id, at, note, at))
                }
                else -> symptoms(id, at, emptyList(), data["mood_level"], null, data["notes"])
            }
        }

        private fun readSymptoms(row: JsonElement): Outcome {
            val o = row as? JsonObject ?: return unreadable()
            doses += SYMPTOM_ROW_DOSES.count { (o[it].number() ?: 0.0) > 0 }
            val id = o["id"].idText()?.let { "${ID_PREFIX}symptom:$it" } ?: return unreadable()
            val at = instantOf(o["created_at"].string()) ?: return unreadable()
            val keys = (o["symptoms"] as? JsonArray).orEmpty().mapNotNull { it.string()?.trim()?.takeIf(String::isNotEmpty) }.distinct()
            return symptoms(id, at, keys, o["mood_level"], o["hair_shedding_level"], o["notes"])
        }

        private fun symptoms(id: String, at: Instant, keys: List<String>, mood: JsonElement?, hair: JsonElement?, note: JsonElement?): Outcome {
            val date = at.atZone(zone).toLocalDate()
            val m = scale(mood, 1..10, "mood", date)
            val h = scale(hair, 1..HAIR_SHEDDING_LABELS.size, "hair shedding", date)
            val n = text(note, date)
            if (keys.isEmpty() && m == null && h == null && n.isBlank()) return Outcome.Skipped
            return Outcome.Entry(JournalEntry.Symptoms(id, at, keys, m, h, n, at))
        }

        /** A 1-based scale value; null or 0 means none, anything outside [range] is left out with a warning. */
        private fun scale(el: JsonElement?, range: IntRange, label: String, date: LocalDate): Int? {
            val v = el.number() ?: return null
            if (v == 0.0) return null
            val rounded = v.roundToInt()
            if (rounded in range) return rounded
            later(WebImportWarning.Reason.SCALE, "$label ${formatNumber(v)}", date)
            return null
        }

        private fun readDraw(row: JsonElement): Outcome {
            val o = row as? JsonObject ?: return unreadable()
            if (o["has_pdf"].let { it is JsonPrimitive && it.content == "true" } || o["pdf_filename"].string() != null) pdfs++
            val date = o["test_date"].string()?.trim()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return unreadable()
            val id = "${ID_PREFIX}bloodwork:$date"
            if (id in existingIds) return Outcome.AlreadyThere
            val markers = (o["markers"] as? JsonArray).orEmpty()
            if (markers.isEmpty()) return later(WebImportWarning.Reason.NO_RESULTS, null, date)
            if (date in nativeDrawDates) return later(WebImportWarning.Reason.SAME_DAY_DRAW, null, date)
            val results = ArrayList<MarkerResult>()
            val keys = HashSet<String>()
            markers.forEach { result(it, date, keys)?.let(results::add) }
            if (results.isEmpty()) return later(WebImportWarning.Reason.NO_RESULTS, null, date)
            val at = date.atTime(BloodworkRules.UNKNOWN_DRAW_TIME).atZone(zone).toInstant()
            return Outcome.Entry(JournalEntry.Bloodwork(id, at, results, lab = "", note = text(o["notes"], date), createdAt = at))
        }

        /** One web marker as a result, or null when it is unreadable, impossible or a repeat of a key in this draw. */
        private fun result(el: JsonElement, date: LocalDate, keys: MutableSet<String>): MarkerResult? {
            val o = el as? JsonObject ?: run { unreadable(); return null }
            val webKey = o["key"].string()?.trim()?.takeIf(String::isNotEmpty) ?: run { unreadable(); return null }
            val us = o["us"].let { if (it is JsonObject) it["value"].number() else it.number() }
            val low = o["ref_low"].number()
            val high = o["ref_high"].number()
            val marker = BloodMarkers.find(webKey)?.takeIf { webKey in WEB_MARKERS }
            if (marker != null) {
                if (!keys.add(webKey)) return null
                val si = (o["si"] as? JsonObject)?.get("value").number()
                val value = exactValue(us, si, marker.siToConventional) ?: run { unreadable(); return null }
                if (!BloodworkRules.plausible(webKey, value)) {
                    later(WebImportWarning.Reason.RESULT, "${marker.name} ${formatNumber(value)} ${marker.unit}", date)
                    return null
                }
                // The export fills a side without a lab limit with the web default, so only a differing side proves a
                // lab range; then both sides are kept, so the flag stays the one the web showed.
                val refLow = low ?: marker.refLow
                val refHigh = high ?: marker.refHigh
                val lab = !same(refLow, marker.refLow) || !same(refHigh, marker.refHigh)
                return withRange(MarkerResult(webKey, value), if (lab) refLow else null, if (lab) refHigh else null)
            }
            // Never a known key, not even prolactin or eGFR: the export drops the unit of these results.
            val name = webKey.replace('_', ' ').trim()
            val base = BloodworkRules.otherKey(name)
            val value = us ?: run { unreadable(); return null }
            if (!BloodworkRules.plausible(base, value)) {
                later(WebImportWarning.Reason.RESULT, "$name ${formatNumber(value)}", date)
                return null
            }
            var key = base
            var n = 2
            while (!keys.add(key)) key = "${base}_${n++}"
            later(WebImportWarning.Reason.OTHER_MARKER, name, null)
            return withRange(MarkerResult(key, value, name = name), low, high)
        }

        /** [r] with the lab range [low]–[high], or without one when neither side is set or the range is invalid. */
        private fun withRange(r: MarkerResult, low: Double?, high: Double?): MarkerResult {
            val ranged = r.copy(refLow = low, refHigh = high)
            return if (ranged.labRange() != null) ranged else r
        }

        /** Trimmed free text; longer than a note may be, it is shortened with a warning. */
        private fun text(el: JsonElement?, date: LocalDate): String {
            val s = el.string()?.trim().orEmpty()
            val max = JournalEntry.MAX_NOTE_LENGTH
            if (s.length <= max) return s
            later(WebImportWarning.Reason.SHORTENED, null, date)
            val cut = if (s[max - 2].isHighSurrogate()) max - 2 else max - 1
            return s.take(cut) + "…"
        }

        private fun nativeCopy(e: JournalEntry): Boolean {
            fun near(a: Instant) = Duration.between(a, e.at).abs() <= DUPLICATE_WINDOW
            return when (e) {
                is JournalEntry.BloodPressure -> nativeReadings.any { it.systolic == e.systolic && it.diastolic == e.diastolic && near(it.at) }
                is JournalEntry.Note -> nativeNotes.any { it.text.trim() == e.text && near(it.at) }
                is JournalEntry.Symptoms -> nativeSymptoms.any {
                    it.symptoms.toSet() == e.symptoms.toSet() && it.mood == e.mood && it.hairShedding == e.hairShedding && near(it.at)
                }
                is JournalEntry.Bloodwork -> false
            }
        }
    }

    /**
     * The stored value of a web marker from the export's [us] (the stored value to 2 decimals) and [si] (stored ÷ [factor]
     * to 3 decimals). When [us] gives back [si], [us] is the value as entered (cholesterol 200 mg/dL, never 200.0012);
     * otherwise [si] was entered and carries the decimals [us] lost (creatinine 85 µmol/L = 0.9615 mg/dL, not 0.96).
     */
    internal fun exactValue(us: Double?, si: Double?, factor: Double): Double? {
        val fromSi = si?.let { round4(it * factor) }
        return when {
            us == null -> fromSi
            si == null || abs(us / factor - si) <= 0.0005 + 1e-9 -> us
            abs(fromSi!! - us) <= 0.005 + 1e-9 -> fromSi
            else -> us
        }
    }

    private fun same(a: Double?, b: Double?): Boolean = if (a == null || b == null) a == b else abs(a - b) < 1e-9

    private fun round4(x: Double): Double = (x * 10_000.0).roundToLong() / 10_000.0

    /** Naive times are UTC (how the web stores them); `Z` and offsets are read too. Truncated to milliseconds. */
    internal fun instantOf(s: String?): Instant? {
        val t = s?.trim()?.replace(' ', 'T')?.takeIf(String::isNotEmpty) ?: return null
        val instant = runCatching { LocalDateTime.parse(t).toInstant(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant() }.getOrNull()
        return instant?.truncatedTo(ChronoUnit.MILLIS)
    }

    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    /** A web id: a JSON number or string, as written. */
    private fun JsonElement?.idText(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf(String::isNotEmpty)

    /** A finite number, written as a JSON number or a numeric string. */
    private fun JsonElement?.number(): Double? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        val v = if (p.isString) p.content.trim().toDoubleOrNull() else p.doubleOrNull
        return v?.takeIf { it.isFinite() }
    }
}
