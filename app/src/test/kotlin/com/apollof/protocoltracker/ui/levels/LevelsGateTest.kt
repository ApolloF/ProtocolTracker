package com.apollof.protocoltracker.ui.levels

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.FossGatedApp
import com.apollof.protocoltracker.PlayGatedApp
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.awaitMain
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.entitlement.LEVELS_FEATURES
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.LevelMode
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two compounds in use (testosterone, anastrozole) and a paused one (letrozole); all three are Play presets. */
private fun ProtocolTrackerApp.seedPlan(withLab: Boolean = false) = runBlocking {
    container.repository.seedPresets()
    val start = LocalDate.now().minusDays(40)
    val daily = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))
    container.repository.saveItem(PlanItem("t", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), daily, startDate = start))
    container.repository.saveItem(PlanItem("a", null, "preset:anastrozole", Amount(0.5, DoseUnit.MG), schedule = daily, startDate = start))
    container.repository.saveItem(PlanItem("l", null, "preset:letrozole", Amount(2.5, DoseUnit.MG), schedule = daily, startDate = start, enabled = false))
    val testC = container.repository.protocolNow().compounds.getValue("preset:test-cyp")
    container.repository.logUnscheduled(testC, Amount(125.0, DoseUnit.MG), testC.defaultFormulation, Instant.now().minus(Duration.ofDays(2)))
    if (withLab) {
        val drawn = Instant.now().minus(Duration.ofDays(1))
        container.repository.saveJournal(JournalEntry.Bloodwork("b", drawn, listOf(MarkerResult("total_testosterone", 1100.0)), createdAt = drawn))
    }
}

/** The Levels trial ended (started 20 days ago). */
private fun ProtocolTrackerApp.trialEnded() = runBlocking {
    container.settings.startTrials(LEVELS_FEATURES, Instant.now().minus(Duration.ofDays(20)))
}

private fun ProtocolTrackerApp.group(compoundId: String) = runBlocking { container.repository.protocolNow().compounds.getValue(compoundId).group }

/** A view model whose state is collected, as on screen. */
private fun ProtocolTrackerApp.levels(focus: String? = null): LevelsViewModel {
    val vm = LevelsViewModel(container, focus)
    vm.viewModelScope.launch { vm.state.collect {} }
    awaitMain("levels") { !vm.state.value.loading && vm.state.value.views.isNotEmpty() }
    return vm
}

private val day = Duration.ofDays(1).toMillis()

