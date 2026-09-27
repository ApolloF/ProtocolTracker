package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerFlag
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.RefRange
import com.apollof.protocoltracker.domain.model.flag
import com.apollof.protocoltracker.domain.model.labRange
import com.apollof.protocoltracker.domain.model.range
import com.apollof.protocoltracker.domain.model.unclear
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Import doc §6.2: the optional result fields keep old backups restorable and plain results byte-identical. */
class MarkerResultCompatTest {
    private val t = Instant.parse("2026-09-24T07:30:00Z")

    /** A backup as 0.4.0 writes it: results carry only marker and value. */
    private val backup040 = """{"format":"protocoltracker-backup-2","exportedAt":"2026-09-25T10:00:00Z","compounds":[],"phases":[],""" +
        """"items":[],"logs":[],"journal":[{"type":"bloodwork","id":"b","at":"2026-09-24T07:30:00Z",""" +
        """"results":[{"marker":"estradiol","value":32.5},{"marker":"hematocrit","value":53.0}],"lab":"Lab A","note":"",""" +
        """"createdAt":"2026-09-24T07:30:00Z"}]}"""

    @Test
    fun a040BackupDecodesWithTheNewFieldsEmptyAndEncodesToTheSameBytes() {
        val backup = BackupCodec.decode(backup040)
        val draw = backup.journal.single() as JournalEntry.Bloodwork
        assertEquals(listOf(MarkerResult("estradiol", 32.5), MarkerResult("hematocrit", 53.0)), draw.results)
        draw.results.forEach {
            assertNull(it.qualifier)
            assertNull(it.refLow)
            assertNull(it.refHigh)
            assertNull(it.name)
            assertNull(it.unit)
        }
        assertEquals(1, draw.outOfRange)
        assertEquals(0, draw.unclear)
        assertEquals(backup040, BackupCodec.encode(backup))
    }

    @Test
    fun aPlainResultEncodesExactlyAsBefore() {
        assertEquals("""{"marker":"estradiol","value":32.5}""", BackupCodec.json.encodeToString(MarkerResult.serializer(), MarkerResult("estradiol", 32.5)))
    }

    @Test
    fun everyNewFieldRoundTripsThroughABackup() {
        val results = listOf(
            MarkerResult("fsh", 0.3, qualifier = "<", refLow = 1.5, refHigh = 12.4),
            MarkerResult("egfr", 90.0, qualifier = ">", refLow = 60.0),
            MarkerResult("hematocrit", 51.0, refHigh = 54.0),
            MarkerResult("other:vrij_t4", 15.2, refLow = 10.0, refHigh = 23.0, name = "Vrij T4", unit = "pmol/l"),
        )
        val draw = JournalEntry.Bloodwork("b", t, results, lab = "Lab A", createdAt = t)
        val backup = Backup(exportedAt = t, compounds = emptyList(), phases = emptyList(), items = emptyList(), logs = emptyList(), journal = listOf(draw))
        val text = BackupCodec.encode(backup)
        assertTrue(
            """{"marker":"fsh","value":0.3,"qualifier":"<","refLow":1.5,"refHigh":12.4}""" in text &&
                """{"marker":"egfr","value":90.0,"qualifier":">","refLow":60.0}""" in text &&
                """{"marker":"other:vrij_t4","value":15.2,"refLow":10.0,"refHigh":23.0,"name":"Vrij T4","unit":"pmol/l"}""" in text,
            text,
        )
        assertEquals(backup, BackupCodec.decode(text))
    }

    @Test
    fun badStoredValuesDecodeWithoutThrowing() {
        val text = backup040.replace(
            """{"marker":"hematocrit","value":53.0}""",
            """{"marker":"hematocrit","value":53.0,"qualifier":"≈","refLow":60.0,"refHigh":40.0,"method":"a later field"}""",
        )
        val result = (BackupCodec.decode(text).journal.single() as JournalEntry.Bloodwork).results[1]
        assertEquals("≈", result.qualifier)
        assertNull(result.labRange())
        assertEquals(RefRange(40.0, 52.0), result.range())
        assertNull(result.flag())
        assertTrue(result.unclear)

        val negative = backup040.replace(""""value":53.0}""", """"value":53.0,"refLow":-5.0}""")
        val kept = (BackupCodec.decode(negative).journal.single() as JournalEntry.Bloodwork).results[1]
        assertEquals(-5.0, kept.refLow)
        assertEquals(MarkerFlag.HIGH, kept.flag())
    }
}
