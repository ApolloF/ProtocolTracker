package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.io.WebImportWarning.Reason
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.SymptomCatalog
import com.apollof.protocoltracker.domain.model.labRange
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.units.DisplayFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The web app history import (HIST-1, HIST-2): mapping, skipping, duplicates and warnings. */
class WebExportImportTest {
    private val ams = ZoneId.of("Europe/Amsterdam")
    private val full = WebExportImport.parse(WebExportFixtures.FULL, ams)
    private val messy = WebExportImport.parse(WebExportFixtures.MESSY, ams)
    private val format = DisplayFormat()

    private inline fun <reified T : JournalEntry> WebImport.entry(id: String): T = entries.single { it.id == id } as T
    private fun WebImport.warning(reason: Reason): WebImportWarning = warnings.single { it.reason == reason }
    private fun day(y: Int, m: Int, d: Int) = format.date.format(LocalDate.of(y, m, d))

    @Test
    fun fullExportGivesEveryJournalKindAndCountsWhatHasNoHome() {
        assertEquals(listOf(2, 1, 4, 1), listOf(full.bloodPressure, full.notes, full.symptoms, full.draws))
        assertEquals(WebLeftOut(doses = 7, trackerTicks = 2, weeklyNotes = 1, settings = 6, pdfs = 1), full.leftOut)
        assertEquals(0, full.alreadyThere)
        assertEquals(Instant.parse("2026-05-09T21:00:00.500Z"), full.from)
        assertEquals(Instant.parse("2026-09-26T06:12:44.918Z"), full.to)
        assertEquals(full.entries.sortedWith(compareBy({ it.at }, { it.id })), full.entries)
        assertEquals(listOf(Reason.NO_RESULTS, Reason.OTHER_MARKER), full.warnings.map { it.reason })
    }

    @Test
    fun naiveTimesAreUtcAndTruncatedToMilliseconds() {
        val bp = full.entry<JournalEntry.BloodPressure>("web:log:2210")
        assertEquals(Instant.parse("2026-09-26T06:12:44.918Z"), bp.at)
        assertEquals(bp.at, bp.createdAt)
        assertEquals(Instant.parse("2026-09-12T18:40:00Z"), full.entry<JournalEntry.BloodPressure>("web:log:2101").at)
        assertEquals(Instant.parse("2026-09-02T07:00:00Z"), messy.entry<JournalEntry.BloodPressure>("web:log:3002").at)
        assertEquals(Instant.parse("2026-09-20T10:00:00Z"), WebExportImport.instantOf("2026-09-20T10:00:00"))
        assertEquals(Instant.parse("2026-09-03T07:00:00Z"), WebExportImport.instantOf("2026-09-03T09:00:00+02:00"))
        assertEquals(Instant.parse("2026-09-20T10:00:00.123Z"), WebExportImport.instantOf("2026-09-20 10:00:00.123999"))
        assertNull(WebExportImport.instantOf("yesterday"))
    }

    @Test
    fun bloodPressureReadsNumbersAndStringsAndSkipsImpossibleReadings() {
        assertEquals(62, full.entry<JournalEntry.BloodPressure>("web:log:2210").pulse)
        assertEquals("", full.entry<JournalEntry.BloodPressure>("web:log:2101").note) // notes absent
        val strings = messy.entry<JournalEntry.BloodPressure>("web:log:3001")
        assertEquals(listOf(131, 84, null), listOf(strings.systolic, strings.diastolic, strings.pulse)) // hr 0
        val nullPulse = messy.entry<JournalEntry.BloodPressure>("web:log:3002")
        assertEquals(null to "after coffee", nullPulse.pulse to nullPulse.note)
        val absent = WebExportImport.parse(WebExportFixtures.aiReview(3), ams).entries.filterIsInstance<JournalEntry.BloodPressure>()
        assertEquals(listOf(null, null, null), absent.map { it.pulse }) // hr absent
        val w = messy.warning(Reason.BLOOD_PRESSURE)
        assertEquals(2, w.count)
        assertEquals("2 impossible blood pressure readings left out: 40/30 on ${day(2026, 9, 3)}, 120/130 on ${day(2026, 9, 4)}.", w.text(format))
    }

