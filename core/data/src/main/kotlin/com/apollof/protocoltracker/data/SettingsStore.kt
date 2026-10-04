package com.apollof.protocoltracker.data

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.pk.LevelAdjustments
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.units.DisplayFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.time.Instant
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

/** How much the interface animates. [REDUCED] keeps short fades; [OFF] switches screens instantly. */
enum class Motion(val label: String) {
    FULL("Full"),
    REDUCED("Reduced"),
    OFF("Off"),
}

enum class TimeFormat(val label: String) {
    SYSTEM("System"),
    H24("24-hour"),
    H12("12-hour"),
}

enum class DateOrder(val label: String) {
    SYSTEM("System"),
    DAY_FIRST("26 Sep"),
    MONTH_FIRST("Sep 26"),
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
    val motion: Motion = Motion.REDUCED,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val dateOrder: DateOrder = DateOrder.SYSTEM,
    val labUnits: LabUnits = LabUnits.CONVENTIONAL,
    /** Injection volumes as U-100 syringe units instead of mL. */
    val syringeUnits: Boolean = false,
    /** The user's scaling of level estimates by group (Levels › a group › Adjust level). */
    val levelAdjustments: LevelAdjustments = LevelAdjustments.NONE,
) {
    /** Resolves the "System" choices; [system24Hour] comes from the device setting. */
    fun displayFormat(system24Hour: Boolean): DisplayFormat = DisplayFormat(
        use24Hour = when (timeFormat) {
            TimeFormat.SYSTEM -> system24Hour
            TimeFormat.H24 -> true
            TimeFormat.H12 -> false
        },
        dayFirst = when (dateOrder) {
            DateOrder.SYSTEM -> DisplayFormat.localeDayFirst()
            DateOrder.DAY_FIRST -> true
            DateOrder.MONTH_FIRST -> false
        },
        syringeUnits = syringeUnits,
    )
}

