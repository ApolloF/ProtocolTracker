package com.apollof.protocoltracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.Settings
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.data.WeekBarMode
import com.apollof.protocoltracker.domain.model.shortName
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.SiteRotation
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.model.compoundOrder
import com.apollof.protocoltracker.domain.model.followsLastDose
import com.apollof.protocoltracker.domain.model.lastDrawAge
import com.apollof.protocoltracker.domain.model.measuredMarkers
import com.apollof.protocoltracker.domain.model.shownAt
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.schedule.Agenda
import com.apollof.protocoltracker.domain.schedule.AgendaEntry
import com.apollof.protocoltracker.domain.schedule.AgendaStatus
import com.apollof.protocoltracker.domain.schedule.AgendaWindows
import com.apollof.protocoltracker.domain.schedule.DayStatus
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.LastTime
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.schedule.buildAgenda
import com.apollof.protocoltracker.domain.schedule.buildDay
import com.apollof.protocoltracker.domain.schedule.dateOf
import com.apollof.protocoltracker.domain.schedule.dayStartOf
import com.apollof.protocoltracker.domain.schedule.lastTimes
import com.apollof.protocoltracker.domain.schedule.nextOccurrence
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.schedule.previousOccurrence
import com.apollof.protocoltracker.domain.schedule.stripWeeks
import com.apollof.protocoltracker.domain.schedule.trackedFrom
import com.apollof.protocoltracker.domain.schedule.weekStartOf
import com.apollof.protocoltracker.domain.schedule.weekSummaries
import com.apollof.protocoltracker.domain.schedule.weekSummary
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.UiMessage
import com.apollof.protocoltracker.ui.components.CheckState
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.health.BloodworkInput
import com.apollof.protocoltracker.ui.health.SymptomInput
import com.apollof.protocoltracker.ui.health.toEntry
import com.apollof.protocoltracker.ui.minuteTicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Section order (injectable, oral, support, peptide), then the plan's own order. */
private val todayOrder = compareBy<DoseItem>(
    { it.category?.ordinal ?: Int.MAX_VALUE },
    { it.compound?.supportKind?.ordinal ?: -1 },
    { it.entry.occurrence?.item?.sortOrder ?: Int.MAX_VALUE },
    { it.name },
)

/**
 * Each compound's suggested site from [logs] (every dose log; null until loaded). Nothing before they load, so Today's
 * rows wait for their sites: a row never shows without one and a quick check never records none.
 */
internal fun siteSuggestions(logs: Flow<List<DoseLog>?>): Flow<Map<String, String>> = logs.filterNotNull().map(SiteRotation::suggestions)

/** One dose row on Today, already formatted. [site]: the suggested site of a pending dose, the recorded one of a taken dose. */
data class DoseItem(
    val entry: AgendaEntry,
    val compound: Compound?,
    val commonName: String,
    val name: String,
    val category: CompoundCategory?,
    val detail: String,
    val state: CheckState,
    val site: String? = null,
    /** On a pending dose of today: "Missed last time (Thu 24 Sep) · last taken Mon 21 Sep, 9:00". */
    val note: String? = null,
) {
    val key: String get() = entry.id

    /** What a one-tap check writes: the site this row shows, or nothing new when it shows none. */
    val siteWrite: SiteWrite get() = site?.let { SiteWrite.Set(it) } ?: SiteWrite.Keep

    /** " · R delt" for a message about this row; empty without a site. */
    val siteSuffix: String get() = site?.let { " · ${InjectionSites.label(it)}" }.orEmpty()
}

data class GroupUi(val key: String, val label: String, val slot: DaySlot?, val items: List<DoseItem>) {
    val pending: Int get() = items.count { it.state.open }

    /** Every dose of the group was skipped: its badge says so instead of "Done". */
    val allSkipped: Boolean get() = items.isNotEmpty() && items.all { it.state == CheckState.SKIPPED }
}

