package com.apollof.protocoltracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.domain.model.CompoundCategory

/** Design tokens from the "Ledger" direction (sage palette). Status is never conveyed by these colours alone. */
@Immutable
data class TrackerColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val line2: Color,
    val ink: Color,
    val muted: Color,
    val body2: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val accentMid: Color,
    val warn: Color,
    val band: Color,
    val injectable: Color,
    val oral: Color,
    val support: Color,
    val peptide: Color,
) {
    fun category(category: CompoundCategory): Color = when (category) {
        CompoundCategory.INJECTABLE_STEROID -> injectable
        CompoundCategory.ORAL_STEROID -> oral
        CompoundCategory.SUPPORT -> support
        CompoundCategory.PEPTIDE -> peptide
    }
}

private val LightTokens = TrackerColors(
    bg = Color(0xFFF3F4F2), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE8ECE9), line = Color(0xFFDCE1DE),
    line2 = Color(0xFFE6EAE7), ink = Color(0xFF151917), muted = Color(0xFF56605B), body2 = Color(0xFF36403B),
    outline = Color(0xFF77817C), accent = Color(0xFF1E6B5C), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFD5EAE3),
    accentText = Color(0xFF145446), accentMid = Color(0xFF8DBFB2), warn = Color(0xFF9A5B00), band = Color(0xFFE2EEE9),
    injectable = Color(0xFF4F74C0), oral = Color(0xFFB7802F), support = Color(0xFF7C8882), peptide = Color(0xFF8F68BF),
)

private val DarkTokens = TrackerColors(
    bg = Color(0xFF101312), surface = Color(0xFF191D1B), surface2 = Color(0xFF222826), line = Color(0xFF2A302D),
    line2 = Color(0xFF252B28), ink = Color(0xFFE7ECE9), muted = Color(0xFF9AA59F), body2 = Color(0xFFC3CCC7),
    outline = Color(0xFF66706B), accent = Color(0xFF7CCBB5), onAccent = Color(0xFF06251E), accentSoft = Color(0xFF1B3730),
    accentText = Color(0xFFA6E0D0), accentMid = Color(0xFF3E6A5F), warn = Color(0xFFE9B45A), band = Color(0xFF16231F),
    injectable = Color(0xFF8DAEEB), oral = Color(0xFFDDB271), support = Color(0xFFA3AEA8), peptide = Color(0xFFBE9FE4),
)

private fun scheme(t: TrackerColors, dark: Boolean) = if (dark) darkColorScheme(
    primary = t.accent, onPrimary = t.onAccent, primaryContainer = t.accentSoft, onPrimaryContainer = t.accentText,
    secondary = t.body2, onSecondary = t.bg, secondaryContainer = t.surface2, onSecondaryContainer = t.ink,
    tertiary = t.warn, background = t.bg, onBackground = t.ink, surface = t.bg, onSurface = t.ink,
    surfaceVariant = t.surface2, onSurfaceVariant = t.muted, surfaceContainerLowest = t.bg, surfaceContainerLow = t.surface,
    surfaceContainer = t.surface, surfaceContainerHigh = t.surface2, surfaceContainerHighest = t.surface2,
    outline = t.outline, outlineVariant = t.line, inverseSurface = t.ink, inverseOnSurface = t.bg, inversePrimary = Color(0xFF1E6B5C),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
) else lightColorScheme(
    primary = t.accent, onPrimary = t.onAccent, primaryContainer = t.accentSoft, onPrimaryContainer = t.accentText,
    secondary = t.body2, onSecondary = t.surface, secondaryContainer = t.surface2, onSecondaryContainer = t.ink,
    tertiary = t.warn, background = t.bg, onBackground = t.ink, surface = t.bg, onSurface = t.ink,
    surfaceVariant = t.surface2, onSurfaceVariant = t.muted, surfaceContainerLowest = t.surface, surfaceContainerLow = t.surface,
    surfaceContainer = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.surface2,
    outline = t.outline, outlineVariant = t.line, inverseSurface = Color(0xFF2B302D), inverseOnSurface = Color(0xFFEFF1EF),
    inversePrimary = Color(0xFF7CCBB5),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

@OptIn(ExperimentalTextApi::class)
private fun plexSans(weight: Int) = Font(
    R.font.ibm_plex_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val PlexSans = FontFamily(plexSans(400), plexSans(500), plexSans(600), plexSans(700))

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

private val base = Typography()
private fun TextStyle.plex(weight: FontWeight? = null) = copy(fontFamily = PlexSans, fontWeight = weight ?: fontWeight)

private val AppTypography = Typography(
    displayLarge = base.displayLarge.plex(), displayMedium = base.displayMedium.plex(), displaySmall = base.displaySmall.plex(),
    headlineLarge = base.headlineLarge.plex(FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.plex(FontWeight.SemiBold).copy(fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.2).sp),
    headlineSmall = base.headlineSmall.plex(FontWeight.SemiBold),
    titleLarge = base.titleLarge.plex(FontWeight.SemiBold),
    titleMedium = base.titleMedium.plex(FontWeight.SemiBold),
    titleSmall = base.titleSmall.plex(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.plex(), bodyMedium = base.bodyMedium.plex(), bodySmall = base.bodySmall.plex(),
    labelLarge = base.labelLarge.plex(FontWeight.SemiBold),
    labelMedium = base.labelMedium.plex(FontWeight.Medium),
    labelSmall = base.labelSmall.plex(FontWeight.Medium),
)

/** Tabular mono figures keep doses, times and dates aligned. */
val NumericStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")

/** Small caps-style section labels ("PRE-WORKOUT", "DONE · 2"). */
val SectionLabelStyle = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.8.sp)

private val LocalTrackerColors = staticCompositionLocalOf { LightTokens }

object Tracker {
    val colors: TrackerColors
        @Composable @ReadOnlyComposable get() = LocalTrackerColors.current
}

@Composable
fun ProtocolTrackerTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val tokens = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalTrackerColors provides tokens) {
        MaterialTheme(colorScheme = scheme(tokens, dark), typography = AppTypography, content = content)
    }
}
