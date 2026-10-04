package com.apollof.protocoltracker.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.container
import com.apollof.protocoltracker.domain.entitlement.Feature
import com.apollof.protocoltracker.ui.components.AccentTextButton
import com.apollof.protocoltracker.ui.components.FitRow
import com.apollof.protocoltracker.ui.components.Formats
import com.apollof.protocoltracker.ui.components.LedgerCard
import com.apollof.protocoltracker.ui.components.PrimaryButton
import com.apollof.protocoltracker.ui.components.RowDivider
import com.apollof.protocoltracker.ui.components.SecondaryButton
import com.apollof.protocoltracker.ui.components.SectionLabel
import com.apollof.protocoltracker.ui.theme.Spacing
import com.apollof.protocoltracker.ui.theme.Tracker
import com.apollof.protocoltracker.ui.theme.TrackerType
import java.time.Instant
import kotlinx.coroutines.launch

// Pro UI of the Play build: the paywall, the Levels trial banner and the Settings group. Each shows nothing when the
// build sells no Pro (FeatureGate.sellsPro), e.g. with -Pplay.monetization=none.

/** The Play Billing entitlements when this build sells Pro; null otherwise. */
@Composable
private fun rememberPro(): PlayBillingEntitlements? {
    val container = LocalContext.current.container
    return remember(container) { if (container.gate.sellsPro) container.entitlements as? PlayBillingEntitlements else null }
}

/** The latest one-shot result of a purchase or restore while the caller is shown. */
@Composable
private fun rememberLastMessage(pro: PlayBillingEntitlements): String? {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pro) { pro.messages.collect { message = it } }
    return message
}

@Composable
fun PaywallSheet(reason: Feature, onDismiss: () -> Unit) = ProSheet(reason, onDismiss)

/** The paywall; [reason] is null when it is opened from Settings rather than by a limit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProSheet(reason: Feature?, onDismiss: () -> Unit) {
    val pro = rememberPro()
    if (pro == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val context = LocalContext.current
    val policy = context.container.gate.policy
    val isPro by pro.isPro.collectAsStateWithLifecycle()
    val pending by pro.pending.collectAsStateWithLifecycle()
    val price by pro.price.collectAsStateWithLifecycle()
    val message = rememberLastMessage(pro)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pro) { pro.loadPrice() }
    val c = Tracker.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        PaywallContent(
            title = "${stringResource(R.string.app_name)} Pro",
            reason = reason?.let { paywallReason(it, policy) },
            features = policy.proFeatures.map { proFeatureLine(it, policy) },
            price = price ?: PlayBillingEntitlements.FALLBACK_PRICE,
            isPro = isPro,
            pending = pending,
            message = message,
            onBuy = { context.findActivity()?.let { activity -> scope.launch { pro.buy(activity) } } },
            onDismiss = onDismiss,
        )
    }
}

/** The paywall's content without the sheet and the billing state, so tests can draw it. */
@Composable
internal fun PaywallContent(
    title: String,
    reason: String?,
    features: List<String>,
    price: String,
    isPro: Boolean,
    pending: Boolean,
    message: String?,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tracker.colors
    val state = when {
        isPro -> PlayBillingEntitlements.UNLOCKED
        pending -> PlayBillingEntitlements.PENDING
        else -> null
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = Spacing.lg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(title, style = TrackerType.titleLarge, color = c.ink)
        if (reason != null && !isPro) Text(reason, style = TrackerType.body, color = c.body2)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel("Pro adds")
            features.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = c.accentText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.md))
                    Text(line, style = TrackerType.body, color = c.ink)
                }
            }
        }
        if (!isPro) Text(priceLine(price), style = TrackerType.title, color = c.ink)
        if (state != null) {
            Text(state, style = TrackerType.title, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (!isPro) Text("Pro unlocks when Google Play confirms the payment.", style = TrackerType.caption, color = c.muted)
        }
        if (message != null && message != state) {
            Text(message, style = TrackerType.caption, color = c.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state == null) PrimaryButton("Buy", onBuy, Modifier.fillMaxWidth())
            SecondaryButton(if (state == null) "Not now" else "Close", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** A band over Levels while its trial runs. */
@Composable
fun TrialBanner(endsAt: Instant, modifier: Modifier = Modifier) {
    rememberPro() ?: return
    val container = LocalContext.current.container
    var paywall by remember { mutableStateOf(false) }
    val c = Tracker.colors
    LedgerCard(modifier) {
        FitRow(
            start = {
                Text(
                    "Full Levels free until ${endsAt.atZone(container.zone()).format(Formats.dayMonth)}",
                    style = TrackerType.bodySmall, color = c.body2,
                )
            },
            end = { AccentTextButton("Get Pro", { paywall = true }) },
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs),
        )
    }
    if (paywall) PaywallSheet(Feature.LEVELS_RANGE) { paywall = false }
}

/** Settings › Pro: status, the Levels trial, Buy and Restore purchase. */
@Composable
fun ProSettingsGroup(modifier: Modifier = Modifier) {
    val pro = rememberPro() ?: return
    val container = LocalContext.current.container
    val isPro by pro.isPro.collectAsStateWithLifecycle()
    val pending by pro.pending.collectAsStateWithLifecycle()
    val trialStarts by container.settings.trialStarts.collectAsStateWithLifecycle(emptyMap())
    val message = rememberLastMessage(pro)
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(false) }
    val c = Tracker.colors
    val status = when {
        isPro -> PlayBillingEntitlements.UNLOCKED
        pending -> PlayBillingEntitlements.PENDING
        else -> "Not unlocked"
    }
    val trialEnd = if (isPro) null else levelsTrialEnd(container.gate.policy, trialStarts)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("Pro")
        LedgerCard {
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                FitRow(
                    start = { Text("Status", style = TrackerType.title, color = c.ink) },
                    end = { Text(status, style = TrackerType.body, color = c.body2) },
                )
                if (trialEnd != null) {
                    val day = trialEnd.atZone(container.zone()).format(Formats.dayMonth)
                    val text = if (container.clock().isBefore(trialEnd)) "Full Levels free until $day" else "Levels trial ended $day"
                    Text(text, style = TrackerType.caption, color = c.muted)
                }
            }
            if (!isPro && !pending) {
                RowDivider()
                ProLinkRow("Buy Pro") { sheet = true }
            }
            RowDivider()
            ProLinkRow("Restore purchase") { scope.launch { pro.restore() } }
        }
        if (message != null) {
            Text(message, style = TrackerType.caption, color = c.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (sheet) ProSheet(reason = null) { sheet = false }
}

@Composable
private fun ProLinkRow(title: String, onClick: () -> Unit) {
    val c = Tracker.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(start = Spacing.lg, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TrackerType.body, color = c.accentText, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.muted)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
