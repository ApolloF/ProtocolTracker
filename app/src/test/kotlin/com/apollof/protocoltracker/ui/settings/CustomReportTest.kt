package com.apollof.protocoltracker.ui.settings

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.FossGatedApp
import com.apollof.protocoltracker.PlayGatedApp
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.awaitMain
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.domain.io.ReportPeriod
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The period each report range covers. */
class ReportChoiceTest {
    private val today = LocalDate.of(2026, 10, 4)
    private val earliest = LocalDate.of(2026, 1, 10)
    private val phaseStart = LocalDate.of(2026, 9, 1)

    private fun period(choice: ReportChoice) = choice.period(earliest, phaseStart, today)

    @Test
    fun eachRangeCoversItsDays() {
        assertEquals(ReportPeriod(earliest, today), period(ReportChoice(ReportRange.ALL)))
        assertEquals(ReportPeriod(today.minusDays(29), today), period(ReportChoice(ReportRange.DAYS_30)))
        assertEquals(ReportPeriod(today.minusDays(89), today), period(ReportChoice(ReportRange.DAYS_90)))
        assertEquals(ReportPeriod(phaseStart, today), period(ReportChoice(ReportRange.CURRENT_PHASE)))
        val custom = ReportChoice(ReportRange.CUSTOM, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), today)
        assertEquals(ReportPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), period(custom))
        assertNull(custom.problem)
    }

    @Test
    fun customDatesAreCheckedAndClampedToToday() {
        val reversed = ReportChoice(ReportRange.CUSTOM, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1), today)
        assertNull(period(reversed))
        assertNotNull(reversed.problem)
        val ahead = ReportChoice(ReportRange.CUSTOM, LocalDate.of(2026, 9, 20), today.plusDays(30), today)
        assertEquals(ReportPeriod(LocalDate.of(2026, 9, 20), today), period(ahead))
    }
}

private fun ProtocolTrackerApp.pickCustom(): SettingsViewModel {
    val vm = SettingsViewModel(container, contentResolver)
    vm.setReportRange(ReportRange.CUSTOM)
    awaitMain("custom or paywall") { vm.report.value.range == ReportRange.CUSTOM || vm.paywall.value != null }
    return vm
}

/** Play: Custom opens the paywall; the fixed ranges stay free. */
@RunWith(AndroidJUnit4::class)
@Config(application = PlayGatedApp::class)
class CustomReportGateTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Test
    fun customOpensThePaywall() {
        val vm = app.pickCustom()
        assertEquals(Feature.CUSTOM_REPORTS, vm.paywall.value)
        assertEquals(ReportRange.ALL, vm.report.value.range)
        vm.dismissPaywall()
        vm.setReportRange(ReportRange.DAYS_90)
        awaitMain("90 days") { vm.report.value.range == ReportRange.DAYS_90 }
        assertNull(vm.paywall.value)
        vm.viewModelScope.cancel()
    }
}

/** Foss: Custom works, starting with the last 30 days. */
@RunWith(AndroidJUnit4::class)
@Config(application = FossGatedApp::class)
class CustomReportOpenTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()

    @Test
    fun customStartsWithTheLastThirtyDays() {
        val vm = app.pickCustom()
        assertNull(vm.paywall.value)
        val choice = vm.report.value
        val today = assertNotNull(choice.today)
        assertEquals(today.minusDays(29), choice.from)
        assertEquals(today, choice.to)
        vm.setReportTo(today.minusDays(40))
        assertNotNull(vm.report.value.problem)
        vm.setReportFrom(today.minusDays(45))
        assertNull(vm.report.value.problem)
        vm.viewModelScope.cancel()
    }
}
