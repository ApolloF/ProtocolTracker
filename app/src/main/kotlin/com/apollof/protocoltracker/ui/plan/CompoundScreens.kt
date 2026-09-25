package com.apollof.protocoltracker.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.pk.CompoundColors
import com.apollof.protocoltracker.domain.pk.PkEngine
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.units.formatNumber
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ColorDot
import com.apollof.protocoltracker.ui.components.FieldRow
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.NumberField
import com.apollof.protocoltracker.ui.components.Segmented
import com.apollof.protocoltracker.ui.components.SectionHeader
import com.apollof.protocoltracker.ui.components.toDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompoundsViewModel(private val c: AppContainer) : ViewModel() {
    val compounds: StateFlow<List<Compound>?> = c.repository.compounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(compound: Compound) = viewModelScope.launch { c.repository.saveCompound(compound) }
    fun delete(compound: Compound) = viewModelScope.launch { c.repository.deleteCompound(compound) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompoundsScreen(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val vm = appViewModel { CompoundsViewModel(it) }
    val compounds by vm.compounds.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compounds") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onEdit(null) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Custom") })
        },
    ) { padding ->
        val grouped = compounds.orEmpty().filter { !it.archived }.groupBy { it.category }.toSortedMap()
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item { Text("Half-lives are estimates used for the Levels chart. Tap to edit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            grouped.forEach { (category, list) ->
                item(key = "h-$category") { SectionHeader(category.label) }
                items(list, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClickLabel = "Edit") { onEdit(c.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(c.colorArgb)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "t½ ${Formats.halfLife(c.pk.eliminationHalfLifeH)} · peak ≈ ${Formats.halfLife(PkEngine.tmaxHours(c.pk.ka, c.pk.ke))}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!c.isPreset) Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
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
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var group by remember(existing) { mutableStateOf(existing?.group ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: CompoundCategory.INJECTABLE) }
    var baseUnit by remember(existing) { mutableStateOf(existing?.baseUnit ?: BaseUnit.MG) }
    var color by remember(existing) { mutableLongStateOf(existing?.colorArgb ?: CompoundColors.palette[list.size % CompoundColors.palette.size]) }
    var absorption by remember(existing) { mutableStateOf(existing?.pk?.absorptionHalfLifeH?.let { formatNumber(it, 2) } ?: "") }
    var elimination by remember(existing) { mutableStateOf(existing?.pk?.eliminationHalfLifeH?.let { formatNumber(it, 2) } ?: "") }
    var fraction by remember(existing) { mutableStateOf(formatNumber(existing?.pk?.activeFraction ?: 1.0, 3)) }
    var bioavailability by remember(existing) { mutableStateOf(formatNumber(existing?.pk?.bioavailability ?: 1.0, 3)) }
    var perMl by remember(existing) { mutableStateOf(existing?.defaultFormulation?.perMl?.let { formatNumber(it) } ?: "") }
    var perTablet by remember(existing) { mutableStateOf(existing?.defaultFormulation?.perTablet?.let { formatNumber(it) } ?: "") }

    val pk = runCatching {
        PkParams(absorption.toDecimal()!!, elimination.toDecimal()!!, fraction.toDecimal()!!, bioavailability.toDecimal()!!)
    }.getOrNull()
    val valid = name.isNotBlank() && pk != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New compound" else "Edit compound") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (existing != null) IconButton(onClick = { vm.delete(existing); onDone() }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete compound") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                group, { group = it }, label = { Text("Level group") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Compounds with the same group add up on one Levels chart, e.g. all testosterone esters.") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompoundCategory.entries.forEach { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat.label) }) }
            }
            Segmented(BaseUnit.entries, baseUnit, { if (it == BaseUnit.MG) "Mass (mg)" else "Units (IU)" }) { baseUnit = it }

            SectionHeader("Kinetics")
            FieldRow {
                NumberField("Absorption t½", absorption, { absorption = it }, Modifier.weight(1f), suffix = "h")
                NumberField("Elimination t½", elimination, { elimination = it }, Modifier.weight(1f), suffix = "h")
            }
            FieldRow {
                NumberField("Active fraction", fraction, { fraction = it }, Modifier.weight(1f))
                NumberField("Bioavailability", bioavailability, { bioavailability = it }, Modifier.weight(1f))
            }
            Text(
                pk?.let { "Single-dose peak after ≈ ${Formats.halfLife(PkEngine.tmaxHours(it.ka, it.ke))}. Elimination t½ is the apparent terminal half-life, including ester release." }
                    ?: "Half-lives must be positive; fractions between 0 and 1.",
                style = MaterialTheme.typography.bodySmall,
                color = if (pk == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            existing?.sourceNote?.takeIf { it.isNotBlank() }?.let { Text("Source: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val preset = existing?.let { Presets.byId(it.id) }
            if (preset != null && preset != existing) OutlinedButton(onClick = {
                absorption = formatNumber(preset.pk.absorptionHalfLifeH, 2); elimination = formatNumber(preset.pk.eliminationHalfLifeH, 2)
                fraction = formatNumber(preset.pk.activeFraction, 3); bioavailability = formatNumber(preset.pk.bioavailability, 3)
            }) { Text("Reset kinetics to preset") }

            SectionHeader("Defaults")
            FieldRow {
                NumberField("Concentration", perMl, { perMl = it }, Modifier.weight(1f), suffix = "${baseUnit.label}/mL")
                if (baseUnit == BaseUnit.MG) NumberField("Tablet strength", perTablet, { perTablet = it }, Modifier.weight(1f), suffix = "mg")
            }

            SectionHeader("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompoundColors.palette.forEachIndexed { i, c ->
                    Box(Modifier.size(40.dp).clickable { color = c }.semantics { contentDescription = "Colour ${i + 1}"; selected = c == color }, contentAlignment = Alignment.Center) {
                        ColorDot(c, size = if (c == color) 34.dp else 26.dp)
                        if (c == color) ColorDot(0xFFFFFFFF, size = 10.dp)
                    }
                }
            }
            Button(
                enabled = valid, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trimmed = name.trim()
                    vm.save(
                        (existing ?: Compound(
                            id = TrackerRepository.newId(), name = trimmed, group = trimmed, category = category, baseUnit = baseUnit,
                            colorArgb = color, pk = pk!!, isPreset = false,
                        )).copy(
                            name = trimmed, group = group.trim().ifEmpty { trimmed }, category = category, baseUnit = baseUnit, colorArgb = color, pk = pk!!,
                            defaultFormulation = Formulation(perMl.toDecimal()?.takeIf { it > 0 }, perTablet.toDecimal()?.takeIf { it > 0 && baseUnit == BaseUnit.MG }),
                        ),
                    )
                    onDone()
                },
            ) { Text("Save") }
        }
    }
}
