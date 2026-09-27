package com.apollof.protocoltracker.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.BackupCodec
import com.apollof.protocoltracker.domain.io.HtmlReport
import com.apollof.protocoltracker.domain.io.ImportResult
import com.apollof.protocoltracker.domain.io.MarkdownReport
import com.apollof.protocoltracker.domain.io.ReportBuilder
import com.apollof.protocoltracker.domain.io.WebExportImport
import com.apollof.protocoltracker.domain.io.WebImport as WebHistory
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

    /** Dev only: history from the web app's full export or its AI-review file. */
    data class WebImport(val result: WebHistory) : PendingData
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

    /** A cycletracker-1 export; in the dev build also the web app's export ([readWebExport]). */
    fun readLegacy(uri: Uri) = report {
        val text = read(uri)
        if (BuildConfig.DEV_FEATURES && WebExportImport.matches(text)) return@report readWebExport(text)
        val existing = c.repository.compounds.first()
        pending.value = PendingData.Import(LegacyImport.parse(text, c.zone(), existing))
        null
    }

    /** Asks before saving the web app's history; a file with nothing new gives a message and no dialog. */
    private suspend fun readWebExport(text: String): String? {
        val result = WebExportImport.parse(text, c.zone(), c.repository.journalNow())
        if (result.entries.isEmpty()) return "Nothing new to import from this file."
        pending.value = PendingData.WebImport(result)
        return null
    }

    fun confirm() {
        when (val p = pending.value ?: return) {
            is PendingData.Restore -> io("Backup restored") { c.repository.restoreBackup(p.backup); c.repository.seedPresets() }
            is PendingData.Import -> io("Import complete") { c.repository.applyImport(p.result) }
            is PendingData.WebImport -> {
                val n = p.result.entries.size
                io("Imported $n ${if (n == 1) "entry" else "entries"}") { c.repository.saveJournal(p.result.entries) }
            }
        }
        pending.value = null
    }

    fun dismiss() { pending.value = null }

    private fun read(uri: Uri): String =
        resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Could not open file")

    private fun io(success: String?, block: suspend () -> Unit) = report { block(); success }

    /** Runs [block] off the main thread, then shows the message it returns, or the error. */
    private fun report(block: suspend () -> String?) = viewModelScope.launch {
        message.value = try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: Exception) {
            e.message ?: "Operation failed"
        }
    }
}
