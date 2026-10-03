package com.apollof.protocoltracker.ui.health

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.domain.io.labimport.BlockNotice
import com.apollof.protocoltracker.domain.io.labimport.BloodworkImport
import com.apollof.protocoltracker.domain.io.labimport.DraftDraw
import com.apollof.protocoltracker.domain.io.labimport.ImportMessages
import com.apollof.protocoltracker.domain.io.labimport.LabPrompt
import com.apollof.protocoltracker.domain.io.labimport.Review
import com.apollof.protocoltracker.domain.io.labimport.ReviewDraw
import com.apollof.protocoltracker.domain.io.labimport.ReviewRow
import com.apollof.protocoltracker.domain.io.labimport.RowRead
import com.apollof.protocoltracker.domain.io.labimport.RowState
import com.apollof.protocoltracker.domain.io.labimport.label
import com.apollof.protocoltracker.domain.model.MarkerFlag
import com.apollof.protocoltracker.domain.model.flag
import com.apollof.protocoltracker.ui.appViewModel
import com.apollof.protocoltracker.ui.components.ConfirmDialog
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.theme.NumericStyle
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import kotlinx.coroutines.launch

/** The in-app help (import doc §10.1): the steps, then where the report goes. */
private val HELP_STEPS = listOf(
    "Copy the AI prompt.",
    "In any chatbot, paste it and add your lab report (PDF, photo or text).",
    "Tap Copy on the chatbot's answer, not Share.",
    "Come back here and tap Paste answer.",
)
private const val HELP_PRIVACY = "Your report goes to the chatbot you use. This app stays offline."

/**
 * Bloodwork import: Start (help, Copy AI prompt, Paste answer), then Check
 * (one card per draw, a tap leaves a row out or keeps it, Save). Nothing is saved before Save. [onSaved] opens Journal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodworkImportScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { BloodworkImportViewModel(it) }
    val step by vm.step.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var discard by remember { mutableStateOf(false) }
    val c = Tracker.colors

    LaunchedEffect(saved) { if (saved) onSaved() }
    val check = step as? ImportStep.Check
    // Back from Check returns to Start; after a tap it first asks.
    fun back() {
        when {
            check == null -> onBack()
            check.changed -> discard = true
            else -> vm.discard()
        }
    }
    BackHandler(enabled = check != null) { back() }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text(if (check != null) "Check results" else "Import results") },
                navigationIcon = { IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, titleContentColor = c.ink, navigationIconContentColor = c.ink),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = step) {
            is ImportStep.Start -> StartStep(
                s.message, modifier,
                onCopy = {
                    copyPrompt(context)
                    // Android 13 and later confirm a copy themselves.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbar.showSnackbar("AI prompt copied") }
                },
                onPaste = { vm.paste(readClipboard(context)) },
            )
            is ImportStep.Check -> CheckStep(s.review, modifier, vm::toggle, vm::save)
        }
    }

    if (discard) {
        ConfirmDialog(
            "Discard this import?", "Nothing from this answer is saved.", "Discard",
            onConfirm = { discard = false; vm.discard() }, onDismiss = { discard = false }, dismiss = "Keep checking",
        )
    }
}

private fun copyPrompt(context: Context) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("AI prompt", LabPrompt.text))
}

/** The clipboard's text on the tap (an explicit tap is consent), cut one character past the longest text read. */
private fun readClipboard(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).coerceToText(context) ?: return null
    return (if (text.length > BloodworkImport.MAX_CHARS) text.subSequence(0, BloodworkImport.MAX_CHARS + 1) else text).toString()
}

