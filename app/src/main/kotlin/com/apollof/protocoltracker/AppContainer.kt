package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.billing.Entitlements
import com.apollof.protocoltracker.billing.FeatureGate
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.domain.entitlement.TablePolicy
import com.apollof.protocoltracker.domain.pk.PresetChannel
import com.apollof.protocoltracker.reminders.ReminderScheduler
import com.apollof.protocoltracker.ui.components.CategoryLabels
import com.apollof.protocoltracker.ui.journal.JournalFocus
import java.time.Instant
import java.time.ZoneId

/**
 * Manual dependency graph; one instance per process, owned by [ProtocolTrackerApp]. The preset channel, policy and
 * entitlements come from the flavour's [Distribution]; tests shared by both flavours pass foss values.
 */
class AppContainer(
    context: Context,
    val clock: () -> Instant = Instant::now,
    presetChannel: PresetChannel = Distribution.presetChannel,
    policy: TablePolicy = Distribution.policy,
    entitlements: (Context) -> Entitlements = Distribution::entitlements,
) {
    private val appContext = context.applicationContext
    val zone: () -> ZoneId = ZoneId::systemDefault
    val database = TrackerDatabase.create(appContext)
    /** The flavour's presets: all in foss, the reviewed allowlist in play (Distribution). */
    val repository = TrackerRepository(database, zone, presetChannel, clock)
    /** A day starts at 4:00, so a dose taken after midnight before bed counts for the day before. */
    val settings = SettingsStore(appContext)
    val entitlements: Entitlements = entitlements(appContext)
    /** Free, Pro or trial per feature; everything is free in foss. */
    val gate = FeatureGate(policy, this.entitlements, settings, clock)
    val doseActions = DoseActions(appContext, repository, settings, clock, zone)
    val reminders = ReminderScheduler(appContext, repository, settings, clock, zone)
    val journalFocus = JournalFocus()
    /** Section names in this flavour's wording, for text built outside Compose (reports). */
    val categoryLabels = CategoryLabels(appContext.resources)
    /** The launcher label, which differs per flavour: report titles and export file names use it. */
    val appName: String = appContext.getString(R.string.app_name)
}

val Context.container: AppContainer get() = (applicationContext as ProtocolTrackerApp).container
