package com.apollof.protocoltracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apollof.protocoltracker.AppContainer
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
