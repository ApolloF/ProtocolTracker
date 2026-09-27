package com.apollof.protocoltracker.domain.io.labimport

/**
 * The bloodwork import format (import doc §3.7). [HEADER] is the one constant that the prompt, the parser and the tests
 * share; a changed field or line kind bumps [VERSION], new aliases, units or keys never do.
 */
object BloodworkImport {
    const val HEADER = "protocoltracker-bloodwork-1"
    const val VERSION = 1

    /** Longer text is refused (M8): no lab report answer comes close. */
    const val MAX_CHARS = 200_000

    /** The prompt's first words; text holding them and no answer is the prompt pasted back (M4). */
    const val PROMPT_SIGNATURE = "ProtocolTracker prompt (format $HEADER)"
}
