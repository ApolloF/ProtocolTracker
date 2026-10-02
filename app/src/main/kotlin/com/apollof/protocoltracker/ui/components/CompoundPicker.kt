package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.compoundOrder
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/**
 * Searchable compound list in section order: injectable steroids, oral steroids, support, peptides. [pinned] compounds
 * (for example those in the plan) come first under "In your plan" while nothing is searched.
 */
@Composable
fun CompoundPicker(
    compounds: List<Compound>,
    onPick: (Compound) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Choose compound",
    footer: (@Composable () -> Unit)? = null,
    pinned: Set<String> = emptySet(),
) {
    val c = Tracker.colors
    var query by remember { mutableStateOf("") }
    val shown = compounds.filter { !it.archived }
        .filter { q -> query.isBlank() || listOf(q.displayName, q.group).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compoundOrder)
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = c.ink)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            val top = if (query.isBlank()) shown.filter { it.id in pinned } else emptyList()
            if (top.isNotEmpty()) {
                item(key = "h-pinned") { SectionLabel("In your plan", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(top, key = { "p-${it.id}" }) { compound -> PickerRow(compound, onPick) }
            }
            for (category in CompoundCategory.entries) {
                val section = shown.filter { it.category == category && it !in top }
                if (section.isEmpty()) continue
                item(key = "h-$category") { SectionLabel(category.plural, Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(section, key = { it.id }) { compound -> PickerRow(compound, onPick) }
            }
            if (shown.isEmpty()) item { Text("No compound matches \"$query\".", color = c.muted, modifier = Modifier.padding(vertical = 16.dp)) }
        }
        footer?.invoke()
    }
}

@Composable
private fun PickerRow(compound: Compound, onPick: (Compound) -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(compound) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CompoundName(compound.commonName, compound.name, size = 15)
            val sub = listOfNotNull(compound.supportKind?.label, if (compound.pk == null) "no level data" else null).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = TrackerType.numericSmall, color = c.muted)
        }
    }
    RowDivider()
}
