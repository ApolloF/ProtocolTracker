package com.apollof.protocoltracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.theme.Motions
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/** One destination in the bottom bar or rail. */
data class NavDestinationItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Bottom navigation on the page colour with a hairline on top. The selected tab gets a filled icon on a soft
 * pill and a bold label, so selection never depends on colour alone. Press feedback stays inside the pill.
 */
@Composable
fun TrackerNavBar(items: List<NavDestinationItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Column(modifier.fillMaxWidth().background(c.bg)) {
        HorizontalDivider(thickness = 1.dp, color = c.line)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 8.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item -> NavItem(item, index == selected, { onSelect(index) }, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun NavItem(item: NavDestinationItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick, interactionSource = interaction, indication = null),
        contentAlignment = Alignment.Center,
    ) { NavItemContent(item, selected, interaction) }
}

@Composable
private fun NavItemContent(item: NavDestinationItem, selected: Boolean, interaction: MutableInteractionSource) {
    val c = Tracker.colors
    val motion = Motions.current
    val pill by animateColorAsState(if (selected) c.accentSoft else c.accentSoft.copy(alpha = 0f), Motions.spec(motion, 180), label = "pill")
    val tint by animateColorAsState(if (selected) c.accentText else c.muted, Motions.spec(motion, 180), label = "tint")
    val pillShape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(width = 60.dp, height = 32.dp).clip(pillShape).background(pill).indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            item.label,
            style = TrackerType.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
        )
    }
}

/** Side rail for wide screens (tablets, landscape), same items and states as [TrackerNavBar]. */
@Composable
fun TrackerNavRail(items: List<NavDestinationItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Row(modifier.fillMaxHeight().background(c.bg)) {
        Column(
            Modifier.width(88.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(top = 24.dp).selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEachIndexed { index, item ->
                NavItem(item, index == selected, { onSelect(index) }, Modifier.fillMaxWidth().height(64.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        VerticalDivider(thickness = 1.dp, color = c.line)
    }
}
