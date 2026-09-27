package com.apollof.protocoltracker.ui.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings › Export and data › Import CycleTracker export with the web app's full export. */
@RunWith(AndroidJUnit4::class)
class WebHistoryImportTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
    private val journal get() = runBlocking { app.container.repository.journalNow() }

    @Test
    fun devAsksThenImportsOnce() {
        if (!BuildConfig.DEV_FEATURES) return
        val vm = SettingsViewModel(app.container, app.contentResolver)
        val file = WebExportSample.uri()
        vm.readLegacy(file)
        val result = assertIs<PendingData.WebImport>(awaitMain { vm.pending.value }).result
        assertEquals(listOf(2, 1, 2, 1), listOf(result.bloodPressure, result.notes, result.symptoms, result.draws))
        assertEquals(0, result.alreadyThere)
        assertTrue(journal.isEmpty(), "nothing saved before Import")

        vm.confirm()
        assertEquals("Imported ${WebExportSample.ENTRIES} entries", awaitMain { vm.message.value })
        assertNull(vm.pending.value)
        assertEquals(result.entries.toSet(), journal.toSet())

        vm.message.value = null
        vm.readLegacy(file)
        assertEquals("Nothing new to import from this file.", awaitMain { vm.message.value })
        assertNull(vm.pending.value)
        assertEquals(WebExportSample.ENTRIES, journal.size)
    }

    @Test
    fun devCancelSavesNothing() {
        if (!BuildConfig.DEV_FEATURES) return
        val vm = SettingsViewModel(app.container, app.contentResolver)
        vm.readLegacy(WebExportSample.uri())
        awaitMain { vm.pending.value }
        vm.dismiss()
        assertNull(vm.pending.value)
        assertTrue(journal.isEmpty())
    }

    @Test
    fun stableRejectsTheWebExport() {
        if (BuildConfig.DEV_FEATURES) return
        val vm = SettingsViewModel(app.container, app.contentResolver)
        vm.readLegacy(WebExportSample.uri())
        assertEquals("Unsupported export format; expected cycletracker-1", awaitMain { vm.message.value })
        assertNull(vm.pending.value)
        assertTrue(journal.isEmpty())
    }
}