@RunWith(AndroidJUnit4::class)
@Config(application = PlayGatedApp::class)
class LevelsGateTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    private fun trials() = runBlocking { app.container.settings.trialStarts.first() }

    @Test
    fun openingLevelsStartsTheTrialOnce() {
        app.seedPlan()
        assertTrue(trials().isEmpty())
        val first = app.levels()
        awaitMain("trial start") { trials().keys == LEVELS_FEATURES }
        val started = trials()
        // During the trial everything is open, and the banner says when it ends.
        assertEquals(started.values.min().plus(Duration.ofDays(14)), first.state.value.access.trialEndsAt)
        assertEquals(3, first.state.value.current.size + first.state.value.others.size)
        assertEquals(2, first.state.value.views.size)
        first.viewModelScope.cancel()

        Thread.sleep(30)
        val second = app.levels(focus = app.group("preset:test-cyp"))
        // Its start would have been written by now.
        Thread.sleep(300)
        awaitMain("idle") { true }
        assertEquals(started, trials())
        second.setRange(LevelRange.Y1)
        awaitMain("year") { second.state.value.window.range == LevelRange.Y1 }
        assertNull(second.paywall.value)
        second.viewModelScope.cancel()
    }

    @Test
    fun afterTheTrialLongRangesOpenThePaywallAndTheWindowStaysInTwoWeeks() {
        app.seedPlan()
        app.trialEnded()
        val vm = app.levels()
        val s = vm.state.value
        assertNull(s.access.trialEndsAt)
        assertEquals(14, s.access.maxDays)
        // The default month falls back to two weeks.
        assertEquals(LevelRange.W2, s.window.range)

        vm.setRange(LevelRange.M3)
        awaitMain("paywall") { vm.paywall.value != null }
        assertEquals(Feature.LEVELS_RANGE, vm.paywall.value)
        assertEquals(LevelRange.W2, vm.state.value.window.range)
        vm.dismissPaywall()

        // Panning far back and zooming out stop at 14 days before now.
        repeat(20) { vm.pan(-1f) }
        vm.zoom(0.1f)
        awaitMain("clamped window") { vm.state.value.window.centerOffsetMs < 0 }
        val w = vm.state.value
        assertTrue(w.toMs - w.fromMs <= 14 * day + 1, "span ${(w.toMs - w.fromMs) / day} days")
        assertTrue(w.fromMs >= w.nowMs - 14 * day - 60_000, "from ${(w.nowMs - w.fromMs) / day} days back")
        vm.viewModelScope.cancel()
    }

    @Test
    fun afterTheTrialTheOverviewShowsOneChartAtATime() {
        app.seedPlan()
        app.trialEnded()
        val vm = app.levels()
        val t = app.group("preset:test-cyp")
        val a = app.group("preset:anastrozole")
        val l = app.group("preset:letrozole")
        assertTrue(vm.state.value.oneAtATime)
        assertEquals(listOf(t), vm.state.value.views.map { it.name })

        vm.select(a)
        awaitMain("anastrozole") { vm.state.value.views.map { it.name } == listOf(a) }
        // A chip of a compound not in use now switches the chart too, and shows as the selected one.
        vm.toggleOther(l)
        awaitMain("letrozole") { vm.state.value.views.map { it.name } == listOf(l) }
        assertEquals(setOf(l), vm.state.value.opened)

        vm.showPaywall(Feature.LEVELS_MULTI_COMPOUND)
        assertEquals(Feature.LEVELS_MULTI_COMPOUND, vm.paywall.value)
        vm.viewModelScope.cancel()
    }

    @Test
    fun afterTheTrialTheDetailKeepsLoggedPlusPlan() {
        app.seedPlan()
        app.trialEnded()
        val vm = app.levels(focus = app.group("preset:test-cyp"))
        assertEquals(1, vm.state.value.views.size)
        vm.setMode(LevelMode.RECORDED)
        awaitMain("paywall") { vm.paywall.value != null }
        assertEquals(Feature.LEVELS_PLANNED_VS_LOGGED, vm.paywall.value)
        assertEquals(LevelMode.COMBINED, vm.state.value.window.mode)
        vm.dismissPaywall()
        vm.setMode(LevelMode.PLANNED)
        awaitMain("paywall") { vm.paywall.value != null }
        assertEquals(LevelMode.COMBINED, vm.state.value.window.mode)
        vm.viewModelScope.cancel()
    }

    @Test
    fun afterTheTrialLabResultsAreLeftOffTheCurve() {
        app.seedPlan(withLab = true)
        app.trialEnded()
        val vm = app.levels(focus = app.group("preset:test-cyp"))
        val view = vm.state.value.views.single()
        assertTrue(view.measured.isEmpty())
        assertTrue(view.hiddenLabs)
        vm.viewModelScope.cancel()
    }

    @Test
    fun duringTheTrialLabResultsAreDrawn() {
        app.seedPlan(withLab = true)
        val vm = app.levels(focus = app.group("preset:test-cyp"))
        val view = vm.state.value.views.single()
        assertEquals(1, view.measured.size)
        assertFalse(view.hiddenLabs)
        vm.viewModelScope.cancel()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = FossGatedApp::class)
class LevelsUngatedTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Test
    fun everythingIsOpenWithoutATrial() {
        app.seedPlan(withLab = true)
        app.trialEnded()
        val vm = app.levels()
        val s = vm.state.value
        assertEquals(LevelsAccess(), s.access)
        assertFalse(s.oneAtATime)
        assertEquals(2, s.views.size)
        vm.setRange(LevelRange.Y1)
        awaitMain("year") { vm.state.value.window.range == LevelRange.Y1 }
        repeat(5) { vm.pan(-1f) }
        awaitMain("panned") { vm.state.value.fromMs < vm.state.value.nowMs - 400 * day }
        assertNull(vm.paywall.value)
        vm.viewModelScope.cancel()

        val detail = app.levels(focus = app.group("preset:test-cyp"))
        detail.setMode(LevelMode.RECORDED)
        awaitMain("mode") { detail.state.value.window.mode == LevelMode.RECORDED }
        assertNotNull(detail.state.value.views.single().measured.singleOrNull())
        assertNull(detail.paywall.value)
        detail.viewModelScope.cancel()
    }
}
