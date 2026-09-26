package com.apollof.protocoltracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Creates tracker.db with the exported version 2 schema and one note, as an installed 0.3 app left it. */
    private fun createVersion2() {
        val schema = File("schemas/com.apollof.protocoltracker.data.db.TrackerDatabase/2.json").readText()
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
        db.execSQL(
            "INSERT INTO journal (id, kind, atMs, systolic, diastolic, pulse, text, createdAtMs) VALUES ('n', 'NOTE', 1000, NULL, NULL, NULL, 'Kept', 1000)",
        )
        db.version = 2
        db.close()
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
