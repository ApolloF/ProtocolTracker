package com.apollof.protocoltracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which time a one-tap check records for exact-time doses; part-of-day doses taken today log the current time. */
enum class CheckTime { SCHEDULED, NOW }

/** How the week strip shows on Today. */
enum class WeekBarMode(val label: String) {
    HIDDEN("Hidden"),
    COLLAPSIBLE("Collapsible"),
    COMPACT("Compact"),
    FULL("Full"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val doseReminders: Boolean = true,
    val snoozeMinutes: Int = 15,
    val dailySummary: Boolean = false,
    val dailySummaryTime: LocalTime = LocalTime.of(8, 0),
    val checkTime: CheckTime = CheckTime.SCHEDULED,
    val weekBar: WeekBarMode = WeekBarMode.COLLAPSIBLE,
    val slotTimes: SlotTimes = SlotTimes.DEFAULT,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val doseReminders = booleanPreferencesKey("dose_reminders")
        val snooze = intPreferencesKey("snooze_minutes")
        val dailySummary = booleanPreferencesKey("daily_summary")
        val dailySummaryTime = stringPreferencesKey("daily_summary_time")
        val checkTime = stringPreferencesKey("check_time")
        val weekBar = stringPreferencesKey("week_bar")
        val anyTimeReminder = stringPreferencesKey("any_time_reminder")
        fun slot(slot: DaySlot) = stringPreferencesKey("slot_${slot.name}")
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    private fun Preferences.time(key: Preferences.Key<String>): LocalTime? = this[key]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            doseReminders = this[Keys.doseReminders] ?: defaults.doseReminders,
            snoozeMinutes = this[Keys.snooze] ?: defaults.snoozeMinutes,
            dailySummary = this[Keys.dailySummary] ?: defaults.dailySummary,
            dailySummaryTime = time(Keys.dailySummaryTime) ?: defaults.dailySummaryTime,
            checkTime = this[Keys.checkTime]?.let { runCatching { CheckTime.valueOf(it) }.getOrNull() } ?: defaults.checkTime,
            weekBar = this[Keys.weekBar]?.let { runCatching { WeekBarMode.valueOf(it) }.getOrNull() } ?: defaults.weekBar,
            slotTimes = SlotTimes(
                times = DaySlot.entries.mapNotNull { slot -> time(Keys.slot(slot))?.let { slot to it } }.toMap(),
                anyTimeReminder = time(Keys.anyTimeReminder) ?: defaults.slotTimes.anyTimeReminder,
            ),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.theme] = next.theme.name
            p[Keys.doseReminders] = next.doseReminders
            p[Keys.snooze] = next.snoozeMinutes.coerceIn(5, 240)
            p[Keys.dailySummary] = next.dailySummary
            p[Keys.dailySummaryTime] = next.dailySummaryTime.toString()
            p[Keys.checkTime] = next.checkTime.name
            p[Keys.weekBar] = next.weekBar.name
            p[Keys.anyTimeReminder] = next.slotTimes.anyTimeReminder.toString()
            for (slot in DaySlot.entries) {
                val time = next.slotTimes.times[slot]
                if (time == null || time == slot.defaultTime) p.remove(Keys.slot(slot)) else p[Keys.slot(slot)] = time.toString()
            }
        }
    }
}
