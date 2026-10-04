package com.apollof.protocoltracker.domain.pk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/**
 * The user's own scaling of level estimates, per level group: "Adjust level" (docs/MODELS.md).
 * A group's curve is multiplied by `1 + percent / 100`, for past and planned doses alike. It describes how the user's
 * levels compare with the estimate, not a dose, so it is never part of a logged dose's snapshot.
 *
 * Groups without an entry are not adjusted. Percentages are whole numbers in [MIN]..[MAX]; 0 is never stored.
 */
class LevelAdjustments private constructor(private val percents: Map<String, Int>) {
    /** Groups with an adjustment and their percentages. */
    val byGroup: Map<String, Int> get() = percents

    fun percent(group: String): Int = percents[group] ?: 0

    /** Factor on [group]'s curve: 1.25 for +25 %, 0.1 for −90 %. The floor keeps every curve visible. */
    fun factor(group: String): Double = factorOf(percent(group))

    /** A copy with [group] at [percent] (clamped to [MIN]..[MAX]); 0 removes the group. */
    fun with(group: String, percent: Int): LevelAdjustments {
        val clamped = percent.coerceIn(MIN, MAX)
        return LevelAdjustments(if (clamped == 0) percents - group else percents + (group to clamped))
    }

    /** JSON object of group → percent, for the settings store; empty when nothing is adjusted. */
    fun encode(): String = if (percents.isEmpty()) "" else JsonObject(percents.mapValues { JsonPrimitive(it.value) }).toString()

    override fun equals(other: Any?): Boolean = other is LevelAdjustments && other.percents == percents
    override fun hashCode(): Int = percents.hashCode()
    override fun toString(): String = "LevelAdjustments($percents)"

    companion object {
        const val MIN = -90
        const val MAX = 100

        val NONE = LevelAdjustments(emptyMap())

        fun of(vararg entries: Pair<String, Int>): LevelAdjustments = entries.fold(NONE) { acc, (g, p) -> acc.with(g, p) }

        fun factorOf(percent: Int): Double = 1 + percent.coerceIn(MIN, MAX) / 100.0

        /** "+15%" or "−40%"; null when not adjusted. */
        fun label(percent: Int): String? = when {
            percent > 0 -> "+$percent%"
            percent < 0 -> "−${-percent}%"
            else -> null
        }

        /**
         * Reads [text] from [encode]. Never throws: text that is not a JSON object reads as no adjustment, entries that
         * are not finite numbers or have a blank group are left out, fractions round and values out of range are clamped.
         */
        fun decode(text: String?): LevelAdjustments {
            if (text.isNullOrBlank()) return NONE
            val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return NONE
            var out = NONE
            for ((group, value) in obj) {
                if (group.isBlank()) continue
                val number = (value as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() } ?: continue
                out = out.with(group, number.coerceIn(MIN.toDouble(), MAX.toDouble()).roundToInt())
            }
            return out
        }
    }
}
