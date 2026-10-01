package com.apollof.protocoltracker.ui.plan

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import kotlin.test.assertEquals

/** Dev: a new plan item starts today, so Today never lists the days before it was added as missed. */
@RunWith(AndroidJUnit4::class)
class NewItemStartTest {
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Test
    fun aNewItemStartsTodayInDevAndOpenInStable() {
        val vm = ItemEditorViewModel(container, itemId = null, phaseId = null)
        val deadline = System.currentTimeMillis() + 10_000
        while (!vm.loaded.value || vm.draft.value.sortOrder == 0) {
            check(System.currentTimeMillis() < deadline) { "editor did not load" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        val today = LocalDate.now(container.zone())
        assertEquals(if (BuildConfig.DEV_FEATURES) today else null, vm.draft.value.startDate)
    }
}