@Composable
private fun StartStep(message: String?, modifier: Modifier, onCopy: () -> Unit, onPaste: () -> Unit) {
    val c = Tracker.colors
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(top = Spacing.sm, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HELP_STEPS.forEachIndexed { i, text ->
                Row {
                    Text("${i + 1}.", style = TrackerType.body, color = c.ink, modifier = Modifier.width(24.dp))
                    Text(text, style = TrackerType.body, color = c.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        Text(HELP_PRIVACY, style = TrackerType.bodySmall, color = c.muted)
        SecondaryButton("Copy AI prompt", onCopy, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
        if (message != null) {
            Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
                Text(message, style = TrackerType.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
        PrimaryButton("Paste answer", onPaste, Modifier.fillMaxWidth(), Icons.Outlined.ContentPaste)
    }
}

@Composable
private fun CheckStep(review: Review, modifier: Modifier, onToggle: (ReviewRow) -> Unit, onSave: () -> Unit) {
    val c = Tracker.colors
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(top = Spacing.sm, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) {
        Text(ImportMessages.TAP_TO_LEAVE_OUT, style = TrackerType.caption, color = c.muted)
        BlockNotice.entries.filter { it in review.draft.notices }.forEach { notice ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.warn, modifier = Modifier.size(20.dp))
                Text(notice.message, style = TrackerType.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
            }
        }
        // Newest draw first; a draw with an uncertain date (left out whole) last.
        review.draws.sortedWith(compareBy<ReviewDraw> { it.leftOut }.thenByDescending { it.draw.date }).forEach { d ->
            DrawCard(d, onToggle)
        }
        val rows = review.draws.flatMap { d -> d.rows.map { d to it } }
        if (review.alreadySaved > 0) {
            Fold(
                "${review.alreadySaved} already saved",
                rows.filter { (_, r) -> r.state == RowState.ALREADY_SAVED }.map { (d, r) -> r.label to "${r.value()} · ${drawDate(d.draw)}" },
            )
        }
        if (review.notImported > 0) {
            Fold(
                "${review.notImported} not imported",
                rows.filter { (_, r) -> r.state == RowState.NOT_IMPORTED }.map { (_, r) -> r.label to r.text } +
                    review.draft.unread.map { it.message to null },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            review.line?.let { Text(it, style = TrackerType.bodySmall, color = c.muted) }
            PrimaryButton(review.saveLabel, onSave, Modifier.fillMaxWidth(), enabled = review.canSave)
        }
    }
}

/** Rows shown in the draw's card; already saved and not imported rows sit in the folds below. */
private val IN_CARD = setOf(RowState.READY, RowState.LEFT_OUT, RowState.SAME_DAY, RowState.UNCERTAIN)

@Composable
private fun DrawCard(d: ReviewDraw, onToggle: (ReviewRow) -> Unit) {
    val c = Tracker.colors
    val rows = d.rows.filter { it.state in IN_CARD }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(listOf(drawDate(d.draw), d.draw.lab).filter { it.isNotBlank() }.joinToString(" · "))
        (d.draw.leftOutReason ?: d.draw.caption)?.let { Text(it, style = TrackerType.caption, color = c.muted) }
        if (rows.isNotEmpty()) {
            LedgerCard {
                rows.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    ImportRow(r, drawLeftOut = d.leftOut, onToggle)
                }
            }
        }
    }
}

/** "Wed 12 Mar 2025 · 08:15", or the date as printed when the draw is left out. */
private fun drawDate(draw: DraftDraw): String {
    val date = draw.date ?: return draw.printed.ifBlank { "Blood draw" }
    return listOfNotNull(date.format(Formats.dayYear), draw.time?.format(Formats.time)).joinToString(" · ")
}

/**
 * One result: the app's name (the printed one underneath when it differs), the value and unit as printed, the flag as a
 * word, and one line: why it is left out, or its caption. A left-out row is struck through and says so in words.
 */
@Composable
private fun ImportRow(r: ReviewRow, drawLeftOut: Boolean, onToggle: (ReviewRow) -> Unit) {
    val c = Tracker.colors
    val out = drawLeftOut || r.state != RowState.READY
    val strike = if (out) TextDecoration.LineThrough else null
    val ready = r.row.read as? RowRead.Ready
    val flag = ready?.result?.flag()?.takeIf { !out }
    val name = r.label
    val printed = r.row.printed.name.trim().takeIf { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }
    val line = if (r.state == RowState.LEFT_OUT) ImportMessages.LEFT_OUT_TAP_TO_KEEP else r.text
    val note = r.row.printed.note.trim().takeIf { it.isNotEmpty() && ready != null }
    val tap = if (r.toggles) {
        Modifier.clickable(onClickLabel = if (r.state == RowState.READY) "Leave out" else "Keep") { onToggle(r) }
    } else {
        Modifier
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(tap).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = TrackerType.bodySmall.copy(textDecoration = strike), color = if (out) c.muted else c.ink)
            printed?.let { Text(it, style = TrackerType.caption, color = c.muted) }
            line?.let { Text(it, style = TrackerType.caption, color = c.muted) }
            note?.let { Text("Note: $it", style = TrackerType.caption, color = c.muted) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.value(), style = NumericStyle.copy(textDecoration = strike), color = if (out) c.muted else c.ink)
            if (flag != null) {
                Text(
                    flag.label, style = TrackerType.caption, color = if (flag == MarkerFlag.NORMAL) c.muted else c.warn,
                )
            }
        }
    }
}

/** "N already saved" / "N not imported": a row that opens in place to its list (a title and an optional line each). */
@Composable
private fun Fold(label: String, items: List<Pair<String, String?>>) {
    val c = Tracker.colors
    var open by rememberSaveable(label) { mutableStateOf(false) }
    LedgerCard {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = !open }.padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = TrackerType.bodySmall, color = c.ink, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Hide" else "Show", tint = c.ink)
        }
        if (open) {
            items.forEach { (title, detail) ->
                RowDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = TrackerType.bodySmall, color = c.ink)
                    detail?.let { Text(it, style = TrackerType.caption, color = c.muted) }
                }
            }
        }
    }
}

/** The app's marker name; an unlisted result or one never read: the printed name. */

/** The value and unit as printed (a read value with a decimal point). */
private fun ReviewRow.value(): String = when (val read = row.read) {
    is RowRead.Ready -> listOf(read.value, read.unit)
    else -> listOf(row.printed.value, row.printed.unit)
}.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