    @Test
    fun notesAndSymptomsKeepWhatWasWrittenAndDropDoses() {
        assertEquals("Slept badly, headache in the evening.", full.entry<JournalEntry.Note>("web:log:1733").text)
        assertEquals(7, full.entry<JournalEntry.Symptoms>("web:log:1402").mood) // old mood log
        val row = full.entry<JournalEntry.Symptoms>("web:symptom:388")
        assertEquals(listOf("acne", "extreme_oiliness", "water_retention"), row.symptoms)
        assertEquals(listOf(6, 2), listOf(row.mood, row.hairShedding))
        assertEquals("Oily skin after the gym", row.note)
        assertEquals(listOf("high_e2", "bloating"), full.entry<JournalEntry.Symptoms>("web:symptom:301").symptoms) // unknown keys kept
        assertTrue(full.entries.none { it.id == "web:symptom:381" || it.id == "web:symptom:360" }) // AI or Dbol dose only

        val cleaned = messy.entry<JournalEntry.Symptoms>("web:symptom:501")
        assertEquals(listOf("acne", "high_e2"), cleaned.symptoms)
        assertEquals(listOf(null, null, ""), listOf(cleaned.mood, cleaned.hairShedding, cleaned.note)) // mood 0, hair 9, notes null
        assertEquals(8, messy.entry<JournalEntry.Symptoms>("web:symptom:503").mood) // mixed row with a dose
        val flat = messy.entry<JournalEntry.Symptoms>("web:log:3010")
        assertEquals(null to "Flat day", flat.mood to flat.note)
        assertTrue(messy.entries.none { it.id == "web:symptom:502" || it.id == "web:log:3006" })
        assertEquals(2, messy.leftOut.doses)
        assertEquals(
            "2 mood or hair shedding values outside the scale left out: hair shedding 9 on ${day(2026, 9, 9)}, mood 12 on ${day(2026, 9, 10)}.",
            messy.warning(Reason.SCALE).text(format),
        )
    }

    @Test
    fun drawKeepsExactValuesAndOnlyRealLabRanges() {
        val draw = full.entry<JournalEntry.Bloodwork>("web:bloodwork:2026-06-05")
        assertEquals("Fasted, 8:30", draw.note)
        assertEquals("", draw.lab)
        assertEquals(3605.0, draw.value("total_testosterone"))
        val e2 = draw.value("estradiol")!!
        assertEquals(103.98, e2, 0.005)
        assertEquals(381.7, BloodMarkers.find("estradiol")!!.fromStored(e2, LabUnits.SI), 0.001)
        assertEquals(0.9615, draw.value("creatinine")) // not the export's rounded 0.96
        assertEquals(48.0, draw.value("hematocrit"))
        val hct = draw.result("hematocrit")!!
        assertEquals(40.0 to 50.0, hct.refLow to hct.refHigh) // one side differs: both kept
        assertNull(draw.result("creatinine")!!.labRange()) // both sides are the defaults
        assertEquals(MarkerResult("other:vitamin_d", 75.0, name = "vitamin d"), draw.result("other:vitamin_d"))
        assertEquals(listOf("total_testosterone", "estradiol", "hematocrit", "creatinine", "other:vitamin_d"), draw.results.map { it.marker })
    }

    @Test
    fun drawDropsImpossibleResultsAndNeverMapsUnknownKeys() {
        val draw = messy.entry<JournalEntry.Bloodwork>("web:bloodwork:2026-07-10")
        assertEquals(listOf("hematocrit", "other:prolactin", "other:ferritine"), draw.results.map { it.marker })
        assertNull(draw.result("prolactin"))
        assertNull(draw.result("hematocrit")!!.labRange())
        assertEquals(30.0 to 400.0, draw.result("other:ferritine")!!.let { it.refLow to it.refHigh })
        assertEquals("Ferritine", draw.result("other:ferritine")!!.name)
        assertTrue(messy.entries.none { it.id == "web:bloodwork:2026-07-20" }) // its only result was impossible

        val results = messy.warning(Reason.RESULT)
        assertEquals(3, results.count)
        assertEquals(
            "3 impossible lab results left out: Hemoglobin 146 g/dL on ${day(2026, 7, 10)}, crp -1 on ${day(2026, 7, 10)}, " +
                "Hemoglobin 150 g/dL on ${day(2026, 7, 20)}.",
            results.text(format),
        )
        assertEquals("2 results of tests the app does not list kept without a unit: prolactin, Ferritine.", messy.warning(Reason.OTHER_MARKER).text(format))
        assertEquals("1 blood draw without results left out: ${day(2026, 7, 20)}.", messy.warning(Reason.NO_RESULTS).text(format))
        assertEquals("1 blood draw without results left out: ${day(2026, 8, 14)}.", full.warning(Reason.NO_RESULTS).text(format)) // PDF only
        assertEquals(8, messy.warning(Reason.UNREADABLE).count)
    }

