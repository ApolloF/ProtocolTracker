package com.apollof.protocoltracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.data.Palette
import com.apollof.protocoltracker.data.ThemeMode
import com.apollof.protocoltracker.domain.model.CompoundCategory

/** Design tokens of the "Ledger" direction; values per scheme live in Palettes.kt. Status is never conveyed by colour alone. */
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
    val danger: Color,
    val onDanger: Color,
    val dangerSoft: Color,
    val onDangerSoft: Color,
    val dark: Boolean,
) {
    fun category(category: CompoundCategory): Color = when (category) {
        CompoundCategory.INJECTABLE_STEROID -> injectable
        CompoundCategory.ORAL_STEROID -> oral
        CompoundCategory.SUPPORT -> support
        CompoundCategory.PEPTIDE -> peptide
    }

    /** A compound or phase colour (stored as ARGB) adjusted so it reads on this theme's surfaces. */
    fun series(argb: Long): Color {
        val color = Color(argb)
        return if (dark) lerp(color, Color.White, 0.28f) else color
    }
}

/** Maps the tokens onto Material roles so stock components (dialogs, pickers, fields) match. */
internal fun materialScheme(t: TrackerColors) = if (t.dark) darkColorScheme(
    primary = t.accent, onPrimary = t.onAccent, primaryContainer = t.accentSoft, onPrimaryContainer = t.accentText,
    secondary = t.body2, onSecondary = t.bg, secondaryContainer = t.accentSoft, onSecondaryContainer = t.accentText,
    tertiary = t.warn, onTertiary = t.bg, background = t.bg, onBackground = t.ink, surface = t.bg, onSurface = t.ink,
    surfaceVariant = t.surface2, onSurfaceVariant = t.muted, surfaceContainerLowest = t.bg, surfaceContainerLow = t.surface,
    surfaceContainer = t.surface, surfaceContainerHigh = t.surface2, surfaceContainerHighest = t.surface2,
    outline = t.outline, outlineVariant = t.line, inverseSurface = t.ink, inverseOnSurface = t.bg, inversePrimary = t.accentMid,
    error = t.danger, onError = t.onDanger, errorContainer = t.dangerSoft, onErrorContainer = t.onDangerSoft,
) else lightColorScheme(
    primary = t.accent, onPrimary = t.onAccent, primaryContainer = t.accentSoft, onPrimaryContainer = t.accentText,
    secondary = t.body2, onSecondary = t.surface, secondaryContainer = t.accentSoft, onSecondaryContainer = t.accentText,
    tertiary = t.warn, onTertiary = t.surface, background = t.bg, onBackground = t.ink, surface = t.bg, onSurface = t.ink,
    surfaceVariant = t.surface2, onSurfaceVariant = t.muted, surfaceContainerLowest = t.surface, surfaceContainerLow = t.surface,
    surfaceContainer = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.surface2,
    outline = t.outline, outlineVariant = t.line, inverseSurface = t.ink, inverseOnSurface = t.bg, inversePrimary = t.accentMid,
    error = t.danger, onError = t.onDanger, errorContainer = t.dangerSoft, onErrorContainer = t.onDangerSoft,
)

/** Corner radii: small (tags, pills in rows), medium (buttons, chips, fields), large (cards, sheets). */
object Radii {
    val small = 6.dp
    val medium = 10.dp
    val large = 14.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.small), small = RoundedCornerShape(Radii.small),
    medium = RoundedCornerShape(Radii.medium), large = RoundedCornerShape(Radii.large), extraLarge = RoundedCornerShape(24.dp),
)

/** Spacing steps. Screens use [screen] side gutters and [section] between cards. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val section = 20.dp
    val screen = 16.dp
}

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

/** The app's text roles, used instead of ad-hoc font sizes. Colours come from [Tracker.colors]. */
object TrackerType {
    /** Row and card titles. */
    val title = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    /** Titles in sheets and large cards. */
    val titleLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, lineHeight = 21.sp)
    val bodySmall = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp)
    /** Helper text under a field or row. */
    val caption = TextStyle(fontFamily = PlexSans, fontSize = 12.sp, lineHeight = 16.sp)
    /** Buttons and chips. */
    val label = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    /** Tiny bold labels: weekday initials, figure cell captions. */
    val overline = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp)
    /** Small mono figures: axis labels, tags, figure captions. */
    val micro = NumericStyle.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp)
    /** Secondary mono figures: ranges, strengths, meta lines. */
    val numericSmall = NumericStyle.copy(fontSize = 12.sp, lineHeight = 16.sp)
    /** Figures in figure cells and row values. */
    val figure = NumericStyle.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    /** Headline figure of a card (weekly total). */
    val figureLarge = NumericStyle.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
}

private val LocalTrackerColors = staticCompositionLocalOf { trackerColors(Palette.SAGE, dark = false) }

object Tracker {
    val colors: TrackerColors
        @Composable @ReadOnlyComposable get() = LocalTrackerColors.current
}

/** Whether [mode] resolves to dark right now. */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ProtocolTrackerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    palette: Palette = Palette.SAGE,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(mode)
    val context = LocalContext.current
    val tokens = if (palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicTrackerColors(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), dark, pureBlack)
    } else {
        trackerColors(palette, dark, pureBlack)
    }
    CompositionLocalProvider(LocalTrackerColors provides tokens) {
        MaterialTheme(colorScheme = materialScheme(tokens), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
