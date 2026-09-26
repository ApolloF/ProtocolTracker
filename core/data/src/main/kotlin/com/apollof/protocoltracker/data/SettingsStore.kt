package com.apollof.protocoltracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.pk.CompareBaseline
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App colour scheme. [DYNAMIC] follows the wallpaper (Android 12+) and falls back to [SAGE] below. */
enum class Palette(val label: String) {
    SAGE("Sage"),
    OCEAN("Ocean"),
    PLUM("Plum"),
    CLAY("Clay"),
    GRAPHITE("Graphite"),
    DYNAMIC("Wallpaper"),
}

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
    val palette: Palette = Palette.SAGE,
    /** Dark theme uses a true black background (saves power on OLED screens). */
    val pureBlack: Boolean = false,
    val doseReminders: Boolean = true,
    val snoozeMinutes: Int = 15,
    val dailySummary: Boolean = false,
    val dailySummaryTime: LocalTime = LocalTime.of(8, 0),
    val checkTime: CheckTime = CheckTime.SCHEDULED,
    val weekBar: WeekBarMode = WeekBarMode.COLLAPSIBLE,
    val slotTimes: SlotTimes = SlotTimes.DEFAULT,
    /** Experimental: compare mode on the Levels screen. */
    val experimentalCompare: Boolean = false,
    val compareBaseline: CompareBaseline = CompareBaseline.PLAN,
    /** Anchor group for the shared-dose baseline; null picks one automatically. */
    val compareAnchor: String? = null,
    /** Groups left out of the comparison; everything in use is compared by default. */
    val compareExcluded: Set<String> = emptySet(),
)

/** App settings in a Preferences DataStore. */
class SettingsStore(context: Context) {
    private val store: DataStore<Preferences> = open(context)

    private companion object {
        // DataStore allows one active instance per file. The app creates one store per process; when a store is
        // created again for the same file (a new Application in the same process, as in tests), the old one is closed.
        private val scopes = HashMap<String, CoroutineScope>()

        @Synchronized
        fun open(context: Context): DataStore<Preferences> {
            val file = context.applicationContext.preferencesDataStoreFile("settings")
            scopes.remove(file.absolutePath)?.let { old -> runBlocking { old.coroutineContext.job.cancelAndJoin() } }
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scopes[file.absolutePath] = scope
            return PreferenceDataStoreFactory.create(scope = scope) { file }
        }
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val palette = stringPreferencesKey("palette")
        val pureBlack = booleanPreferencesKey("pure_black")
        val doseReminders = booleanPreferencesKey("dose_reminders")
        val snooze = intPreferencesKey("snooze_minutes")
        val dailySummary = booleanPreferencesKey("daily_summary")
        val dailySummaryTime = stringPreferencesKey("daily_summary_time")
        val checkTime = stringPreferencesKey("check_time")
        val weekBar = stringPreferencesKey("week_bar")
        val anyTimeReminder = stringPreferencesKey("any_time_reminder")
        val experimentalCompare = booleanPreferencesKey("experimental_compare")
        val compareBaseline = stringPreferencesKey("compare_baseline")
        val compareAnchor = stringPreferencesKey("compare_anchor")
        val compareExcluded = stringSetPreferencesKey("compare_excluded")
        fun slot(slot: DaySlot) = stringPreferencesKey("slot_${slot.name}")
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    private fun Preferences.time(key: Preferences.Key<String>): LocalTime? = this[key]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            palette = this[Keys.palette]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: defaults.palette,
            pureBlack = this[Keys.pureBlack] ?: defaults.pureBlack,
            doseReminders = this[Keys.doseReminders] ?: defaults.doseReminders,
            snoozeMinutes = this[Keys.snooze] ?: defaults.snoozeMinutes,
            dailySummary = this[Keys.dailySummary] ?: defaults.dailySummary,
            dailySummaryTime = time(Keys.dailySummaryTime) ?: defaults.dailySummaryTime,
            checkTime = this[Keys.checkTime]?.let { runCatching { CheckTime.valueOf(it) }.getOrNull() } ?: defaults.checkTime,
            weekBar = this[Keys.weekBar]?.let { runCatching { WeekBarMode.valueOf(it) }.getOrNull() } ?: defaults.weekBar,
            experimentalCompare = this[Keys.experimentalCompare] ?: defaults.experimentalCompare,
            compareBaseline = this[Keys.compareBaseline]?.let { runCatching { CompareBaseline.valueOf(it) }.getOrNull() } ?: defaults.compareBaseline,
            compareAnchor = this[Keys.compareAnchor],
            compareExcluded = this[Keys.compareExcluded] ?: defaults.compareExcluded,
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
            p[Keys.palette] = next.palette.name
            p[Keys.pureBlack] = next.pureBlack
            p[Keys.doseReminders] = next.doseReminders
            p[Keys.snooze] = next.snoozeMinutes.coerceIn(5, 240)
            p[Keys.dailySummary] = next.dailySummary
            p[Keys.dailySummaryTime] = next.dailySummaryTime.toString()
            p[Keys.checkTime] = next.checkTime.name
            p[Keys.weekBar] = next.weekBar.name
            p[Keys.anyTimeReminder] = next.slotTimes.anyTimeReminder.toString()
            p[Keys.experimentalCompare] = next.experimentalCompare
            p[Keys.compareBaseline] = next.compareBaseline.name
            next.compareAnchor?.let { p[Keys.compareAnchor] = it } ?: p.remove(Keys.compareAnchor)
            p[Keys.compareExcluded] = next.compareExcluded
            for (slot in DaySlot.entries) {
                val time = next.slotTimes.times[slot]
                if (time == null || time == slot.defaultTime) p.remove(Keys.slot(slot)) else p[Keys.slot(slot)] = time.toString()
            }
        }
    }
}
