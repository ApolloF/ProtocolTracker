package com.apollof.protocoltracker.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.model.CompoundCategory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The foss build keeps the full section names, the same as the domain's report defaults. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FossCategoryLabelsTest {
    private val labels get() = CategoryLabels(ApplicationProvider.getApplicationContext<Application>().resources)

    @Test
    fun labelsAreUnchanged() {
        val expected = mapOf(
            CompoundCategory.INJECTABLE_STEROID to Triple("Injectable steroid", "Injectable steroids", "INJ"),
            CompoundCategory.ORAL_STEROID to Triple("Oral steroid", "Oral steroids", "ORAL"),
            CompoundCategory.HORMONE to Triple("Hormone", "Hormones", "HORMONE"),
            CompoundCategory.RESEARCH to Triple("Research compound", "SARMs and research compounds", "RESEARCH"),
            CompoundCategory.SUPPORT to Triple("Support", "Support", "SUPPORT"),
            CompoundCategory.PEPTIDE to Triple("Peptide", "Peptides", "PEPTIDE"),
        )
        for ((category, words) in expected) {
            assertEquals(words, Triple(labels.label(category), labels.plural(category), labels.tag(category)), category.name)
            assertEquals(words, Triple(category.label, category.plural, category.tag), "domain ${category.name}")
        }
        assertTrue(labels.offerEmptyResearch)
    }
}
