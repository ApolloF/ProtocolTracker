package com.apollof.protocoltracker.ui.levels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Sick
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.SymptomCatalog
import com.apollof.protocoltracker.domain.timeline.NearbyItem
import com.apollof.protocoltracker.domain.timeline.Timeline
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Where the reading cursor is: which chart and which moment. */
data class ChartCursor(val group: String, val atMs: Long)

/** Scrub ticks: per day in windows up to three months, per week (Monday) in longer ones. */
internal fun crossesBoundary(fromMs: Long, toMs: Long, spanMs: Long, zone: ZoneId): Boolean {
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
    val weekly = spanMs > 92L * 86_400_000
    // Epoch day 0 was a Thursday; +3 makes weeks start on Monday.
    fun bucket(ms: Long) = if (weekly) Math.floorDiv(day(ms) + 3, 7L) else day(ms)
    return bucket(fromMs) != bucket(toMs)
}

/** "2 d 4 h before", "35 min after", "same time". */
internal fun relativeOffset(itemMs: Long, cursorMs: Long): String {
    val minutes = abs(itemMs - cursorMs) / 60_000
    if (minutes < 1) return "same time"
    val days = minutes / 1_440
    val hours = (minutes % 1_440) / 60
    val text = when {
        days > 0 -> if (hours > 0 && days < 7) "$days d $hours h" else "$days d"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
    return "$text ${if (itemMs < cursorMs) "before" else "after"}"
}

/**
 * Logs around the cursor (experimental): last dose of the group, blood pressure at that time and entries within
 * two days, nearest first. With nothing that close, the nearest entry with its distance.
 */
@Composable
fun ScrubPanel(timeline: Timeline, cursor: ChartCursor, zone: ZoneId, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    val near = remember(timeline, cursor) { timeline.near(cursor.atMs, cursor.group) }
    Column(
        modifier.fillMaxWidth()
            .padding(top = Spacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            if (near.withinWindow) "LOGGED NEAR ${chartTime(cursor.atMs, zone).uppercase()}" else "NEAREST LOG",
            style = TrackerType.overline, color = c.accentText,
        )
        near.lastDose?.let { d ->
            PanelLine(
                Icons.Outlined.Vaccines, "Last dose: ${d.snapshot.displayName} ${describeDose(d.amount, d.snapshot.baseUnit, d.snapshot.formulation)}",
                relativeOffset(d.takenAt.toEpochMilli(), cursor.atMs),
            )
        }
        near.bloodPressure?.let { bp ->
            PanelLine(Icons.Outlined.MonitorHeart, "Blood pressure ${bp.systolic}/${bp.diastolic}" + (bp.pulse?.let { " · $it bpm" } ?: ""), bp.at.atZone(zone).format(Formats.dayMonth))
        }
        val items = near.items.filterNot { it is NearbyItem.Dose && it.log.id == near.lastDose?.id }
            .filterNot { it is NearbyItem.Entry && it.entry.id == near.bloodPressure?.id }
        if (items.isEmpty() && near.lastDose == null && near.bloodPressure == null) {
            Text("Nothing logged yet.", style = TrackerType.caption, color = c.muted)
        }
        items.forEach { item ->
            val offset = relativeOffset(item.atMs, cursor.atMs)
            when (item) {
                is NearbyItem.Dose -> PanelLine(
                    Icons.Outlined.Vaccines, "${item.log.snapshot.displayName} ${describeDose(item.log.amount, item.log.snapshot.baseUnit, item.log.snapshot.formulation)}", offset,
                )
                is NearbyItem.Entry -> when (val e = item.entry) {
                    is JournalEntry.BloodPressure -> PanelLine(Icons.Outlined.MonitorHeart, "Blood pressure ${e.systolic}/${e.diastolic}", offset)
                    is JournalEntry.Note -> PanelLine(Icons.Outlined.EditNote, e.text, offset)
                    is JournalEntry.Symptoms -> PanelLine(Icons.Outlined.Sick, symptomLine(e), offset)
                    is JournalEntry.Bloodwork -> PanelLine(
                        Icons.Outlined.Bloodtype, "Bloodwork · ${e.results.size} results" + if (e.outOfRange > 0) " · ${e.outOfRange} out of range" else "", offset,
                    )
                }
            }
        }
    }
}

/** "Acne, Night sweats +2 · mood 6/10". */
internal fun symptomLine(e: JournalEntry.Symptoms): String {
    val names = e.symptoms.map(SymptomCatalog::label)
    val shown = names.take(2).joinToString(", ") + if (names.size > 2) " +${names.size - 2}" else ""
    return listOfNotNull(shown.ifEmpty { null }, e.mood?.let { "mood $it/10" }, e.note.takeIf { it.isNotBlank() && names.isEmpty() }).joinToString(" · ")
        .ifEmpty { "Symptoms" }
}

@Composable
private fun PanelLine(icon: ImageVector, text: String, trailing: String) {
    val c = Tracker.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
        Text(text, style = TrackerType.bodySmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(trailing, style = TrackerType.numericSmall, color = c.muted, maxLines = 1)
    }
}
