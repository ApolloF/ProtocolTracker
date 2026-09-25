package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.pk.Presets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImportAndBackupTest {
    private val zone = ZoneId.of("Europe/Amsterdam")

    /** Synthetic export matching the cycletracker-1 schema (packages/domain/src/index.ts in the old repo). */
    private val legacy = """
    {"format":"cycletracker-1","exportedAt":"2026-09-10T10:00:00.000Z",
     "workspace":{"version":3,"preferences":{},"protocol":{
       "activePhaseId":"p1",
       "activations":[{"phaseId":"p1","at":"2026-08-01T08:00:00.000Z"}],
       "phases":[{"id":"p1","name":"Cruise","category":"Ongoing","notes":"","archived":false,"entries":[
         {"id":"e1","version":2,"compoundId":"testosterone","formulationId":"testosterone-cypionate-im","name":"Test C","route":"IM",
          "enabled":true,"amount":{"value":0.4,"unit":"mL"},"concentration":{"value":200,"unit":"mg"},"notes":"",
          "schedule":{"kind":"weekdays","timeZone":"Europe/Amsterdam","startDate":"2026-08-01","weekdays":[1,4],
            "slots":[{"id":"s1","time":"09:00","amount":{"value":0.4,"unit":"mL"}}],"intervalDays":2,"intervalHours":48}},
         {"id":"e2","version":1,"compoundId":"custom","formulationId":"custom-x","name":"Vitamin D","route":"Oral",
          "enabled":true,"amount":{"value":2,"unit":"mcg"},"notes":"",
          "schedule":{"kind":"daily","timeZone":"Europe/Amsterdam","startDate":"2026-08-01","weekdays":[],
            "slots":[{"id":"s1","time":"08:00","amount":{"value":2,"unit":"mcg"}}],"intervalDays":2,"intervalHours":48}},
         {"id":"e3","version":1,"compoundId":"hcg","formulationId":"hcg-urinary-sc","name":"hCG","route":"SC",
          "enabled":true,"amount":{"value":500,"unit":"IU"},"notes":"",
          "schedule":{"kind":"elapsed","timeZone":"Europe/Amsterdam","startDate":"2026-08-01","weekdays":[],"anchorInstant":"2026-08-01T07:00:00.000Z",
            "slots":[{"id":"s1","time":"09:00","amount":{"value":500,"unit":"IU"}}],"intervalDays":2,"intervalHours":84}}
       ]},
       {"id":"p2","name":"Draft","category":"Ongoing","notes":"","archived":false,"entries":[]},
       {"id":"p3","name":"PCT","category":"Ongoing","notes":"","archived":false,"plannedStart":"2026-12-01","entries":[
         {"id":"e4","version":1,"compoundId":"x","formulationId":"anastrozole-oral","name":"Adex","route":"Oral",
          "enabled":true,"amount":{"value":1,"unit":"mg"},"notes":"",
          "schedule":{"kind":"daily","timeZone":"Europe/Amsterdam","startDate":"2026-12-01","weekdays":[],
            "slots":[{"id":"m","time":"08:00","amount":{"value":0.5,"unit":"mg"}},{"id":"n","time":"20:00","amount":{"value":0.25,"unit":"mg"}}]}}
       ]}]}},
     "records":[
       {"id":"r1","kind":"administration","version":1,"event_at":"2026-09-07T07:00:00.000Z","data":{"occurrenceId":"e1:2:s1:2026-09-07T07:00:00.000Z","phaseId":"p1","entryId":"e1",
         "at":"2026-09-07T07:02:00.000Z","status":"taken","amount":{"value":0.4,"unit":"mL"},"site":"Left glute","note":"",
         "snapshot":{"id":"e1","version":2,"compoundId":"testosterone","formulationId":"testosterone-cypionate-im","name":"Test C","route":"IM",
          "enabled":true,"amount":{"value":0.4,"unit":"mL"},"concentration":{"value":200,"unit":"mg"},"notes":"",
          "schedule":{"kind":"daily","timeZone":"Europe/Amsterdam","startDate":"2026-08-01","weekdays":[],"slots":[{"id":"s1","time":"09:00","amount":{"value":0.4,"unit":"mL"}}]}}}},
       {"id":"r2","kind":"administration","version":1,"event_at":"2026-09-08T07:00:00.000Z","data":{"at":"2026-09-08T07:00:00.000Z","status":"retracted",
         "amount":{"value":1,"unit":"mg"},"snapshot":{"id":"e1","name":"Test C","formulationId":"testosterone-cypionate-im"}}},
       {"id":"r3","kind":"health","version":1,"event_at":"2026-09-08T07:00:00.000Z","data":{"kind":"note","title":"x"}}
     ]}
    """.trimIndent()

    @Test
    fun mapsProtocolLogsAndReportsSkips() {
        val r = LegacyImport.parse(legacy, zone, Presets.all)
        assertEquals(listOf("Cruise", "PCT"), r.phases.map { it.name }) // undated draft skipped
        assertEquals(LocalDate.parse("2026-08-01"), r.phases.first().startDate)
        assertTrue(r.warnings.any { "Draft" in it })
        assertEquals(5, r.items.size)
        val adex = r.items.filter { it.compoundId == "preset:anastrozole" }
        assertEquals(setOf(0.5, 0.25), adex.map { it.dose.value }.toSet()) // per-time amounts kept by splitting
        val testC = r.items.first { it.compoundId == "preset:test-cyp" }
        assertEquals(Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), listOf(java.time.LocalTime.of(9, 0))), testC.schedule)
        assertEquals(200.0, testC.formulation.perMl)
        assertEquals(DoseUnit.ML, testC.dose.unit)
        val hcg = r.items.first { it.compoundId == "preset:hcg" }
        assertEquals(84.0, (hcg.schedule as Schedule.EveryHours).hours)
        assertEquals(listOf("Vitamin D"), r.compounds.map { it.name })

        val log = r.logs.single()
        assertEquals(LogStatus.TAKEN, log.status)
        assertEquals(Instant.parse("2026-09-07T07:02:00Z"), log.takenAt)
        assertTrue("Left glute" in log.note)
        assertEquals(200.0, log.snapshot.formulation.perMl)
        assertEquals("ct1:item:e1@${Instant.parse("2026-09-07T07:00:00Z").epochSecond}", log.occurrenceKey)
        assertEquals("ct1:item:e1", log.planItemId)
        assertEquals(1, r.skippedHealthRecords)
    }

    @Test
    fun reimportYieldsSameIds() {
        val a = LegacyImport.parse(legacy, zone, Presets.all)
        val b = LegacyImport.parse(legacy, zone, Presets.all + a.compounds)
        assertEquals(a.items.map { it.id }, b.items.map { it.id })
        assertEquals(a.logs.map { it.id }, b.logs.map { it.id })
        assertEquals(emptyList(), b.compounds) // custom compound already exists
    }

    @Test
    fun rejectsOtherFormats() {
        assertFailsWith<ImportFormatException> { LegacyImport.parse("""{"format":"other"}""", zone, emptyList()) }
        assertFailsWith<ImportFormatException> { LegacyImport.parse("not json", zone, emptyList()) }
    }

    @Test
    fun backupRoundTrips() {
        val r = LegacyImport.parse(legacy, zone, Presets.all)
        val backup = Backup(exportedAt = Instant.parse("2026-09-25T10:00:00Z"), compounds = Presets.all + r.compounds, phases = r.phases, items = r.items, logs = r.logs)
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun backupRejectsInvalidSchedules() {
        val r = LegacyImport.parse(legacy, zone, Presets.all)
        val bad = r.items.first().copy(schedule = Schedule.EveryNDays(0, LocalDate.EPOCH, listOf(java.time.LocalTime.NOON)))
        val broken = Backup(exportedAt = Instant.EPOCH, compounds = Presets.all + r.compounds, phases = r.phases, items = listOf(bad), logs = emptyList())
        assertFailsWith<ImportFormatException> { BackupCodec.decode(BackupCodec.encode(broken)) }
    }

    @Test
    fun backupRejectsDanglingReferences() {
        val r = LegacyImport.parse(legacy, zone, Presets.all)
        val broken = Backup(exportedAt = Instant.EPOCH, compounds = emptyList(), phases = r.phases, items = r.items, logs = emptyList())
        assertFailsWith<ImportFormatException> { BackupCodec.decode(BackupCodec.encode(broken)) }
    }
}
