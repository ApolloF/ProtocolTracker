package com.apollof.protocoltracker.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.available
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.BackupCodec
import com.apollof.protocoltracker.domain.io.HtmlReport
import com.apollof.protocoltracker.domain.io.ImportResult
import com.apollof.protocoltracker.domain.io.MarkdownReport
import com.apollof.protocoltracker.domain.io.ReportBuilder
import com.apollof.protocoltracker.domain.io.ReportPeriod
import com.apollof.protocoltracker.domain.io.WebExportImport
import com.apollof.protocoltracker.domain.io.WebImport as WebHistory
import com.apollof.protocoltracker.domain.model.shownAt
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.io.LegacyImport
import com.apollof.protocoltracker.domain.schedule.dateOf
import com.apollof.protocoltracker.domain.schedule.trackedFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class ReportRange(val label: String) { ALL("All"), DAYS_30("30 d"), DAYS_90("90 d"), CURRENT_PHASE("Phase"), CUSTOM("Custom") }

/** The report's range; [from] and [to] are the custom dates, set when Custom is first chosen. */
data class ReportChoice(val range: ReportRange = ReportRange.ALL, val from: LocalDate? = null, val to: LocalDate? = null, val today: LocalDate? = null) {
    /** Why the custom dates cannot make a report, or null. */
    val problem: String?
        get() = if (range == ReportRange.CUSTOM && from != null && to != null && today != null) ReportPeriod.customProblem(from, to, today) else null

    /**
     * The days the report covers: from the first entry ([earliest]) for All, from the current phase's start
     * ([phaseStart]) for Phase, the picked dates for Custom (null when they do not make a period).
     */
    fun period(earliest: LocalDate?, phaseStart: LocalDate?, today: LocalDate): ReportPeriod? = when (range) {
        ReportRange.ALL -> ReportPeriod.all(earliest, today)
        ReportRange.DAYS_30 -> ReportPeriod.lastDays(30, today)
        ReportRange.DAYS_90 -> ReportPeriod.lastDays(90, today)
        ReportRange.CURRENT_PHASE -> ReportPeriod.currentPhase(phaseStart, today)
        ReportRange.CUSTOM -> ReportPeriod.custom(from ?: today.minusDays(29), to ?: today, today)
    }
}

/** A pending destructive/merging data operation awaiting confirmation. */
sealed interface PendingData {
    data class Restore(val backup: Backup) : PendingData
    data class Import(val result: ImportResult) : PendingData

    /** History from the web app's full export or its AI-review file. */
    data class WebImport(val result: WebHistory) : PendingData
}

class SettingsViewModel(private val c: AppContainer, private val resolver: ContentResolver) : ViewModel() {
    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val pending = MutableStateFlow<PendingData?>(null)
    val message = MutableStateFlow<String?>(null)
    val report = MutableStateFlow(ReportChoice())
    /** The feature whose paywall is open (Custom reports without Pro). */
    val paywall = MutableStateFlow<Feature?>(null)

    fun update(transform: (Settings) -> Settings) = viewModelScope.launch { c.settings.update(transform) }

    fun canScheduleExact(): Boolean = c.reminders.canScheduleExact()

    fun export(uri: Uri) = io("Backup saved") {
        write(uri, BackupCodec.encode(c.repository.exportBackup().copy(settings = c.settings.exportMap())))
    }

    /** Picks the report range; Custom opens the paywall when the gate limits custom reports (the other ranges are free). */
    fun setReportRange(range: ReportRange) = viewModelScope.launch {
        if (range == ReportRange.CUSTOM && !c.gate.resolveNow(Feature.CUSTOM_REPORTS).available) {
            paywall.value = Feature.CUSTOM_REPORTS
            return@launch
        }
        val today = c.settings.current().slotTimes.dateOf(c.clock(), c.zone())
        report.update { it.copy(range = range, from = it.from ?: today.minusDays(29), to = it.to ?: today, today = today) }
    }

    fun setReportFrom(date: LocalDate) = report.update { it.copy(from = date) }
    fun setReportTo(date: LocalDate) = report.update { it.copy(to = date) }
    fun dismissPaywall() { paywall.value = null }

    /** Human-readable (HTML) or AI-friendly (Markdown) report of everything in the chosen range ([report]). */
    fun exportReport(uri: Uri, markdown: Boolean) = io("Report saved") {
        val choice = report.value
        val protocol = c.repository.protocolNow()
        val logs = c.repository.allLogsNow()
        val journal = c.repository.journalNow()
        val zone = c.zone()
        val now = c.clock()
        val slotTimes = c.settings.current().slotTimes
        val today = slotTimes.dateOf(now, zone)
        val earliest = (logs.map { it.shownAt } + journal.map { it.at }).minOrNull()?.let { slotTimes.dateOf(it, zone) }
            ?: protocol.phases.minOfOrNull { it.startDate }
        val period = choice.period(earliest, PhaseTimeline(protocol.phases).phaseOn(today)?.startDate, today)
            ?: error(choice.problem ?: "Choose the report dates")
        // Missed doses and adherence count from the first dose log (MISS-1); with no log yet, from today.
        val countFrom = trackedFrom(logs, today, zone, slotTimes)
        val built = ReportBuilder.build(protocol, logs, journal, period.from, period.to, now, zone, slotTimes, countFrom = countFrom)
        write(uri, if (markdown) MarkdownReport.render(built) else HtmlReport.render(built))
    }

    private fun write(uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Could not open file")
    }

    fun readBackup(uri: Uri) = io(null) {
        pending.value = PendingData.Restore(BackupCodec.decode(read(uri)))
    }

    /** A cycletracker-1 export, or the web app's export ([readWebExport]). */
    fun readLegacy(uri: Uri) = report {
        val text = read(uri)
        if (WebExportImport.matches(text)) return@report readWebExport(text)
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
            is PendingData.Restore -> io("Backup restored") {
                c.repository.restoreBackup(p.backup)
                c.repository.seedPresets()
                p.backup.settings?.let { c.settings.importMap(it) }
            }
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
