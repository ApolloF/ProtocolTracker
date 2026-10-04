package com.apollof.protocoltracker.ui.health

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.SymptomGroup
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Review 2026-10, F2: the Symptoms sheet shows hedged estrogen headings and a caption, counts nothing, and keeps old keys. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class SymptomSheetGroupsTest {
    @get:Rule
    val compose = createComposeRule()

    private val at = Instant.parse("2026-09-20T07:30:00Z")
    private var saved: SymptomInput? = null

    private fun show(existing: JournalEntry.Symptoms?) = compose.setContent {
        ProtocolTrackerTheme {
            SymptomSheet(now = Instant.parse("2026-09-26T10:00:00Z"), zone = ZoneOffset.UTC, onDismiss = {}, onSave = { saved = it }, existing = existing)
        }
    }

    private fun count(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().size
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun hedgedHeadingsAndACaptionWithoutCounts() {
        show(JournalEntry.Symptoms("s", at, listOf("acne", "insomnia", "moon_face"), createdAt = at))
        SymptomGroup.entries.forEach { assertEquals(1, count(it.label), it.label) }
        assertEquals(1, count("Often listed as low-estrogen signs"))
        assertEquals(1, count("Often listed as high-estrogen signs"))
        assertEquals(1, count("Common community lists, not a diagnosis. The two lists overlap; only bloodwork tells them apart."))
        assertEquals(0, count("signs ·", substring = true), "no count in a heading")
        assertEquals(0, count("Gynecomastia"))
        assertEquals(1, count("Breast tenderness / lump"))
        assertEquals(1, count("Puffy face"))
    }

    @Test
    fun aSymptomInBothListsIsTickedPerKey() {
        show(JournalEntry.Symptoms("s", at, listOf("loss_of_libido_high"), mood = 6, createdAt = at))
        assertEquals(2, count("Loss of libido"), "one chip in each list")
        click("Save")
        assertEquals(listOf("loss_of_libido_high"), assertNotNull(saved).symptoms)

        saved = null
        compose.onAllNodesWithText("Loss of libido")[0].performSemanticsAction(SemanticsActions.OnClick)
        click("Save")
        assertEquals(listOf("loss_of_libido_low", "loss_of_libido_high"), assertNotNull(saved).symptoms)
    }
}
