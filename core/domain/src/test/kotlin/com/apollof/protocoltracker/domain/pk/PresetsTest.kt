package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Rise
import com.apollof.protocoltracker.domain.model.compoundOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetsTest {
    private fun byId(id: String) = assertNotNull(Presets.byId("preset:$id"), id)

    @Test
    fun idsAreUniqueAndEveryCategoryIsCovered() {
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
        assertEquals(CompoundCategory.entries.toSet(), Presets.all.map { it.category }.toSet())
        assertTrue(Presets.all.filter { it.category == CompoundCategory.SUPPORT }.all { it.supportKind != null })
        assertTrue(Presets.all.all { it.sourceNote.isNotBlank() })
    }

    @Test
    fun namesAreColloquialWithScientificInParentheses() {
        assertEquals("Anavar (oxandrolone)", byId("oxandrolone").displayName)
        assertEquals("Test E (testosterone enanthate)", byId("test-enan").displayName)
        assertEquals("Cialis (tadalafil)", byId("tadalafil").displayName)
        assertEquals("Telmisartan", byId("telmisartan").displayName)
        assertEquals("Tirzepatide", byId("tirzepatide").displayName)
        assertEquals("BPC-157", byId("bpc-157").displayName)
    }

    @Test
    fun sortsInjectablesOralsSupportPeptides() {
        val sorted = Presets.all.sortedWith(compoundOrder)
        val categories = sorted.map { it.category }.distinct()
        assertEquals(listOf(CompoundCategory.INJECTABLE_STEROID, CompoundCategory.ORAL_STEROID, CompoundCategory.SUPPORT, CompoundCategory.PEPTIDE), categories)
        val support = sorted.filter { it.category == CompoundCategory.SUPPORT }.map { it.supportKind!!.ordinal }
        assertEquals(support.sorted(), support)
    }

    @Test
    fun sheetValuesAreCopiedInHoursAndPerMg() {
        val te = byId("test-enan").pk!!
        assertEquals(7.19 * 24, te.halfLifeH, 1e-9)
        assertEquals(1.3875 * 24, te.tmaxH, 1e-9)
        assertEquals(11.3095 * 0.33, te.peakPerUnit!!, 1e-9) // the Test E multiplier
        // Tadalafil 20 mg: sheet Cmax gives 345 ng/mL (label reports 378 ng/mL).
        val cialis = byId("tadalafil").pk!!
        assertEquals(345.0, 20 * cialis.peakPerUnit!! * cialis.levelUnit.perNgDl, 1e-6)
    }

    @Test
    fun basicSheetRowsUseTheBasicModel() {
        // Tren E: t½ 11 d, F 0.71, no multiplier → Tmax 3/8 t½, peak 14 × F × 87.5 % per mg.
        val tren = byId("tren-enan").pk!!
        assertEquals(11 * 24.0, tren.halfLifeH, 1e-9)
        assertEquals(11 * 24.0 * 3 / 8, tren.tmaxH, 1e-9)
        assertEquals(14 * 0.71 * 0.875, tren.peakPerUnit!!, 1e-9)
        assertEquals(Rise.FIRST_ORDER, tren.rise)
        assertEquals(Rise.LINEAR, byId("test-enan").pk!!.rise)
    }

    @Test
    fun everySheetCompoundPresetIsAbsolute() {
        val ids = listOf("test-pp", "test-iso", "test-dec", "tren-ace", "tren-enan", "tren-hex", "mast-prop", "mast-enan",
            "primo-enan", "bold-cyp", "dhb", "ment", "methandienone", "turinabol", "stanozolol", "superdrol", "primo-oral")
        for (id in ids) assertNotNull(byId(id).pk!!.peakPerUnit, id)
    }

    @Test
    fun boldenoneCypionateClonesTestC() {
        assertEquals(byId("test-cyp").pk, byId("bold-cyp").pk)
    }

    @Test
    fun peptidesWithoutHumanDataAreLogOnly() {
        for (id in listOf("bpc-157", "tb-500", "ghk-cu", "melanotan-2", "aod-9604", "mots-c")) assertNull(byId(id).pk, id)
        assertNotNull(byId("semaglutide").pk)
    }
}
