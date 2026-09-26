package com.apollof.protocoltracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.SupportKind
import com.apollof.protocoltracker.domain.model.compoundOrder
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.ColorSwatchPicker
import com.apollof.protocoltracker.ui.components.CompoundName
import com.apollof.protocoltracker.ui.components.ConfirmDialog
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.toDecimal
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import com.apollof.protocoltracker.ui.components.QuickChip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompoundsViewModel(private val c: AppContainer) : ViewModel() {
    val compounds: StateFlow<List<Compound>?> = c.repository.compounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(compound: Compound) = viewModelScope.launch { c.repository.saveCompound(compound) }
    fun delete(compound: Compound) = viewModelScope.launch { c.repository.deleteCompound(compound) }
}

private fun levelSummary(c: Compound): String {
    val pk = c.pk ?: return "No reliable level data"
    val peak = if (pk.peakPerUnit != null) "peak after ${Formats.halfLife(pk.tmaxH)}" else "relative curve"
    return "t½ ${Formats.halfLife(pk.halfLifeH)} · $peak"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompoundsScreen(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val vm = appViewModel { CompoundsViewModel(it) }
    val compounds by vm.compounds.collectAsStateWithLifecycle()
    val c = Tracker.colors
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text("Compounds") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { PrimaryButton("Custom", { onEdit(null) }, Modifier.padding(end = 8.dp), Icons.Outlined.Add) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg),
            )
        },
    ) { padding ->
        val list = compounds.orEmpty().filter { !it.archived }.sortedWith(compoundOrder)
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item { Text("Level data are estimates used for the Levels chart. Tap a compound to edit it.", style = TrackerType.bodySmall, color = c.muted) }
            for (category in CompoundCategory.entries) {
                val section = list.filter { it.category == category }
                if (section.isEmpty()) continue
                item(key = "h-$category") { SectionLabel(category.plural, Modifier.padding(top = 20.dp, bottom = 4.dp)) }
                items(section, key = { it.id }) { compound ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Edit") { onEdit(compound.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CompoundName(compound.commonName, compound.name, size = 15)
                            Text(
                                listOfNotNull(compound.supportKind?.label, levelSummary(compound)).joinToString(" · "),
                                style = TrackerType.numericSmall, color = c.muted,
                            )
                        }
                        when {
                            !compound.isPreset -> Text("Custom", style = TrackerType.caption, color = c.accentText)
                            compound.edited -> Text("Edited", style = TrackerType.caption, color = c.accentText)
                        }
                    }
                    RowDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompoundEditorScreen(compoundId: String?, onDone: () -> Unit) {
    val vm = appViewModel(key = "compound-$compoundId") { CompoundsViewModel(it) }
    val compounds by vm.compounds.collectAsStateWithLifecycle()
    val list = compounds ?: return
    val existing = list.firstOrNull { it.id == compoundId }
    val t = Tracker.colors
    var commonName by remember(existing) { mutableStateOf(existing?.commonName ?: "") }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var group by remember(existing) { mutableStateOf(existing?.group ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: CompoundCategory.INJECTABLE_STEROID) }
    var supportKind by remember(existing) { mutableStateOf(existing?.supportKind ?: SupportKind.OTHER) }
    var route by remember(existing) { mutableStateOf(existing?.route ?: Route.INJECTION) }
    var baseUnit by remember(existing) { mutableStateOf(existing?.baseUnit ?: BaseUnit.MG) }
    var color by remember(existing) { mutableLongStateOf(existing?.colorArgb ?: CompoundColors.palette[list.size % CompoundColors.palette.size]) }
    var hasLevels by remember(existing) { mutableStateOf(existing?.pk != null || existing == null) }
    var halfLifeDays by remember(existing) { mutableStateOf(existing?.pk?.halfLifeH?.let { formatNumber(it / 24, 3) } ?: "") }
    var tmaxHours by remember(existing) { mutableStateOf(existing?.pk?.tmaxH?.let { formatNumber(it, 2) } ?: "") }
    var peak by remember(existing) { mutableStateOf(existing?.pk?.peakPerUnit?.let { formatNumber(it, 4) } ?: "") }
    var levelUnit by remember(existing) { mutableStateOf(existing?.pk?.levelUnit ?: LevelUnit.NG_DL) }
    var fraction by remember(existing) { mutableStateOf(formatNumber(existing?.pk?.activeFraction ?: 1.0, 4)) }
    var perMl by remember(existing) { mutableStateOf(existing?.defaultFormulation?.perMl?.let { formatNumber(it) } ?: "") }
    var perTablet by remember(existing) { mutableStateOf(existing?.defaultFormulation?.perTablet?.let { formatNumber(it) } ?: "") }

    val pk = if (!hasLevels) null else runCatching {
        PkParams(halfLifeDays.toDecimal()!! * 24, tmaxHours.toDecimal()!!, peak.toDecimal()?.takeIf { it > 0 }, fraction.toDecimal()!!, levelUnit)
    }.getOrNull()
    val valid = (commonName.isNotBlank() || name.isNotBlank()) && (!hasLevels || pk != null)

    fun build(): Compound {
        val scientific = name.trim().ifEmpty { commonName.trim() }
        val base = existing ?: Compound(
            id = TrackerRepository.newId(), name = scientific, group = scientific, category = category, route = route,
            baseUnit = baseUnit, colorArgb = color, pk = pk, isPreset = false,
        )
        return base.copy(
            name = scientific, commonName = commonName.trim(), group = group.trim().ifEmpty { scientific },
            category = category, supportKind = supportKind.takeIf { category == CompoundCategory.SUPPORT }, route = route,
            baseUnit = baseUnit, colorArgb = color, pk = pk,
            defaultFormulation = Formulation(perMl.toDecimal()?.takeIf { it > 0 }, perTablet.toDecimal()?.takeIf { it > 0 && baseUnit == BaseUnit.MG }),
            edited = base.isPreset,
        )
    }

    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = t.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New compound" else "Edit compound") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete compound") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FieldRow {
                OutlinedTextField(commonName, { commonName = it }, label = { Text("Common name") }, placeholder = { Text("e.g. Anavar") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(name, { name = it }, label = { Text("Scientific name") }, placeholder = { Text("e.g. oxandrolone") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                group, { group = it }, label = { Text("Level group") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Compounds with the same group add up on one Levels chart, e.g. all testosterone esters.") },
            )
            SectionLabel("Section")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompoundCategory.entries.forEach { cat -> QuickChip(cat.label, category == cat) { category = cat } }
            }
            if (category == CompoundCategory.SUPPORT) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SupportKind.entries.forEach { kind -> QuickChip(kind.label, supportKind == kind) { supportKind = kind } }
            }
            Segmented(Route.entries, route, { it.label }) { route = it }
            Segmented(BaseUnit.entries, baseUnit, { if (it == BaseUnit.MG) "Mass (mg)" else "Units (IU)" }) { baseUnit = it }

            SectionLabel("Defaults")
            FieldRow {
                NumberField("Strength", perMl, { perMl = it }, Modifier.weight(1f), suffix = "${baseUnit.label}/mL")
                if (baseUnit == BaseUnit.MG) NumberField("Tablet", perTablet, { perTablet = it }, Modifier.weight(1f), suffix = "mg")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Level data")
                    Text("Turn off when there is no reliable data; the compound is then logged without a curve.", style = TrackerType.caption, color = t.muted)
                }
                Switch(checked = hasLevels, onCheckedChange = { hasLevels = it })
            }
            if (hasLevels) {
                FieldRow {
                    NumberField("Half-life", halfLifeDays, { halfLifeDays = it }, Modifier.weight(1f), suffix = "days")
                    NumberField("Time to peak", tmaxHours, { tmaxHours = it }, Modifier.weight(1f), suffix = "h")
                }
                FieldRow {
                    NumberField("Peak per ${baseUnit.label}", peak, { peak = it }, Modifier.weight(1f), suffix = "ng/dL")
                    NumberField("Active fraction", fraction, { fraction = it }, Modifier.weight(1f))
                }
                Segmented(LevelUnit.entries, levelUnit, { it.label }) { levelUnit = it }
                Text(
                    if (pk == null) "Half-life and time to peak must be positive; active fraction between 0 and 1."
                    else "The level rises to its peak at the time to peak, then halves every half-life. Without a peak value the curve is relative (active amount).",
                    style = TrackerType.caption, color = if (pk == null) MaterialTheme.colorScheme.error else t.muted,
                )
            }
            existing?.sourceNote?.takeIf { it.isNotBlank() }?.let { Text("Source: $it", style = TrackerType.caption, color = t.muted) }
            val preset = existing?.let { Presets.byId(it.id) }
            if (preset != null && existing.edited) SecondaryButton("Reset to preset", {
                vm.save(preset.copy(archived = existing.archived)); onDone()
            }, Modifier.fillMaxWidth())

            SectionLabel("Chart colour")
            ColorSwatchPicker(CompoundColors.palette, color, { color = it })
            PrimaryButton("Save", { vm.save(build()); onDone() }, Modifier.fillMaxWidth(), Icons.Outlined.Check, enabled = valid)
        }
    }
    if (confirmDelete && existing != null) ConfirmDialog(
        title = "Delete ${existing.displayName}?",
        text = "Compounds used in the plan or in logged doses are archived instead, so history stays intact.",
        confirm = "Delete",
        onConfirm = { confirmDelete = false; vm.delete(existing); onDone() },
        onDismiss = { confirmDelete = false },
    )
}
