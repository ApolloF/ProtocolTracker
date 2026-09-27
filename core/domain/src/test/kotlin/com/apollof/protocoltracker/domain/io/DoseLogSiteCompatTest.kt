package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `DoseLog.site` is optional: backups without sites keep their bytes, old backups restore, and sites round-trip. */
class DoseLogSiteCompatTest {
    private val t = Instant.parse("2026-09-24T07:30:00Z")
    private val snapshot = DoseSnapshot("Test C (testosterone cypionate)", "testosterone", CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null, Formulation(perMl = 200.0))
    private val taken = DoseLog(
        "l1", "tc", "preset:test-cyp", "tc@1790235000", t, t, Amount(125.0, DoseUnit.MG), Amount(125.0, DoseUnit.MG), LogStatus.TAKEN, "", snapshot, t,
    )
    private val skipped = taken.copy(id = "l2", occurrenceKey = "tc@1790321400", status = LogStatus.SKIPPED, plannedAmount = null, note = "away")
    private fun backup(vararg logs: DoseLog) = Backup(exportedAt = t, compounds = emptyList(), phases = emptyList(), items = emptyList(), logs = logs.toList())

    /** Encoded by the app before `DoseLog.site` existed. */
    private val golden = """{"format":"protocoltracker-backup-2","exportedAt":"2026-09-24T07:30:00Z","compounds":[],"phases":[],"items":[],"logs":[""" +
        """{"id":"l1","planItemId":"tc","compoundId":"preset:test-cyp","occurrenceKey":"tc@1790235000","scheduledAt":"2026-09-24T07:30:00Z",""" +
        """"takenAt":"2026-09-24T07:30:00Z","amount":{"value":125.0,"unit":"MG"},"plannedAmount":{"value":125.0,"unit":"MG"},"status":"TAKEN","note":"",""" +
        """"snapshot":{"displayName":"Test C (testosterone cypionate)","group":"testosterone","category":"INJECTABLE_STEROID","baseUnit":"MG","pk":null,""" +
        """"formulation":{"perMl":200.0,"perTablet":null}},"createdAt":"2026-09-24T07:30:00Z"},""" +
        """{"id":"l2","planItemId":"tc","compoundId":"preset:test-cyp","occurrenceKey":"tc@1790321400","scheduledAt":"2026-09-24T07:30:00Z",""" +
        """"takenAt":"2026-09-24T07:30:00Z","amount":{"value":125.0,"unit":"MG"},"plannedAmount":null,"status":"SKIPPED","note":"away",""" +
        """"snapshot":{"displayName":"Test C (testosterone cypionate)","group":"testosterone","category":"INJECTABLE_STEROID","baseUnit":"MG","pk":null,""" +
        """"formulation":{"perMl":200.0,"perTablet":null}},"createdAt":"2026-09-24T07:30:00Z"}],"journal":[]}"""

    @Test
    fun aBackupWithoutSitesEncodesExactlyAsBefore() {
        assertEquals(golden, BackupCodec.encode(backup(taken, skipped)))
    }

    @Test
    fun aBackupFromBeforeSitesDecodesWithoutThemAndKeepsItsBytes() {
        val decoded = BackupCodec.decode(golden)
        assertEquals(listOf(taken, skipped), decoded.logs)
        decoded.logs.forEach { assertNull(it.site) }
        assertEquals(golden, BackupCodec.encode(decoded))
    }

    @Test
    fun aSiteRoundTripsAndOnlyAddsItsField() {
        val sited = backup(taken.copy(site = "vg_r"), skipped, taken.copy(id = "l3", site = "calf_l"))
        val text = BackupCodec.encode(sited)
        assertTrue(""""createdAt":"2026-09-24T07:30:00Z","site":"vg_r"}""" in text, text)
        assertTrue(""""site":"calf_l"""" in text, text)
        assertEquals(1, Regex(""""site":"vg_r"""").findAll(text).count())
        assertEquals(sited, BackupCodec.decode(text))
        // Without its sites the backup is byte for byte the one written before sites existed.
        assertEquals(golden, BackupCodec.encode(sited.copy(logs = sited.logs.take(2).map { it.copy(site = null) })))
    }
}
