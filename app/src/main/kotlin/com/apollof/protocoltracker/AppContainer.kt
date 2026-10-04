package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.billing.Entitlements
import com.apollof.protocoltracker.billing.FeatureGate
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.reminders.ReminderScheduler
import com.apollof.protocoltracker.ui.journal.JournalFocus
import java.time.Instant
import java.time.ZoneId

/**
 * Manual dependency graph; one instance per process, owned by [ProtocolTrackerApp]. Tests pass a [policy] and
 * [entitlementsOf] to check either flavour's tiers.
 */
class AppContainer(
    context: Context,
    val clock: () -> Instant = Instant::now,
    policy: TablePolicy = Distribution.policy,
    entitlementsOf: (Context) -> Entitlements = Distribution::entitlements,
) {
    private val appContext = context.applicationContext
    val zone: () -> ZoneId = ZoneId::systemDefault
    val database = TrackerDatabase.create(appContext)
    /** The flavour's presets: all in foss, the reviewed allowlist in play (Distribution). */
    val repository = TrackerRepository(database, zone, Distribution.presetChannel, clock)
    /** A day starts at 4:00, so a dose taken after midnight before bed counts for the day before. */
    val settings = SettingsStore(appContext)
    val entitlements: Entitlements = entitlementsOf(appContext)
    /** Free, Pro or trial per feature; everything is free in foss. */
    val gate = FeatureGate(policy, entitlements, settings, clock)
    val doseActions = DoseActions(appContext, repository, settings, clock, zone)
    val reminders = ReminderScheduler(appContext, repository, settings, clock, zone)
    val journalFocus = JournalFocus()
}

val Context.container: AppContainer get() = (applicationContext as ProtocolTrackerApp).container
