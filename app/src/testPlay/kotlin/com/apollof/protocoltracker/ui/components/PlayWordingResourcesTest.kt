package com.apollof.protocoltracker.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.pk.PlayWording
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Play build's section names, app name, privacy and import wording: neutral, and no claim to be offline. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlayWordingResourcesTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources
    private val labels get() = CategoryLabels(resources)

    @Test
    fun categoryStringsHaveNoBannedWord() {
        for (category in CompoundCategory.entries) {
            for (id in listOf(CategoryLabels.labelRes(category), CategoryLabels.pluralRes(category), CategoryLabels.tagRes(category))) {
                val text = resources.getString(id)
                assertNull(PlayWording.bannedTokenIn(text), "${resources.getResourceEntryName(id)} = \"$text\"")
            }
        }
    }

    @Test
    fun categoryLabelsAreThePlayWording() {
        val expected = mapOf(
            CompoundCategory.INJECTABLE_STEROID to Triple("Injectable", "Injectables", "INJ"),
            CompoundCategory.ORAL_STEROID to Triple("Oral", "Orals", "ORAL"),
            CompoundCategory.HORMONE to Triple("Hormone", "Hormones", "HORMONE"),
            CompoundCategory.RESEARCH to Triple("Other", "Other", "OTHER"),
            CompoundCategory.SUPPORT to Triple("Support", "Support", "SUPPORT"),
            CompoundCategory.PEPTIDE to Triple("Peptide", "Peptides", "PEPTIDE"),
        )
        for ((category, words) in expected) {
            assertEquals(words, Triple(labels.label(category), labels.plural(category), labels.tag(category)), category.name)
        }
        assertFalse(labels.offerEmptyResearch)
    }

    @Test
    fun noOfflineClaimOrOldAppName() {
        for (id in listOf(R.string.app_name, R.string.privacy_policy_where, R.string.privacy_line, R.string.bloodwork_help_privacy, R.string.settings_import_legacy, R.string.settings_import_legacy_title, R.string.settings_import_history_title)) {
            val text = resources.getString(id).lowercase()
            for (claim in listOf("offline", "no internet", "no network", "network access", "cycletracker")) {
                assertFalse(claim in text, "${resources.getResourceEntryName(id)} says \"$claim\": $text")
            }
            assertNull(PlayWording.bannedTokenIn(text), resources.getResourceEntryName(id))
        }
    }
}
