package com.apollof.protocoltracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.data.db.toEntity
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.MarkerResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Creates tracker.db with the exported schema of [version], then runs [fill] on it, as an installed app left it. */
    private fun createVersion(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = File("schemas/com.apollof.protocoltracker.data.db.TrackerDatabase/$version.json").readText()
        val database = JSONObject(schema).getJSONObject("database")
        context.deleteDatabase("tracker.db")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("tracker.db").apply { parentFile?.mkdirs() }, null)
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = database.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        fill(db)
        db.version = version
        db.close()
    }

    /** Version 2 as a 0.3 app left it: one note. It migrates 2 -> 3 -> 4. */
    private fun createVersion2() = createVersion(2) { db ->
        db.execSQL(
            "INSERT INTO journal (id, kind, atMs, systolic, diastolic, pulse, text, createdAtMs) VALUES ('n', 'NOTE', 1000, NULL, NULL, NULL, 'Kept', 1000)",
        )
    }

    @Test
    fun version3KeepsEveryFieldAndAddsNoSite() = runTest {
        val at = Instant.ofEpochMilli(1_790_000_000_000)
        val snapshot = DoseSnapshot("Test C (testosterone cypionate)", "testosterone", CompoundCategory.INJECTABLE_STEROID, BaseUnit.MG, null, Formulation(perMl = 200.0))
        val log = DoseLog(
            "l", "i", "preset:test-cyp", "i@2026-09-21/ANY_TIME", at, at.plusSeconds(60), Amount(120.0, DoseUnit.MG),
            Amount(125.0, DoseUnit.MG), LogStatus.TAKEN, "left glute", snapshot, at.plusSeconds(90),
        )
        val bloodwork = JournalEntry.Bloodwork("b", at, listOf(MarkerResult("estradiol", 32.5)), lab = "Lab A", note = "Fasted", createdAt = at)
        createVersion(3) { db ->
            val row = log.toEntity()
            db.execSQL(
                "INSERT INTO dose_logs (id, planItemId, compoundId, occurrenceKey, scheduledAtMs, takenAtMs, amountValue, amountUnit, plannedValue, " +
                    "plannedUnit, status, note, snapshotJson, createdAtMs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    row.id, row.planItemId, row.compoundId, row.occurrenceKey, row.scheduledAtMs, row.takenAtMs, row.amountValue, row.amountUnit,
                    row.plannedValue, row.plannedUnit, row.status, row.note, row.snapshotJson, row.createdAtMs,
                ),
            )
            val entry = bloodwork.toEntity()
            db.execSQL(
                "INSERT INTO journal (id, kind, atMs, systolic, diastolic, pulse, text, createdAtMs, dataJson) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(entry.id, entry.kind, entry.atMs, entry.systolic, entry.diastolic, entry.pulse, entry.text, entry.createdAtMs, entry.dataJson),
            )
        }
        val db = TrackerDatabase.create(context)
        try {
            val repo = TrackerRepository(db) { Instant.EPOCH }
            assertEquals(listOf(log), repo.allLogsNow())
            assertNull(repo.allLogsNow().single().site)
            assertEquals(listOf<JournalEntry>(bloodwork), repo.journalNow())
            repo.updateLog(log.copy(site = "vg_r"))
            assertEquals("vg_r", repo.allLogsNow().single().site)
        } finally {
            db.close()
            context.deleteDatabase("tracker.db")
        }
    }

    @Test
    fun version2KeepsJournalAndStoresHealthEntries() = runTest {
        createVersion2()
        val db = TrackerDatabase.create(context)
        try {
            val repo = TrackerRepository(db) { Instant.EPOCH }
            assertEquals(listOf("Kept"), repo.journal.first().filterIsInstance<JournalEntry.Note>().map { it.text })
            val at = Instant.ofEpochMilli(2000)
            val symptoms = JournalEntry.Symptoms("s", at, listOf("acne"), mood = 4, note = "Oily", createdAt = at)
            val bloodwork = JournalEntry.Bloodwork("b", at, listOf(MarkerResult("estradiol", 32.5)), lab = "Lab A", createdAt = at)
            repo.saveJournal(symptoms)
            repo.saveJournal(bloodwork)
            val stored = repo.journalNow().associateBy { it.id }
            assertEquals(symptoms, stored["s"])
            assertEquals(bloodwork, stored["b"])
        } finally {
            db.close()
            context.deleteDatabase("tracker.db")
        }
    }
}
