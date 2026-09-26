package com.apollof.protocoltracker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apollof.protocoltracker.reminders.Notifications
import com.apollof.protocoltracker.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

open class ProtocolTrackerApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** On-demand WorkManager initialisation (the default startup initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    /** Tests override this to pin the clock (design-review screenshots). */
    protected open fun createContainer() = AppContainer(this)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        Notifications.createChannels(this)
        scope.launch { container.repository.seedPresets() }
        // Any change to plan, logs or settings re-plans alarms and redraws the widget.
        scope.launch {
            combine(container.repository.protocol, container.repository.logChanges, container.repository.anchors, container.settings.settings) { _, _, _, _ -> }
                .debounce(300)
                .collect {
                    try {
                        container.reminders.resync()
                        TodayWidget.refresh(this@ProtocolTrackerApp)
                    } catch (e: Exception) {
                        Log.e("ProtocolTrackerApp", "Resync failed", e)
                    }
                }
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ResyncWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ResyncWorker>(12, TimeUnit.HOURS).build(),
        )
    }
}

/** Safety net: re-arms alarms and refreshes the widget even if a system broadcast was missed. */
class ResyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.reminders.resync()
        TodayWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        const val NAME = "resync"
    }
}
