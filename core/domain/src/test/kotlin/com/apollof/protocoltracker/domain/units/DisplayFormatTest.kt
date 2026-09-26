package com.apollof.protocoltracker.domain.units

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayFormatTest {
    private val locale = Locale.getDefault()

    @AfterTest
    fun reset() {
        DisplayFormat.current = DisplayFormat()
        Locale.setDefault(locale)
    }

    @Test
    fun volumesInSyringeUnits() {
        val units = DisplayFormat(syringeUnits = true)
        assertEquals("25 u", formatVolume(0.25, units))
        assertEquals("12.5 u", formatVolume(0.125, units))
        assertEquals("0.25 mL", formatVolume(0.25, DisplayFormat()))
        assertEquals("250 mg · 1.0 mL", describeDose(Amount(250.0, DoseUnit.MG), BaseUnit.MG, Formulation(perMl = 250.0), DisplayFormat()))
        assertEquals("250 mg · 100 u", describeDose(Amount(250.0, DoseUnit.MG), BaseUnit.MG, Formulation(perMl = 250.0), units))
    }

    @Test
    fun describeDoseFollowsTheCurrentFormat() {
        DisplayFormat.current = DisplayFormat(syringeUnits = true)
        assertEquals("2 mg · 20 u", describeDose(Amount(2.0, DoseUnit.MG), BaseUnit.MG, Formulation(perMl = 10.0)))
    }

    @Test
    fun timeAndDateOrder() {
        Locale.setDefault(Locale.ENGLISH)
        val h12 = DisplayFormat(use24Hour = false, dayFirst = false)
        assertEquals("21:05", LocalTime.of(21, 5).format(DisplayFormat().time))
        assertEquals("9:05 PM", LocalTime.of(21, 5).format(h12.time).uppercase())
        val date = LocalDate.of(2026, 9, 26)
        assertEquals("26 Sep", date.format(DisplayFormat().dayMonth))
        assertEquals("Sep 26", date.format(h12.dayMonth))
        assertEquals("Sat, Sep 26", date.format(h12.dayShort))
    }

    @Test
    fun localeDayOrder() {
        assertTrue(DisplayFormat.localeDayFirst(Locale.UK))
        assertTrue(DisplayFormat.localeDayFirst(Locale.GERMANY))
        assertFalse(DisplayFormat.localeDayFirst(Locale.US))
    }
}
