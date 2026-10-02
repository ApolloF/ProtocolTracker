package com.apollof.protocoltracker.ui.plan

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * POL-19: the compound editor's peak field. Dev shows and takes it in the curve's level unit (stable in ng/dL); the
 * stored value stays ng/dL, and an untouched field saves it exactly, in both flavors.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class CompoundPeakFieldTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private val dev = BuildConfig.DEV_FEATURES

    // 5.123456 ng/dL per mg is 0.05123456 ng/mL: more decimals than the field shows.
    private val compound = Compound(
        "custom:x", "examplide", group = "Examplide", category = CompoundCategory.PEPTIDE, route = Route.INJECTION, baseUnit = BaseUnit.MG,
        colorArgb = 0xFF336699, pk = PkParams(48.0, 6.0, 5.123456, 1.0, LevelUnit.NG_ML),
    )

    @Before
    fun seed(): Unit = runBlocking { container.repository.saveCompound(compound) }

    private fun stored() = runBlocking { container.repository.compounds.first().single { it.id == compound.id } }.pk!!.peakPerUnit
    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun open() {
        compose.setContent { ProtocolTrackerTheme { CompoundEditorScreen(compound.id, onDone = {}) } }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasSetTextAction() and hasText("Peak per", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun save(expected: Double) {
        compose.onNodeWithText("Save").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MS) { stored() == expected }
    }

    @Test
    fun anUntouchedPeakSavesExactly() {
        open()
        assertEquals(true, shown(if (dev) "0.0512346" else "5.1235"), "the field shows the peak in its unit")
        assertEquals(true, shown(if (dev) "ng/mL" else "ng/dL"))
        // Any save: the name changes, the peak not.
        compose.onNode(hasSetTextAction() and hasText("examplide")).performTextReplacement("examplide b")
        save(5.123456)
    }

    @Test
    fun aTypedPeakIsInTheShownUnit() {
        open()
        compose.onNode(hasSetTextAction() and hasText("Peak per", substring = true)).performTextReplacement("0.1")
        save(if (dev) 10.0 else 0.1)
    }

    @Test
    fun switchingUnitsKeepsTheExactPeak() {
        open()
        if (dev) assertEquals(true, shown("0.0512346"), "6 significant digits")
        compose.onNode(hasText("ng/dL") and hasClickAction() and !hasSetTextAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("ng/mL") and hasClickAction() and !hasSetTextAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasSetTextAction() and hasText("examplide")).performTextReplacement("examplide b")
        save(5.123456)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
