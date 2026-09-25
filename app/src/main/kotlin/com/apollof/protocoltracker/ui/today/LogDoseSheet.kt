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
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LogStatus
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
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.TimePickDialog
import com.apollof.protocoltracker.ui.components.UnitSelector
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.components.unitsFor
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.PlexMono
import com.apollof.protocoltracker.ui.theme.PlexSans
import com.apollof.protocoltracker.ui.theme.Tracker
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Log one dose. Scheduled doses start at the plan's amount and can be adjusted for this dose only;
 * unscheduled doses start with a compound picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDoseSheet(
    target: LogTarget,
    compounds: List<Compound>,
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSaveScheduled: (LogTarget.Scheduled, Amount, Instant, String) -> Unit,
    onSkip: (LogTarget.Scheduled, String) -> Unit,
    onSaveUnscheduled: (Compound, Amount, Instant, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picked by remember { mutableStateOf((target as? LogTarget.Unscheduled)?.compound) }
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
                initialTime = target.existing?.takenAt
                    ?: if (target.occurrence.localDate == now.atZone(zone).toLocalDate()) now else target.occurrence.at,
                initialNote = target.existing?.note.orEmpty(),
                canSkip = target.existing == null,
                saveLabel = if (target.existing != null) "Save" else null,
                zone = zone,
                now = now,
                onCancel = onDismiss,
                onSkip = { note -> onSkip(target, note) },
                onSave = { amount, at, note -> onSaveScheduled(target, amount, at, note) },
            )
            is LogTarget.Unscheduled -> {
                val compound = picked
                if (compound == null) {
                    CompoundPicker(compounds, onPick = { picked = it })
                } else {
                    DoseForm(
                        compound = compound,
                        heading = "Log extra dose",
                        planned = null,
                        formulation = compound.defaultFormulation,
                        initialAmount = null,
                        initialTime = now,
                        initialNote = "",
                        canSkip = false,
                        saveLabel = null,
                        zone = zone,
                        now = now,
                        onCancel = { picked = null },
                        onSkip = {},
                        onSave = { amount, at, note -> onSaveUnscheduled(compound, amount, at, note) },
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
    canSkip: Boolean,
    saveLabel: String?,
    zone: ZoneId,
    now: Instant,
    onCancel: () -> Unit,
    onSkip: (String) -> Unit,
    onSave: (Amount, Instant, String) -> Unit,
    cancelLabel: String = "Cancel",
) {
    val c = Tracker.colors
    val units = unitsFor(compound.baseUnit, formulation)
    var unit by remember { mutableStateOf(initialAmount?.unit ?: planned?.unit ?: if (compound.baseUnit == BaseUnit.IU) DoseUnit.IU else DoseUnit.MG) }
    var text by remember { mutableStateOf(initialAmount?.let { formatNumber(it.value, 4) } ?: "") }
    var time by remember { mutableStateOf(initialTime) }
    var usingNow by remember { mutableStateOf(abs(initialTime.epochSecond - now.epochSecond) < 60) }
    var pickTime by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(initialNote) }

    val value = text.toDecimal()?.takeIf { it > 0 }
    val amount = value?.let { Amount(it, unit) }
    val base = amount?.let { toBaseOrNull(it, compound.baseUnit, formulation) }
    val sameUnitPlan = planned?.takeIf { it.unit == unit }
    val steps = DoseAdjust.steps(sameUnitPlan ?: amount ?: Amount(1.0, unit), formulation)
    val reference = sameUnitPlan ?: amount

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
                Text(heading.uppercase(), style = NumericStyle.copy(fontSize = 12.sp, letterSpacing = 0.5.sp), color = c.muted)
                CompoundName(compound.commonName, compound.name, size = 20)
                if (planned != null) {
                    Text("Plan: ${describeDose(planned, compound.baseUnit, formulation)}", style = NumericStyle, color = c.body2)
                }
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

        if (sameUnitPlan != null && value != null && abs(value - sameUnitPlan.value) > 1e-9) {
            val diff = value - sameUnitPlan.value
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (diff > 0) "+" else "−"}${formatNumber(abs(diff), 3)} ${unit.label}",
                    style = NumericStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = c.accentText,
                )
                Text(" vs plan. Only this dose changes.", fontSize = 14.sp, color = c.body2)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip("Now · ${Formats.time(now, zone)}", usingNow, modifier = Modifier.weight(1f)) { usingNow = true; time = now }
                QuickChip(if (usingNow) "Earlier…" else Formats.time(time, zone), !usingNow, modifier = Modifier.weight(1f)) { pickTime = true }
            }
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(), maxLines = 4,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canSkip) SecondaryButton("Skip", { onSkip(note.trim()) }, Modifier.weight(1f))
            else SecondaryButton(cancelLabel, onCancel, Modifier.weight(1f))
            val label = saveLabel ?: amount?.let { "Log ${formatNumber(it.value, 3)} ${it.unit.label}" } ?: "Log"
            PrimaryButton(label, { amount?.let { onSave(it, if (usingNow) now else time, note.trim()) } }, Modifier.weight(2f), Icons.Outlined.Check, enabled = amount != null)
        }
    }

    if (pickTime) {
        val local = time.atZone(zone)
        TimePickDialog(
            initial = LocalTime.of(local.hour, local.minute),
            onDismiss = { pickTime = false },
            onConfirm = { t ->
                time = local.toLocalDate().atTime(t).atZone(zone).toInstant()
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

/** Selectable pill; selection is shown by border, fill and weight, and announced. */
@Composable
fun QuickChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, mono: Boolean = false, onClick: () -> Unit) {
    val c = Tracker.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(c.accentSoft).border(BorderStroke(1.5.dp, c.accent), shape)
                else Modifier.border(BorderStroke(1.dp, c.line), shape),
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontFamily = if (mono) PlexMono else PlexSans, fontSize = 14.sp, maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> c.muted.copy(alpha = 0.5f)
                selected -> c.accentText
                else -> c.ink
            },
        )
    }
}
