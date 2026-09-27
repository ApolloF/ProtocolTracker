package com.apollof.protocoltracker.data.db

import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

/** Import doc §6.2: bloodwork rows store results in `dataJson`; plain results keep today's exact string. */
class BloodworkMapperTest {
    private val t = Instant.parse("2026-09-24T07:30:00Z")
    private val oldDataJson = """{"results":[{"marker":"estradiol","value":32.5},{"marker":"hematocrit","value":53.0}],"lab":"Lab A"}"""

    private fun row(dataJson: String) = JournalEntity("b", "BLOODWORK", t.toEpochMilli(), null, null, null, "Fasting", t.toEpochMilli(), dataJson)

    @Test
    fun anOldRowDecodesAndAPlainEntryStoresTheSameString() {
        val entry = row(oldDataJson).toDomain() as JournalEntry.Bloodwork
        val expected = JournalEntry.Bloodwork(
            "b", t, listOf(MarkerResult("estradiol", 32.5), MarkerResult("hematocrit", 53.0)), lab = "Lab A", note = "Fasting", createdAt = t,
        )
        assertEquals(expected, entry)
        assertEquals(row(oldDataJson), expected.toEntity())
    }

    @Test
    fun theNewFieldsRoundTrip() {
        val entry = JournalEntry.Bloodwork(
            "b", t,
            listOf(
                MarkerResult("fsh", 0.3, qualifier = "<", refLow = 1.5, refHigh = 12.4),
                MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"),
            ),
            lab = "Lab A", note = "Fasting", createdAt = t,
        )
        val stored = entry.toEntity()
        assertEquals(
            """{"results":[{"marker":"fsh","value":0.3,"qualifier":"<","refLow":1.5,"refHigh":12.4},""" +
                """{"marker":"other:vrij_t4","value":15.2,"refLow":10.0,"refHigh":23.0,"name":"Vrij T4","unit":"pmol/l"}],"lab":"Lab A"}""",
            stored.dataJson,
        )
        assertEquals(entry, stored.toDomain())
    }
}
