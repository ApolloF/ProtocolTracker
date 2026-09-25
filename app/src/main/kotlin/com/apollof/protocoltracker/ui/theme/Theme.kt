package com.apollof.protocoltracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apollof.protocoltracker.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF1E6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE2),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8E4),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF3F6FD8),
    inversePrimary = Color(0xFF8ED3C1),
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F8F7),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF7F8F7),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFE2E6E4),
    onSurfaceVariant = Color(0xFF444947),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F2),
    surfaceContainer = Color(0xFFEBEEEC),
    surfaceContainerHigh = Color(0xFFE5E8E6),
    surfaceContainerHighest = Color(0xFFDFE3E1),
    outline = Color(0xFF747977),
    outlineVariant = Color(0xFFC4C8C6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8ED3C1),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0E5145),
    onPrimaryContainer = Color(0xFFCDEBE2),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCDE8E0),
    tertiary = Color(0xFFAFC6FF),
    inversePrimary = Color(0xFF1E6B5C),
    inverseSurface = Color(0xFFE0E3E1),
    inverseOnSurface = Color(0xFF2D3130),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFC0C8C5),
    surfaceContainerLowest = Color(0xFF0C0F0E),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D201F),
    surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323534),
    outline = Color(0xFF8A9390),
    outlineVariant = Color(0xFF3F4946),
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures keep times and doses aligned in lists. */
val NumericStyle = TextStyle(fontFeatureSettings = "tnum", fontSize = 14.sp)

@Composable
fun ProtocolTrackerTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
