package com.apollof.protocoltracker.domain.units

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConversionTest {
    private val oil = Formulation(perMl = 250.0)

    @Test
    fun convertsVolumeTabletsAndMass() {
        assertEquals(125.0, toBase(Amount(0.5, DoseUnit.ML), BaseUnit.MG, oil))
        assertEquals(0.5, toBase(Amount(500.0, DoseUnit.MCG), BaseUnit.MG, Formulation()))
        assertEquals(12.5, toBase(Amount(0.5, DoseUnit.TABLET), BaseUnit.MG, Formulation(perTablet = 25.0)))
        assertEquals(500.0, toBase(Amount(0.1, DoseUnit.ML), BaseUnit.IU, Formulation(perMl = 5000.0)))
    }

    @Test
    fun neverMixesMassAndIu() {
        assertFailsWith<ConversionException> { toBase(Amount(1.0, DoseUnit.MG), BaseUnit.IU, Formulation()) }
        assertFailsWith<ConversionException> { toBase(Amount(1.0, DoseUnit.ML), BaseUnit.MG, Formulation()) }
        assertNull(toBaseOrNull(Amount(1.0, DoseUnit.IU), BaseUnit.MG, Formulation()))
    }

    @Test
    fun describesDoses() {
        assertEquals("125 mg · 0.5 mL", describeDose(Amount(125.0, DoseUnit.MG), BaseUnit.MG, oil))
        assertEquals("0.5 mL · 125 mg", describeDose(Amount(0.5, DoseUnit.ML), BaseUnit.MG, oil))
        assertEquals("1 tab · 25 mg", describeDose(Amount(1.0, DoseUnit.TABLET), BaseUnit.MG, Formulation(perTablet = 25.0)))
        assertEquals("250 IU", describeDose(Amount(250.0, DoseUnit.IU), BaseUnit.IU, Formulation()))
        assertEquals("0.33", formatNumber(1.0 / 3))
    }
}
