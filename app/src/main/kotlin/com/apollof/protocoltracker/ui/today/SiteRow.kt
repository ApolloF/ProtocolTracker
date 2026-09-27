package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.SiteChoice
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.QuickChip
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Dev: the injection site of a dose. Shows the sites used before (plus the suggestion) and "More" for the rest, or one
 * "Choose site" chip before any site was recorded. Tapping the selected chip clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SiteRow(choice: SiteChoice, selected: String?, today: LocalDate, zone: ZoneId, onSelect: (String?) -> Unit) {
    val c = Tracker.colors
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) choice.all else choice.offered
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel("Site", Modifier.weight(1f))
            choice.state?.last?.let { last ->
                Text(
                    "Last: ${InjectionSites.label(last.site)} · ${Formats.relativeDay(last.at.atZone(zone).toLocalDate(), today)}",
                    style = TrackerType.caption, color = c.body2,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (key in shown) {
                val name = InjectionSites.longLabel(key)
                QuickChip(InjectionSites.label(key), key == selected, Modifier.semantics { contentDescription = name }) {
                    onSelect(if (key == selected) null else key)
                }
            }
            if (!expanded && shown.size < choice.all.size) {
                QuickChip(if (shown.isEmpty()) "Choose site" else "More", selected = false, role = Role.Button) { expanded = true }
            }
        }
    }
}
