package com.apollof.protocoltracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apollof.protocoltracker.ui.theme.Tracker
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.levels.LevelsScreen
import com.apollof.protocoltracker.ui.plan.CompoundEditorScreen
import com.apollof.protocoltracker.ui.plan.CompoundsScreen
import com.apollof.protocoltracker.ui.plan.ItemEditorScreen
import com.apollof.protocoltracker.ui.plan.PlanScreen
import com.apollof.protocoltracker.ui.settings.SettingsScreen
import com.apollof.protocoltracker.ui.today.TodayScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object TodayRoute
@Serializable object PlanRoute
@Serializable object LevelsRoute
@Serializable object JournalRoute
@Serializable object SettingsRoute
@Serializable object CompoundsRoute

/** [phaseId] is used only for new items; null places the item in the Always group. */
@Serializable data class ItemEditorRoute(val itemId: String? = null, val phaseId: String? = null)
@Serializable data class CompoundEditorRoute(val compoundId: String? = null)

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(TodayRoute, TodayRoute::class, "Today", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    Tab(PlanRoute, PlanRoute::class, "Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Tab(LevelsRoute, LevelsRoute::class, "Levels", Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Filled.ShowChart),
    Tab(JournalRoute, JournalRoute::class, "Journal", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
)

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = tabs.any { t -> destination?.hasRoute(t.type) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) NavigationBar(containerColor = Tracker.colors.surface, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    val selected = destination?.hasRoute(tab.type) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Tracker.colors.accentText, selectedTextColor = Tracker.colors.accentText,
                            indicatorColor = Tracker.colors.accentSoft,
                            unselectedIconColor = Tracker.colors.muted, unselectedTextColor = Tracker.colors.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            val settings = { nav.navigate(SettingsRoute) }
            composable<TodayRoute> {
                TodayScreen(onOpenSettings = settings, onOpenPlan = {
                    nav.navigate(PlanRoute) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true }
                })
            }
            composable<PlanRoute> {
                PlanScreen(
                    onOpenSettings = settings,
                    onEditItem = { itemId, phaseId -> nav.navigate(ItemEditorRoute(itemId, phaseId)) },
                    onOpenCompounds = { nav.navigate(CompoundsRoute) },
                )
            }
            composable<LevelsRoute> { LevelsScreen(onOpenSettings = settings) }
            composable<JournalRoute> { JournalScreen(onOpenSettings = settings) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable<CompoundsRoute> {
                CompoundsScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(CompoundEditorRoute(it)) })
            }
            composable<ItemEditorRoute> { backStack ->
                val route = backStack.toRoute<ItemEditorRoute>()
                ItemEditorScreen(
                    itemId = route.itemId, phaseId = route.phaseId, onDone = { nav.popBackStack() },
                    onNewCompound = { nav.navigate(CompoundEditorRoute(null)) },
                )
            }
            composable<CompoundEditorRoute> { backStack ->
                CompoundEditorScreen(compoundId = backStack.toRoute<CompoundEditorRoute>().compoundId, onDone = { nav.popBackStack() })
            }
        }
    }
}
