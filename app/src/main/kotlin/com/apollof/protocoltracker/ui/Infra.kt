package com.apollof.protocoltracker.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.container
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/** ViewModel built from the app container, scoped to the current navigation entry. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel(key = key) { create(container) }
}

/** Emits the current time now and at each minute boundary, so time-based lists stay current. */
fun minuteTicker(clock: () -> Instant): Flow<Instant> = flow {
    while (true) {
        val now = clock()
        emit(now)
        delay(60_000 - now.toEpochMilli() % 60_000)
    }
}

/** One-shot message for a snackbar, optionally with an undo action. */
data class UiMessage(val text: String, val undo: (suspend () -> Unit)? = null)

/**
 * [dev] in the dev build, [stable] in the released app: the switch for dev-only copy, counts and order. Call it
 * with named arguments and keep [stable] as the expression it replaces, copied verbatim. Both sides are evaluated,
 * so pass plain values; a subtree that differs gets an `if (BuildConfig.DEV_FEATURES)` branch instead. Every switch
 * has a test in `DevEntryPointsTest`.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> devOr(dev: T, stable: T): T = if (BuildConfig.DEV_FEATURES) dev else stable

/**
 * Top inset of a tab screen inside its own Scaffold. The Scaffold's padding already holds the status bar, so the
 * extra [statusBarsPadding] the released app adds doubles the gap above the header on devices (Robolectric draws no
 * bars, so screenshots never showed it). Dev drops it; stable keeps its layout.
 */
fun Modifier.tabScreenTop(): Modifier = if (BuildConfig.DEV_FEATURES) this else statusBarsPadding()
