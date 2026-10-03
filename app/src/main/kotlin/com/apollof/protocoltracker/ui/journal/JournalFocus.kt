package com.apollof.protocoltracker.ui.journal

import com.apollof.protocoltracker.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Hands a saved bloodwork import to Journal, which selects the Bloodwork chip and shows [UiMessage] with Undo.
 * The screen consumes it, not its ViewModel, so the message is never emitted before a snackbar host listens.
 */
class JournalFocus {
    private val _pending = MutableStateFlow<UiMessage?>(null)
    val pending: StateFlow<UiMessage?> = _pending

    fun request(message: UiMessage) {
        _pending.value = message
    }

    fun take(): UiMessage? = _pending.getAndUpdate { null }
}
