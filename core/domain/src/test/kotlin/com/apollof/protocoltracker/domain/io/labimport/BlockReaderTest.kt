package com.apollof.protocoltracker.domain.io.labimport

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Finding and refusing blocks (import doc §3.1-3.2, §3.7-3.8, §4.5 M1-M12 and N1-N3). */
class BlockReaderTest {
    private val today = LocalDate.of(2026, 9, 27)

    private fun found(text: String): BlockRead.Found = assertIs<BlockRead.Found>(BlockReader.read(text, today))

    private fun refused(text: String): InputProblem = assertIs<BlockRead.Refused>(BlockReader.read(text, today)).problem

    private fun BlockDraw.values() = rows.map { it.printed.value }

    // Clean, messy and broken answers

    @Test
    fun cleanDutchAnswerWithTwoDraws() {
        val read = found(Fixtures.F02)
        assertEquals(emptySet(), read.notices)
        assertEquals(emptyList(), read.unread, "chatter around the block is not a line of it")
        val (june, march) = read.draws
        assertEquals(LocalDate.of(2025, 6, 2), june.date.date)
        assertEquals(LocalTime.of(8, 40), june.date.time)
        assertEquals("Datum afname: 02-06-2025 08:40", june.date.printed)
        assertEquals(LocalDate.of(2025, 3, 12), march.date.date)
        assertNull(march.date.time)
        assertEquals(listOf("Atalmedial", "Atalmedial"), read.draws.map { it.lab }, "a lab applies until changed")
        assertEquals(listOf("24,1", "142", "0,52", "10,8"), june.values())
        assertEquals(listOf("18,4", "96", "0,49", "9,9"), march.values())
        assertEquals(
            PrintedRow("estradiol", "Oestradiol", "142", "pmol/l", "M <200", ""),
            june.rows[1].printed,
            "cells are kept as printed",
        )
        assertEquals(7, june.rows.first().line)
        assertEquals("total_testosterone | Testosteron | 24,1 | nmol/l | 6,7 - 29 |", june.rows.first().source)
    }

    @Test
    fun messyWholeMessageCopy() {
        val read = found(Fixtures.F03)
        assertEquals(emptySet(), read.notices)
        assertEquals(emptyList(), read.unread)
        val draw = read.draws.single()
        assertEquals(LocalDate.of(2025, 3, 12), draw.date.date, "non-breaking hyphens in the date")
        assertEquals(LocalTime.of(8, 15), draw.date.time)
        assertEquals("", draw.lab)
        assertEquals(listOf("18,4", "9,9", "96", "?", "> 90"), draw.values())
        assertEquals("8,6 - 29,0", draw.rows[0].printed.range, "en dash")
        assertEquals("μmol/l", draw.rows[2].printed.unit, "a Greek mu")
        assertEquals(PrintedRow("psa", "PSA totaal", "?", "µg/l".normalized(), "< 4,0", "onleesbaar (vlek op de foto)"),
            draw.rows[3].printed)
        assertEquals("eGFR (CKD-EPI)", draw.rows[4].printed.name)
    }

    @Test
    fun cutOffAnswerNeverReadsItsLastResultLine() {
        val read = found(Fixtures.F09)
        assertEquals(setOf(BlockNotice.CUT_OFF), read.notices)
        assertEquals(listOf("18,4", "9,9"), read.draws.single().values())
        val unread = read.unread.single()
        assertEquals(UnreadLine(6, "hematocrit | Hematocriet | 0,4", cutOff = true), unread)
        assertEquals("Line 6 may be cut off: \"hematocrit | Hematocriet | 0,4\"", unread.message)
        assertEquals(
            "The answer stops early, so results may be missing. Ask the chatbot to write the whole answer again, then " +
                "copy it.",
            BlockNotice.CUT_OFF.message,
        )
    }

