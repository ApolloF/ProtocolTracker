package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The generated AI prompt (import doc §12). */
class LabPromptTest {
    private val text = LabPrompt.text
    private val lines = text.lines()
    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun everyMarkerHasOneKeyLineWithNameUnitsAndAliasesInTableOrder() {
        val keyLines = lines.filter { line -> BloodMarkers.all.any { line.startsWith("${it.key}: ") } }
        assertEquals(BloodMarkers.all.map { it.key }, keyLines.map { it.substringBefore(": ") }, "one line each, in order")
        for (marker in BloodMarkers.all) {
            val line = keyLines.single { it.startsWith("${marker.key}: ") }
            assertTrue(line.startsWith("${marker.key}: ${marker.name}; "), line)
            val fields = line.substringAfter(": ").split("; ", limit = 4)
            val units = fields[1].split(", ")
            val aliases = fields[2].split(", ")
            for (unit in MarkerVocabulary.units.getValue(marker.key)) {
                assertTrue(unit.display in units, "${marker.key} lists ${unit.display}")
            }
            assertEquals(MarkerVocabulary.names(marker.key)!!.aliases, aliases, "${marker.key} aliases")
            MarkerVocabulary.names(marker.key)!!.promptNote?.let { assertEquals(it, fields[3], "${marker.key} note") }
        }
        assertTrue(lines.last().startsWith("other: "), "the other line comes last")
        assertEquals(lines.indexOf(keyLines.last()) + 1, lines.lastIndex, "other follows the last key line")
    }

    @Test
    fun signatureFirstAndTheHeaderOnceAsALine() {
        assertTrue(lines.first().startsWith(BloodworkImport.PROMPT_SIGNATURE))
        assertEquals(1, lines.count { it.trim() == BloodworkImport.HEADER }, "the layout block holds the header once")
    }

    @Test
    fun pastedBackItIsRefusedAndBeforeAnAnswerItIsSkipped() {
        assertEquals(InputProblem.Prompt, assertIs<BlockRead.Refused>(BlockReader.read(text, today)).problem)
        val answer = assertIs<BlockRead.Found>(BlockReader.read(Fixtures.F02, today))
        val conversation = assertIs<BlockRead.Found>(BlockReader.read(text + "\n\n" + Fixtures.F02, today))
        fun BlockRead.Found.content() = draws.map { Triple(it.date, it.lab, it.rows.map { row -> row.printed }) }
        assertEquals(answer.content(), conversation.content())
        assertEquals(answer.notices, conversation.notices)
        assertEquals(emptyList(), conversation.unread, "no line of the prompt is read as a result")
    }

    @Test
    fun shortEnoughAndNoExampleNumbers() {
        assertTrue(text.length <= 6_500, "${text.length} characters")
        assertTrue(lines.all { it.length <= 400 }, "no line over 400 characters")
        val withoutUnits = MarkerVocabulary.units.values.flatten().map { it.display }.distinct()
            .fold(text) { s, unit -> s.replace(unit, "") }
        assertEquals(null, Regex("\\d+[.,]\\d+").find(withoutUnits)?.value, "no example number")
    }

    @Test
    fun theImportDocHoldsTheGeneratedText() {
        val doc = File("../../docs/BLOODWORK_IMPORT.md").readText().replace("\r\n", "\n")
        assertTrue("\n## 12. The AI prompt\n" in doc, "the doc has §12")
        val section = doc.substringAfter("\n## 12. The AI prompt\n")
        val fenced = section.substringAfter("````text\n").substringBefore("\n````")
        assertEquals(text, fenced, "docs/BLOODWORK_IMPORT.md §12 differs from LabPrompt.text")
    }
}
