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

    /** F01: clean, Dutch, one draw with a time: nothing left out, no caption (20 results, 2 unlisted). */
    val F01 = """
        ```text
        protocoltracker-bloodwork-1
        lab: Saltro
        date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15
        total_testosterone | Testosteron totaal | 18,4 | nmol/l | 8,6 - 29,0 |
        free_testosterone | Vrij testosteron (berekend) | 412 | pmol/l | 225 - 725 |
        estradiol | Oestradiol | 96 | pmol/l | < 150 |
        shbg | SHBG | 31 | nmol/l | 18 - 54 |
        lh | LH | 0,4 | E/l | 1,7 - 8,6 |
        fsh | FSH | <0,3 | E/l | 1,5 - 12,4 |
        hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
        hematocrit | Hematocriet | 0,49 | l/l | 0,41 - 0,51 |
        cholesterol | Cholesterol totaal | 4,6 | mmol/l | < 6,5 |
        hdl | HDL-cholesterol | 1,02 | mmol/l | > 1,0 |
        creatinine | Kreatinine | 96 | µmol/l | 64 - 104 |
        egfr | eGFR (CKD-EPI) | >90 | ml/min/1,73m2 | > 60 |
        alt | ALAT | 38 | U/l | < 45 |
        ast | ASAT | 29 | U/l | < 35 |
        ggt | Gamma-GT | 24 | U/l | < 55 |
        ck | CK | 1209 | U/l | < 190 |
        psa | PSA totaal | 0,8 | µg/l | < 4,0 |
        tsh | TSH | 1,9 | mE/l | 0,5 - 4,0 |
        other | Vrij T4 | 15,2 | pmol/l | 10 - 23 |
        other | Ferritine | 145 | µg/l | 30 - 400 |
        end
        ```
    """.trimIndent()

    /** F08: clean, English, LabCorp: grouping and decimal dots settled by the block, a direct free T range, AM date. */
    val F08 = """
        protocoltracker-bloodwork-1
        lab: LabCorp
        date: 2025-03-12 07:50 | Date Collected: 03/12/2025 07:50 AM
        total_testosterone | Testosterone, Serum | 1,050 | ng/dL | 264-916 |
        free_testosterone | Free Testosterone(Direct) | 31.2 | pg/mL | 8.7-25.1 |
        estradiol | Estradiol | 41.8 | pg/mL | 7.6-42.6 |
        hematocrit | Hematocrit | 51.2 | % | 37.5-51.0 |
        tsh | TSH | 2.110 | uIU/mL | 0.450-4.500 |
        egfr | eGFR | 83 | mL/min/1.73 | >59 |
        end
    """.trimIndent()

    /** F20: ranges by sex: one with a prefix (no caption), men's and women's (C5), a censored SHBG. */
    val F20 = """
        protocoltracker-bloodwork-1
        date: 2025-05-20 | Datum afname: 20-05-2025
        estradiol | Oestradiol | 142 | pmol/l | M <200 |
        hemoglobin | Hemoglobine | 9,1 | mmol/l | M: 8,5-11,0 V: 7,5-10,0 |
        shbg | SHBG | >200 | nmol/l | 18 - 54 |
        end
    """.trimIndent()

    /** N02: units missing: one unit fits (C1), or several (left out). */
    val N02 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        shbg | SHBG | 31 | | 18 - 54 |
        hematocrit | Hematocriet | 0,49 | | 0,41 - 0,51 |
        hemoglobin | Hemoglobine | 9,9 | | 8,5 - 11,0 |
        alt | ALAT | 38 | | < 45 |
        ast | ASAT | 29 | | |
        end
    """.trimIndent()

    /** N16, N17: unit slips: a range that fits another unit, impossible values, a unit that is not one. */
    val N16 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        hemoglobin | Hemoglobine | 15,9 | mmol/l | 13,5 - 17,5 |
        estradiol | Oestradiol | 96 | pg/ml | < 150 |
        free_testosterone | Vrij testosteron | 412 | pg/ml | 225 - 725 |
        hematocrit | Hematocriet | 0,47 | % | 0,41 - 0,51 |
        prolactin | Prolactine | 210 | U/l | < 500 |
        creatinine | Kreatinine | 96 | pmol/l | 64 - 104 |
        ck | CK | 600000 | U/l | |
        total_testosterone | Testosteron | 18,4 | nmol/l | 1,5 - 12,4 |
        end
    """.trimIndent()

    /** N15: lost decimals are asked; a training CK and a suppressed LH against one-sided or low ranges are not. */
    val N15 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        glucose | Glucose nuchter | 52 | mmol/l | 4,0 - 5,6 |
        total_testosterone | Testosteron | 184 | nmol/l | 8,6 - 29,0 |
        ck | CK | 1209 | U/l | < 190 |
        lh | LH | 0,4 | E/l | 1,7 - 8,6 |
        end
    """.trimIndent()

    /** N18: numbers with one mark and three digits in a block without a decimal style. */
    val N18 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        total_testosterone | Testosteron | 1,050 | ng/dl | 264 - 916 |
        shbg | SHBG | 31 | nmol/l | 18 - 54 |
        hemoglobin | Hemoglobine | 9,900 | mmol/l | |
        ck | CK | 1.200 | U/l | < 190 |
        end
    """.trimIndent()

    /** F18, N25: two blocks of one answer (a corrected Hb, a repeated Hct) and two LDL values in one block. */
    val F18 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        hemoglobin | Hemoglobine | 9,1 | mmol/l | 8,5 - 11,0 |
        hematocrit | Hematocriet | 0,45 | l/l | 0,41 - 0,51 |
        ldl | LDL-cholesterol | 2,1 | mmol/l | < 3,0 |
        ldl | LDL-cholesterol | 2,4 | mmol/l | < 3,0 |
        end

        Correction: I misread the hemoglobin value.

        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        hemoglobin | Hemoglobine | 9,7 | mmol/l | 8,5 - 11,0 |
        hematocrit | Hematocriet | 0,45 | l/l | 0,41 - 0,51 |
        end
    """.trimIndent()

    /** N08: date traps: a birth date, no date, dates that disagree, the future; a received date saves with a caption. */
    val N08 = """
        protocoltracker-bloodwork-1
        date: 1985-04-12 | Geboortedatum: 12-04-1985
        total_testosterone | Testosteron | 18,4 | nmol/l | 8,6 - 29,0 |
        date: ? | Afnamedatum onleesbaar
        hemoglobin | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
        date: 2025-04-03 | Afnamedatum: 04-03-2025
        shbg | SHBG | 31 | nmol/l | 18 - 54 |
        date: 2027-01-05 | Afnamedatum: 05-01-2027
        lh | LH | 4,1 | E/l | 1,7 - 8,6 |
        date: 2025-03-14 | Ontvangen: 14-03-2025
        fsh | FSH | 3,2 | E/l | 1,5 - 12,4 |
        end
    """.trimIndent()

    /** N10, N11, N22-N24: names and keys that disagree, unlisted results with one name, plain Glucose. */
    val N22 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        hdl | Cholesterol/HDL | 3,8 | ratio | |
        ast | ASAT/GOT | 29 | U/l | < 35 |
        free_testosterone | Vrije testosteronfractie | 0,45 | nmol/l | |
        hemoglobin | Hb totaal | 9,9 | | |
        hematocrit | Hemoglobine | 9,9 | mmol/l | 8,5 - 11,0 |
        other | Kreatinine | 12 | mmol/l | 3 - 20 |
        other | CRP | 4 | mg/l | < 5 |
        other | CRP | 7 | mg/l | < 5 |
        other | CRP | 4 | mg/l | < 5 |
        glucose | Glucose | 5,2 | mmol/l | 4,0 - 5,6 |
        end
    """.trimIndent()

    /** F21: qualitative results only. */
    val F21 = """
        protocoltracker-bloodwork-1
        date: 2025-04-01 | Afnamedatum: 01-04-2025
        other | HBsAg | negatief | | |
        other | Anti-HCV | negatief | | | niet reactief
        end
    """.trimIndent()

    /** The prompt's opening and layout block (`LabPrompt.text` has the same shape). */
    val PROMPT = """
        SteroidTracker prompt (format protocoltracker-bloodwork-1). Give the app the chatbot's answer, not this prompt.

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
