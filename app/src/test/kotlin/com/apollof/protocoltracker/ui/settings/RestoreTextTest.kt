package com.apollof.protocoltracker.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.domain.io.Backup
import com.apollof.protocoltracker.ui.components.Formats
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** The restore question names the local day the backup was saved (dev); stable keeps its UTC date. */
@RunWith(AndroidJUnit4::class)
class RestoreTextTest {
    @Test
    fun aBackupSavedAfterMidnightNamesTheLocalDay() {
        // 01:33 in Amsterdam on Oct 2 is 23:33 UTC on Oct 1.
        val backup = Backup(exportedAt = Instant.parse("2026-10-01T23:33:00Z"), compounds = emptyList(), phases = emptyList(), items = emptyList(), logs = emptyList())
        val text = restoreText(backup, ZoneId.of("Europe/Amsterdam"))
        val expected = if (BuildConfig.DEV_FEATURES) {
            "The backup from ${LocalDate.of(2026, 10, 2).format(Formats.date)} has 0 phases, 0 plan items, 0 logged doses and 0 journal entries. " +
                "Current data on this device is replaced."
        } else {
            "The backup from 2026-10-01 has 0 phases, 0 plan items, 0 logged doses and 0 journal entries. Current data on this device is replaced."
        }
        assertEquals(expected, text)
    }
}
