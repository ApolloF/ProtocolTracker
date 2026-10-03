package com.apollof.protocoltracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Settings in a backup: every stored kind comes back, and unknown, mistyped or out-of-range values read as defaults. */
@RunWith(AndroidJUnit4::class)
class SettingsBackupTest {
    private val store get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container.settings

    @Test
    fun settingsRoundTripIncludingSets() = runBlocking {
        store.update {
            it.copy(
                snoozeMinutes = 30, pureBlack = true, compareExcluded = setOf("preset:test-cyp", "custom:1"),
                slotTimes = it.slotTimes.copy(dayStart = LocalTime.of(2, 0)),
            )
        }
        val saved = store.current()
        val map = store.exportMap()
        store.update { it.copy(snoozeMinutes = 60, pureBlack = false, compareExcluded = emptySet()) }

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
                "compare_excluded" to "",
            ),
        )
        val read = store.current()
        assertEquals(SettingsStore.SNOOZE_RANGE.first, read.snoozeMinutes)
        assertEquals(defaults.pureBlack, read.pureBlack)
        assertEquals(defaults.slotTimes.dayStart, read.slotTimes.dayStart)
        assertEquals(emptySet(), read.compareExcluded)
        assertFalse("unknown_key" in store.exportMap())
    }
}
