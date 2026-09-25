package com.apollof.protocoltracker.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.BackupCodec
import com.apollof.protocoltracker.domain.io.HtmlReport
import com.apollof.protocoltracker.domain.io.ImportResult
import com.apollof.protocoltracker.domain.io.MarkdownReport
import com.apollof.protocoltracker.domain.io.ReportBuilder
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.io.LegacyImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportRange(val label: String) { ALL("All"), DAYS_30("30 d"), DAYS_90("90 d"), CURRENT_PHASE("Phase") }

/** A pending destructive/merging data operation awaiting confirmation. */
sealed interface PendingData {
    data class Restore(val backup: Backup) : PendingData
    data class Import(val result: ImportResult) : PendingData
}

class SettingsViewModel(private val c: AppContainer, private val resolver: ContentResolver) : ViewModel() {
    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val pending = MutableStateFlow<PendingData?>(null)
    val message = MutableStateFlow<String?>(null)

    fun update(transform: (Settings) -> Settings) = viewModelScope.launch { c.settings.update(transform) }

    fun canScheduleExact(): Boolean = c.reminders.canScheduleExact()

    fun export(uri: Uri) = io("Backup saved") {
        write(uri, BackupCodec.encode(c.repository.exportBackup()))
    }

    /** Human-readable (HTML) or AI-friendly (Markdown) report of everything in [range]. */
    fun exportReport(uri: Uri, range: ReportRange, markdown: Boolean) = io("Report saved") {
        val protocol = c.repository.protocolNow()
        val logs = c.repository.allLogsNow()
        val journal = c.repository.journalNow()
        val zone = c.zone()
        val now = c.clock()
        val today = now.atZone(zone).toLocalDate()
        val earliest = (logs.map { it.takenAt } + journal.map { it.at }).minOrNull()?.atZone(zone)?.toLocalDate()
            ?: protocol.phases.minOfOrNull { it.startDate } ?: today
        val from = when (range) {
            ReportRange.ALL -> minOf(earliest, today)
            ReportRange.DAYS_30 -> today.minusDays(29)
            ReportRange.DAYS_90 -> today.minusDays(89)
            ReportRange.CURRENT_PHASE -> PhaseTimeline(protocol.phases).phaseOn(today)?.startDate ?: today.minusDays(29)
        }
        val report = ReportBuilder.build(protocol, logs, journal, from, today, now, zone, c.settings.current().slotTimes)
        write(uri, if (markdown) MarkdownReport.render(report) else HtmlReport.render(report))
    }

    private fun write(uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Could not open file")
    }

    fun readBackup(uri: Uri) = io(null) {
        pending.value = PendingData.Restore(BackupCodec.decode(read(uri)))
    }

    fun readLegacy(uri: Uri) = io(null) {
        val existing = c.repository.compounds.first()
        pending.value = PendingData.Import(LegacyImport.parse(read(uri), c.zone(), existing))
    }

    fun confirm() {
        when (val p = pending.value ?: return) {
            is PendingData.Restore -> io("Backup restored") { c.repository.restoreBackup(p.backup); c.repository.seedPresets() }
            is PendingData.Import -> io("Import complete") { c.repository.applyImport(p.result) }
        }
        pending.value = null
    }

    fun dismiss() { pending.value = null }

    private fun read(uri: Uri): String =
        resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Could not open file")

    private fun io(success: String?, block: suspend () -> Unit) = viewModelScope.launch {
        message.value = try {
            withContext(Dispatchers.IO) { block() }
            success
        } catch (e: Exception) {
            e.message ?: "Operation failed"
        }
    }
}