    @Test
    fun omissionsRaiseTheLeftOutNotice() {
        val text = """
            protocoltracker-bloodwork-1
            date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
            total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
            hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
            ...
            (the remaining 18 results follow the same pattern)
            end
        """.trimIndent()
        val read = found(text)
        assertEquals(setOf(BlockNotice.LEFT_OUT), read.notices)
        assertEquals(emptyList(), read.unread)
        assertEquals(2, read.draws.single().rows.size)
        assertEquals(setOf(BlockNotice.LEFT_OUT), found(text.replace("...", "…")).notices)
        assertEquals(setOf(BlockNotice.LEFT_OUT), found(text.replace("the remaining 18", "overige 18")).notices)
        assertEquals(setOf(BlockNotice.LEFT_OUT), found(text.replace("...", "enz.")).notices)
    }

    @Test
    fun blockWithoutHeaderIsReadWithANotice() {
        val read = found(
            """
            date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
            hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
            end
            """.trimIndent(),
        )
        assertEquals(setOf(BlockNotice.NO_HEADER), read.notices)
        assertEquals(listOf("9,9"), read.draws.single().values())
        assertEquals(
            "The answer has no first line \"protocoltracker-bloodwork-1\". If you copied only part of it, results may " +
                "be missing.",
            BlockNotice.NO_HEADER.message,
        )
    }

    @Test
    fun blockWithoutHeaderNeedsDateEndAndAKnownKey() {
        val row = "hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |"
        assertEquals(InputProblem.NoBlock, refused("date: 2025-03-12\n$row"), "no end")
        assertEquals(InputProblem.NoBlock, refused("$row\nend"), "no date line")
        assertEquals(InputProblem.NoBlock, refused("date: 2025-03-12\nother | Vrij T4 | 15,2 | pmol/l |\nend"))
    }

    // Lines inside a block

    @Test
    fun headingsEntitiesAndEscapedPipesInsideABlock() {
        val read = found(
            """
            protocoltracker-bloodwork-1
            date: 2025-03-12 | Afnamedatum: 12-03-2025
            Hematologie
            hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
            KLINISCHE CHEMIE
            other | CRP | &lt;0,1 | mg/l | &lt; 10 | a &#124; b
            other | Opmerking | ? | | | zie 1 \| 2
            end
            """.trimIndent(),
        )
        assertEquals(emptyList(), read.unread, "headings are skipped silently")
        val rows = read.draws.single().rows
        assertEquals(listOf("9,9", "<0,1", "?"), rows.map { it.printed.value })
        assertEquals("< 10", rows[1].printed.range)
        assertEquals(listOf("", "a | b", "zie 1 | 2"), rows.map { it.printed.note }, "notes keep literal pipes")
    }

    @Test
    fun markdownTablesTabsAndDecorationAreRead() {
        val read = found(
            Fixtures.expand(
                """
                ```protocoltracker-bloodwork-1
                > date: 2025-03-12 | Afnamedatum 12-03-2025
                | key | name | value | unit | range | note |
                |---|---|---|---|---|---|
                | **total_testosterone** | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 | |
                - hemoglobin{TAB}Hemoglobine{TAB}9,9{TAB}mmol/l{TAB}8,5 - 11,0
                1. hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51
                **end**
                ```
                """.trimIndent(),
            ),
        )
        assertEquals(emptyList(), read.unread, "table header and delimiter rows are skipped")
        assertEquals(listOf("18,4", "9,9", "0,49"), read.draws.single().values())
        assertEquals("total_testosterone", read.draws.single().rows[0].printed.key)
        assertEquals("8,5 - 11,0", read.draws.single().rows[1].printed.range)
    }

    @Test
    fun linesThatAreNotUnderstoodAreListed() {
        val long = "Totaal 28 uitslagen, " + "x".repeat(100)
        val read = found(
            """
            protocoltracker-bloodwork-1
            date: 2025-03-12
            hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
            $long
            12,5 | mmol/l
            end
            """.trimIndent(),
        )
        assertEquals(listOf(4, 5), read.unread.map { it.line })
        assertEquals("Line 4 not understood: \"${long.take(79)}…\"", read.unread[0].message)
        assertEquals("Line 5 not understood: \"12,5 | mmol/l\"", read.unread[1].message)
    }

    @Test
    fun labLinesAreTrimmedAndCut() {
        val row = "hemoglobin | Hemoglobine | 9,9 | mmol/l |"
        val labs = found(
            "protocoltracker-bloodwork-1\nlab: ?\ndate: 2025-03-12\n$row\n" +
                "lab: ${"L".repeat(95)}\ndate: 2025-02-01\n$row\ndate: 2025-01-02\nlab: Saltro\n$row\nend",
        ).draws.map { it.lab }
        assertEquals(listOf("", "L".repeat(80), "Saltro"), labs, "a lab line before a draw's first row applies to it")
    }

