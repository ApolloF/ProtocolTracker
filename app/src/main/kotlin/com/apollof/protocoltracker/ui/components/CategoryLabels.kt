package com.apollof.protocoltracker.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.domain.model.CompoundCategory

/**
 * Section names of [CompoundCategory] as the app shows them. They come from string resources because each flavour
 * words them its own way (`app/src/play/res` overrides the foss text); the domain's `label` fields are only the
 * report default.
 */
class CategoryLabels(private val resources: Resources) {
    fun label(category: CompoundCategory): String = resources.getString(labelRes(category))
    fun plural(category: CompoundCategory): String = resources.getString(pluralRes(category))
    fun tag(category: CompoundCategory): String = resources.getString(tagRes(category))

    /** Whether the custom-compound editor offers the research section when no compound uses it. */
    val offerEmptyResearch: Boolean get() = resources.getBoolean(R.bool.offer_empty_research_category)

    companion object {
        fun labelRes(category: CompoundCategory): Int = when (category) {
            CompoundCategory.INJECTABLE_STEROID -> R.string.category_injectable_steroid_label
            CompoundCategory.ORAL_STEROID -> R.string.category_oral_steroid_label
            CompoundCategory.HORMONE -> R.string.category_hormone_label
            CompoundCategory.RESEARCH -> R.string.category_research_label
            CompoundCategory.SUPPORT -> R.string.category_support_label
            CompoundCategory.PEPTIDE -> R.string.category_peptide_label
        }

        fun pluralRes(category: CompoundCategory): Int = when (category) {
            CompoundCategory.INJECTABLE_STEROID -> R.string.category_injectable_steroid_plural
            CompoundCategory.ORAL_STEROID -> R.string.category_oral_steroid_plural
            CompoundCategory.HORMONE -> R.string.category_hormone_plural
            CompoundCategory.RESEARCH -> R.string.category_research_plural
            CompoundCategory.SUPPORT -> R.string.category_support_plural
            CompoundCategory.PEPTIDE -> R.string.category_peptide_plural
        }

        fun tagRes(category: CompoundCategory): Int = when (category) {
            CompoundCategory.INJECTABLE_STEROID -> R.string.category_injectable_steroid_tag
            CompoundCategory.ORAL_STEROID -> R.string.category_oral_steroid_tag
            CompoundCategory.HORMONE -> R.string.category_hormone_tag
            CompoundCategory.RESEARCH -> R.string.category_research_tag
            CompoundCategory.SUPPORT -> R.string.category_support_tag
            CompoundCategory.PEPTIDE -> R.string.category_peptide_tag
        }
    }
}

/** The [CategoryLabels] of the current configuration. */
@Composable
fun categoryLabels(): CategoryLabels {
    val resources = LocalResources.current
    return remember(resources) { CategoryLabels(resources) }
}
