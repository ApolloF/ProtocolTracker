package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType

/** Dose units that can be converted for a compound with the given formulation data. */
fun unitsFor(base: BaseUnit, formulation: Formulation): List<DoseUnit> = buildList {
    if (base == BaseUnit.MG) { add(DoseUnit.MG); add(DoseUnit.MCG) } else add(DoseUnit.IU)
    if (formulation.perMl != null) add(DoseUnit.ML)
    if (base == BaseUnit.MG && formulation.perTablet != null) add(DoseUnit.TABLET)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val c = Tracker.colors
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = c.accentSoft, activeContentColor = c.accentText, activeBorderColor = c.accentMid,
                    inactiveContainerColor = c.surface, inactiveContentColor = c.ink, inactiveBorderColor = c.line,
                ),
                label = { Text(label(option), maxLines = 1, style = TrackerType.label) },
            )
        }
    }
}

@Composable
fun UnitSelector(units: List<DoseUnit>, selected: DoseUnit, onSelect: (DoseUnit) -> Unit) =
    Segmented(units, selected, { it.label }, onSelect = onSelect)
