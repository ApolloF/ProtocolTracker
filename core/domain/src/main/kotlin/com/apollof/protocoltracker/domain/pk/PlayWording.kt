package com.apollof.protocoltracker.domain.pk

/**
 * Words the Play build never shows in preset names or category labels: use-pattern slang and every entry of the
 * "Related to anabolic steroids" section of Google Play's list of unapproved substances (support.google.com,
 * answer 9217430). Guard tests in the domain and the Play app check names and labels against it.
 */
object PlayWording {
    val BANNED_TOKENS: List<String> = listOf(
        "steroid", "sarm", "research", "cycle", "blast", "cruise", "pct", "pin", "stack", "biohack",
        "superdrol", "turinabol", "tren", "tren 250", "trena", "sus 500", "androstenedione", "methyldrostanolone", "arimidex",
    )

    /**
     * The first banned token that starts a word of [text], ignoring case and punctuation ("Tren E" and "steroids"
     * match; "somatropin" does not match "pin"); null when there is none.
     */
    fun bannedTokenIn(text: String): String? {
        val words = text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ", prefix = " ")
        return BANNED_TOKENS.firstOrNull { words.contains(" $it") }
    }
}