    // Draws

    @Test
    fun resultsBeforeAnyDateLineFormADrawWithoutDate() {
        val draw = found("protocoltracker-bloodwork-1\nhemoglobin | Hemoglobine | 9,9 | mmol/l |\nend").draws.single()
        assertNull(draw.date.date)
        assertEquals(DateQuestion.NoDate, draw.date.question)
    }

    @Test
    fun blocksOfOneDrawMergeAndDifferentTimesStaySeparate() {
        fun block(date: String, row: String) = "```\nprotocoltracker-bloodwork-1\ndate: $date\n$row\nend\n```\n"
        val hb = "hemoglobin | Hemoglobine | 9,9 | mmol/l |"
        val ht = "hematocrit | Hematocriet | 0,49 | l/l |"
        val merged = found("Page 1:\n" + block("2025-03-12", hb) + "Page 2:\n" + block("2025-03-12 08:15", ht))
        val draw = merged.draws.single()
        assertEquals(listOf("9,9", "0,49"), draw.values())
        assertEquals(LocalTime.of(8, 15), draw.date.time, "the time of the other part fills a missing time")
        assertEquals(listOf(0, 1), draw.rows.map { it.block })

        val twoDraws = found(block("2025-03-12 08:15", hb) + block("2025-03-12 14:30", ht))
        assertEquals(2, twoDraws.draws.size)
        assertEquals(1, found(block("2025-03-12", hb) + block("?", ht)).draws.count { it.date.date != null })
    }

    @Test
    fun dateLinesWithoutResultsGiveNoDraw() {
        val read = found(
            "protocoltracker-bloodwork-1\ndate: 2025-06-02\ndate: 2025-03-12\n" +
                "hemoglobin | Hemoglobine | 9,9 | mmol/l |\nend",
        )
        assertEquals(listOf(LocalDate.of(2025, 3, 12)), read.draws.map { it.date.date })
    }

    // The prompt and whole conversations

    @Test
    fun thePromptIsRefusedAndSkippedInAConversation() {
        assertEquals(InputProblem.Prompt, refused(Fixtures.PROMPT))
        assertEquals(InputProblem.Prompt, refused(Fixtures.PROMPT.lines().first()), "the signature alone")
        val conversation = found(Fixtures.PROMPT + "\n\n[lab report attached]\n\n" + Fixtures.F02 + "\n\nThanks!")
        assertEquals(found(Fixtures.F02).draws.map { it.values() }, conversation.draws.map { it.values() })
        assertEquals(emptySet(), conversation.notices)
    }

    @Test
    fun aPromptCopiedBeforeTheRenameIsStillRecognised() {
        val old = Fixtures.PROMPT.replaceFirst("SteroidTracker prompt", "ProtocolTracker prompt")
        assertEquals(InputProblem.Prompt, refused(old))
        assertEquals(InputProblem.Prompt, refused(old.lines().first()), "the old signature alone")
    }

    // Refusals (M1-M12), in the order of import doc §3.8

    @Test
    fun emptyAndTooLongText() {
        assertEquals(InputProblem.Empty, refused(""))
        assertEquals(InputProblem.Empty, refused(" \n\t "))
        assertEquals(InputProblem.Empty, refused("​﻿"), "only invisible characters")
        assertEquals(InputProblem.TooLong, refused("a".repeat(200_001)))
        val padded = Fixtures.F02 + "\n" + " ".repeat(BloodworkImport.MAX_CHARS - Fixtures.F02.length - 1)
        assertEquals(2, found(padded).draws.size, "exactly the limit is read")
    }

    @Test
    fun rawReports() {
        assertEquals(InputProblem.RawReport, refused(Fixtures.R01))
        assertEquals(InputProblem.RawReport, refused(Fixtures.R03))
        val threeRows = Fixtures.R01.lines().take(8).joinToString("\n")
        assertEquals(InputProblem.NoBlock, refused(threeRows), "3 rows are not enough")
    }