    @Test
    fun drawKeepsItsCalendarDayInEveryZone() {
        for (zone in listOf(ams, ZoneId.of("Pacific/Honolulu"), ZoneId.of("Pacific/Kiritimati"))) {
            val draw = WebExportImport.parse(WebExportFixtures.FULL, zone).entry<JournalEntry.Bloodwork>("web:bloodwork:2026-06-05")
            val local = draw.at.atZone(zone)
            assertEquals(LocalDate.of(2026, 6, 5), local.toLocalDate())
            assertEquals(LocalTime.NOON, local.toLocalTime())
        }
    }

    @Test
    fun entriesAlreadyInTheAppAreSkipped() {
        val again = WebExportImport.parse(WebExportFixtures.FULL, ams, full.entries)
        assertEquals(emptyList(), again.entries)
        assertEquals(full.entries.size, again.alreadyThere)

        val bp = full.entry<JournalEntry.BloodPressure>("web:log:2210")
        val note = full.entry<JournalEntry.Note>("web:log:1733")
        val symptoms = full.entry<JournalEntry.Symptoms>("web:symptom:388")
        val native = listOf(
            JournalEntry.BloodPressure("a", bp.at + Duration.ofMinutes(15), 131, 84, createdAt = bp.at),
            JournalEntry.BloodPressure("b", Instant.parse("2026-09-12T18:40:00Z") + Duration.ofMinutes(16), 128, 80, createdAt = bp.at),
            JournalEntry.Note("c", note.at - Duration.ofMinutes(10), "Slept badly, headache in the evening.", createdAt = note.at),
            JournalEntry.Symptoms("d", symptoms.at, symptoms.symptoms.reversed(), mood = 5, hairShedding = 2, createdAt = symptoms.at),
            // An imported entry is never a copy of another web record.
            JournalEntry.Note("web:log:1", note.at, "Slept badly, headache in the evening.", createdAt = note.at),
        )
        val result = WebExportImport.parse(WebExportFixtures.FULL, ams, native)
        assertEquals(2, result.alreadyThere)
        assertEquals(full.entries.map { it.id } - setOf("web:log:2210", "web:log:1733"), result.entries.map { it.id })
    }

    @Test
    fun aWebDrawOnADayWithADrawMadeInTheAppIsLeftOut() {
        val mine = JournalEntry.Bloodwork("mine", Instant.parse("2026-06-05T06:30:00Z"), listOf(MarkerResult("estradiol", 30.0)), createdAt = Instant.EPOCH)
        val result = WebExportImport.parse(WebExportFixtures.FULL, ams, listOf(mine))
        assertEquals(0, result.draws)
        assertEquals("1 blood draw on a day that already has a draw in the app left out: ${day(2026, 6, 5)}.", result.warning(Reason.SAME_DAY_DRAW).text(format))
        assertTrue(result.warnings.none { it.reason == Reason.OTHER_MARKER }) // the draw's own warnings go with it
    }

    @Test
    fun longTextsAreShortenedWithOneWarning() {
        val text = WebExportFixtures.FULL.replace("Slept badly, headache in the evening.", "x".repeat(5_001))
        val result = WebExportImport.parse(text, ams)
        val note = result.entry<JournalEntry.Note>("web:log:1733").text
        assertEquals(JournalEntry.MAX_NOTE_LENGTH, note.length)
        assertTrue(note.endsWith("…"))
        assertEquals("1 text over 5,000 characters shortened: ${day(2026, 8, 30)}.", result.warning(Reason.SHORTENED).text(format))
    }

    @Test
    fun aCappedAiReviewFileSaysEntriesMayBeMissing() {
        val capped = WebExportImport.parse(WebExportFixtures.aiReview(5_000), ams)
        assertEquals(5_000, capped.bloodPressure)
        assertEquals(1, capped.symptoms)
        assertEquals(listOf(Reason.CAPPED), capped.warnings.map { it.reason })
        assertTrue(capped.warnings.single().text(format).startsWith("This file holds at most 5,000 logs"))
        assertTrue(WebExportImport.parse(WebExportFixtures.aiReview(20), ams).warnings.isEmpty())
    }

