package com.apollof.protocoltracker.domain.io.labimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The forgiving text layer of import doc §3.2 and §4.1. */
class LabTextTest {
    private val text = LabText

    // §4.1 step 1: line ends and byte order marks

    @Test
    fun lineEndsBecomeLfAndByteOrderMarksGo() {
        assertEquals("a\nb\nc\nd", text.normalize("\uFEFFa\r\nb\rc\u2028d"))
    }

    // Step 2: citation debris

    @Test
    fun citationDebrisIsRemoved() {
        assertEquals("18,4 | nmol/l", text.normalize("18,4\uE200cite\uE202turn0search3\uE201 | nmol/l"))
        assertEquals("Saltro", text.normalize("Saltro【4:2†source】"))
        assertEquals("18,4 |", text.normalize("18,4citeturn0search3turn1file2 |"))
        assertEquals("12", text.normalize("12fileciteturn0file1"))
        assertEquals("ab", text.normalize("a\uE000b"))
        assertEquals("ab", text.normalize("a\uDB80\uDC00b"), "a supplementary private-use character")
    }

    @Test
    fun wordsThatOnlyLookLikeCitationsStay() {
        assertEquals("cite this", text.normalize("cite this"))
    }

    // Step 3: HTML entities

    @Test
    fun entitiesAreDecodedOnce() {
        assertEquals("<0,3 >90 ≤ 45 ≥ 1,0 A & B", text.normalize("&lt;0,3 &gt;90 &le; 45 &ge; 1,0 A &amp; B"))
        assertEquals("&lt;", text.normalize("&amp;lt;"), "&amp; is decoded last, so nothing is decoded twice")
        assertEquals("1 209", text.normalize("1&nbsp;209"))
        assertEquals("a &#124; b", text.normalize("a &#124; b"), "&#124; waits until the cells are split")
    }

    // Steps 4-6: superscripts before NFKC, dashes, spaces, invisible characters, quotes

    @Test
    fun superscriptPowersSurviveNfkc() {
        assertEquals("7,1 x10^9/l", text.normalize("7,1 x10⁹/l"))
        assertEquals("5,0 ×10^12/L", text.normalize("5,0 ×10¹²/L"))
        assertEquals("ml/min/1,73m2", text.normalize("ml/min/1,73m²"))
        assertEquals(
            "18,4 | μg/l",
            text.normalize("１８，４ | µg/l"),
            "fullwidth forms and the micro sign",
        )
    }

    @Test
    fun dashesSpacesAndInvisibleCharactersAreUnified() {
        assertEquals("2025-03-12", text.normalize("2025‑03‑12"))
        assertEquals("8,5 - 11,0", text.normalize("8,5 – 11,0"))
        assertEquals("-2,5", text.normalize("−2,5"))
        assertEquals("1 209 1 209", text.normalize("1\u202F209 1\u2009209"))
        assertEquals("Hemoglobine", text.normalize("Hemo\u00ADglo\u200Bbine"))
        assertEquals("'a' \"b\"", text.normalize("‘a’ “b”"))
        assertEquals("≤ 45\t≥ 1", text.normalize("≤ 45\t≥ 1"), "≤, ≥ and tabs stay")
    }

    // Step 7: decoration

    @Test
    fun decorationIsStrippedFromLinesTheFormatKnows() {
        assertEquals("ck | CK | 12", text.stripDecoration("- ck | CK | 12"))
        assertEquals("ck | CK | 12", text.stripDecoration("• ck | CK | 12"))
        assertEquals("ck | CK | 12", text.stripDecoration("3. ck | CK | 12"))
        assertEquals("ck | CK | 12", text.stripDecoration("3) ck | CK | 12"))
        assertEquals("date: 2025-03-12", text.stripDecoration("> date: 2025-03-12"))
        assertEquals("protocoltracker-bloodwork-1", text.stripDecoration("### protocoltracker-bloodwork-1"))
        assertEquals("protocoltracker-bloodwork-1", text.stripDecoration("**protocoltracker-bloodwork-1**"))
        assertEquals("end", text.stripDecoration("`end`"))
        assertEquals("lab: Saltro", text.stripDecoration("12 lab: Saltro"), "a code view's line number")
        assertEquals("ck | CK | 12", text.stripDecoration("**ck** | CK | 12"))
        assertEquals("ck | CK | 12", text.stripDecoration("* `ck` | CK | 12"))
        assertEquals("| ck | CK | 12 |", text.stripDecoration("| __ck__ | CK | 12 |"))
        assertEquals("date: 2025-03-12", text.stripDecoration("**date:** 2025-03-12"))
        assertEquals("lab: Saltro", text.stripDecoration("  __lab__: Saltro  "))
    }

