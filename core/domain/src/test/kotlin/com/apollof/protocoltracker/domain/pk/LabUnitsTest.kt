package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.MarkerFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabUnitsTest {
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
        assertEquals("264–916 ng/dL", t.referenceText(LabUnits.CONVENTIONAL))
        assertEquals(MarkerFlag.HIGH, t.flag(1200.0))
        assertEquals(MarkerFlag.LOW, t.flag(200.0))
        assertEquals(MarkerFlag.NORMAL, t.flag(600.0))
        val ldl = BloodMarkers.find("ldl")!!
        assertEquals("< 3.36 mmol/L", ldl.referenceText(LabUnits.SI))
        assertEquals(MarkerFlag.NORMAL, BloodMarkers.find("hdl")!!.flag(60.0))
    }

    @Test
    fun markerKeysAreUniqueAndFactorsPositive() {
        assertEquals(BloodMarkers.all.size, BloodMarkers.all.map { it.key }.distinct().size)
        assertTrue(BloodMarkers.all.all { it.siToConventional > 0 })
    }
}
