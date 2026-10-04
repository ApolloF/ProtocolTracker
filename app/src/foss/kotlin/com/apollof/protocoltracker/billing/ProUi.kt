package com.apollof.protocoltracker.billing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apollof.protocoltracker.domain.entitlement.Feature
import java.time.Instant

// The foss build sells nothing: these never show anything (FeatureGate never limits a feature there).

@Suppress("UNUSED_PARAMETER")
@Composable
fun PaywallSheet(reason: Feature, onDismiss: () -> Unit) = Unit

@Suppress("UNUSED_PARAMETER")
@Composable
fun TrialBanner(endsAt: Instant, modifier: Modifier = Modifier) = Unit

@Suppress("UNUSED_PARAMETER")
@Composable
fun ProSettingsGroup(modifier: Modifier = Modifier) = Unit