    @Test
    fun newerVersionJoinedLinesJsonAndShareLinks() {
        val v2 = "protocoltracker-bloodwork-2\ndate: 2025-03-12 | Afnamedatum: 12-03-2025\n" +
            "hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 | | extra\nend"
        assertEquals(InputProblem.NewerVersion(2), refused(v2))
        assertEquals(InputProblem.JoinedLines, refused(
            "protocoltracker-bloodwork-1 date: 2025-03-12 | Afnamedatum 12-03-2025 hemoglobin | Hemoglobine | 9,9 | " +
                "mmol/l | 8,5 - 11,0 | hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 | end",
        ))
        val json = """
            ```json
            {
              "format": "protocoltracker-bloodwork-1",
              "date": "2025-03-12",
              "results": [
                {"key": "hemoglobin", "name": "Hemoglobine", "value": 9,9, "unit": "mmol/l"},
              ]
            }
            ```
        """.trimIndent()
        assertEquals(InputProblem.OtherFormat, refused(json))
        assertEquals(InputProblem.OtherFormat, refused(Fixtures.expand(json.replace("\"results\"", "{LDQ}results{RDQ}"))))
        val table = "| Test | Uitslag | Eenheid |\n| Hemoglobine | 9,9 | mmol/l |\n| Hematocriet | 0,49 | l/l |"
        assertEquals(InputProblem.OtherFormat, refused(table), "a table without the header")
        assertEquals(InputProblem.ShareLink, refused("https://chatgpt.com/share/6812ab34-1c2d-8000-9e0f-0123456789ab"))
        assertEquals(InputProblem.ShareLink, refused("Bekijk dit gesprek: https://g.co/gemini/share/a1b2c3d4e5f6"))
        assertEquals(InputProblem.ShareLink, refused("https://example.org/lab/report"), "any lone link")
    }

    @Test
    fun blocksWithoutResultsAndTextWithoutBlocks() {
        assertEquals(InputProblem.NoResults, refused("protocoltracker-bloodwork-1\ndate: 2025-03-12\nend"))
        // Cells split by ; or , are another layout (M5), not an answer without results; chatter with commas is not.
        val semicolons = """
            protocoltracker-bloodwork-1
            date: 2025-03-12 | Afnamedatum: 12-03-2025
            total_testosterone; Testosteron totaal; 18,4; nmol/l; 8,6 - 29,0;
            other; Vrij T4; 15,2; pmol/l; 10 - 23;
            end
        """.trimIndent()
        assertEquals(InputProblem.OtherFormat, refused(semicolons))
        assertEquals(InputProblem.OtherFormat, refused(semicolons.replace("; ", ", ").replace(";", "")), "commas")
        val chatter = "protocoltracker-bloodwork-1\ndate: 2025-03-12\nI read 12 results, 3 flagged, 2 unclear, 1 missing.\nend"
        assertEquals(InputProblem.NoResults, refused(chatter))
        assertEquals(InputProblem.NoBlock, refused("Sorry, I can't read the photo. Could you send a sharper one?"))
    }

    @Test
    fun refusalTexts() {
        assertEquals(
            "There is no text to import. In the chatbot, tap Copy on its answer first.",
            InputProblem.Empty.message,
        )
        assertEquals(
            "No results found in this text. In the chatbot, tap Copy on its answer, then try again.",
            InputProblem.NoBlock.message,
        )
        assertEquals(
            "This is a link to a chat. The app can't open it. Copy the answer itself instead; a " +
                "shared chat link is public.",
            InputProblem.ShareLink.message,
        )
        assertEquals(
            "This answer uses a newer layout (version 2). Update SteroidTracker, or copy the prompt again.",
            InputProblem.NewerVersion(2).message,
        )
        assertEquals(
            "The chatbot used another layout. Ask it: \"Use the protocoltracker-bloodwork-1 layout from my first " +
                "message.\"",
            InputProblem.OtherFormat.message,
        )
        assertTrue(listOf(
            InputProblem.TooLong, InputProblem.JoinedLines, InputProblem.Prompt,
            InputProblem.RawReport, InputProblem.NoResults,
        ).all { it.message.endsWith(".") })
    }

    private fun String.normalized() = LabText.normalize(this)
}
