package com.apollof.protocoltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.SectionLabelStyle
import com.apollof.protocoltracker.ui.theme.Tracker

private val CardShape = RoundedCornerShape(12.dp)

/** White card with a thin border; the base of every list section. */
@Composable
fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Tracker.colors
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(c.surface).border(1.dp, c.line, CardShape),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Tracker.colors.muted) {
    Text(text.uppercase(), style = SectionLabelStyle, color = color, modifier = modifier.semantics { heading() })
}

/** Card with its header inside: icon, title, optional count and an action on the right. */
@Composable
fun GroupCard(
    title: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    count: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tracker.colors
    LedgerCard(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title.uppercase(), style = SectionLabelStyle, color = c.ink, modifier = Modifier.semantics { heading() })
            if (count != null) Text(" · $count", style = SectionLabelStyle.copy(fontWeight = FontWeight.Normal), color = c.muted)
            Spacer(Modifier.weight(1f))
            action?.invoke(this)
        }
        content()
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Tracker.colors.line2)

/** Bordered category label with a coloured dot; the text carries the meaning. */
@Composable
fun CategoryTag(category: CompoundCategory, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    Row(
        modifier.border(1.dp, c.line, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(c.category(category), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(category.tag, style = NumericStyle.copy(fontSize = 10.5.sp, letterSpacing = 0.6.sp), color = c.muted)
    }
}

enum class CheckState { PENDING, TAKEN, SKIPPED }

/** 48 dp round check. Taken is filled with a tick, skipped shows a dash; pending is an empty ring. */
@Composable
fun CheckButton(state: CheckState, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tracker.colors
    val label = when (state) {
        CheckState.PENDING -> "Mark $name taken"
        CheckState.TAKEN -> "Undo $name"
        CheckState.SKIPPED -> "Undo skip of $name"
    }
    val status = when (state) {
        CheckState.PENDING -> "Not taken"
        CheckState.TAKEN -> "Taken"
        CheckState.SKIPPED -> "Skipped"
    }
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (state == CheckState.TAKEN) Modifier.background(c.accent)
                else Modifier.border(1.5.dp, c.outline, CircleShape),
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = label; stateDescription = status },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            CheckState.TAKEN -> Icon(Icons.Outlined.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(24.dp))
            CheckState.SKIPPED -> Icon(Icons.Outlined.Remove, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
            CheckState.PENDING -> Unit
        }
    }
}

/** "Anavar" bold with " (oxandrolone)" muted; a plain name when there is no colloquial name. */
@Composable
fun CompoundName(commonName: String, name: String, modifier: Modifier = Modifier, size: Int = 16) {
    val c = Tracker.colors
    val text = buildAnnotatedString {
        if (commonName.isBlank()) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name.replaceFirstChar { it.titlecase() }) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(commonName) }
            if (!commonName.equals(name, ignoreCase = true) && name.isNotBlank()) {
                withStyle(SpanStyle(color = c.muted, fontSize = (size - 2).sp, fontWeight = FontWeight.Normal)) { append(" ($name)") }
            }
        }
    }
    Text(text, fontSize = size.sp, lineHeight = (size + 5).sp, color = c.ink, modifier = modifier)
}

/**
 * One dose: check on the left, name and dose line, category tag on the right.
 * Tapping the row (not the check) opens the log sheet.
 */
@Composable
fun DoseRow(
    commonName: String,
    name: String,
    detail: String,
    category: CompoundCategory?,
    state: CheckState,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tracker.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClickLabel = "Log with details", onClick = onOpen)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckButton(state, commonName.ifBlank { name }, onCheck)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompoundName(commonName, name)
            Text(detail, style = NumericStyle, color = c.body2)
        }
        if (category != null) CategoryTag(category)
    }
}

fun timingIcon(slot: DaySlot?): ImageVector = when (slot) {
    DaySlot.MORNING -> Icons.Outlined.WbSunny
    DaySlot.MIDDAY -> Icons.Outlined.LightMode
    DaySlot.PRE_WORKOUT, DaySlot.POST_WORKOUT -> Icons.Outlined.FitnessCenter
    DaySlot.EVENING -> Icons.Outlined.WbTwilight
    DaySlot.BEDTIME -> Icons.Outlined.Bedtime
    DaySlot.ANY_TIME, null -> Icons.Outlined.Schedule
}

/** Thin progress bar; the text next to it carries the value. */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, track: Color = Tracker.colors.surface2) {
    val c = Tracker.colors
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
    }
}
