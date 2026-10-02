package com.apollof.protocoltracker.domain.model

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class MoodTrendTest {
    private val zone = ZoneId.of("Europe/Amsterdam")

    private fun log(id: String, at: String, mood: Int?) = JournalEntry.Symptoms(id, Instant.parse(at), listOf("acne"), mood = mood, createdAt = Instant.parse(at))

    @Test
    fun ratingsWithinNinetyDaysOfTheLatest() {
        val journal = listOf(
            log("old", "2026-05-01T10:00:00Z", 3),
            log("a", "2026-07-10T10:00:00Z", 5),
            log("none", "2026-08-01T10:00:00Z", null),
            log("b", "2026-09-24T10:00:00Z", 7),
            JournalEntry.Note("n", Instant.parse("2026-09-25T10:00:00Z"), "x", Instant.parse("2026-09-25T10:00:00Z")),
        )
        assertEquals(listOf(5, 7), moodTrend(journal, zone).map { it.mood })
    }

    @Test
    fun ratingsOnOneDayDrawNothing() {
        val journal = listOf(log("a", "2026-09-24T08:00:00Z", 5), log("b", "2026-09-24T18:00:00Z", 6))
        assertEquals(emptyList(), moodTrend(journal, zone))
    }
}
