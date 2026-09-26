package com.apollof.protocoltracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.apollof.protocoltracker.ui.components.NavDestinationItem
import com.apollof.protocoltracker.ui.components.TrackerNavBar
import com.apollof.protocoltracker.ui.components.TrackerNavRail
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.levels.LevelsScreen
import com.apollof.protocoltracker.ui.plan.CompoundEditorScreen
import com.apollof.protocoltracker.ui.plan.CompoundsScreen
import com.apollof.protocoltracker.ui.plan.ItemEditorScreen
import com.apollof.protocoltracker.ui.plan.PlanScreen
import com.apollof.protocoltracker.ui.settings.SettingsPage
import com.apollof.protocoltracker.ui.settings.SettingsPageScreen
import com.apollof.protocoltracker.ui.settings.SettingsScreen
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.today.TodayScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object TodayRoute
@Serializable object PlanRoute
@Serializable object LevelsRoute
@Serializable object JournalRoute
@Serializable object SettingsRoute
@Serializable data class SettingsPageRoute(val page: String)
@Serializable object CompoundsRoute

/** [phaseId] is used only for new items; null places the item in the Always group. */
@Serializable data class ItemEditorRoute(val itemId: String? = null, val phaseId: String? = null)
@Serializable data class CompoundEditorRoute(val compoundId: String? = null)

private data class Tab(val route: Any, val type: KClass<*>, val item: NavDestinationItem)

private val tabs = listOf(
    Tab(TodayRoute, TodayRoute::class, NavDestinationItem("Today", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle)),
    Tab(PlanRoute, PlanRoute::class, NavDestinationItem("Plan", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth)),
    Tab(LevelsRoute, LevelsRoute::class, NavDestinationItem("Levels", Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Filled.ShowChart)),
    Tab(JournalRoute, JournalRoute::class, NavDestinationItem("Journal", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook)),
)

@Composable
fun AppNav(nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val selected = tabs.indexOfFirst { t -> destination?.hasRoute(t.type) == true }
    val onTab = selected >= 0
    // Tablets and landscape phones get a side rail instead of the bottom bar.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    fun openTab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val items = tabs.map { it.item }

    Row(Modifier.fillMaxSize()) {
        if (wide) AnimatedVisibility(onTab, enter = fadeIn(), exit = fadeOut()) {
            TrackerNavRail(items, selected.coerceAtLeast(0), { openTab(tabs[it].route) })
        }
        Scaffold(
            containerColor = Tracker.colors.bg,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(onTab && !wide, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    TrackerNavBar(items, selected.coerceAtLeast(0), { openTab(tabs[it].route) })
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                val settings = { nav.navigate(SettingsRoute) }
                composable<TodayRoute> { TodayScreen(onOpenSettings = settings, onOpenPlan = { openTab(PlanRoute) }) }
                composable<PlanRoute> {
                    PlanScreen(
                        onOpenSettings = settings,
                        onEditItem = { itemId, phaseId -> nav.navigate(ItemEditorRoute(itemId, phaseId)) },
                        onOpenCompounds = { nav.navigate(CompoundsRoute) },
                    )
                }
                composable<LevelsRoute> { LevelsScreen(onOpenSettings = settings) }
                composable<JournalRoute> { JournalScreen(onOpenSettings = settings) }
                composable<SettingsRoute> {
                    SettingsScreen(onBack = { nav.popBackStack() }, onOpenPage = { nav.navigate(SettingsPageRoute(it.name)) })
                }
                composable<SettingsPageRoute> { backStack ->
                    val page = SettingsPage.valueOf(backStack.toRoute<SettingsPageRoute>().page)
                    SettingsPageScreen(page, onBack = { nav.popBackStack() })
                }
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
}
