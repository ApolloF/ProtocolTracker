package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.compoundOrder
import kotlin.math.abs
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
        assertEquals(11.3095, te.peakPerUnit!!, 1e-9)
        // Tadalafil 20 mg: sheet Cmax gives 345 ng/mL (label reports 378 ng/mL).
        val cialis = byId("tadalafil").pk!!
        assertEquals(345.0, 20 * cialis.peakPerUnit!! * cialis.levelUnit.perNgDl, 1e-6)
    }

    @Test
    fun derivedPeaksKeepAreaPerActiveMg() {
        val ref = byId("test-enan").pk!!
        val pp = byId("test-pp").pk!!
        val refArea = ref.peakPerUnit!! * ref.areaPerPeakH / ref.activeFraction
        val ppArea = pp.peakPerUnit!! * pp.areaPerPeakH / pp.activeFraction
        assertTrue(abs(refArea - ppArea) / refArea < 1e-9)
        assertEquals(Presets.estimatedTmaxH(2.5 * 24, Route.INJECTION), pp.tmaxH, 1e-9)
    }

    @Test
    fun peptidesWithoutHumanDataAreLogOnly() {
        for (id in listOf("bpc-157", "tb-500", "ghk-cu", "melanotan-2", "aod-9604", "mots-c")) assertNull(byId(id).pk, id)
        assertNotNull(byId("semaglutide").pk)
    }

    @Test
    fun basicCompoundsWithoutReferenceStayRelative() {
        assertNull(byId("mast-enan").pk!!.peakPerUnit)
        assertNull(byId("tren-enan").pk!!.peakPerUnit)
    }
}
