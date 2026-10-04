package com.apollof.protocoltracker.domain.units

/** What a number field accepts while typing: up to 7 digits, then `.` or `,` and up to 4 decimals. */
object DecimalInput {
    // ASCII digits on purpose: Android's `\d` also matches other scripts' digits, which no number parser reads.
    private val DRAFT = Regex("[0-9]{0,7}(?:[.,][0-9]{0,4})?")

    fun accepts(text: String): Boolean = DRAFT.matches(text)
}