data class TodayState(
    val loading: Boolean = true,
    /** The day Today shows (it moves on at Settings › Day starts at); null until loaded. */
    val date: LocalDate? = null,
    val dateLabel: String = "",
    val cycleTitle: String? = null,
    val cycleSubtitle: String = "",
    val progress: Float? = null,
    val weekBar: WeekBarMode = WeekBarMode.COLLAPSIBLE,
    val week: List<DayStatus> = emptyList(),
    val missed: List<DoseItem> = emptyList(),
    /** Earlier doses logged today; shown checked next to the missed ones. */
    val caughtUp: List<DoseItem> = emptyList(),
    val groups: List<GroupUi> = emptyList(),
    val extras: List<DoseItem> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    /** [extras] and [journal] in one list by time, for the one "Logged today" section. */
    val logged: List<LoggedRow> = emptyList(),
    val hasPlan: Boolean = false,
    /** On a day with nothing due: "Tirzepatide · Tomorrow, 9:00 AM"; null otherwise or with nothing in 60 days. */
    val nextDue: String? = null,
    val compounds: List<Compound> = emptyList(),
    /** Compound ids of active plan items, listed first when logging an extra dose. */
    val planCompoundIds: Set<String> = emptySet(),
    val labUnits: LabUnits = LabUnits.CONVENTIONAL,
    /** Settings › Times of day, for the dose sheet's day names. */
    val slotTimes: SlotTimes = SlotTimes.DEFAULT,
)

/** A row of Today's "Logged today": an extra dose or a journal entry. */
sealed interface LoggedRow {
    val at: Instant

    data class Extra(val item: DoseItem, val log: DoseLog) : LoggedRow {
        override val at: Instant get() = log.takenAt
    }

    data class Entry(val entry: JournalEntry) : LoggedRow {
        override val at: Instant get() = entry.at
    }
}

/** The weeks the strip swipes through (their Mondays) and each one's days. */
data class StripUi(val weeks: List<LocalDate>, val days: Map<LocalDate, List<DayStatus>>, val today: LocalDate)

