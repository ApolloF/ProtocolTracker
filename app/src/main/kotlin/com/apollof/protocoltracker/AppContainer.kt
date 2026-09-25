package com.apollof.protocoltracker

import android.content.Context
import com.apollof.protocoltracker.data.SettingsStore
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.data.db.TrackerDatabase
import com.apollof.protocoltracker.reminders.ReminderScheduler
import java.time.Instant
import java.time.ZoneId

/** Manual dependency graph; one instance per process, owned by [ProtocolTrackerApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val clock: () -> Instant = Instant::now
    val zone: () -> ZoneId = ZoneId::systemDefault
    val database = TrackerDatabase.create(appContext)
    val repository = TrackerRepository(database, clock)
    val settings = SettingsStore(appContext)
    val doseActions = DoseActions(appContext, repository, settings, clock, zone)
    val reminders = ReminderScheduler(appContext, repository, settings, clock, zone)
}

val Context.container: AppContainer get() = (applicationContext as ProtocolTrackerApp).container