/** App settings in a Preferences DataStore. */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val store: DataStore<Preferences> = open(context)

    companion object {
        /** The day starts this setting offers: midnight to 6:00, on the hour. */
        val DAY_STARTS: List<LocalTime> = (0..6).map { LocalTime.of(it, 0) }

        /** The day start until one is chosen: a dose taken before 4:00 still counts for the evening before. */
        val DEFAULT_DAY_START: LocalTime = LocalTime.of(4, 0)

        private val BOOLEAN_KEYS = setOf("pure_black", "dose_reminders", "daily_summary", "syringe_units")
        private val INT_KEYS = setOf("snooze_minutes")

        /** Snooze lengths the setting allows, in minutes; a restored value outside reads as the nearest end. */
        val SNOOZE_RANGE = 5..240

        private val STRING_KEYS = setOf(
            "theme", "palette", "daily_summary_time", "check_time", "week_bar", "any_time_reminder", "day_start", "motion", "time_format", "date_order", "lab_units",
            "level_adjustments",
        ) + DaySlot.entries.map { "slot_${it.name}" }

        // DataStore allows one active instance per file. The app creates one store per process; when a store is
        // created again for the same file (a new Application in the same process, as in tests), the old one is closed.
        private val scopes = HashMap<String, CoroutineScope>()

        @Synchronized
        private fun open(context: Context): DataStore<Preferences> {
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
        val dayStart = stringPreferencesKey("day_start")
        val motion = stringPreferencesKey("motion")
        val timeFormat = stringPreferencesKey("time_format")
        val dateOrder = stringPreferencesKey("date_order")
        val labUnits = stringPreferencesKey("lab_units")
        val syringeUnits = booleanPreferencesKey("syringe_units")
        val levelAdjustments = stringPreferencesKey("level_adjustments")
        fun slot(slot: DaySlot) = stringPreferencesKey("slot_${slot.name}")

        /** When the first-run notice was acknowledged. State of this device: never exported, kept on a restore. */
        val noticeAcknowledgedAt = longPreferencesKey("notice_acknowledged_at")
    }

    /**
     * Current settings. Each read also updates [DisplayFormat.current] before collectors see the value, so anything
     * formatted from the new settings already uses the chosen time, date and volume format.
     */
    val settings: Flow<Settings> = store.data.map { it.toSettings() }.onEach { applyDisplayFormat(it) }

    private fun applyDisplayFormat(settings: Settings) {
        DisplayFormat.current = settings.displayFormat(DateFormat.is24HourFormat(appContext))
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private fun Preferences.time(key: Preferences.Key<String>): LocalTime? = this[key]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            palette = this[Keys.palette]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: defaults.palette,
            pureBlack = this[Keys.pureBlack] ?: defaults.pureBlack,
            doseReminders = this[Keys.doseReminders] ?: defaults.doseReminders,
            snoozeMinutes = (this[Keys.snooze] ?: defaults.snoozeMinutes).coerceIn(SNOOZE_RANGE),
            dailySummary = this[Keys.dailySummary] ?: defaults.dailySummary,
            dailySummaryTime = time(Keys.dailySummaryTime) ?: defaults.dailySummaryTime,
            checkTime = this[Keys.checkTime]?.let { runCatching { CheckTime.valueOf(it) }.getOrNull() } ?: defaults.checkTime,
            weekBar = this[Keys.weekBar]?.let { runCatching { WeekBarMode.valueOf(it) }.getOrNull() } ?: defaults.weekBar,
            motion = enum(Keys.motion, defaults.motion),
            timeFormat = enum(Keys.timeFormat, defaults.timeFormat),
            dateOrder = enum(Keys.dateOrder, defaults.dateOrder),
            labUnits = enum(Keys.labUnits, defaults.labUnits),
            syringeUnits = this[Keys.syringeUnits] ?: defaults.syringeUnits,
            levelAdjustments = LevelAdjustments.decode(this[Keys.levelAdjustments]),
            slotTimes = SlotTimes(
                times = DaySlot.entries.mapNotNull { slot -> time(Keys.slot(slot))?.let { slot to it } }.toMap(),
                anyTimeReminder = time(Keys.anyTimeReminder) ?: defaults.slotTimes.anyTimeReminder,
                dayStart = time(Keys.dayStart)?.takeIf { it in DAY_STARTS } ?: DEFAULT_DAY_START,
            ),
        )
    }

    suspend fun current(): Settings = settings.first()

    /** Whether the first-run notice was acknowledged on this device. */
    val noticeAcknowledged: Flow<Boolean> = store.data.map { it[Keys.noticeAcknowledgedAt] != null }

    suspend fun acknowledgeNotice(at: Instant) {
        store.edit { it[Keys.noticeAcknowledgedAt] = at.toEpochMilli() }
    }

    /** Every stored setting this version knows, as text by key, for a backup (keys of removed settings are left out). */
    suspend fun exportMap(): Map<String, String> = store.data.first().asMap().entries.mapNotNull { (key, value) ->
        when {
            key.name !in BOOLEAN_KEYS && key.name !in INT_KEYS && key.name !in STRING_KEYS -> null
            value is String || value is Boolean || value is Int -> key.name to value.toString()
            else -> null
        }
    }.toMap()

    /**
     * Replaces the settings with [map] from a backup. Keys this version does not know and values of the wrong type are
     * left out, so they read as defaults; values out of range fall back when they are read. The notice stays
     * acknowledged.
     */
    suspend fun importMap(map: Map<String, String>) {
        store.edit { p ->
            val acknowledged = p[Keys.noticeAcknowledgedAt]
            p.clear()
            acknowledged?.let { p[Keys.noticeAcknowledgedAt] = it }
            for ((name, text) in map) {
                when (name) {
                    in BOOLEAN_KEYS -> text.toBooleanStrictOrNull()?.let { p[booleanPreferencesKey(name)] = it }
                    in INT_KEYS -> text.toIntOrNull()?.let { p[intPreferencesKey(name)] = it }
                    in STRING_KEYS -> p[stringPreferencesKey(name)] = text
                }
            }
        }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.theme] = next.theme.name
            p[Keys.palette] = next.palette.name
            p[Keys.pureBlack] = next.pureBlack
            p[Keys.doseReminders] = next.doseReminders
            p[Keys.snooze] = next.snoozeMinutes.coerceIn(SNOOZE_RANGE)
            p[Keys.dailySummary] = next.dailySummary
            p[Keys.dailySummaryTime] = next.dailySummaryTime.toString()
            p[Keys.checkTime] = next.checkTime.name
            p[Keys.weekBar] = next.weekBar.name
            p[Keys.anyTimeReminder] = next.slotTimes.anyTimeReminder.toString()
            p[Keys.dayStart] = next.slotTimes.dayStart.takeIf { it in DAY_STARTS }?.toString() ?: DEFAULT_DAY_START.toString()
            p[Keys.motion] = next.motion.name
            p[Keys.timeFormat] = next.timeFormat.name
            p[Keys.dateOrder] = next.dateOrder.name
            p[Keys.labUnits] = next.labUnits.name
            p[Keys.syringeUnits] = next.syringeUnits
            next.levelAdjustments.encode().let { if (it.isEmpty()) p.remove(Keys.levelAdjustments) else p[Keys.levelAdjustments] = it }
            for (slot in DaySlot.entries) {
                val time = next.slotTimes.times[slot]
                if (time == null || time == slot.defaultTime) p.remove(Keys.slot(slot)) else p[Keys.slot(slot)] = time.toString()
            }
        }
    }
}
