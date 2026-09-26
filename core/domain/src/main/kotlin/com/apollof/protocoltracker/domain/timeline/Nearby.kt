package com.apollof.protocoltracker.domain.timeline

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import java.time.Duration

/** Something logged near a point on a level chart: a dose of the charted group or a journal entry. */
sealed interface NearbyItem {
    val atMs: Long

    data class Dose(val log: DoseLog) : NearbyItem {
        override val atMs: Long get() = log.takenAt.toEpochMilli()
    }

    data class Entry(val entry: JournalEntry) : NearbyItem {
        override val atMs: Long get() = entry.at.toEpochMilli()
    }
}

/**
 * What was logged around one moment. [items] are within the window, nearest first; when nothing is that close,
 * [items] holds the single nearest item and [withinWindow] is false.
 */
data class Nearby(
    val atMs: Long,
    val items: List<NearbyItem>,
    val withinWindow: Boolean,
    /** Latest taken dose of the group at or before the moment. */
    val lastDose: DoseLog?,
    /** Latest blood pressure reading at or before the moment (carried forward), if within [Timeline.BP_CARRY]. */
    val bloodPressure: JournalEntry.BloodPressure?,
)

/**
 * Logs sorted by time for quick lookups while scrubbing a chart. Build once per data change; [near] is a binary
 * search plus a short scan, cheap enough to call on every drag frame.
 */
class Timeline(logs: List<DoseLog>, journal: List<JournalEntry>) {
    private val doses: List<DoseLog> = logs.filter { it.status == LogStatus.TAKEN }.sortedBy { it.takenAt }
    private val doseTimes = LongArray(doses.size) { doses[it].takenAt.toEpochMilli() }
    private val entries: List<JournalEntry> = journal.sortedBy { it.at }
    private val entryTimes = LongArray(entries.size) { entries[it].at.toEpochMilli() }
    private val readings = entries.filterIsInstance<JournalEntry.BloodPressure>()
    private val readingTimes = LongArray(readings.size) { readings[it].at.toEpochMilli() }

    val isEmpty: Boolean get() = doses.isEmpty() && entries.isEmpty()

    /** Sorted times of the group's taken doses and all journal entries: the points a scrub "feels". */
    fun marks(group: String?): LongArray {
        val doseMarks = doses.indices.filter { group == null || doses[it].snapshot.group == group }.map { doseTimes[it] }
        return (doseMarks + entryTimes.toList()).sorted().toLongArray()
    }

    /** Whether any of [marks] lies between two cursor positions (either direction, end inclusive). */
    fun crosses(marks: LongArray, from: Long, to: Long): Boolean {
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        if (lo == hi) return false
        val first = firstAfter(marks, lo)
        return first < marks.size && marks[first] <= hi
    }

    fun near(atMs: Long, group: String?, window: Duration = WINDOW, limit: Int = 6): Nearby {
        val w = window.toMillis()
        val groupDoses = { d: DoseLog -> group == null || d.snapshot.group == group }
        val close = ArrayList<NearbyItem>()
        for (i in range(doseTimes, atMs - w, atMs + w)) if (groupDoses(doses[i])) close += NearbyItem.Dose(doses[i])
        for (i in range(entryTimes, atMs - w, atMs + w)) close += NearbyItem.Entry(entries[i])
        close.sortBy { kotlin.math.abs(it.atMs - atMs) }

        val items = if (close.isNotEmpty()) close.take(limit) else listOfNotNull(nearestOutside(atMs, groupDoses))
        val lastIndex = lastAtOrBefore(doseTimes, atMs)
        var lastDose: DoseLog? = null
        for (i in lastIndex downTo 0) if (groupDoses(doses[i])) { lastDose = doses[i]; break }
        val bp = lastAtOrBefore(readingTimes, atMs).takeIf { it >= 0 }?.let { readings[it] }
            ?.takeIf { atMs - it.at.toEpochMilli() <= BP_CARRY.toMillis() }
        return Nearby(atMs, items, close.isNotEmpty(), lastDose, bp)
    }

    private fun nearestOutside(atMs: Long, keep: (DoseLog) -> Boolean): NearbyItem? {
        val candidates = ArrayList<NearbyItem>(4)
        // Closest entry on each side.
        lastAtOrBefore(entryTimes, atMs).takeIf { it >= 0 }?.let { candidates += NearbyItem.Entry(entries[it]) }
        firstAfter(entryTimes, atMs).takeIf { it < entries.size }?.let { candidates += NearbyItem.Entry(entries[it]) }
        // Closest matching dose on each side.
        var i = lastAtOrBefore(doseTimes, atMs)
        while (i >= 0 && !keep(doses[i])) i--
        if (i >= 0) candidates += NearbyItem.Dose(doses[i])
        var j = firstAfter(doseTimes, atMs)
        while (j < doses.size && !keep(doses[j])) j++
        if (j < doses.size) candidates += NearbyItem.Dose(doses[j])
        return candidates.minByOrNull { kotlin.math.abs(it.atMs - atMs) }
    }

    companion object {
        val WINDOW: Duration = Duration.ofDays(2)
        val BP_CARRY: Duration = Duration.ofDays(30)

        /** Index of the last time ≤ [t], or -1. */
        internal fun lastAtOrBefore(times: LongArray, t: Long): Int {
            var lo = 0
            var hi = times.size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (times[mid] <= t) { found = mid; lo = mid + 1 } else hi = mid - 1
            }
            return found
        }

        /** Index of the first time > [t], or times.size. */
        internal fun firstAfter(times: LongArray, t: Long): Int = lastAtOrBefore(times, t) + 1

        /** Indices with [from] ≤ time ≤ [to]. */
        internal fun range(times: LongArray, from: Long, to: Long): IntRange {
            val start = lastAtOrBefore(times, from - 1) + 1
            val end = lastAtOrBefore(times, to)
            return start..end
        }
    }
}
