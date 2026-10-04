package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers

/**
 * The AI prompt the owner pastes into a chatbot with a lab report (import doc §12). Built from [BloodMarkers] and
 * [MarkerVocabulary], so every marker's key, name, accepted units and aliases are listed; `LabPromptTest` fails when a
 * marker is missing and keeps the copy in `docs/BLOODWORK_IMPORT.md` equal to [text]. It holds no example numbers,
 * because models copy them when a photo is unreadable.
 */
object LabPrompt {
    val text: String by lazy {
        (INTRO + BloodMarkers.all.map { keyLine(it.key) } + OTHER_LINE).joinToString("\n")
    }

    /** `key: name; units; aliases` plus `; notes` when the marker has them. */
    fun keyLine(key: String): String {
        val marker = requireNotNull(BloodMarkers.find(key)) { "unknown marker $key" }
        val names = requireNotNull(MarkerVocabulary.names(key)) { "no names for $key" }
        val units = MarkerVocabulary.units[key].orEmpty().map { it.display }.distinct()
        val fields = listOf(marker.name, units.joinToString(), names.aliases.joinToString()) + listOfNotNull(names.promptNote)
        return "$key: " + fields.joinToString("; ")
    }

    private const val OTHER_LINE = "other: any other test; copy its name, result, unit and range as printed"

    private val INTRO: List<String> = """
        ${BloodworkImport.PROMPT_SIGNATURE}. Give the app the chatbot's answer, not this prompt.

        Turn the attached lab report (PDF, photo or text) into one data block for the SteroidTracker app. Read every page. Use only the report.

        Reply with one code block and nothing before or after it, in exactly this layout. Parts in <angle brackets> are placeholders.

        ```text
        ${BloodworkImport.HEADER}
        lab: <lab name; leave this line out if none is printed>
        date: <draw date YYYY-MM-DD> <draw time HH:MM, only if printed> | <date label and date exactly as printed>
        <key> | <test name as printed> | <result as printed> | <unit as printed> | <reference range as printed> | <note>
        end
        ```

        Rules
        1. One line per result, for every result on the report, also tests that are not in the key list. Never shorten the list or write "..." or "etc.".
        2. Copy the result, unit and reference range character for character, with every decimal comma or point, thousands separator and < or >. Never round, convert, calculate, estimate or invent a value (such as free testosterone, LDL, eGFR or a ratio that is not printed). Leave out flags such as H, L, * and arrows.
        3. If a result is unreadable, missing or a word (such as "volgt" or "negatief"), write ? as the result, which means no value, and put the reason or the word in the note.
        4. If the report prints several reference ranges (for example for men and women, or by age), copy all of them with their labels. If it prints none, leave the range empty. Never add one yourself.
        5. Key: pick it from the list below. Use other when the test is not in the list or you are not sure. A similar name is not enough: follow every note.
        6. Date: the day the blood was drawn (afname, afnamedatum, datum afname, datum van afname, collected). Never the date of birth, or the received, report or print date. If no draw date is printed, write date: ? and put the dates you see after the |.
        7. Several draw dates (for example earlier results in extra columns): one date line per draw, newest first, each followed by its own results.
        8. Leave out names, dates of birth, patient numbers and addresses. No advice, comments or questions: put doubts in the note of that line.
        9. End the block with the line end. Before you answer, check every line against the report once more.

        Keys (key: name; units; also printed as; notes)
    """.trimIndent().lines()
}