    @Test
    fun otherLinesKeepTheirDecoration() {
        assertEquals(">90", text.stripDecoration(">90"), "a value never loses its >")
        assertEquals("> 1,0 (streefwaarde)", text.stripDecoration("> 1,0 (streefwaarde)"))
        assertEquals("- Hematologie", text.stripDecoration("- Hematologie"))
        assertEquals("**Let op**", text.stripDecoration("**Let op**"))
        assertEquals("ck | CK | 12", text.stripDecoration("  ck | CK | 12 "), "known lines are only trimmed")
    }

    @Test
    fun headersAreReadWithTheirVersion() {
        assertEquals(1, text.headerVersion("protocoltracker-bloodwork-1"))
        assertEquals(1, text.headerVersion("ProtocolTracker Bloodwork v1"))
        assertEquals(1, text.headerVersion("protocoltracker-bloodwork-1.0"))
        assertEquals(2, text.headerVersion("protocol-tracker-blood-work-2"))
        assertEquals(1, text.headerVersion("```protocoltracker-bloodwork-1"), "as a fence's info string")
        assertEquals(1, text.headerVersion("~~~ protocoltracker-bloodwork-1"))
        assertNull(text.headerVersion("protocoltracker-bloodwork-1 | ck | CK | 12 | U/l"))
        assertNull(text.headerVersion("protocoltracker-bloodwork"))
        assertNull(text.headerVersion("```"))
    }

    // §3.2: cells

    @Test
    fun cellsSplitOnPipesAndDropTheBorders() {
        assertEquals(listOf("ck", "CK", "1.209", "U/l", "< 190"), text.cells("| ck | CK | 1.209 | U/l | < 190 | |"))
        assertEquals(listOf("psa", "PSA", "", "µg/l"), text.cells("psa | PSA |  | µg/l"), "an empty middle cell stays")
        assertEquals(listOf("other", "a | b", "1"), text.cells("other | a \\| b | 1"), "\\| is a literal pipe")
        assertEquals(listOf("other", "a | b", "1"), text.cells("other | a &#124; b | 1"))
        assertEquals(listOf("hemoglobin", "Hb", "9,9", "mmol/l"), text.cells("hemoglobin\tHb\t9,9\tmmol/l\t"))
        assertEquals(listOf("a\tb\tc"), text.cells("a\tb\tc"), "two tabs are not a table")
        assertEquals(listOf("Hematologie"), text.cells("Hematologie"))
    }

    @Test
    fun aPrintedRowHasSixFields() {
        assertEquals(
            PrintedRow("ck", "CK", "12", "", "", ""),
            text.printedRow(text.cells("ck | CK | 12")),
        )
        assertEquals(
            PrintedRow("ck", "CK", "12", "U/l", "< 190", "hemolytisch | zie opm"),
            text.printedRow(text.cells("ck | CK | 12 | U/l | < 190 | hemolytisch | zie opm")),
        )
        assertEquals("", text.printedRow(text.cells("ck | CK | 12 | U/l | < 190 | -")).note)
    }

    @Test
    fun keysAreLowerCasedWithUnderscores() {
        assertEquals("free_testosterone", text.normalizeKey("Free Testosterone"))
        assertEquals("free_testosterone", text.normalizeKey(" free-testosterone "))
        assertEquals("other", text.normalizeKey("OTHER"))
        assertNull(text.normalizeKey("1ck"))
        assertNull(text.normalizeKey("Vrij T4 (berekend)"))
        assertNull(text.normalizeKey(""))
    }
}
