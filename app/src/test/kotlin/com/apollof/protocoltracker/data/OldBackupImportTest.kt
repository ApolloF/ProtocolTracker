package com.apollof.protocoltracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.BackupCodec
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A backup saved by the released ProtocolTracker v0.5.1 (before the rename to SteroidTracker) restores the way
 * Settings › Restore backup does it, and a new backup keeps the same format name.
 */
@RunWith(AndroidJUnit4::class)
class OldBackupImportTest {
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    private fun fixture(): String = checkNotNull(javaClass.getResource("/backups/protocoltracker-v0.5.1.json")).readText()

    @Test
    fun v051BackupRestores() = runBlocking {
        val backup = BackupCodec.decode(fixture())
        assertEquals("protocoltracker-backup-2", backup.format)

        val repo = container.repository
        repo.restoreBackup(backup)
        repo.seedPresets()
        backup.settings?.let { container.settings.importMap(it) }

        val restored = repo.exportBackup()
        assertEquals(backup.items, restored.items)
        // PresetMigration may move snapshot kinetics that still equal an older preset to the current one (AGENTS.md).
        fun withoutPk(logs: List<DoseLog>) = logs.map { it.copy(snapshot = it.snapshot.copy(pk = null)) }
        assertEquals(withoutPk(backup.logs), withoutPk(restored.logs))
        assertEquals(backup.journal, restored.journal)
        assertTrue(backup.compounds.map { it.id }.toSet().all { id -> restored.compounds.any { it.id == id } })

        val log = restored.logs.single()
        assertEquals("Test C (testosterone cypionate)", log.snapshot.displayName)
        assertEquals(75.0, log.amount.value)
        val bp = restored.journal.single() as JournalEntry.BloodPressure
        assertEquals(Triple(128, 82, 64), Triple(bp.systolic, bp.diastolic, bp.pulse))

        assertTrue(BackupCodec.encode(restored).startsWith("""{"format":"${Backup.FORMAT}""""))
        assertEquals("protocoltracker-backup-2", Backup.FORMAT)
    }
}
