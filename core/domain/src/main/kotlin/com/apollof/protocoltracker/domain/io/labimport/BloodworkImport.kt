package com.apollof.protocoltracker.domain.io.labimport

import java.time.LocalDate

/**
 * The bloodwork import format (import doc §3.7). [HEADER] is the one constant that the prompt, the parser and the tests
 * share; a changed field or line kind bumps [VERSION], new aliases, units or keys never do.
 */
object BloodworkImport {
    const val HEADER = "protocoltracker-bloodwork-1"
    const val VERSION = 1

    /** Longer text is refused (M8): no lab report answer comes close. */
    const val MAX_CHARS = 200_000

    /** The prompt's first words. */
    const val PROMPT_SIGNATURE = "Bloodwork import prompt (format $HEADER)"

    /**
     * Text holding this and no answer is the prompt pasted back (M4). It leaves out the app name, so a prompt copied
     * before the rename from ProtocolTracker ("ProtocolTracker prompt (format …)") is still recognised.
     */
    const val PROMPT_MARK = "prompt (format $HEADER)"

    /** Reads pasted text into a draft, or refuses it with the most specific message. [today] is for the date checks. */
    fun read(text: String, today: LocalDate): ImportRead = when (val read = BlockReader.read(text, today)) {
        is BlockRead.Refused -> ImportRead.Refused(read.problem)
        is BlockRead.Found -> ImportRead.Found(ImportDrafts.of(read))
    }
}