    @Test
    fun outputIsStableAndSafeToBackUp() {
        assertEquals(full, WebExportImport.parse(WebExportFixtures.FULL, ams))
        val journal = full.entries + messy.entries
        val backup = Backup(exportedAt = Instant.EPOCH, compounds = Presets.all, phases = emptyList(), items = emptyList(), logs = emptyList(), journal = journal)
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun onlyWebExportsMatch() {
        assertTrue(WebExportImport.matches(WebExportFixtures.FULL))
        assertTrue(WebExportImport.matches(WebExportFixtures.aiReview(1)))
        assertTrue(WebExportImport.matches("""{"bloodwork":[]}"""))
        assertFalse(WebExportImport.matches("""{"format":"cycletracker-1","logs":[]}"""))
        val backup = BackupCodec.encode(Backup(exportedAt = Instant.EPOCH, compounds = emptyList(), phases = emptyList(), items = emptyList(), logs = emptyList()))
        assertFalse(WebExportImport.matches(backup))
        assertFalse(WebExportImport.matches("{}"))
        assertFalse(WebExportImport.matches("[]"))
        assertFalse(WebExportImport.matches("not json"))
        assertEquals("Not a JSON export file", assertFailsWith<ImportFormatException> { WebExportImport.parse("not json", ams) }.message)
        assertFailsWith<ImportFormatException> { WebExportImport.parse("{}", ams) }
    }

    /** Guard: every symptom key the web app writes (frontend/src/data/symptoms.js) is in the catalog. */
    @Test
    fun everyWebSymptomKeyIsKnown() {
        val web = listOf(
            "dry_skin_lips", "dehydration_feeling", "dry_achy_joints", "loss_of_libido_low", "erectile_dysfunction",
            "loss_of_sensitivity", "dry_glans", "white_glans", "loss_of_girth", "irritability_low", "crying_no_reason",
            "dht_rage", "dull_orgasm", "urination_hesitation", "night_sweats", "loss_of_appetite", "constant_fatigue",
            "constipation_dehydr", "diuretic_effect", "itchy_scalp", "obsessive_thoughts",
            "acne", "loss_of_libido_high", "water_retention", "moon_face", "scrotum_high", "extreme_oiliness",
            "moodiness_high", "lethargy_high", "insomnia", "soft_erections", "sugar_cravings", "high_bp", "bp_spikes",
            "enlarged_prostate", "pressure_urinating", "thin_stream", "constipation_water", "itchy_nipples", "gynecomastia",
        )
        assertEquals(40, web.toSet().size)
        assertEquals(emptyList(), web.filter { SymptomCatalog.find(it) == null })
    }

    /** Guard: the web app's markers (backend/units.py: canonical unit, SI unit, factor, default range) equal ours. */
    @Test
    fun everyWebMarkerMatchesOurs() {
        data class Web(val unit: String, val si: String, val factor: Double, val low: Double?, val high: Double?)
        val web = mapOf(
            "total_testosterone" to Web("ng/dL", "nmol/L", 28.84, 264.0, 916.0),
            "free_testosterone" to Web("pg/mL", "pmol/L", 0.2884, 46.0, 224.0),
            "estradiol" to Web("pg/mL", "pmol/L", 0.2724, 10.0, 40.0),
            "shbg" to Web("nmol/L", "nmol/L", 1.0, 18.3, 54.1),
            "lh" to Web("IU/L", "IU/L", 1.0, 1.7, 8.6),
            "fsh" to Web("IU/L", "IU/L", 1.0, 1.5, 12.4),
            "tsh" to Web("mIU/L", "mIU/L", 1.0, 0.27, 4.20),
            "hemoglobin" to Web("g/dL", "mmol/L", 1.611, 13.5, 17.5),
            "hematocrit" to Web("%", "L/L", 100.0, 40.0, 52.0),
            "cholesterol" to Web("mg/dL", "mmol/L", 38.67, null, 200.0),
            "hdl" to Web("mg/dL", "mmol/L", 38.67, 40.0, null),
            "ldl" to Web("mg/dL", "mmol/L", 38.67, null, 130.0),
            "non_hdl" to Web("mg/dL", "mmol/L", 38.67, null, 130.0),
            "triglycerides" to Web("mg/dL", "mmol/L", 88.57, null, 150.0),
            "glucose" to Web("mg/dL", "mmol/L", 18.0, 70.0, 99.0),
            "creatinine" to Web("mg/dL", "µmol/L", 0.011312, 0.67, 1.17),
            "albumin" to Web("g/dL", "g/L", 0.1, 3.5, 5.2),
            "ast" to Web("U/L", "U/L", 1.0, null, 40.0),
            "alt" to Web("U/L", "U/L", 1.0, null, 50.0),
            "ggt" to Web("U/L", "U/L", 1.0, null, 60.0),
            "ck" to Web("U/L", "U/L", 1.0, null, 190.0),
            "psa" to Web("µg/L", "µg/L", 1.0, null, 4.0),
        )
        assertEquals(web.keys, WebExportImport.WEB_MARKERS)
        web.forEach { (key, w) ->
            val m = BloodMarkers.find(key)!!
            assertEquals(w, Web(m.unit, m.siUnit, m.siToConventional, m.refLow, m.refHigh), key)
        }
    }
}
