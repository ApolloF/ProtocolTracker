package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.RefRange
import kotlin.test.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabUnitsTest {
    @Test
    fun theTestosteroneReadingNamesTAndE2AsReported() {
        val at = Instant.parse("2026-09-20T07:00:00Z")
        val draw = JournalEntry.Bloodwork(
            "b", at, listOf(MarkerResult("total_testosterone", 1100.0), MarkerResult("estradiol", 10.0, qualifier = "<"), MarkerResult("hematocrit", 49.0)),
            createdAt = at,
        )
        assertEquals("Bloodwork · T 1100 ng/dL · E2 <10 pg/mL", labReadingLine(draw, "Testosterone", LabUnits.CONVENTIONAL))
        assertEquals("Bloodwork · T 38.1 nmol/L · E2 <36.7 pmol/L", labReadingLine(draw, "Testosterone", LabUnits.SI))
        val onlyE2 = draw.copy(results = listOf(MarkerResult("estradiol", 45.0)))
        assertEquals("Bloodwork · E2 45 pg/mL", labReadingLine(onlyE2, "Testosterone", LabUnits.CONVENTIONAL))
        assertNull(labReadingLine(draw.copy(results = listOf(MarkerResult("hematocrit", 49.0))), "Testosterone", LabUnits.CONVENTIONAL))
        assertNull(labReadingLine(draw, "Nandrolone", LabUnits.CONVENTIONAL))
    }

    @Test
    fun testosteroneInNanomolesPerLitre() {
        val scale = LevelScale(relative = false, unit = LevelUnit.NG_DL, baseUnit = BaseUnit.MG)
        val si = levelDisplay(scale, "Testosterone", LabUnits.SI)
        assertEquals("nmol/L", si.label)
        // The usual lab factor: 1 nmol/L = 28.84 ng/dL.
        assertEquals(1000.0 / 28.84, 1000.0 * si.factor, 0.05)
        assertEquals(LevelDisplay("ng/dL", 1.0), levelDisplay(scale, "Testosterone", LabUnits.CONVENTIONAL))
    }

    @Test
    fun picogramsBecomePicomoles() {
        val scale = LevelScale(relative = false, unit = LevelUnit.PG_ML, baseUnit = BaseUnit.MG)
        val si = levelDisplay(scale, "Cabergoline", LabUnits.SI)
        assertEquals("pmol/L", si.label)
        assertEquals(1000.0 / 451.60, si.factor, 1e-9)
    }

    @Test
    fun relativeAndUnknownGroupsKeepTheirUnit() {
        val relative = LevelScale(relative = true, unit = LevelUnit.NG_DL, baseUnit = BaseUnit.MG)
        assertEquals(1.0, levelDisplay(relative, "Testosterone", LabUnits.SI).factor)
        val absolute = LevelScale(relative = false, unit = LevelUnit.NG_ML, baseUnit = BaseUnit.MG)
        assertEquals(LevelDisplay("ng/mL", 1.0), levelDisplay(absolute, "Semaglutide", LabUnits.SI))
    }

    @Test
    fun presetGroupsWithAbsoluteCurvesHaveAMolarMassOrAreMassMeasured() {
        val massMeasured = setOf("hCG", "Somatropin", "Semaglutide", "Tirzepatide", "Retatrutide", "Mazdutide", "Liraglutide",
            "Cagrilintide", "Tesamorelin", "Bremelanotide", "CJC-1295", "CJC-1295 DAC", "Ipamorelin", "BPC-157", "TB-500", "GHK-Cu",
            "Melanotan II", "AOD-9604", "MOTS-c")
        val missing = Presets.all.filter { it.pk?.peakPerUnit != null }.map { it.group }.distinct()
            .filter { it !in MolarMass.byGroup && it !in massMeasured }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun bloodMarkersConvertBothWays() {
        val t = BloodMarkers.find("total_testosterone")!!
        assertEquals(865.2, t.toStored(30.0, LabUnits.SI), 1e-9)
        assertEquals(30.0, t.fromStored(865.2, LabUnits.SI), 1e-9)
        assertEquals("865 ng/dL", t.format(865.2, LabUnits.CONVENTIONAL))
        assertEquals("30 nmol/L", t.format(865.2, LabUnits.SI))
        assertEquals("264–916 ng/dL", t.rangeText(RefRange(264.0, 916.0), LabUnits.CONVENTIONAL))
        val ldl = BloodMarkers.find("ldl")!!
        assertEquals("< 3.36 mmol/L", ldl.rangeText(RefRange(null, 130.0), LabUnits.SI))
    }

    @Test
    fun markerKeysAreUniqueAndFactorsPositive() {
        assertEquals(BloodMarkers.all.size, BloodMarkers.all.map { it.key }.distinct().size)
        assertTrue(BloodMarkers.all.all { it.siToConventional > 0 })
    }
}
