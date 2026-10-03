package com.apollof.protocoltracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker

/** Stable: one day's doses in a sheet (dev shows the picked day on Today itself). See [DayGroups]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    day: DayUi,
    onDismiss: () -> Unit,
    onShift: (Long) -> Unit,
    onCheck: (DoseItem) -> Unit,
    onOpen: (DoseItem) -> Unit,
    onLogGroup: (GroupUi) -> Unit,
    onOpenExtra: (DoseLog) -> Unit = {},
) {
    val c = Tracker.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DayHeader(day, onShift)
            DayGroups(day, onCheck, onOpen, onLogGroup, onOpenExtra)
        }
    }
}
