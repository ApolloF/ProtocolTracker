package com.apollof.protocoltracker.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Non-dose entries in the journal: blood pressure readings and free-text notes. */
@Serializable
sealed interface JournalEntry {
    val id: String
    val at: Instant
    val createdAt: Instant

    @Serializable @SerialName("blood_pressure")
    data class BloodPressure(
        override val id: String,
        override val at: InstantS,
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int? = null,
        val note: String = "",
        override val createdAt: InstantS,
    ) : JournalEntry {
        init {
            val problems = bloodPressureProblems(systolic, diastolic, pulse)
            require(problems.isEmpty()) { problems.first() }
        }
    }

    @Serializable @SerialName("note")
    data class Note(
        override val id: String,
        override val at: InstantS,
        val text: String,
        override val createdAt: InstantS,
    ) : JournalEntry {
        init {
            require(text.isNotBlank()) { "Write a note" }
            require(text.length <= MAX_NOTE_LENGTH) { "Notes can be at most $MAX_NOTE_LENGTH characters" }
        }
    }

    /** Ticked symptoms ([SymptomCatalog] keys) with optional mood (1–10) and hair shedding (1–5). */
    @Serializable @SerialName("symptoms")
    data class Symptoms(
        override val id: String,
        override val at: InstantS,
        val symptoms: List<String> = emptyList(),
        val mood: Int? = null,
        val hairShedding: Int? = null,
        val note: String = "",
        override val createdAt: InstantS,
    ) : JournalEntry {
        init {
            require(symptoms.isNotEmpty() || mood != null || hairShedding != null || note.isNotBlank()) { "Choose a symptom or add a note" }
            require(mood == null || mood in 1..10) { "Mood must be 1–10" }
            require(hairShedding == null || hairShedding in 1..HAIR_SHEDDING_LABELS.size) { "Hair shedding must be 1–${HAIR_SHEDDING_LABELS.size}" }
            require(note.length <= MAX_NOTE_LENGTH) { "Notes can be at most $MAX_NOTE_LENGTH characters" }
        }
    }

    /** Lab results of one blood draw, stored in each marker's conventional unit ([BloodMarkers]). */
    @Serializable @SerialName("bloodwork")
    data class Bloodwork(
        override val id: String,
        override val at: InstantS,
        val results: List<MarkerResult>,
        val lab: String = "",
        val note: String = "",
        override val createdAt: InstantS,
    ) : JournalEntry {
        init {
            require(results.isNotEmpty()) { "Enter at least one result" }
            require(results.map { it.marker }.distinct().size == results.size) { "Each marker can be entered once" }
            require(note.length <= MAX_NOTE_LENGTH) { "Notes can be at most $MAX_NOTE_LENGTH characters" }
        }

        fun value(marker: String): Double? = results.firstOrNull { it.marker == marker }?.value

        /** Results outside the reference range of known markers. */
        val outOfRange: Int get() = results.count { r -> BloodMarkers.find(r.marker)?.flag(r.value)?.let { it != MarkerFlag.NORMAL } == true }
    }

    companion object {
        const val MAX_NOTE_LENGTH = 5_000
    }
}

/** Plausibility checks for a manual reading; returns messages for the form. */
fun bloodPressureProblems(systolic: Int?, diastolic: Int?, pulse: Int?): List<String> = buildList {
    if (systolic == null) add("Enter the upper value (systolic)")
    else if (systolic !in 50..300) add("Systolic must be 50–300 mmHg")
    if (diastolic == null) add("Enter the lower value (diastolic)")
    else if (diastolic !in 30..200) add("Diastolic must be 30–200 mmHg")
    if (systolic != null && diastolic != null && systolic <= diastolic) add("Systolic must be higher than diastolic")
    if (pulse != null && pulse !in 20..250) add("Pulse must be 20–250 bpm")
}
