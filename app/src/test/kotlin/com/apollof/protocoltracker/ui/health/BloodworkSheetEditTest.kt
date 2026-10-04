package com.apollof.protocoltracker.ui.health

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Editing a draw in the Bloodwork sheet keeps every result the user did not type in, exactly (import doc §10.7).
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

    /** Review 2026-10, L2: a hematocrit of 45 typed in SI (L/L) would be stored as 4500 %. */
    @Test
    fun anImpossibleTypedValueIsMarkedAndBlocksSaving() {
        show(null)
        click(LabUnits.SI.label)
        field("Hematocrit").performTextReplacement("45")
        compose.onNodeWithText("Not a possible value in L/L. Check the units on the report.").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        field("Hematocrit").performTextReplacement("0.45")
        compose.onNodeWithText("Not a possible value in L/L. Check the units on the report.").assertDoesNotExist()
        assertEquals(45.0, save().results.single { it.marker == "hematocrit" }.value, 1e-9)
    }

    @Test
    fun aNewDrawSavesWhatIsTyped() {
        show(null)
        field("Total testosterone").performTextReplacement("650")
        field("Hemoglobin").performTextReplacement("15,2")
        assertEquals(listOf(MarkerResult("total_testosterone", 650.0), MarkerResult("hemoglobin", 15.2)), save().results)
    }
}

