package com.apollof.protocoltracker.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.apollof.protocoltracker.ui.health.BloodworkImportScreen
import com.apollof.protocoltracker.ui.components.NavDestinationItem
import com.apollof.protocoltracker.ui.components.TrackerNavBar
import com.apollof.protocoltracker.ui.components.TrackerNavRail
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.levels.LevelDetailScreen
import com.apollof.protocoltracker.ui.levels.LevelsScreen
import com.apollof.protocoltracker.ui.plan.CompoundEditorScreen
import com.apollof.protocoltracker.ui.plan.CompoundsScreen
import com.apollof.protocoltracker.ui.plan.ItemEditorScreen
import com.apollof.protocoltracker.ui.plan.PlanScreen
import com.apollof.protocoltracker.ui.settings.SettingsPage
import com.apollof.protocoltracker.ui.settings.SettingsPageScreen
import com.apollof.protocoltracker.ui.settings.SettingsScreen
import com.apollof.protocoltracker.ui.theme.Motions
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
@Serializable data class LevelDetailRoute(val group: String)

@Serializable object BloodworkImportRoute

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
    // Tablets and landscape phones get a side rail instead of the bottom bar.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val motion = Motions.current
    fun openTab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val items = tabs.map { it.item }
    fun isTab(d: NavDestination) = tabs.any { d.hasRoute(it.type) }
    fun move(from: NavDestination, to: NavDestination) = if (isTab(from) && isTab(to)) Motions.NavMove.TAB else Motions.NavMove.PUSH

    // Each tab screen carries the bar, so the bar moves with its screen and the screen area keeps its size.
    @Composable
    fun TabFrame(index: Int, content: @Composable () -> Unit) {
        val onSelect: (Int) -> Unit = { openTab(tabs[it].route) }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                TrackerNavRail(items, index, onSelect)
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // The bar holds the navigation bar inset, so the screen above it leaves none.
                Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) { content() }
                TrackerNavBar(items, index, onSelect)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Tracker.colors.bg)) {
        NavHost(
            nav, startDestination = TodayRoute, modifier = Modifier.fillMaxSize(),
            enterTransition = { Motions.screenEnter(motion, move(initialState.destination, targetState.destination)) },
            exitTransition = { Motions.screenExit(motion) },
            popEnterTransition = { Motions.screenEnter(motion, Motions.NavMove.POP) },
            popExitTransition = { Motions.screenExit(motion) },
            // A back swipe shows the screen below as it is while the one on top moves away.
            predictivePopEnterTransition = { _ -> EnterTransition.None },
            predictivePopExitTransition = { edge -> Motions.predictivePopExit(motion, edge) },
        ) {
            val settings = { nav.navigate(SettingsRoute) }
            val importBloodwork = { nav.navigate(BloodworkImportRoute) }
            composable<TodayRoute> {
                TabFrame(0) { TodayScreen(onOpenSettings = settings, onOpenPlan = { openTab(PlanRoute) }, onImportBloodwork = importBloodwork) }
            }
            composable<PlanRoute> {
                TabFrame(1) {
                    PlanScreen(
                        onOpenSettings = settings,
                        onEditItem = { itemId, phaseId -> nav.navigate(ItemEditorRoute(itemId, phaseId)) },
                        onOpenCompounds = { nav.navigate(CompoundsRoute) },
                    )
                }
            }
            composable<LevelsRoute> {
                TabFrame(2) { LevelsScreen(onOpenSettings = settings, onOpenGroup = { nav.navigate(LevelDetailRoute(it)) }, onOpenPlan = { openTab(PlanRoute) }) }
            }
            composable<LevelDetailRoute> { backStack ->
                LevelDetailScreen(backStack.toRoute<LevelDetailRoute>().group, onBack = { nav.popBackStack() })
            }
            composable<JournalRoute> { TabFrame(3) { JournalScreen(onOpenSettings = settings, onImportBloodwork = importBloodwork) } }
            composable<BloodworkImportRoute> {
                // Saved: the import leaves the back stack and Journal shows the draws with Undo.
                BloodworkImportScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack(); openTab(JournalRoute) })
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { nav.popBackStack() }, onOpenPage = { nav.navigate(SettingsPageRoute(it.name)) })
            }
            composable<SettingsPageRoute> { backStack ->
                // A restored back stack can name a page that no longer exists (Experimental, removed in 0.5.0).
                val name = backStack.toRoute<SettingsPageRoute>().page
                val page = SettingsPage.entries.firstOrNull { it.name == name } ?: SettingsPage.ABOUT
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
                    onOpenLevels = { nav.navigate(LevelDetailRoute(it)) },
                )
            }
            composable<CompoundEditorRoute> { backStack ->
                CompoundEditorScreen(compoundId = backStack.toRoute<CompoundEditorRoute>().compoundId, onDone = { nav.popBackStack() })
            }
        }
    }
}
