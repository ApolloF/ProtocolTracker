package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.SiteChoice
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.followsLastDose
import com.apollof.protocoltracker.domain.units.DoseAdjust
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.domain.units.formatVolume
import com.apollof.protocoltracker.domain.units.tablets
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import com.apollof.protocoltracker.domain.units.volumeMl
import com.apollof.protocoltracker.ui.components.CategoryTag
import com.apollof.protocoltracker.ui.components.CompoundName
import com.apollof.protocoltracker.ui.components.CompoundPicker
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.DeleteEntryButton
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.TimePickDialog
import com.apollof.protocoltracker.ui.components.UnitSelector
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.components.unitsFor
import com.apollof.protocoltracker.ui.devOr
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Log one dose. Scheduled doses start at the plan's amount and can be adjusted for this dose only;
 * unscheduled doses start with a compound picker.
 * [sites] (dev) gives an injectable's site choice for a compound id and the dose being edited, and adds the Site row;
 * without it (stable) there is no row and saving keeps a dose's stored site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDoseSheet(
    target: LogTarget,
    compounds: List<Compound>,
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSaveScheduled: (LogTarget.Scheduled, Amount, Instant, String, SiteWrite) -> Unit,
    onSkip: (LogTarget.Scheduled, String) -> Unit,
    onSaveUnscheduled: (Compound, Amount, Instant, String, SiteWrite) -> Unit,
    sites: ((compoundId: String, editing: DoseLog?) -> SiteChoice)? = null,
    /** Dev: the newest taken dose of a compound, so an extra dose starts at that amount and says when it was. */
    lastTaken: ((compoundId: String) -> DoseLog?)? = null,
    /** Compounds listed first in the extra-dose picker (dev: those in the plan). */
    planCompounds: Set<String> = emptySet(),
    /** Dev, [LogTarget.Edit]: the changed log (same id, key and snapshot) and its deletion. */
    onSaveEdit: (DoseLog) -> Unit = {},
    onDelete: ((DoseLog) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf((target as? LogTarget.Unscheduled)?.compound) }
    // Runs an action once, after the sheet has slid away: a second tap can neither save twice nor land on
    // whatever appears underneath (such as the snackbar's Undo).
    var closing by remember { mutableStateOf(false) }
    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    fun siteChoice(compound: Compound, editing: DoseLog?) = sites?.takeIf { compound.route == Route.INJECTION }?.invoke(compound.id, editing)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tracker.colors.surface) {
        when (target) {
            is LogTarget.Scheduled -> DoseForm(
                compound = target.compound,
                heading = "Log dose · ${target.partLabel}",
                planned = target.occurrence.dose,
                formulation = Formulation(
                    perMl = target.occurrence.item.formulation.perMl ?: target.compound.defaultFormulation.perMl,
                    perTablet = target.occurrence.item.formulation.perTablet ?: target.compound.defaultFormulation.perTablet,
                ),
                initialAmount = target.existing?.takeIf { it.status == LogStatus.TAKEN }?.amount ?: target.occurrence.dose,
                // Earlier doses default to their planned time, unless taking one restarts the interval.
                initialTime = target.existing?.takenAt
                    ?: if (target.backfill) target.occurrence.at
                    else if (target.occurrence.localDate == now.atZone(zone).toLocalDate() || target.occurrence.item.schedule.followsLastDose) now
                    else target.occurrence.at,
                initialNote = target.existing?.note.orEmpty(),
                site = siteChoice(target.compound, target.existing),
                canSkip = target.existing == null,
                saveLabel = if (target.existing != null) "Save" else null,
                zone = zone,
                now = now,
                onCancel = onDismiss,
                onSkip = { note -> closeThen { onSkip(target, note) } },
                onSave = { amount, at, note, site -> closeThen { onSaveScheduled(target, amount, at, note, site) } },
            )
            is LogTarget.Edit -> {
                val log = target.log
                val compound = compounds.firstOrNull { it.id == log.compoundId } ?: log.snapshot.asCompound(log.compoundId)
                // Skipping applies to planned doses only.
                var status by remember { mutableStateOf(log.status) }
                DoseForm(
                    compound = compound,
                    heading = "Edit dose",
                    planned = log.plannedAmount,
                    formulation = log.snapshot.formulation,
                    initialAmount = log.amount,
                    initialTime = log.takenAt,
                    initialNote = log.note,
                    site = siteChoice(compound, log),
                    canSkip = false,
                    saveLabel = "Save",
                    zone = zone,
                    now = now,
                    onCancel = onDismiss,
                    onSkip = {},
                    onSave = { amount, at, note, site ->
                        closeThen { onSaveEdit(log.copy(takenAt = at, amount = amount, status = status, note = note, site = site.resolve(log.site))) }
                    },
                    editing = true,
                    status = status.takeIf { log.planItemId != null },
                    onStatus = { status = it },
                    onDelete = onDelete?.let { delete -> { closeThen { delete(log) } } },
                )
            }
            is LogTarget.Unscheduled -> {
                val compound = picked
                if (compound == null) {
                    CompoundPicker(compounds, onPick = { picked = it }, pinned = planCompounds)
                } else {
                    val last = lastTaken?.invoke(compound.id)
                    DoseForm(
                        compound = compound,
                        heading = "Log extra dose",
                        planned = null,
                        formulation = compound.defaultFormulation,
                        initialAmount = last?.amount,
                        lastLine = last?.let {
                            val day = Formats.relativeDay(it.takenAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
                            "Last taken: ${describeDose(it.amount, compound.baseUnit, it.snapshot.formulation)} · $day ${Formats.time(it.takenAt, zone)}"
                        },
                        initialTime = now,
                        initialNote = "",
                        site = siteChoice(compound, null),
                        canSkip = false,
                        saveLabel = null,
                        zone = zone,
                        now = now,
                        onCancel = { picked = null },
                        onSkip = {},
                        onSave = { amount, at, note, site -> closeThen { onSaveUnscheduled(compound, amount, at, note, site) } },
                        cancelLabel = "Back",
                    )
                }
            }
        }
    }
}

