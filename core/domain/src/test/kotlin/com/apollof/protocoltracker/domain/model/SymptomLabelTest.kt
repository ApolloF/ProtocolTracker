package com.apollof.protocoltracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SymptomLabelTest {
    @Test
    fun unknownKeysReadAsNames() {
        assertEquals("Acne", SymptomCatalog.readableLabel("acne"))
        assertEquals("High E2", SymptomCatalog.readableLabel("high_e2"))
        assertEquals("Bloating", SymptomCatalog.readableLabel("bloating"))
        assertEquals("high e2", SymptomCatalog.label("high_e2"), "reports keep the stored form")
    }
}