/**
 * The sheet shows what an edit keeps: lab ranges, censored values as reported, and unlisted results under
 * "Other tests", edited as printed (import doc §7, §10.7).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class BloodworkSheetLabTest {
    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-09-20T07:30:00Z")
    private val e2 = MarkerResult("estradiol", 40 * 0.2724, qualifier = "<") // <40 pmol/L, no lab range
    private val lh = MarkerResult("lh", 0.3, qualifier = "<", refLow = 1.7, refHigh = 8.6)
    private val hb = MarkerResult("hemoglobin", 15.9489, refLow = 13.6935, refHigh = 17.721) // 9.9 mmol/L, lab range 8.5–11
    private val creatinine = MarkerResult("creatinine", 1.085952)
    private val ft4 = MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l")
    private val crp = MarkerResult("other:crp", 5.0, qualifier = "<", name = "CRP", unit = "mg/l")
    private val draw = JournalEntry.Bloodwork("b", at, listOf(e2, lh, hb, creatinine, ft4, crp), "Lab A", "Fasted", at)
    private val reportedLh = "Reported as <0.3 IU/L. A typed number replaces it."

    private var saved: BloodworkInput? = null

    private fun show() = compose.setContent {
        ProtocolTrackerTheme {
            BloodworkSheet(
                now = Instant.parse("2026-09-26T10:00:00Z"), zone = ZoneOffset.UTC, defaultUnits = LabUnits.CONVENTIONAL,
                onDismiss = {}, onSave = { saved = it }, existing = draw,
            )
        }
    }

    private fun field(name: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText(name))
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    private fun shows(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    private fun reference(key: String, units: LabUnits) = "Reference " + BloodMarkers.find(key)!!.referenceText(units)
    private fun save(): List<MarkerResult> {
        click("Save")
        return assertNotNull(saved).results
    }

    @Test
    fun labRangesReplaceTheTypicalReferenceInEitherUnits() {
        show()
        assertTrue(shows("Lab range 1.7–8.6 IU/L\n$reportedLh"))
        assertTrue(shows("Lab range 13.7–17.7 g/dL"))
        assertTrue(shows(reference("creatinine", LabUnits.CONVENTIONAL)))
        // Without a lab range the typical reference stays; the reported number is the field's own text.
        assertTrue(shows(reference("estradiol", LabUnits.CONVENTIONAL) + "\nReported as <10.896 pg/mL. A typed number replaces it."))
        assertFalse(shows(reference("hemoglobin", LabUnits.CONVENTIONAL)))
        click(LabUnits.SI.label)
        assertTrue(shows("Lab range 8.5–11 mmol/L"))
        assertTrue(shows(reference("creatinine", LabUnits.SI)))
        assertTrue(shows(reference("estradiol", LabUnits.SI) + "\nReported as <40 pmol/L. A typed number replaces it."))
    }

    @Test
    fun captionsFollowWhatWillBeSaved() {
        show()
        field("LH").performTextReplacement("0.5")
        assertTrue(shows("Lab range 1.7–8.6 IU/L"))
        assertFalse(shows(reportedLh, substring = true))
        field("Hemoglobin").performTextReplacement("")
        assertTrue(shows(reference("hemoglobin", LabUnits.CONVENTIONAL)))
        assertFalse(shows("Lab range 13.7–17.7 g/dL"))
        field("LH").performTextReplacement("0.3")
        assertTrue(shows("Lab range 1.7–8.6 IU/L\n$reportedLh"))
    }

    @Test
    fun otherTestsAreShownAndEditedAsPrinted() {
        show()
        field("Vrij T4").assert(hasText("15.2")).assert(hasText("pmol/l"))
        assertTrue(shows("Lab range 10–23 pmol/l"))
        assertTrue(shows("Reported as <5 mg/l. A typed number replaces it."))
        click(LabUnits.SI.label)
        field("Vrij T4").assert(hasText("15.2"))
        field("Vrij T4").performTextReplacement("16,1")
        field("CRP").performTextReplacement("")
        assertEquals(listOf(e2, lh, hb, creatinine, ft4.copy(value = 16.1)), save())
        assertTrue(shows("Lab range 10–23 pmol/l"))
        field("Vrij T4").performTextReplacement("15.2")
        field("CRP").performTextReplacement("5")
        assertEquals(draw.results, save())
    }

    @Test
    fun aTypedOtherTestDropsItsSign() {
        show()
        field("CRP").performTextReplacement("3")
        assertFalse(shows("Reported as <5 mg/l. A typed number replaces it."))
        assertEquals(listOf(e2, lh, hb, creatinine, ft4, crp.copy(value = 3.0, qualifier = null)), save())
    }
}

/** The sheet lists measured markers up front and folds the rest under "More markers" (SIM-12). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class BloodworkSheetFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-09-26T10:00:00Z")
    private var saved: BloodworkInput? = null

    private fun show(measured: Set<String>, existing: JournalEntry.Bloodwork? = null) = compose.setContent {
        ProtocolTrackerTheme {
            BloodworkSheet(at, ZoneOffset.UTC, LabUnits.CONVENTIONAL, onDismiss = {}, onSave = { saved = it }, existing = existing, measured = measured)
        }
    }

    private fun hasField(name: String) = compose.onAllNodes(hasSetTextAction() and hasText(name)).fetchSemanticsNodes().isNotEmpty()
    private fun click(text: String) = compose.onNodeWithText(text, substring = true).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun measuredMarkersComeFirstAndTheRestFold() {
        show(setOf("hematocrit", "psa", "other:ferritine"))
        assertTrue(hasField("Hematocrit"))
        assertTrue(hasField("PSA"))
        assertFalse(hasField("Total testosterone"))
        click("More markers (${BloodMarkers.all.size - 2})")
        assertTrue(hasField("Total testosterone"))
        compose.onNode(hasSetTextAction() and hasText("Total testosterone")).performTextReplacement("650")
        click("Save")
        assertEquals(listOf(MarkerResult("total_testosterone", 650.0)), assertNotNull(saved).results)
    }

    @Test
    fun withoutHistoryHormonesAndBloodCountAreUpFront() {
        show(emptySet())
        assertTrue(hasField("Total testosterone"))
        assertTrue(hasField("Hemoglobin"))
        assertFalse(hasField("Creatinine"))
    }

    @Test
    fun theEditedDrawsMarkersAreUpFront() {
        show(emptySet(), JournalEntry.Bloodwork("b", at, listOf(MarkerResult("creatinine", 1.0)), createdAt = at))
        assertTrue(hasField("Creatinine"))
        assertFalse(hasField("Total testosterone"))
    }
}
