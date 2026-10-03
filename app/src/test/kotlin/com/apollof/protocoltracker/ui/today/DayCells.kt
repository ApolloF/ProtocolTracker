package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The week strip's cell of [date]. */
internal fun dayCell(date: LocalDate) = SemanticsMatcher("cell of $date") { node ->
    node.config.getOrNull(SemanticsActions.OnClick)?.label == "Open day" &&
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any {
            it.startsWith("${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${date.dayOfMonth}:")
        }
}

/** The week strip, which offers "Previous week" and "Next week" to accessibility services. */
internal fun weekPager(action: String) = SemanticsMatcher("week pager") { node ->
    node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == action }
}

/** Turns the week strip one week back or on, as TalkBack would. */
internal fun ComposeContentTestRule.turnWeek(action: String) {
    val custom = onNode(weekPager(action)).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == action }
    runOnUiThread { custom.action() }
    waitForIdle()
}

/**
 * Opens [day], an earlier day, with the week strip shown: the strip turns back a week when [day] is not in this one, and
 * a tap shows the day below the strip.
 */
internal fun ComposeContentTestRule.openPastDay(day: LocalDate) {
    val today = LocalDate.now()
    waitUntil(15_000) { onAllNodes(dayCell(today)).fetchSemanticsNodes().isNotEmpty() }
    if (onAllNodes(dayCell(day)).fetchSemanticsNodes().isEmpty()) {
        turnWeek("Previous week")
        waitUntil(15_000) { onAllNodes(dayCell(day)).fetchSemanticsNodes().isNotEmpty() }
    }
    onNode(dayCell(day)).performSemanticsAction(SemanticsActions.OnClick)
    waitUntil(15_000) { onAllNodesWithText("Back to today").fetchSemanticsNodes().isNotEmpty() }
}
