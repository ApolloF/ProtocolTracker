package com.apollof.protocoltracker.ui.health

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Editing a draw in the Bloodwork sheet keeps every result the user did not type in, exactly (import doc §10.7).
 * Runs in both flavors: a dev backup restored in stable must survive an edit there too.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class BloodworkSheetEditTest {
    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-09-20T07:30:00Z")
    private val lh = MarkerResult("lh", 0.3, qualifier = "<", refLow = 1.7, refHigh = 8.6)
    private val hb = MarkerResult("hemoglobin", 15.9489, refLow = 13.6935, refHigh = 17.721) // 9.9 mmol/L
    private val creatinine = MarkerResult("creatinine", 1.085952) // shown as 1.086 mg/dL or 96 µmol/L
    private val ft4 = MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l")
    private val crp = MarkerResult("other:crp", 5.0, qualifier = "<", name = "CRP", unit = "mg/l")
    private val draw = JournalEntry.Bloodwork("b", at, listOf(lh, hb, creatinine, ft4, crp), "Lab A", "Fasted", at)

    private var saved: BloodworkInput? = null

    private fun show(existing: JournalEntry.Bloodwork?) = compose.setContent {
        ProtocolTrackerTheme {
            BloodworkSheet(
                now = Instant.parse("2026-09-26T10:00:00Z"), zone = ZoneOffset.UTC, defaultUnits = LabUnits.CONVENTIONAL,
                onDismiss = {}, onSave = { saved = it }, existing = existing,
            )
        }
    }

    private fun field(name: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText(name))
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    private fun save(): BloodworkInput {
        click("Save")
        return assertNotNull(saved)
    }

    @Test
    fun anUntouchedSaveEqualsTheOriginal() {
        show(draw)
        val out = save()
        assertEquals(draw.results, out.results)
        assertEquals(draw, out.toEntry(draw.id, draw.createdAt))
    }

    @Test
    fun togglingUnitsTwiceChangesNothing() {
        show(draw)
        click(LabUnits.SI.label)
        field("Creatinine").assert(hasText("96"))
        field("Hemoglobin").assert(hasText("9.9"))
        click(LabUnits.CONVENTIONAL.label)
        field("Creatinine").assert(hasText("1.086"))
        assertEquals(draw.results, save().results)
    }

    @Test
    fun anEditedHemoglobinKeepsItsRange() {
        show(draw)
        click(LabUnits.SI.label)
        field("Hemoglobin").performTextReplacement("10.1")
        val stored = BloodMarkers.find("hemoglobin")!!.toStored(10.1, LabUnits.SI)
        assertEquals(listOf(lh, hb.copy(value = stored), creatinine, ft4, crp), save().results)
    }

    @Test
    fun aTypedNumberDropsTheQualifierUnlessTheSavedValueIsTypedBack() {
        show(draw)
        field("LH").performTextReplacement("0.5")
        field("Creatinine").performTextReplacement("")
        assertEquals(listOf(lh.copy(value = 0.5, qualifier = null), hb, ft4, crp), save().results)
        field("LH").performTextReplacement("0.3")
        field("Creatinine").performTextReplacement("1.086")
        assertEquals(draw.results, save().results)
    }

    @Test
    fun aTypedValueIsConvertedWhenUnitsChange() {
        show(draw)
        field("Creatinine").performTextReplacement("1.2")
        click(LabUnits.SI.label)
        field("Creatinine").assert(hasText("106.082"))
        val out = save().results.first { it.marker == "creatinine" }
        assertEquals(1.2, out.value, 0.0005)
    }

    @Test
    fun aNewDrawSavesWhatIsTyped() {
        show(null)
        field("Total testosterone").performTextReplacement("650")
        field("Hemoglobin").performTextReplacement("15,2")
        assertEquals(listOf(MarkerResult("total_testosterone", 650.0), MarkerResult("hemoglobin", 15.2)), save().results)
    }
}
