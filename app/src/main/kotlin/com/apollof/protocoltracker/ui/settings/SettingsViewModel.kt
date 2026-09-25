package com.apollof.protocoltracker.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.domain.io.BackupCodec
import com.apollof.protocoltracker.domain.io.ImportResult
import com.apollof.protocoltracker.domain.io.LegacyImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val text = BackupCodec.encode(c.repository.exportBackup())
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