/** One chosen day, opened from the week strip or the date picker, to check off or backfill its doses. */
data class DayUi(
    val date: LocalDate,
    val title: String,
    /** "2 taken · 1 missed", "2 of 3 done", "3 planned" or "Nothing scheduled". */
    val summary: String,
    val groups: List<GroupUi>,
    val extras: List<DoseItem>,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/** What the log sheet edits: a scheduled dose (pending or already logged) or a new unscheduled one. */
sealed interface LogTarget {
    /** [backfill]: opened from a past day, so the time starts at the planned time. */
    data class Scheduled(val occurrence: Occurrence, val compound: Compound, val existing: DoseLog?, val partLabel: String, val backfill: Boolean = false) : LogTarget
    data class Unscheduled(val compound: Compound?) : LogTarget
    /** A logged dose, planned or extra, opened to change it or delete it. */
    data class Edit(val log: DoseLog) : LogTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    private val ticker = minuteTicker(c.clock).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The day Today shows and when it started: it moves on at Settings › Day starts at, not at midnight. */
    private val todayStart: Flow<Pair<LocalDate, Instant>> = combine(ticker, c.settings.settings) { now, settings ->
        val day = settings.slotTimes.dateOf(now, c.zone())
        day to settings.slotTimes.dayStartOf(day, c.zone())
    }.distinctUntilChanged()

    private val today: Flow<LocalDate> = todayStart.map { it.first }.distinctUntilChanged()

    /** Logs from the start of the week (for the strip) or the missed-dose lookback, whichever is earlier. */
    private val logs = today.flatMapLatest { day -> c.repository.logsSince(windowStart(day)) }

    private val journal = todayStart.flatMapLatest { (_, start) -> c.repository.journalSince(start) }

    /** Every dose log, for the Site row of the dose sheet and the rows' sites; null until loaded. */
    val siteLogs: StateFlow<List<DoseLog>?> =
        c.repository.allLogs.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The suggested site per compound id, from every taken dose (not the windowed logs). */
    private val suggestions: Flow<Map<String, String>> = siteSuggestions(siteLogs)

    /** Every dose log once loaded, for "missed last time" and the week strip. */
    private val everyLog: Flow<List<DoseLog>> = siteLogs.filterNotNull()

    /** The history's first day; earlier unlogged doses are not counted as missed. */
    private val countFrom: Flow<LocalDate> =
        combine(everyLog, c.settings.settings, today) { all, settings, today -> trackedFrom(all, today, c.zone(), settings.slotTimes) }.distinctUntilChanged()

    private data class Logs(val window: List<DoseLog>, val anchors: IntervalAnchors, val sites: Map<String, String>, val all: List<DoseLog>)

    val state: StateFlow<TodayState> = combine(
        c.repository.protocol, combine(logs, c.repository.anchors, suggestions, everyLog, ::Logs), journal, c.settings.settings, ticker,
    ) { protocol, logs, journal, settings, now ->
        build(protocol, logs, journal, settings, now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Marker keys with a result in any draw, for the Bloodwork sheet's up-front markers. */
    val measuredMarkers: StateFlow<Set<String>> =
        c.repository.journal.map(::measuredMarkers).flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** "Last draw 3 days ago" for the Log menu's Bloodwork row; null without a past draw. */
    val lastDraw: StateFlow<String?> =
        combine(c.repository.bloodworkTimes, ticker) { draws, now -> lastDrawAge(draws, now, c.zone())?.let { "Last draw $it" } }
            .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** The day picked in the strip or the date picker; null shows today. */
    val selected: StateFlow<LocalDate?> = selectedDay

    /**
     * The picked day, shown below the strip; null when none. It keeps the previous day
     * until the new one has loaded. Logs from two days before cover doses logged early.
     */
    val day: StateFlow<DayUi?> = selectedDay.flatMapLatest { date ->
        if (date == null) flowOf(null)
        else combine(
            c.repository.protocol,
            c.repository.logsSince(date.minusDays(2).atStartOfDay(c.zone()).toInstant()),
            c.repository.anchors,
            c.settings.settings,
            combine(today, countFrom, ::Pair),
        ) { protocol, logs, anchors, settings, (today, countFrom) -> buildDayUi(protocol, logs, anchors, settings, date, today, countFrom) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The strip's weeks: they change only with the plan, the first log, today or a picked day outside them. */
    private val weekRange: Flow<List<LocalDate>> = combine(
        c.repository.protocol,
        combine(everyLog, c.settings.settings) { all, settings -> all.minOfOrNull { it.shownAt }?.let { settings.slotTimes.dateOf(it, c.zone()) } }.distinctUntilChanged(),
        today,
        selectedDay,
    ) { protocol, firstLog, today, selected -> stripWeeks(protocol.phases, protocol.items, firstLog, today, include = selected) }.distinctUntilChanged()

    /** The swipeable week strip; null until loaded. */
    val strip: StateFlow<StripUi?> =
        combine(c.repository.protocol, everyLog, c.repository.anchors, c.settings.settings, combine(weekRange, today, countFrom, ::Triple)) { protocol, all, anchors, settings, (weeks, today, countFrom) ->
            StripUi(weeks, weekSummaries(protocol.phases, protocol.items, all, weeks, today, c.zone(), anchors, settings.slotTimes, countFrom), today)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildDayUi(
        protocol: Protocol,
        logs: List<DoseLog>,
        anchors: IntervalAnchors,
        settings: Settings,
        date: LocalDate,
        today: LocalDate,
        countFrom: LocalDate?,
    ): DayUi {
        val zone = c.zone()
        val slotTimes = settings.slotTimes
        val day = buildDay(protocol.phases, protocol.items, logs, date, today, zone, anchors, slotTimes, countFrom)
        return DayUi(
            date = date,
            title = Formats.relativeDay(date, today).let { rel -> if (rel == date.format(Formats.dayShort)) rel else "$rel · ${date.format(Formats.dayShort)}" },
            summary = day.summary,
            groups = day.groups.map { g -> GroupUi(g.key, g.label, g.slot, g.entries.map { doseItem(it, protocol, zone, slotTimes, emptyMap()) }.sortedWith(todayOrder)) },
            extras = day.extras.map { doseItem(it, protocol, zone, slotTimes, emptyMap()) },
            isToday = day.isToday,
            isFuture = day.isFuture,
        )
    }

    /** Picking today shows Today itself. */
    fun openDay(date: LocalDate) { selectedDay.value = date.takeUnless { it == state.value.date } }
    fun closeDay() { selectedDay.value = null }
    fun shiftDay(days: Long) { selectedDay.value?.plusDays(days)?.let(::openDay) }
    fun today(): LocalDate = state.value.date ?: c.clock().atZone(c.zone()).toLocalDate()

    /** The check on a picked day: past days record the planned time, today follows the usual check rules. */
    fun checkOnDay(item: DoseItem, day: DayUi) {
        val occ = item.entry.occurrence
        if (item.entry.log != null || occ == null || day.isToday) return check(item)
        if (day.isFuture) return
        val label = item.commonName.ifBlank { item.name.replaceFirstChar { it.titlecase() } }
        launchLogged("$label logged for ${day.date.format(Formats.dayShort)}") { listOf(c.doseActions.take(occ, takenAt = occ.at)) }
    }

    fun logDayGroup(group: GroupUi, day: DayUi) {
        if (day.isToday) return logGroup(group)
        if (day.isFuture) return
        val pending = group.items.filter { it.state.open }.mapNotNull { it.entry.occurrence }
        if (pending.isEmpty()) return
        launchLogged("${pending.size} doses logged for ${day.date.format(Formats.dayShort)}") { pending.map { c.doseActions.take(it, takenAt = it.at) } }
    }

    fun dayTarget(item: DoseItem, day: DayUi): LogTarget? {
        val occ = item.entry.occurrence ?: return null
        val compound = item.compound ?: return null
        return LogTarget.Scheduled(occ, compound, item.entry.log, day.date.format(Formats.dayShort), backfill = !day.isToday)
    }

    private fun windowStart(day: LocalDate): Instant {
        val zone = c.zone()
        val weekStart = weekStartOf(day).atStartOfDay(zone).toInstant()
        return minOf(weekStart, day.atStartOfDay(zone).toInstant().minus(AgendaWindows.missedLookback))
    }

    private fun build(protocol: Protocol, inputs: Logs, journal: List<JournalEntry>, settings: Settings, now: Instant): TodayState {
        val (logs, anchors, sites, all) = inputs
        val zone = c.zone()
        val slotTimes = settings.slotTimes
        val agenda = buildAgenda(protocol.phases, protocol.items, logs, now, zone, anchors, slotTimes)
        val today = agenda.date
        val weekStart = weekStartOf(today)
        val pinIndex = pinNumbers(protocol, weekStart, zone, anchors, settings)
        val countFrom = trackedFrom(all, today, zone, slotTimes)
        val notes = lastTimeNotes(protocol, agenda, all, zone, anchors, slotTimes, countFrom)

        fun item(e: AgendaEntry, dayPrefix: String? = null) = doseItem(e, protocol, zone, slotTimes, pinIndex, dayPrefix).let { item ->
            notes[item.key]?.let { item.copy(note = it) } ?: item
        }

        val phase = agenda.phase
        val week = if (settings.weekBar == WeekBarMode.HIDDEN) emptyList() else
            weekSummary(protocol.phases, protocol.items, logs, weekStart, today, zone, anchors, settings.slotTimes, countFrom)
        val extras = agenda.extras.map { item(it) }.withSites(sites)
        val sortedJournal = journal.sortedBy { it.at }
        return TodayState(
            loading = false,
            date = today,
            dateLabel = today.format(Formats.dayYear).uppercase(),
            cycleTitle = phase?.phase?.name?.ifBlank { "Current phase" } ?: if (week.isNotEmpty()) "This week" else null,
            cycleSubtitle = phase?.let { p -> listOfNotNull("Week ${p.week}" + (p.totalWeeks?.let { " / $it" } ?: ""), "day ${p.day}").joinToString(" · ") }
                ?: "${agenda.doneToday} of ${agenda.scheduledToday} done today",
            progress = phase?.fraction,
            weekBar = settings.weekBar,
            week = week,
            missed = agenda.missed.map { e -> item(e, earlierDay(e.occurrence!!)) }.withSites(sites),
            caughtUp = agenda.caughtUp.map { e -> item(e, earlierDay(e.occurrence!!)) }.withSites(sites),
            groups = agenda.groups.map { g ->
                GroupUi(g.key, g.label, g.slot, g.entries.map { item(it) }.sortedWith(todayOrder).withSites(sites))
            },
            extras = extras,
            journal = sortedJournal,
            logged = (extras.mapNotNull { item -> item.entry.log?.let { LoggedRow.Extra(item, it) } } + sortedJournal.map(LoggedRow::Entry)).sortedBy { it.at },
            hasPlan = protocol.items.isNotEmpty(),
            nextDue = if (agenda.groups.isEmpty() && agenda.missed.isEmpty()) {
                nextDueText(protocol, now, today, zone, anchors, settings)
            } else null,
            compounds = protocol.compounds.values.filter { !it.archived }.sortedWith(compoundOrder),
            planCompoundIds = protocol.items.filter { it.enabled }.mapTo(HashSet()) { it.compoundId },
            labUnits = settings.labUnits,
            slotTimes = slotTimes,
        )
    }

    /** The next planned dose after today: "Tirzepatide · Tomorrow, Morning" or "… · Tue, Oct 6, 9:00 AM". */
    private fun nextDueText(protocol: Protocol, now: Instant, today: LocalDate, zone: ZoneId, anchors: IntervalAnchors, settings: Settings): String? {
        val from = maxOf(now, today.plusDays(1).atStartOfDay(zone).toInstant())
        val occ = nextOccurrence(protocol.phases, protocol.items, from, zone, anchors, settings.slotTimes) ?: return null
        val name = protocol.compounds[occ.item.compoundId]?.let { it.commonName.ifBlank { it.displayName } } ?: return null
        val slot = (occ.timing as? Timing.Slot)?.slot?.takeIf { it != DaySlot.ANY_TIME }
        val day = Formats.relativeDay(occ.localDate, today)
        return "$name · $day, ${slot?.label ?: Formats.time(occ.at, zone)}"
    }

    /**
     * Pending doses of today whose previous dose was missed or skipped, with what to say: "Missed last time (Thu 24 Sep)
     * · last taken Mon 21 Sep, 9:00". [all] is every dose log, so a previous dose any time back is found. A previous
     * dose before [countFrom] (the history's first day) says nothing.
     */
    private fun lastTimeNotes(
        protocol: Protocol,
        agenda: Agenda,
        all: List<DoseLog>,
        zone: ZoneId,
        anchors: IntervalAnchors,
        slotTimes: SlotTimes,
        countFrom: LocalDate,
    ): Map<String, String> {
        val pending = agenda.groups.flatMap { it.entries }.filter { it.status == AgendaStatus.PENDING }.mapNotNull { it.occurrence }
        if (pending.isEmpty()) return emptyMap()
        val previous = pending.mapNotNull { occ ->
            previousOccurrence(protocol.phases, occ, zone, anchors, slotTimes)?.takeIf { it.localDate >= countFrom }?.let { occ.key to it }
        }.toMap()
        if (previous.isEmpty()) return emptyMap()
        val wanted = previous.values.mapTo(HashSet()) { it.key }
        val logsByKey = all.filter { it.occurrenceKey in wanted }.associateBy { it.occurrenceKey!! }
        val latest = all.filter { it.status == LogStatus.TAKEN }.groupBy { it.compoundId }.mapValues { (_, logs) -> logs.maxBy { it.takenAt } }
        val missed = agenda.missed.mapNotNullTo(HashSet()) { it.occurrence?.key }
        return lastTimes(pending, previous, logsByKey, latest, missed).mapValues { (_, last) -> lastTimeText(last, agenda.date, zone, slotTimes) }
    }

    private fun lastTimeText(last: LastTime, today: LocalDate, zone: ZoneId, slotTimes: SlotTimes): String {
        val what = if (last.skipped) "Skipped" else "Missed"
        val taken = last.lastTaken?.let { log ->
            " · last taken ${Formats.relativeDay(slotTimes.dateOf(log.takenAt, zone), today)}, ${Formats.time(log.takenAt, zone)}"
        }.orEmpty()
        return "$what last time (${Formats.relativeDay(last.previous.localDate, today)})$taken"
    }

    /**
     * One formatted dose row. [dayPrefix] names an earlier day ("Thu Morning"); [pins] adds "injection 1 of 2". A missed dose
     * shown on its own day says "Missed"; a skipped one says "Skipped" (it has no time of its own).
     */
    private fun doseItem(e: AgendaEntry, protocol: Protocol, zone: ZoneId, slotTimes: SlotTimes, pins: Map<String, String>, dayPrefix: String? = null): DoseItem {
        val log = e.log
        val occ = e.occurrence
        val compound = protocol.compounds[log?.compoundId ?: occ!!.item.compoundId]
        // A dose logged on another day than planned says which day. Its own date counts by the calendar (a dose due at
        // 0:30 taken at 0:35) or by the logical day (an evening dose taken at 1:00 before the day start).
        fun taken(at: Instant): String {
            val onItsDay = occ == null || slotTimes.dateOf(at, zone) == occ.localDate || at.atZone(zone).toLocalDate() == occ.localDate
            return if (onItsDay) Formats.time(at, zone) else "${at.atZone(zone).format(Formats.dayShort)} ${Formats.time(at, zone)}"
        }
        val missedHere = e.status == AgendaStatus.MISSED && dayPrefix == null
        val detail = when {
            log == null -> {
                val dose = compound?.let { describeDose(occ!!.dose, it.baseUnit, formulationFor(occ.item.formulation, it)) } ?: ""
                listOfNotNull(dayPrefix, "Missed".takeIf { missedHere }, dose, pins[occ!!.key]).joinToString(" · ")
            }
            log.status == LogStatus.SKIPPED -> listOfNotNull(dayPrefix, "Skipped").joinToString(" · ")
            else -> {
                val amount = "${formatNumber(log.amount.value, 3)} ${log.amount.unit.label}"
                val planned = log.plannedAmount?.takeIf { log.adjusted }?.let { " (plan ${formatNumber(it.value, 2)} ${it.unit.label})" }.orEmpty()
                listOfNotNull(dayPrefix, "$amount$planned · taken ${taken(log.takenAt)}").joinToString(" · ")
            }
        }
        val state = when (log?.status) {
            null -> if (e.status == AgendaStatus.MISSED) CheckState.MISSED else CheckState.PENDING
            LogStatus.TAKEN -> CheckState.TAKEN
            LogStatus.SKIPPED -> CheckState.SKIPPED
        }
        return DoseItem(
            entry = e, compound = compound,
            commonName = compound?.commonName ?: "",
            name = compound?.name ?: log?.snapshot?.displayName ?: "Unknown",
            category = compound?.category ?: log?.snapshot?.category,
            detail = detail, state = state,
        )
    }

    /**
     * Ends the rows of one card with their site. Taken doses show the recorded one; pending injectables show the
     * compound's suggestion from [suggestions], on its first pending row only, so Log all never records a site twice.
     * Other days' rows do not call this: they show and record no site.
     */
    private fun List<DoseItem>.withSites(suggestions: Map<String, String>): List<DoseItem> {
        val suggested = HashSet<String>()
        return map { item ->
            val site = when (item.state) {
                CheckState.TAKEN -> item.entry.log?.site?.takeIf { it.isNotBlank() }
                CheckState.PENDING, CheckState.MISSED -> item.compound?.takeIf { it.route == Route.INJECTION && suggested.add(it.id) }?.let { suggestions[it.id] }
                CheckState.SKIPPED -> null
            }
            if (site == null) item else item.copy(site = site, detail = "${item.detail} · ${InjectionSites.label(site)}")
        }
    }

    private fun earlierDay(occ: Occurrence): String {
        val day = occ.localDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return listOfNotNull(day, occ.slot?.label).joinToString(" ")
    }

    /** "injection 2 of 2" for weekly-dosed injections, counted Monday to Sunday. */
    private fun pinNumbers(protocol: Protocol, weekStart: LocalDate, zone: ZoneId, anchors: IntervalAnchors, settings: Settings): Map<String, String> {
        val weekly = protocol.items.filter { item ->
            item.doseBasis == DoseBasis.PER_WEEK && protocol.compounds[item.compoundId]?.route == Route.INJECTION
        }
        if (weekly.isEmpty()) return emptyMap()
        val from = weekStart.atStartOfDay(zone).toInstant()
        val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
        return occurrences(protocol.phases, weekly, from, to, zone, anchors, settings.slotTimes).groupBy { it.item.id }
            .filterValues { it.size > 1 }
            .flatMap { (_, occs) -> occs.mapIndexed { i, o -> o.key to "injection ${i + 1} of ${occs.size}" } }
            .toMap()
    }

    fun formulationFor(item: Formulation, compound: Compound) = Formulation(
        perMl = item.perMl ?: compound.defaultFormulation.perMl,
        perTablet = item.perTablet ?: compound.defaultFormulation.perTablet,
    )

    fun targetFor(item: DoseItem, partLabel: String): LogTarget? {
        val occ = item.entry.occurrence ?: return null
        val compound = item.compound ?: return null
        return LogTarget.Scheduled(occ, compound, item.entry.log, partLabel)
    }

    /** The round check: log the planned dose, or remove an existing entry (with undo). */
    fun check(item: DoseItem) {
        val log = item.entry.log
        val label = item.commonName.ifBlank { item.name.replaceFirstChar { it.titlecase() } }
        if (log != null) {
            viewModelScope.launch {
                val removed = c.repository.deleteLog(log.id) ?: return@launch
                _messages.emit(UiMessage("$label unchecked") { c.repository.restoreLog(removed) })
            }
            return
        }
        val occ = item.entry.occurrence ?: return
        launchLogged("$label taken${item.siteSuffix}") { listOf(c.doseActions.take(occ, site = item.siteWrite)) }
    }

    /** Log all: each pending dose with the site its row shows. */
    fun logGroup(group: GroupUi) {
        val pending = group.items.filter { it.state.open }.mapNotNull { item -> item.entry.occurrence?.let { it to item.siteWrite } }
        if (pending.isEmpty()) return
        launchLogged("${pending.size} doses taken") { pending.map { (occ, site) -> c.doseActions.take(occ, site = site) } }
    }

    fun logMissedAsTaken(item: DoseItem) {
        val occ = item.entry.occurrence ?: return
        // A dose that restarts its interval is recorded now, so the next one is counted from today.
        val takenAt = if (occ.item.schedule.followsLastDose) c.clock() else occ.at
        launchLogged("${item.commonName.ifBlank { item.name }} logged${item.siteSuffix}") { listOf(c.doseActions.take(occ, takenAt = takenAt, site = item.siteWrite)) }
    }

    /** Logs or re-logs a scheduled dose; undo restores the previous entry when one existed. */
    fun saveScheduled(target: LogTarget.Scheduled, amount: Amount, takenAt: Instant, note: String, site: SiteWrite = SiteWrite.Keep) = viewModelScope.launch {
        val log = c.doseActions.take(target.occurrence, takenAt, amount, note, site)
        _messages.emit(UiMessage("${shortName(target.compound)} logged") { undoEdit(log, target.existing) })
    }

    fun skipScheduled(target: LogTarget.Scheduled, note: String) = viewModelScope.launch {
        val log = c.doseActions.skip(target.occurrence, note)
        _messages.emit(UiMessage("${shortName(target.compound)} skipped") { undoEdit(log, target.existing) })
    }

    private suspend fun undoEdit(log: DoseLog, previous: DoseLog?) {
        if (previous != null) c.repository.restoreLog(previous) else c.repository.deleteLog(log.id)
    }

    private fun shortName(compound: Compound) = compound.commonName.ifBlank { compound.displayName }

    fun logUnscheduled(compound: Compound, amount: Amount, takenAt: Instant, note: String, site: SiteWrite = SiteWrite.Keep) {
        launchLogged("${shortName(compound)} logged") {
            listOf(c.repository.logUnscheduled(compound, amount, compound.defaultFormulation, takenAt, note, site))
        }
    }

    fun saveBloodPressure(systolic: Int, diastolic: Int, pulse: Int?, at: Instant, note: String) = viewModelScope.launch {
        val entry = JournalEntry.BloodPressure(TrackerRepository.newId(), at, systolic, diastolic, pulse, note.trim(), c.clock())
        c.repository.saveJournal(entry)
        _messages.emit(UiMessage("Blood pressure saved") { c.repository.deleteJournal(entry.id) })
    }

    fun saveNote(text: String, at: Instant) = viewModelScope.launch {
        val entry = JournalEntry.Note(TrackerRepository.newId(), at, text.trim(), c.clock())
        c.repository.saveJournal(entry)
        _messages.emit(UiMessage("Note saved") { c.repository.deleteJournal(entry.id) })
    }

    fun saveSymptoms(input: SymptomInput) = viewModelScope.launch {
        val entry = input.toEntry(TrackerRepository.newId(), c.clock())
        c.repository.saveJournal(entry)
        _messages.emit(UiMessage("Symptoms saved") { c.repository.deleteJournal(entry.id) })
    }

    fun saveBloodwork(input: BloodworkInput) = viewModelScope.launch {
        val entry = input.toEntry(TrackerRepository.newId(), c.clock())
        c.repository.saveJournal(entry)
        _messages.emit(UiMessage("Bloodwork saved") { c.repository.deleteJournal(entry.id) })
    }

    /** Saves an edited dose; Undo puts [previous] back. */
    fun saveEdit(log: DoseLog, previous: DoseLog) = viewModelScope.launch {
        c.repository.updateLog(log)
        val commonName = state.value.compounds.firstOrNull { it.id == log.compoundId }?.commonName
        _messages.emit(UiMessage("${log.snapshot.shortName(commonName)} saved") { c.repository.restoreLog(previous) })
    }

    fun deleteLog(log: DoseLog) = viewModelScope.launch {
        val removed = c.repository.deleteLog(log.id) ?: return@launch
        _messages.emit(UiMessage("Entry deleted") { c.repository.restoreLog(removed) })
    }

    /** Saves an edited journal entry (same id and creation time), as Journal does. */
    fun updateEntry(entry: JournalEntry) = viewModelScope.launch { c.repository.saveJournal(entry) }

    fun deleteJournal(entry: JournalEntry) = viewModelScope.launch {
        val removed = c.repository.deleteJournal(entry.id) ?: return@launch
        _messages.emit(UiMessage("Entry deleted") { c.repository.saveJournal(removed) })
    }

    fun now(): Instant = c.clock()
    fun zone(): ZoneId = c.zone()

    private fun launchLogged(message: String, block: suspend () -> List<DoseLog>) {
        viewModelScope.launch {
            val logs = block()
            if (logs.isNotEmpty()) _messages.emit(UiMessage(message) { c.doseActions.undo(logs) })
        }
    }
}
