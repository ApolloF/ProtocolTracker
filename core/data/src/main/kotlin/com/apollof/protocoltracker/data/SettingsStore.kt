package com.apollof.protocoltracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which time a one-tap check records. */
enum class CheckTime { SCHEDULED, NOW }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val doseReminders: Boolean = true,
    val snoozeMinutes: Int = 15,
    val dailySummary: Boolean = false,
    val dailySummaryTime: LocalTime = LocalTime.of(8, 0),
    val checkTime: CheckTime = CheckTime.SCHEDULED,
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
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            doseReminders = this[Keys.doseReminders] ?: defaults.doseReminders,
            snoozeMinutes = this[Keys.snooze] ?: defaults.snoozeMinutes,
            dailySummary = this[Keys.dailySummary] ?: defaults.dailySummary,
            dailySummaryTime = this[Keys.dailySummaryTime]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: defaults.dailySummaryTime,
            checkTime = this[Keys.checkTime]?.let { runCatching { CheckTime.valueOf(it) }.getOrNull() } ?: defaults.checkTime,
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
        }
    }
}
