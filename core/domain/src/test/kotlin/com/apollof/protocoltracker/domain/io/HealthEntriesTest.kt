package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.model.SymptomCatalog
import com.apollof.protocoltracker.domain.model.SymptomGroup
import com.apollof.protocoltracker.domain.model.markerTrends
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.pk.LevelDisplay
import com.apollof.protocoltracker.domain.pk.LevelScale
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.pk.labPoints
import com.apollof.protocoltracker.domain.pk.levelDisplay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Symptom logs and bloodwork (dev builds): validation, backup and reports. */
class HealthEntriesTest {
    private val t = Instant.parse("2026-09-24T07:30:00Z")
    private val symptoms = JournalEntry.Symptoms("s", t, listOf("acne", "night_sweats", "water_retention"), mood = 6, hairShedding = 2, note = "Oily", createdAt = t)
    private val bloodwork = JournalEntry.Bloodwork(
        "b", t, listOf(MarkerResult("total_testosterone", 1200.0), MarkerResult("estradiol", 30.0), MarkerResult("hematocrit", 53.0)),
        lab = "Lab A", createdAt = t,
    )

    @Test
    fun symptomCatalogKeysAreUniqueAndCounted() {
        assertEquals(SymptomCatalog.all.size, SymptomCatalog.all.map { it.key }.distinct().size)
        assertEquals(mapOf(SymptomGroup.LOW_E2 to 1, SymptomGroup.HIGH_E2 to 2), SymptomCatalog.counts(symptoms.symptoms))
        assertEquals("1 low-E2 · 2 high-E2", SymptomCatalog.summary(symptoms.symptoms))
        assertEquals("some new key", SymptomCatalog.label("some_new_key"))
    }

    @Test
    fun entriesValidateTheirValues() {
        assertFailsWith<IllegalArgumentException> { JournalEntry.Symptoms("x", t, createdAt = t) }
        assertFailsWith<IllegalArgumentException> { JournalEntry.Symptoms("x", t, mood = 11, createdAt = t) }
        assertFailsWith<IllegalArgumentException> { JournalEntry.Symptoms("x", t, hairShedding = 6, createdAt = t) }
        assertFailsWith<IllegalArgumentException> { JournalEntry.Bloodwork("x", t, emptyList(), createdAt = t) }
        assertFailsWith<IllegalArgumentException> { MarkerResult("ldl", -1.0) }
        assertFailsWith<IllegalArgumentException> {
            JournalEntry.Bloodwork("x", t, listOf(MarkerResult("ldl", 1.0), MarkerResult("ldl", 2.0)), createdAt = t)
        }
        assertEquals(2, bloodwork.outOfRange)
        assertEquals(1200.0, bloodwork.value("total_testosterone"))
    }

    @Test
    fun trendsShowLatestAndPreviousResultPerMarker() {
        val later = t.plusSeconds(86_400 * 30)
        val second = JournalEntry.Bloodwork("b2", later, listOf(MarkerResult("total_testosterone", 900.0)), createdAt = later)
        val trends = markerTrends(listOf(bloodwork, second, symptoms))
        assertEquals(listOf("total_testosterone", "estradiol", "hematocrit"), trends.map { it.marker.key })
        val testosterone = trends.first()
        assertEquals(900.0, testosterone.value)
        assertEquals(1200.0, testosterone.previous)
        assertEquals(later, testosterone.at)
        assertEquals(null, trends[1].previous)
    }

    @Test
    fun labResultsLandOnTheMatchingCurve() {
        val scale = LevelScale(relative = false, unit = LevelUnit.NG_DL, baseUnit = BaseUnit.MG)
        val conventional = labPoints("Testosterone", scale, levelDisplay(scale, "Testosterone", LabUnits.CONVENTIONAL), listOf(bloodwork, symptoms))
        assertEquals(listOf(1200.0), conventional.map { it.value })
        val si = labPoints("Testosterone", scale, levelDisplay(scale, "Testosterone", LabUnits.SI), listOf(bloodwork))
        assertEquals(1200.0 / 28.842, si.single().value, 0.01)
        assertEquals(emptyList(), labPoints("Nandrolone", scale, LevelDisplay("ng/dL", 1.0), listOf(bloodwork)))
        assertEquals(emptyList(), labPoints("Testosterone", scale.copy(relative = true), LevelDisplay("mg", 1.0), listOf(bloodwork)))
    }

    @Test
    fun backupKeepsSymptomsAndBloodwork() {
        val backup = Backup(exportedAt = t, compounds = Presets.all, phases = emptyList(), items = emptyList(), logs = emptyList(), journal = listOf(symptoms, bloodwork))
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun reportsListSymptomsAndResultsInBothUnits() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val r = ReportBuilder.build(
            Protocol(emptyList(), emptyList(), emptyMap()), emptyList(), listOf(symptoms, bloodwork),
            LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-24"), t, zone, locale = Locale.ENGLISH,
        )
        val md = MarkdownReport.render(r)
        assertTrue("09:30 · Symptoms · Acne, Night sweats, Water retention (bloat) · mood 6/10 · hair shedding mild · note: Oily" in md, md)
        assertTrue("09:30 · Bloodwork · Lab A" in md, md)
        assertTrue("  - Total testosterone 1200 ng/dL (41.6 nmol/L) · ref 264–916 ng/dL · high" in md, md)
        assertTrue("  - Hematocrit 53 % (0.53 L/L) · ref 40–52 % · high" in md, md)
        val html = HtmlReport.render(r)
        assertTrue("<strong>Bloodwork</strong>" in html)
        assertTrue("Night sweats" in html)
    }
}
