package com.apollof.protocoltracker.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.HAIR_SHEDDING_LABELS
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerCategory
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.SymptomCatalog
import com.apollof.protocoltracker.domain.model.SymptomGroup
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import com.apollof.protocoltracker.ui.today.TimeChoice
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Values of a symptom log as entered. */
data class SymptomInput(val symptoms: List<String>, val mood: Int?, val hairShedding: Int?, val note: String, val at: Instant)

/** Values of a bloodwork entry as entered, results already in stored (conventional) units. */
data class BloodworkInput(val results: List<MarkerResult>, val lab: String, val note: String, val at: Instant)

/** Symptom log (dev build): tick symptoms, optionally mood and hair shedding, a note and the time. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SymptomSheet(
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSave: (SymptomInput) -> Unit,
    existing: JournalEntry.Symptoms? = null,
) {
    val selected = remember { mutableStateMapOf<String, Boolean>().apply { existing?.symptoms?.forEach { put(it, true) } } }
    var mood by remember { mutableStateOf(existing?.mood) }
    var hair by remember { mutableStateOf(existing?.hairShedding) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var time by remember { mutableStateOf(existing?.at) }
    val c = Tracker.colors
    val keys = SymptomCatalog.all.map { it.key }.filter { selected[it] == true } +
        existing?.symptoms.orEmpty().filter { SymptomCatalog.find(it) == null }
    val canSave = keys.isNotEmpty() || mood != null || hair != null || note.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Symptoms", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(
                "Low and high estrogen signs overlap. Bloodwork is the way to tell them apart.",
                style = TrackerType.caption, color = c.muted,
            )
            SymptomGroup.entries.forEach { group ->
                val inGroup = SymptomCatalog.all.filter { it.group == group }
                val count = inGroup.count { selected[it.key] == true }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionLabel(if (count > 0) "${group.label} · $count" else group.label)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        inGroup.forEach { s ->
                            QuickChip(s.label, selected[s.key] == true, role = Role.Checkbox) { selected[s.key] = selected[s.key] != true }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("Mood (optional) · 1 low, 10 great")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    (1..10).forEach { n -> QuickChip("$n", mood == n, mono = true) { mood = if (mood == n) null else n } }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("Hair shedding (optional)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HAIR_SHEDDING_LABELS.forEachIndexed { i, label ->
                        val level = i + 1
                        QuickChip(label, hair == level) { hair = if (hair == level) null else level }
                    }
                }
            }
            OutlinedTextField(
                note, { if (it.length <= JournalEntry.MAX_NOTE_LENGTH) note = it }, label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            )
            TimeChoice(now, zone, time) { time = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    "Save", { onSave(SymptomInput(keys, mood, hair, note.trim(), time ?: now)) }, Modifier.weight(2f), Icons.Outlined.Check, enabled = canSave,
                )
            }
            if (!canSave) Text("Choose a symptom or add a note.", style = TrackerType.caption, color = c.muted)
        }
    }
}

/** Bloodwork (dev build): results of one blood draw, entered in either unit system. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodworkSheet(
    now: Instant,
    zone: ZoneId,
    defaultUnits: LabUnits,
    onDismiss: () -> Unit,
    onSave: (BloodworkInput) -> Unit,
    existing: JournalEntry.Bloodwork? = null,
) {
    var units by remember { mutableStateOf(defaultUnits) }
    val start = (existing?.at ?: now).atZone(zone)
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(LocalTime.of(start.hour, start.minute)) }
    var lab by remember { mutableStateOf(existing?.lab ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    // Raw text per marker, in the units currently selected.
    val texts = remember {
        mutableStateMapOf<String, String>().apply {
            existing?.results?.forEach { r ->
                BloodMarkers.find(r.marker)?.let { put(r.marker, formatNumber(it.fromStored(r.value, defaultUnits), 3)) }
            }
        }
    }
    val c = Tracker.colors
    val results = BloodMarkers.all.mapNotNull { m ->
        val v = texts[m.key]?.toDecimal() ?: return@mapNotNull null
        MarkerResult(m.key, m.toStored(v, units))
    } + existing?.results.orEmpty().filter { BloodMarkers.find(it.marker) == null }

    fun switchUnits(next: LabUnits) {
        if (next == units) return
        // Keep the entered values: convert what is typed to the other system.
        BloodMarkers.all.forEach { m ->
            val v = texts[m.key]?.toDecimal() ?: return@forEach
            texts[m.key] = formatNumber(m.fromStored(m.toStored(v, units), next), 3)
        }
        units = next
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Bloodwork", style = MaterialTheme.typography.titleLarge, color = c.ink)
            FieldRow {
                DateField("Blood draw", date, { if (it != null) date = it }, Modifier.weight(1.3f))
                TimeField("Time", time, { time = it }, Modifier.weight(1f))
            }
            OutlinedTextField(lab, { lab = it.take(80) }, label = { Text("Lab (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("Units on the lab report")
                Segmented(LabUnits.entries, units, { it.label }) { switchUnits(it) }
            }
            Text("Fill in the results you have; leave the rest empty. Reference ranges are typical adult male ranges; your lab's can differ.",
                style = TrackerType.caption, color = c.muted)
            MarkerCategory.entries.forEach { category ->
                val markers = BloodMarkers.all.filter { it.category == category }
                if (markers.isEmpty()) return@forEach
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionLabel(category.label)
                    markers.forEach { m ->
                        NumberField(
                            m.name, texts[m.key] ?: "", { texts[m.key] = it }, Modifier.fillMaxWidth(), suffix = m.unitFor(units),
                            error = null,
                        )
                        m.referenceText(units)?.let { Text("Reference $it", style = TrackerType.caption, color = c.muted, modifier = Modifier.padding(start = 4.dp)) }
                    }
                }
            }
            OutlinedTextField(
                note, { if (it.length <= JournalEntry.MAX_NOTE_LENGTH) note = it }, label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(), maxLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    "Save", { onSave(BloodworkInput(results, lab.trim(), note.trim(), date.atTime(time).atZone(zone).toInstant())) },
                    Modifier.weight(2f), Icons.Outlined.Check, enabled = results.isNotEmpty(),
                )
            }
            if (results.isEmpty()) Text("Enter at least one result.", style = TrackerType.caption, color = c.muted)
        }
    }
}

fun SymptomInput.toEntry(id: String, createdAt: Instant) = JournalEntry.Symptoms(id, at, symptoms, mood, hairShedding, note, createdAt)

fun BloodworkInput.toEntry(id: String, createdAt: Instant) = JournalEntry.Bloodwork(id, at, results, lab, note, createdAt)
