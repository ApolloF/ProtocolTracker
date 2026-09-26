package com.apollof.protocoltracker.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Phase
import com.apollof.protocoltracker.domain.model.timings
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.CompoundName
import com.apollof.protocoltracker.ui.components.ConfirmDialog
import com.apollof.protocoltracker.ui.components.EmptyState
import com.apollof.protocoltracker.ui.components.FigureCell
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.ScreenHeader
import com.apollof.protocoltracker.ui.components.SettingsButton
import com.apollof.protocoltracker.ui.components.WeekSegments
import com.apollof.protocoltracker.ui.components.timingIcon
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Radii
import com.apollof.protocoltracker.ui.theme.SectionLabelStyle
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

@Composable
fun PlanScreen(onOpenSettings: () -> Unit, onEditItem: (itemId: String?, phaseId: String?) -> Unit, onOpenCompounds: () -> Unit) {
    val vm = appViewModel { PlanViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Phase?>(null) }
    var deleting by remember { mutableStateOf<Phase?>(null) }
    var menu by remember { mutableStateOf(false) }
    val c = Tracker.colors

    Scaffold(containerColor = c.bg) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.section, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            item(key = "header") {
                ScreenHeader("Plan", eyebrow = state.cycle?.let { "${it.name} · ${it.range}" }) {
                    PrimaryButton("Add", { onEditItem(null, state.selected?.id) }, icon = Icons.Outlined.Add)
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = c.ink) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("New phase") }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                onClick = { menu = false; editing = vm.newPhase(state.phases.map { it.phase }) })
                            state.selected?.let { phase ->
                                DropdownMenuItem(text = { Text("Edit phase") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; editing = phase })
                                DropdownMenuItem(text = { Text("Delete phase") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; deleting = phase })
                            }
                            DropdownMenuItem(text = { Text("Compounds") }, leadingIcon = { Icon(Icons.Outlined.Science, null) }, onClick = { menu = false; onOpenCompounds() })
                        }
                    }
                    SettingsButton(onOpenSettings)
                }
            }

            if (state.phases.size > 1) item(key = "phases") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.phases.forEach { chip ->
                        PhasePill(chip, selected = chip.phase.id == state.selected?.id) { vm.select(chip.phase.id) }
                    }
                }
            }

            state.cycle?.let { cycle ->
                item(key = "cycle") {
                    LedgerCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(cycle.name, style = TrackerType.title, color = c.ink, modifier = Modifier.weight(1f))
                                Text(cycle.range.uppercase(), style = TrackerType.numericSmall, color = c.muted)
                            }
                            cycle.totalWeeks?.let { WeekSegments(it, cycle.currentWeek ?: if (cycle.note == "Ended") it + 1 else 0) }
                            Text(cycle.note, style = TrackerType.bodySmall, color = c.muted)
                        }
                    }
                }
            }

            if (!state.loading && state.sections.isEmpty()) item(key = "empty") {
                EmptyState(
                    title = if (state.phases.isEmpty()) "No plan yet" else "Nothing planned in this phase",
                    body = "Add each compound with its dose and when you take it. Injectables are planned per week; the app works out each injection.",
                    actionLabel = "Add compound", onAction = { onEditItem(null, state.selected?.id) },
                )
            }

            state.sections.forEach { section ->
                item(key = "s-${section.category}") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(section.category, section.total)
                        if (section.category == CompoundCategory.SUPPORT) {
                            LedgerCard {
                                section.items.forEachIndexed { i, ui ->
                                    if (i > 0) RowDivider()
                                    CompactRow(ui) { onEditItem(ui.item.id, ui.item.phaseId) }
                                }
                            }
                        } else {
                            section.items.forEach { ui -> PlanCard(ui) { onEditItem(ui.item.id, ui.item.phaseId) } }
                        }
                    }
                }
            }
        }
    }

    editing?.let { phase -> PhaseDialog(phase, onDismiss = { editing = null }, onSave = { vm.savePhase(it); editing = null }) }
    deleting?.let { phase ->
        ConfirmDialog(
            title = "Delete ${phase.name.ifBlank { "this phase" }}?",
            text = "Its plan items are removed. Logged doses stay in the journal.",
            confirm = "Delete",
            onConfirm = { vm.deletePhase(phase.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PhasePill(chip: PhaseChip, selected: Boolean, onClick: () -> Unit) =
    QuickChip("${chip.phase.name.ifBlank { "Unnamed" }} · ${chip.status.label}", selected, onClick = onClick)

@Composable
private fun SectionHeader(category: CompoundCategory, total: String?) {
    val c = Tracker.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(c.category(category), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(category.plural.uppercase(), style = SectionLabelStyle, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
        total?.let { Text(it, style = TrackerType.numericSmall, color = c.muted) }
    }
}

@Composable
private fun PlanCard(ui: PlanItemUi, onClick: () -> Unit) {
    val c = Tracker.colors
    val f = ui.figures
    LedgerCard(Modifier.clickable(onClickLabel = "Edit", onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompoundName(ui.compound.commonName.ifBlank { ui.compound.displayName }, if (ui.compound.commonName.isBlank()) "" else ui.compound.name, size = 17)
                    val flags = listOfNotNull("paused".takeIf { !ui.item.enabled }, "all phases".takeIf { ui.allPhases })
                    if (flags.isNotEmpty()) Text(flags.joinToString(" · "), style = TrackerType.caption, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(f.total, style = TrackerType.figureLarge, color = c.ink)
                    Text(f.totalLabel, style = TrackerType.caption, color = c.muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.medium)).background(c.band).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FigureCell(f.perDoseLabel, f.perDose, Modifier.weight(1f))
                f.detail?.let { detail -> FigureCell(f.detailLabel ?: "", detail, Modifier.weight(1f)) }
                FigureCell("DAYS", f.days, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val slot = (ui.item.schedule.timings.firstOrNull() as? Timing.Slot)?.slot
                Icon(timingIcon(slot), contentDescription = null, tint = c.body2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(f.timing.ifBlank { f.days }, style = TrackerType.bodySmall, color = c.body2, modifier = Modifier.weight(1f))
                f.strength?.let { Text(it, style = TrackerType.numericSmall, color = c.muted) }
            }
        }
    }
}

@Composable
private fun CompactRow(ui: PlanItemUi, onClick: () -> Unit) {
    val c = Tracker.colors
    val f = ui.figures
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClickLabel = "Edit", onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompoundName(ui.compound.commonName.ifBlank { ui.compound.displayName }, if (ui.compound.commonName.isBlank()) "" else ui.compound.name, size = 15)
            val meta = listOfNotNull(f.days, f.timing.lowercase().ifBlank { null }, "paused".takeIf { !ui.item.enabled }).joinToString(" · ")
            Text(meta, style = TrackerType.numericSmall, color = c.muted)
        }
        Text(f.perDose, style = TrackerType.figure, color = c.ink)
    }
}
