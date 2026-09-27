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
    const val CUT_OFF = "The answer stops early, so results may be missing. Ask the chatbot to write the whole " +
        "answer again, then copy it."
    const val LEFT_OUT = "The chatbot left out some results. Ask it to list every result."
    const val NO_HEADER = "The answer has no first line \"${BloodworkImport.HEADER}\". If you copied only part of " +
        "it, results may be missing."

    // Not imported: lines of a block that give no result
    fun lineNotUnderstood(line: Int, original: String) = "Line $line not understood: \"${shorten(original)}\""

    fun lineCutOff(line: Int, original: String) = "Line $line may be cut off: \"${shorten(original)}\""

    // Draws left out whole (D1-D4; v1 asks nothing) and the draw caption
    const val NO_DATE = "No draw date on the report."
    const val RECEIVED = "Received date; the draw can be a day earlier."

    fun notDrawDate(label: String) = "This date is from \"$label\", not the blood draw."

    fun whichDate(printed: String) = "The report says \"$printed\". Which date is it?"

    fun futureDate(date: String) = "$date is in the future."

    fun before1990(date: String) = "$date is before 1990."

    // Results left out: what the full design asks (Q1-Q4)
    fun noUnit(name: String, value: String) = "$name $value: no unit on the report."

    fun notAUnit(name: String, value: String, unit: String, marker: String) =
        "$name $value $unit: $unit is not a unit for $marker."

    fun rangeFits(name: String, value: String, unit: String, range: String, other: String) =
        "$name $value $unit: the range $range fits $other, not $unit."

    fun notPossibleAsk(name: String, value: String, unit: String, marker: String) =
        "$name $value $unit is not possible for $marker."

    fun lostDecimal(name: String, value: String, unit: String, range: String, suggestion: String) =
        "$name $value $unit is far outside the range $range. Does the report say $suggestion?"

    fun thousands(name: String, printed: String, a: String, b: String) = "$name $printed: this can mean $a or $b."

    fun twoValues(marker: String, date: String?) =
        if (date == null) "Two values for $marker." else "Two values for $marker on $date."

    // Not imported
    const val NEGATIVE = "Negative results are not supported."
    const val NO_UNIT = "No unit on the report."

    fun noResult(text: String?) = if (text.isNullOrBlank()) "No result." else "No result: ${text.trim().removeSuffix(".")}."

    fun notANumber(text: String) = "\"$text\" is not a number."

    fun notPossible(value: String, unit: String, marker: String) = "$value $unit is not possible for $marker."

    fun unitNotAccepted(unit: String, marker: String) = "$unit is not a unit for $marker."

    // Row captions (C1-C12; C10 is Later)
    const val MATCHED_BY_CHATBOT = "Matched by the chatbot."
    const val MENS_RANGE = "Men's range used."

    fun onlyUnitFits(unit: String) = "No unit on the report; only $unit fits."

    fun keptAs(name: String, marker: String) = "Kept as $name, not $marker."

    fun readAsMarker(marker: String) = "Read as $marker."

    fun rangeNotUsed(problem: RangeProblem) = "Range not used: " + when (problem) {
        RangeProblem.SEVERAL -> "several ranges."
        RangeProblem.NOT_A_RANGE -> "not a range."
        RangeProblem.LOW_ABOVE_HIGH -> "low is above high."
        RangeProblem.NEGATIVE -> "negative limits are not supported."
        RangeProblem.WOMEN_ONLY -> "women's range."
        RangeProblem.UNCLEAR -> "unclear numbers."
    }

    fun rangeDoesNotFit(marker: String) = "Range not used: it doesn't fit $marker."

    fun changedLater(was: String) = "Changed later in the answer (was $was)."

    fun savedEarlier(value: String) = "Saved earlier this day: $value."

    fun readAsNumber(number: String) = "Read as $number."

    fun fastingNotStated(name: String) = "Kept as $name: fasting not stated."

    fun numbersDoNotFit(name: String, marker: String) = "Kept as $name: the numbers don't fit $marker."

    // Save area (S2-S5; S1 is Later) and the button
    const val ALL_SAVED = "Everything here is already saved."
    const val NO_NUMBERS = "Nothing to save. No result has a number."
    const val NOTHING_TO_SAVE = "Nothing to save."

    /** S2 in v1: the names of the results left out with a reason, or their number. */
    fun leftOut(names: List<String>) = when {
        names.size == 1 -> "${names[0]} is left out."
        names.size == 2 && names[0] != names[1] -> "${names[0]} and ${names[1]} are left out."
        else -> "${names.size} results are left out."
    }

    fun save(results: Int, draws: Int) = when {
        results == 0 -> "Save"
        draws > 1 -> "Save $draws draws"
        results == 1 -> "Save 1 result"
        else -> "Save $results results"
    }

    /** The Journal snackbar after saving [draws] entries. */
    fun saved(draws: Int) = if (draws == 1) "Bloodwork saved" else "$draws blood draws saved"

    private const val MAX_QUOTE = 80

    private fun shorten(text: String) = if (text.length <= MAX_QUOTE) text else text.take(MAX_QUOTE - 1) + "…"
}
