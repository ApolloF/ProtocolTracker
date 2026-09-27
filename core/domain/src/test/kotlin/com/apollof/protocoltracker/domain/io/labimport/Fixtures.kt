package com.apollof.protocoltracker.domain.io.labimport

/**
 * Chatbot answers and report texts for the bloodwork import tests (import doc §13.1). All data is synthetic. Raw strings
 * do not process `\u` escapes, so invisible characters are written as tokens and expanded by [expand].
 */
object Fixtures {
    private val TOKENS = mapOf(
        "{NNBSP}" to " ", "{NBSP}" to " ", "{NBHY}" to "‑", "{EN}" to "–", "{MINUS}" to "−",
        "{ZWSP}" to "​", "{BOM}" to "﻿", "{SHY}" to "­", "{GMU}" to "μ", "{TAB}" to "\t",
        "{LDQ}" to "“", "{RDQ}" to "”", "{CRLF}" to "\r\n", "{CR}" to "\r",
    )

    fun expand(text: String): String = TOKENS.entries.fold(text) { s, (token, char) -> s.replace(token, char) }

    /** F02: clean, Dutch, two draws (the second without a time), chatter around a bare fence. */
    val F02 = """
        Here is the block for ProtocolTracker:

        ```
        protocoltracker-bloodwork-1
        lab: Atalmedial
        date: 2025-06-02 08:40 | Datum afname: 02-06-2025 08:40
        total_testosterone | Testosteron | 24,1 | nmol/l | 6,7 - 29 |
        estradiol | Oestradiol | 142 | pmol/l | M <200 |
        hematocrit | Hematocriet | 0,52 | l/l | 0,41 - 0,51 |
        hemoglobin | Hemoglobine | 10,8 | mmol/l | 8,5 - 11,0 |
        date: 2025-03-12 | Datum afname: 12-03-2025
        total_testosterone | Testosteron | 18,4 | nmol/l | 6,7 - 29 |
        estradiol | Oestradiol | 96 | pmol/l | M <200 |
        hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
        hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
        end
        ```

        Let me know if you want me to explain any of these values.
    """.trimIndent()

    /**
     * F03: messy, a whole-message plain copy: chatter, a lost fence label, a bold header, non-breaking hyphens and
     * spaces, en dashes, a Greek mu, no trailing pipes, an unreadable value with a note.
     */
    val F03 = expand(
        """
        I've read both pages of your report. One value was hard to read, see the note.

        text
        **protocoltracker-bloodwork-1**
        date: 2025{NBHY}03{NBHY}12 08:15 | Afnamedatum: 12{NBHY}03{NBHY}2025 08:15
        total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 {EN} 29,0
        hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 {EN} 11,0
        creatinine | Kreatinine | 96 | {GMU}mol/l | 64 {EN} 104
        psa | PSA totaal | ? | µg/l | < 4,0 | onleesbaar (vlek op de foto)
        egfr | eGFR (CKD{NBHY}EPI) | >{NNBSP}90 | ml/min/1,73m² | >{NNBSP}60
        end

        Note: I'm not a doctor. Please discuss these results with your GP.
        """.trimIndent(),
    )

    /** F09: broken, cut off inside the last line; `0,4` must never be read (it was `0,49`). */
    val F09 = """
        ```text
        protocoltracker-bloodwork-1
        date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
        total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
        hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
        hematocrit | Hematocriet | 0,4
    """.trimIndent()

    /** The prompt's opening and layout block (`LabPrompt.text` has the same shape). */
    val PROMPT = """
        ProtocolTracker prompt (format protocoltracker-bloodwork-1). Give the app the chatbot's answer, not this prompt.

        Reply with one code block and nothing before or after it, in exactly this layout.

        ```text
        protocoltracker-bloodwork-1
        lab: <lab name; leave this line out if none is printed>
        date: <draw date YYYY-MM-DD> <draw time HH:MM, only if printed> | <date label and date exactly as printed>
        <key> | <test name as printed> | <result as printed> | <unit as printed> | <reference range as printed> | <note>
        end
        ```

        Rules
        1. One line per result, for every result on the report. Never write "..." or "etc.".
        6. If no draw date is printed, write date: ? and put the dates you see after the |.

        Keys (key: name; units; also printed as; notes)
        total_testosterone: Total testosterone; nmol/L, ng/dL; Testosteron, Testosteron totaal
        hemoglobin: Hemoglobin; mmol/L, g/dL; Hemoglobine, Hb
    """.trimIndent()

    /** R01: a Dutch report's text layer, rows in order (what a patient portal copy gives). */
    val R01 = expand(
        """
        Labrapport
        Datum van ontvangst: 13.03.25 10:02
        Datum van afname: 12.03.25 08:15
        Waarde Uitslag Referentiewaarde
        HEMATOLOGIE
        Hemoglobine 9.9 mmol/l 8.5 {EN} 11.0
        Hematocriet 0.49 l/l 0.41 - 0.51
        Leukocyten 6.1 /nl 4.0 {EN} 10.0
        KLINISCHE CHEMIE
        Cholesterol 4.60 mmol/l < 6.50
        ALAT (GPT) 38 U/l < 50
        CK (creatine-kinase) ↑↑↑ 1209 U/l < 190
        ENDOCRINOLOGIE
        Testosteron 18.4 nmol/l 8.64 {EN} 29.00
        """.trimIndent(),
    )

    /** R03: a US report's text layer with aligned columns. */
    val R03 = """
        Date Collected: 03/12/2025 07:50 AM    Date Received: 03/12/2025    Date Reported: 03/14/2025
        TESTS                          RESULT   FLAG   UNITS        REFERENCE INTERVAL
        Testosterone, Serum            1050     High   ng/dL        264-916
        Free Testosterone(Direct)      31.2     High   pg/mL        8.7-25.1
        Estradiol                      41.8            pg/mL        7.6-42.6
        Hematocrit                     51.2     High   %            37.5-51.0
        eGFR                           83              mL/min/1.73  >59
    """.trimIndent()
}
