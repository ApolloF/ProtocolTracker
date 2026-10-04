package com.apollof.protocoltracker.ui.plan

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.FossGatedApp
import com.apollof.protocoltracker.PlayGatedApp
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.awaitMain
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.dueReminders
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))

private fun ProtocolTrackerApp.seed(on: Int, off: Int = 0) = runBlocking {
    container.repository.seedPresets()
    val compound = container.repository.protocolNow().compounds.keys.first { it.startsWith("preset:") }
    val start = LocalDate.now().minusDays(3)
    (1..on).forEach { container.repository.saveItem(PlanItem("on$it", null, compound, Amount(1.0, DoseUnit.MG), schedule = daily, startDate = start, sortOrder = it)) }
    (1..off).forEach { container.repository.saveItem(PlanItem("off$it", null, compound, Amount(1.0, DoseUnit.MG), schedule = daily, startDate = start, enabled = false)) }
}

private fun ProtocolTrackerApp.items() = runBlocking { container.repository.protocolNow().items }

/** Opens the editor for [itemId] (null: a new item, with a compound and a dose) and saves the draft after [edit]. */
private fun ProtocolTrackerApp.saveInEditor(itemId: String?, edit: (ItemDraft) -> ItemDraft = { it }): Pair<Boolean, Feature?> {
    val vm = ItemEditorViewModel(container, itemId, phaseId = null)
    awaitMain("editor") { vm.loaded.value && vm.compounds.value.isNotEmpty() && (itemId != null || vm.draft.value.sortOrder != 0) }
    if (itemId == null) {
        vm.selectCompound(vm.compounds.value.first())
        vm.edit { it.copy(doseText = "2") }
    }
    vm.edit(edit)
    var done = false
    vm.save { done = true }
    awaitMain("save") { done || vm.paywall.value != null }
    val result = done to vm.paywall.value
    vm.viewModelScope.cancel()
    return result
}

/** The Play cap on plan items switched on: a sixth is blocked, items already on (also from a restore) never are. */
@RunWith(AndroidJUnit4::class)
@Config(application = PlayGatedApp::class)
class ActiveItemCapTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Before
    fun noPro() = assertTrue(app.container.gate.sellsPro)

    @Test
    fun aSixthActiveItemOpensThePaywallAndIsNotSaved() {
        app.seed(on = 5)
        assertEquals(false to Feature.ACTIVE_PLAN_ITEMS, app.saveInEditor(null))
        assertEquals(5, app.items().size)
    }

    @Test
    fun editingPausingAndPausedNewItemsAreAllowed() {
        app.seed(on = 5, off = 1)
        assertEquals(true to null, app.saveInEditor("on2") { it.copy(doseText = "3") })
        assertEquals(3.0, app.items().first { it.id == "on2" }.dose.value)
        // Switching the paused one on would be the sixth.
        assertEquals(false to Feature.ACTIVE_PLAN_ITEMS, app.saveInEditor("off1") { it.copy(enabled = true) })
        assertEquals(false, app.items().first { it.id == "off1" }.enabled)
        // A new item saved paused is fine.
        assertEquals(true to null, app.saveInEditor(null) { it.copy(enabled = false) })
        // Pausing one frees a place.
        assertEquals(true to null, app.saveInEditor("on1") { it.copy(enabled = false) })
        assertEquals(true to null, app.saveInEditor("off1") { it.copy(enabled = true) })
        assertEquals(5, app.items().count { it.enabled })
    }

    @Test
    fun aRestoredPlanKeepsEveryItemOnWithReminders() {
        app.seed(on = 8)
        val backup = runBlocking { app.container.repository.exportBackup() }
        runBlocking {
            app.items().forEach { app.container.repository.deleteItem(it.id) }
            app.container.repository.restoreBackup(backup)
        }
        val items = app.items()
        assertEquals(8, items.count { it.enabled && it.remind })
        // Each of them is reminded today: the gate never touches reminders.
        val now = Instant.now()
        val due = runBlocking {
            val p = app.container.repository.protocolNow()
            dueReminders(p.phases, p.items, emptySet(), now.minus(Duration.ofDays(2)), now, app.container.zone(), IntervalAnchors.NONE)
        }
        assertEquals(items.map { it.id }.toSet(), due.map { it.item.id }.toSet())
        // Editing one of them is fine; a ninth is not.
        assertEquals(true to null, app.saveInEditor("on7") { it.copy(notes = "kept") })
        assertEquals(false to Feature.ACTIVE_PLAN_ITEMS, app.saveInEditor(null))
        assertEquals(8, app.items().count { it.enabled })
    }
}

/** The foss build has no cap. */
@RunWith(AndroidJUnit4::class)
@Config(application = FossGatedApp::class)
class ActiveItemNoCapTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Test
    fun aSixthActiveItemIsSaved() {
        app.seed(on = 5)
        val (done, paywall) = app.saveInEditor(null)
        assertTrue(done)
        assertNull(paywall)
        assertEquals(6, app.items().count { it.enabled })
    }
}
