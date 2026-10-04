package com.apollof.protocoltracker.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.pk.Presets
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The foss build seeds all 78 presets with their common names. */
@RunWith(AndroidJUnit4::class)
@Config(application = ProtocolTrackerApp::class)
class FossSeedingTest {
    private val repo get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container.repository

    @Test
    fun seedsEveryPreset() = runBlocking {
        repo.seedPresets()
        val all = repo.compounds.first()
        assertEquals(78, all.size)
        assertEquals(Presets.all.map { it.id }.toSet(), all.map { it.id }.toSet())
        assertEquals("Test E", all.single { it.id == "preset:test-enan" }.commonName)
    }
}