@Composable
private fun DoseForm(
    compound: Compound,
    heading: String,
    planned: Amount?,
    formulation: Formulation,
    initialAmount: Amount?,
    initialTime: Instant,
    initialNote: String,
    site: SiteChoice?,
    canSkip: Boolean,
    saveLabel: String?,
    zone: ZoneId,
    now: Instant,
    onCancel: () -> Unit,
    onSkip: (String) -> Unit,
    onSave: (Amount, Instant, String, SiteWrite) -> Unit,
    cancelLabel: String = "Cancel",
    lastLine: String? = null,
    /** Edit mode: date and time fields instead of Now/Earlier, an optional Taken/Skipped [status], "Delete entry". */
    editing: Boolean = false,
    status: LogStatus? = null,
    onStatus: (LogStatus) -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    val c = Tracker.colors
    val units = unitsFor(compound.baseUnit, formulation)
    var unit by remember { mutableStateOf(initialAmount?.unit ?: planned?.unit ?: if (compound.baseUnit == BaseUnit.IU) DoseUnit.IU else DoseUnit.MG) }
    var text by remember { mutableStateOf(initialAmount?.let { formatNumber(it.value, 4) } ?: "") }
    var time by remember { mutableStateOf(initialTime) }
    var usingNow by remember { mutableStateOf(!editing && abs(initialTime.epochSecond - now.epochSecond) < 60) }
    var pickTime by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(initialNote) }
    // Starts again when the history arrives after the sheet opened.
    var chosenSite by remember(site?.initial) { mutableStateOf(site?.initial) }

    val value = text.toDecimal()?.takeIf { it > 0 }
    // Dev: a field that still shows the plan (or the dose being edited) saves that amount, not its rounded copy.
    val amount = value?.let { if (BuildConfig.DEV_FEATURES) DoseAdjust.fromField(it, unit, planned, initialAmount) else Amount(it, unit) }
    val base = amount?.let { toBaseOrNull(it, compound.baseUnit, formulation) }
    val sameUnitPlan = planned?.takeIf { it.unit == unit }
    val steps = DoseAdjust.steps(sameUnitPlan ?: amount ?: Amount(1.0, unit), formulation)
    val reference = sameUnitPlan ?: amount
    // Dev compares the field with the plan as the field shows it, so an unchanged plan reads as no change.
    val planValue = sameUnitPlan?.let { if (BuildConfig.DEV_FEATURES) DoseAdjust.fieldValue(it) else it.value }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .imePadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(heading.uppercase(), style = NumericStyle.copy(fontSize = TrackerType.caption.fontSize, letterSpacing = 0.5.sp), color = c.muted)
                CompoundName(compound.commonName, compound.name, size = 20)
                if (planned != null) {
                    Text("Plan: ${describeDose(planned, compound.baseUnit, formulation)}", style = NumericStyle, color = c.body2)
                }
                lastLine?.let { Text(it, style = NumericStyle, color = c.body2) }
            }
            CategoryTag(compound.category)
        }

        if (units.size > 1 && planned == null) {
            UnitSelector(units, unit) { new ->
                // Keep the same amount of compound when switching units.
                val oldBase = base
                unit = new
                if (oldBase != null) {
                    val converted = when (new) {
                        DoseUnit.MG -> oldBase
                        DoseUnit.MCG -> oldBase * 1000
                        DoseUnit.IU -> oldBase
                        DoseUnit.ML -> formulation.perMl?.let { oldBase / it }
                        DoseUnit.TABLET -> formulation.perTablet?.let { oldBase / it }
                    }
                    text = converted?.let { formatNumber(it, 4) } ?: text
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton(Icons.Outlined.Remove, "Less") { reference?.let { r -> DoseAdjust.apply(amount ?: r, -steps.first)?.let { text = formatNumber(it.value, 4) } } }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("""\d{0,7}([.,]\d{0,4})?"""))) text = v },
                    label = { Text("Dose") },
                    suffix = { Text(unit.label) },
                    singleLine = true,
                    textStyle = NumericStyle.copy(fontSize = 28.sp, textAlign = TextAlign.Center, color = c.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                val conversion = when {
                    base == null -> null
                    unit == DoseUnit.ML || unit == DoseUnit.TABLET -> "= ${formatNumber(base, 2)} ${compound.baseUnit.label}"
                    volumeMl(base, formulation) != null -> "= ${formatVolume(volumeMl(base, formulation)!!)} at ${formatNumber(formulation.perMl!!)} ${compound.baseUnit.label}/mL"
                    tablets(base, formulation) != null -> "= ${formatNumber(tablets(base, formulation)!!, 2)} × ${formatNumber(formulation.perTablet!!)} ${compound.baseUnit.label} tabs"
                    else -> null
                }
                if (conversion != null) Text(conversion, style = NumericStyle, color = c.body2, modifier = Modifier.padding(top = 6.dp))
            }
            StepButton(Icons.Outlined.Add, "More") { reference?.let { r -> DoseAdjust.apply(amount ?: r, steps.first)?.let { text = formatNumber(it.value, 4) } } }
        }

        if (reference != null) {
            val chips = buildList {
                add(-steps.second); add(-steps.first)
                if (sameUnitPlan != null) add(0.0)
                add(steps.first); add(steps.second)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (delta in chips) {
                    val result = if (sameUnitPlan != null) DoseAdjust.apply(sameUnitPlan, delta) else DoseAdjust.apply(reference, delta)
                    val label = if (delta == 0.0) "Plan" else (if (delta > 0) "+" else "−") + formatNumber(abs(delta), 3)
                    val selected = sameUnitPlan != null && result != null && value != null && abs(result.value - value) < 1e-9
                    QuickChip(label, selected, enabled = result != null, modifier = Modifier.weight(1f), mono = true) {
                        if (result != null) text = formatNumber(result.value, 4)
                    }
                }
            }
        }

        if (planValue != null && value != null && abs(value - planValue) > 1e-9) {
            val diff = value - planValue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (diff > 0) "+" else "−"}${formatNumber(abs(diff), 3)} ${unit.label}",
                    style = NumericStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = TrackerType.bodySmall.fontSize), color = c.accentText,
                )
                Text(" vs plan. Only this dose changes.", style = TrackerType.bodySmall, color = c.body2)
            }
        }

        if (site != null) SiteRow(site, chosenSite, now.atZone(zone).toLocalDate(), zone) { chosenSite = it }

        if (status != null) Segmented(LogStatus.entries, status, { if (it == LogStatus.TAKEN) "Taken" else "Skipped" }) { onStatus(it) }

        if (editing) {
            val local = time.atZone(zone)
            FieldRow {
                DateField("Date", local.toLocalDate(), { d -> if (d != null) time = d.atTime(local.toLocalTime()).atZone(zone).toInstant() }, Modifier.weight(1.3f))
                TimeField("Time", local.toLocalTime().withSecond(0).withNano(0), { t -> time = local.toLocalDate().atTime(t).atZone(zone).toInstant() }, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip("Now · ${Formats.time(now, zone)}", usingNow, modifier = Modifier.weight(1f)) { usingNow = true; time = now }
                QuickChip(if (usingNow) "Earlier…" else pickedLabel(time, now, zone), !usingNow, modifier = Modifier.weight(1f)) { pickTime = true }
            }
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(), maxLines = 4,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canSkip) SecondaryButton("Skip", { onSkip(note.trim()) }, Modifier.weight(1f))
            else SecondaryButton(cancelLabel, onCancel, Modifier.weight(1f))
            // Dev rounds like "Plan:" above.
            val label = saveLabel ?: amount?.let { "Log ${formatNumber(it.value, devOr(dev = 2, stable = 3))} ${it.unit.label}" } ?: "Log"
            val siteWrite = if (site == null) SiteWrite.Keep else SiteWrite.Set(chosenSite)
            PrimaryButton(label, { amount?.let { onSave(it, if (usingNow) now else time, note.trim(), siteWrite) } }, Modifier.weight(2f), Icons.Outlined.Check, enabled = amount != null)
        }
        onDelete?.let { DeleteEntryButton(it) }
    }

    if (pickTime) {
        val local = time.atZone(zone)
        TimePickDialog(
            initial = LocalTime.of(local.hour, local.minute),
            onDismiss = { pickTime = false },
            onConfirm = { t ->
                time = pickedAt(local.toLocalDate(), t, now, zone)
                usingNow = false
                pickTime = false
            },
        )
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Tracker.colors
    Box(
        Modifier.size(56.dp).clip(CircleShape).border(1.dp, c.line, CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp)) }
}

/** The compound a log was taken as, for a log whose compound is gone; only injectable steroids get a Site row. */
private fun DoseSnapshot.asCompound(id: String) = Compound(
    id = id, name = displayName, group = group, category = category,
    route = if (category == CompoundCategory.INJECTABLE_STEROID) Route.INJECTION else Route.ORAL,
    baseUnit = baseUnit, colorArgb = 0, pk = pk, defaultFormulation = formulation,
)
