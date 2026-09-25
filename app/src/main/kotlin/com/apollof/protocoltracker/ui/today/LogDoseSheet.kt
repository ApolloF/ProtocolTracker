package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.units.toBaseOrNull
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.DateField
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.TimeField
import com.apollof.protocoltracker.ui.components.UnitSelector
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.components.unitsFor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Logs an unscheduled dose: extra, catch-up or anything not in the plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDoseSheet(compounds: List<Compound>, onDismiss: () -> Unit, onSave: (Compound, Amount, Instant, String) -> Unit) {
    var compound by remember { mutableStateOf(compounds.firstOrNull()) }
    var menuOpen by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var unit by remember(compound) { mutableStateOf(compound?.let { unitsFor(it.baseUnit, it.defaultFormulation).first() }) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log dose", style = MaterialTheme.typography.titleLarge)
            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = compound?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Compound") },
                    leadingIcon = compound?.let { { ColorDot(it.colorArgb) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    compounds.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, leadingIcon = { ColorDot(c.colorArgb) }, onClick = { compound = c; menuOpen = false })
                    }
                }
            }
            val c = compound
            if (c != null) {
                val units = unitsFor(c.baseUnit, c.defaultFormulation)
                NumberField("Amount", amountText, { amountText = it }, Modifier.fillMaxWidth(), suffix = unit?.label)
                UnitSelector(units, unit ?: units.first()) { unit = it }
            }
            FieldRow {
                DateField("Date", date, { if (it != null) date = it }, Modifier.weight(1f))
                TimeField("Time", time, { time = it }, Modifier.weight(1f))
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            val amount = amountText.toDecimal()?.takeIf { it > 0 }?.let { v -> unit?.let { Amount(v, it) } }
            val valid = c != null && amount != null && toBaseOrNull(amount, c.baseUnit, c.defaultFormulation) != null
            Button(
                onClick = { onSave(c!!, amount!!, date.atTime(time).atZone(ZoneId.systemDefault()).toInstant(), note.trim()) },
                enabled = valid, modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
