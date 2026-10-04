package com.apollof.protocoltracker.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which sections the custom-compound editor offers. */
class CategoryChoicesTest {
    private fun compound(category: CompoundCategory) = Compound(
        id = "c-$category", name = "x", group = "x", category = category, route = Route.ORAL, baseUnit = BaseUnit.MG, colorArgb = 0, pk = null,
    )

    private val withoutResearch = CompoundCategory.entries - CompoundCategory.RESEARCH

    @Test
    fun fossOffersEverySectionAlways() {
        assertEquals(CompoundCategory.entries, categoryChoices(offerEmptyResearch = true, compounds = emptyList(), editing = null))
    }

    @Test
    fun playHidesAnEmptyResearchSection() {
        assertEquals(withoutResearch, categoryChoices(false, emptyList(), null))
        assertEquals(withoutResearch, categoryChoices(false, listOf(compound(CompoundCategory.PEPTIDE)), CompoundCategory.PEPTIDE))
    }

    @Test
    fun playOffersResearchOnceSomethingUsesIt() {
        assertEquals(CompoundCategory.entries, categoryChoices(false, listOf(compound(CompoundCategory.RESEARCH)), null))
        assertEquals(CompoundCategory.entries, categoryChoices(false, emptyList(), CompoundCategory.RESEARCH))
    }
}
