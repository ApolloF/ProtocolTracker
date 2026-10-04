package com.apollof.protocoltracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.pk.LabUnits
import java.time.Instant
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Settings in a backup: every stored kind comes back, and unknown, mistyped or out-of-range values read as defaults. */
@RunWith(AndroidJUnit4::class)
class SettingsBackupTest {
    private val store get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container.settings

    @Test
    fun settingsRoundTrip() = runBlocking {
        store.update {
            it.copy(
                snoozeMinutes = 30, pureBlack = true, syringeUnits = true, labUnits = LabUnits.SI,
                slotTimes = it.slotTimes.copy(dayStart = LocalTime.of(2, 0)),
            )
        }
        val saved = store.current()
        val map = store.exportMap()
        store.update { it.copy(snoozeMinutes = 60, pureBlack = false, syringeUnits = false, labUnits = LabUnits.CONVENTIONAL) }

        store.importMap(map)
        assertEquals(saved, store.current())
    }

    @Test
    fun badValuesReadAsDefaults() = runBlocking {
        val defaults = store.current()
        store.importMap(
            mapOf(
                "snooze_minutes" to "0",
                "pure_black" to "maybe",
                "day_start" to "13:00",
                "unknown_key" to "x",
                "lab_units" to "PARSECS",
            ),
        )
        val read = store.current()
        assertEquals(SettingsStore.SNOOZE_RANGE.first, read.snoozeMinutes)
        assertEquals(defaults.pureBlack, read.pureBlack)
        assertEquals(defaults.slotTimes.dayStart, read.slotTimes.dayStart)
        assertEquals(defaults.labUnits, read.labUnits)
        assertFalse("unknown_key" in store.exportMap())
    }

    /** Review 2026-10, F6: the first-run notice is device state; a backup neither carries nor clears it. */
    @Test
    fun theAcknowledgedNoticeIsNotExportedAndSurvivesARestore() = runBlocking {
        assertFalse(store.noticeAcknowledged.first())
        store.acknowledgeNotice(Instant.parse("2026-10-04T08:00:00Z"))
        assertTrue(store.noticeAcknowledged.first())
        val map = store.exportMap()
        assertFalse(map.keys.any { "notice" in it }, map.toString())

        store.importMap(map)
        assertTrue(store.noticeAcknowledged.first())
        store.importMap(emptyMap())
        assertTrue(store.noticeAcknowledged.first())
    }
}
