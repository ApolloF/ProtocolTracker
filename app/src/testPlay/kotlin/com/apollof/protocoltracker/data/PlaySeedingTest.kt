package com.apollof.protocoltracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.pk.PLAY_PRESET_IDS
import com.apollof.protocoltracker.domain.pk.Presets
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Play build seeds its 45 presets and keeps any other preset already stored (a restored foss backup). */
@RunWith(AndroidJUnit4::class)
@Config(application = ProtocolTrackerApp::class)
class PlaySeedingTest {
    private val repo get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container.repository

    @Test
    fun emptyDatabaseGetsExactlyThePlayPresets() = runBlocking {
        repo.seedPresets()
        val ids = repo.compounds.first().map { it.id.removePrefix("preset:") }.toSet()
        assertEquals(PLAY_PRESET_IDS, ids)
        assertEquals(45, ids.size)
    }

    @Test
    fun presetFromAnotherBuildStays() = runBlocking {
        repo.seedPresets()
        repo.saveCompound(Presets.byId("preset:tren-enan")!!)
        repo.seedPresets()
        val tren = repo.compounds.first().single { it.id == "preset:tren-enan" }
        assertFalse(tren.archived)
        assertEquals(Presets.byId("preset:tren-enan")!!.pk, tren.pk)
        assertEquals(46, repo.compounds.first().size)
    }
}
