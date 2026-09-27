package com.apollof.protocoltracker.domain.io.labimport

/** Every text the bloodwork import shows (import doc §4.5), in one place so the tests pin them. */
object ImportMessages {
    // Start step: why the text is not read (M1-M8, M11, M12)
    const val EMPTY = "There is no text to import. In the chatbot, tap Copy on its answer first."
    const val NO_BLOCK = "No results found in this text. In the chatbot, tap Copy on its answer, then try again."
    const val SHARE_LINK = "This is a link to a chat. The app works offline and can't open it. Copy the answer itself " +
        "instead; a shared chat link is public."
    const val PROMPT = "This is the prompt. Paste it into a chatbot with your lab report, then copy the chatbot's answer."
    const val OTHER_FORMAT = "The chatbot used another layout. Ask it: \"Use the ${BloodworkImport.HEADER} layout " +
        "from my first message.\""
    const val JOINED_LINES = "The lines of the answer were joined into one. Copy it with the Copy button of the " +
        "block itself."
    const val TOO_LONG = "This text is too long for a lab report. Copy only the chatbot's answer."
    const val RAW_REPORT = "This looks like the lab report itself. Give it to a chatbot together with the AI prompt, " +
        "then bring back the chatbot's answer."
    const val NO_RESULTS = "The answer has no results. Ask the chatbot to list every result from the report."

    fun newerVersion(version: Int) =
        "This answer uses a newer layout (version $version). Update ProtocolTracker Dev, or copy the prompt again."

    // Check step notices (N1-N3)
    const val CUT_OFF = "The answer stops early, so results may be missing. Ask the chatbot to continue, then copy " +
        "the whole answer."
    const val LEFT_OUT = "The chatbot left out some results. Ask it to list every result."
    const val NO_HEADER = "The answer has no first line \"${BloodworkImport.HEADER}\". If you copied only part of " +
        "it, results may be missing."

    // Not imported: lines of a block that give no result
    fun lineNotUnderstood(line: Int, original: String) = "Line $line not understood: \"${shorten(original)}\""

    fun lineCutOff(line: Int, original: String) = "Line $line may be cut off: \"${shorten(original)}\""

    private const val MAX_QUOTE = 80

    private fun shorten(text: String) = if (text.length <= MAX_QUOTE) text else text.take(MAX_QUOTE - 1) + "…"
}
