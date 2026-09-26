package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.components.AccentTextButton
import com.apollof.protocoltracker.ui.components.DoseRow
import com.apollof.protocoltracker.ui.components.GroupCard
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.components.timingIcon
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/**
 * One day's doses in a sheet. Checking a dose on an earlier day records its planned time; tapping a dose opens the
 * log sheet to change the amount or time. Later days are shown as planned and can't be logged yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    day: DayUi,
    onDismiss: () -> Unit,
    onShift: (Long) -> Unit,
    onCheck: (DoseItem) -> Unit,
    onOpen: (DoseItem) -> Unit,
    onLogGroup: (GroupUi) -> Unit,
) {
    val c = Tracker.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous day", tint = c.ink)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.title, style = TrackerType.titleLarge, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text(day.summary.uppercase(), style = TrackerType.numericSmall, color = c.muted)
                }
                IconButton(onClick = { onShift(1) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next day", tint = c.ink)
                }
            }
            if (day.isFuture && day.groups.isNotEmpty()) {
                Text("Planned doses. They can be logged on the day.", style = TrackerType.caption, color = c.muted)
            }
            if (day.groups.isEmpty() && day.extras.isEmpty()) {
                Text(
                    "Nothing scheduled on this day. Extra doses are logged with the Log button on Today.",
                    style = TrackerType.bodySmall, color = c.muted, modifier = Modifier.padding(vertical = Spacing.lg),
                )
            }
            day.groups.forEach { group ->
                GroupCard(
                    title = group.label,
                    icon = timingIcon(group.slot),
                    count = "${group.items.size}",
                    action = {
                        if (!day.isFuture && group.pending > 1) AccentTextButton("Log all ${group.pending}", { onLogGroup(group) })
                    },
                ) {
                    group.items.forEach { item ->
                        RowDivider()
                        if (day.isFuture) PlannedRow(item)
                        else DoseRow(
                            item.commonName, item.name, item.detail, item.category, item.state,
                            onCheck = { onCheck(item) }, onOpen = { onOpen(item) },
                        )
                    }
                }
            }
            if (day.extras.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Also logged")
                day.extras.forEach { item ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.entry.log?.snapshot?.displayName ?: item.name, color = c.ink, modifier = Modifier.weight(1f))
                        Text(item.detail, style = NumericStyle, color = c.muted)
                    }
                }
            }
        }
    }
}

/** A dose on a later day: name and planned amount, no check. */
@Composable
private fun PlannedRow(item: DoseItem) {
    val c = Tracker.colors
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.commonName.ifBlank { item.name }, style = TrackerType.title, color = c.ink)
        Text(item.detail, style = NumericStyle, color = c.body2)
    }
}
