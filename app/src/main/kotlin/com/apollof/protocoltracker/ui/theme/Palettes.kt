package com.apollof.protocoltracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.apollof.protocoltracker.data.Palette

/** Neutral surfaces and text of one scheme and mode. */
private data class Neutrals(
    val bg: Long, val surface: Long, val surface2: Long, val line: Long, val line2: Long,
    val ink: Long, val muted: Long, val body2: Long, val outline: Long,
)

/** Accent colours of one scheme and mode. */
private data class Accents(
    val accent: Long, val onAccent: Long, val soft: Long, val text: Long, val mid: Long, val band: Long,
)

private class Scheme(val light: Pair<Neutrals, Accents>, val dark: Pair<Neutrals, Accents>)

/*
 * Hand-tuned schemes. Neutrals carry a faint tint of the accent hue. Every pair used for text is checked
 * for WCAG AA contrast in PaletteContrastTest.
 */
private val schemes: Map<Palette, Scheme> = mapOf(
    Palette.SAGE to Scheme(
        Neutrals(0xFFF3F4F2, 0xFFFFFFFF, 0xFFE8ECE9, 0xFFDCE1DE, 0xFFE6EAE7, 0xFF151917, 0xFF56605B, 0xFF36403B, 0xFF77817C) to
            Accents(0xFF1E6B5C, 0xFFFFFFFF, 0xFFD5EAE3, 0xFF145446, 0xFF8DBFB2, 0xFFE2EEE9),
        Neutrals(0xFF101312, 0xFF191D1B, 0xFF222826, 0xFF2A302D, 0xFF252B28, 0xFFE7ECE9, 0xFF9AA59F, 0xFFC3CCC7, 0xFF66706B) to
            Accents(0xFF7CCBB5, 0xFF06251E, 0xFF1B3730, 0xFFA6E0D0, 0xFF3E6A5F, 0xFF16231F),
    ),
    Palette.OCEAN to Scheme(
        Neutrals(0xFFF2F4F7, 0xFFFFFFFF, 0xFFE6EBF1, 0xFFD9DFE7, 0xFFE4E8EE, 0xFF14181D, 0xFF545E6A, 0xFF343D48, 0xFF75808C) to
            Accents(0xFF1F5FA8, 0xFFFFFFFF, 0xFFD9E6F5, 0xFF164A85, 0xFF8FB2DB, 0xFFE3ECF7),
        Neutrals(0xFF0F1215, 0xFF181C21, 0xFF21262D, 0xFF2A3038, 0xFF242A31, 0xFFE6EAF0, 0xFF9AA3AE, 0xFFC2CAD4, 0xFF66707C) to
            Accents(0xFF8DB8EE, 0xFF0A1E36, 0xFF1A2B40, 0xFFB3D0F5, 0xFF3A5A80, 0xFF152131),
    ),
    Palette.PLUM to Scheme(
        Neutrals(0xFFF5F3F6, 0xFFFFFFFF, 0xFFECE7EF, 0xFFE0DAE4, 0xFFE8E3EB, 0xFF19161B, 0xFF5E5763, 0xFF3E3843, 0xFF7F7784) to
            Accents(0xFF6B3F8F, 0xFFFFFFFF, 0xFFEADDF3, 0xFF54306F, 0xFFBFA2D6, 0xFFF0E8F6),
        Neutrals(0xFF121014, 0xFF1B181E, 0xFF252128, 0xFF2F2A33, 0xFF29252D, 0xFFECE7EF, 0xFFA69EAB, 0xFFCEC6D3, 0xFF716A76) to
            Accents(0xFFCDA8EA, 0xFF2A1240, 0xFF2E2238, 0xFFE0C8F3, 0xFF5E4775, 0xFF221A29),
    ),
    Palette.CLAY to Scheme(
        Neutrals(0xFFF6F3F1, 0xFFFFFFFF, 0xFFEEE8E4, 0xFFE3DBD6, 0xFFEBE4E0, 0xFF1B1614, 0xFF62574F, 0xFF413731, 0xFF837770) to
            Accents(0xFFA34A28, 0xFFFFFFFF, 0xFFF6DFD4, 0xFF823A1F, 0xFFDDA58C, 0xFFF8EBE4),
        Neutrals(0xFF141110, 0xFF1E1917, 0xFF28221F, 0xFF332B28, 0xFF2D2623, 0xFFF0E9E5, 0xFFAEA29B, 0xFFD6CBC5, 0xFF786D67) to
            Accents(0xFFF0A585, 0xFF3A1306, 0xFF3A2219, 0xFFF7C6B1, 0xFF7A4430, 0xFF2A1B15),
    ),
    Palette.GRAPHITE to Scheme(
        Neutrals(0xFFF4F4F5, 0xFFFFFFFF, 0xFFE9EAEC, 0xFFDCDEE1, 0xFFE6E7E9, 0xFF151618, 0xFF595D63, 0xFF393C41, 0xFF7A7E84) to
            Accents(0xFF2F3A45, 0xFFFFFFFF, 0xFFE1E5EA, 0xFF232C35, 0xFFA3ADB8, 0xFFE9ECEF),
        Neutrals(0xFF111213, 0xFF1A1B1D, 0xFF242528, 0xFF2E3033, 0xFF28292C, 0xFFE8E9EB, 0xFFA0A3A8, 0xFFC8CBD0, 0xFF6D7176) to
            Accents(0xFFC9D2DC, 0xFF15191E, 0xFF262B31, 0xFFDDE3EA, 0xFF515A64, 0xFF1D2126),
    ),
)

/** Colours shared by every scheme: categories, warning and danger. */
private fun statusColors(dark: Boolean) = if (dark) StatusColors(
    injectable = Color(0xFF8DAEEB), oral = Color(0xFFDDB271), support = Color(0xFFA3AEA8), peptide = Color(0xFFBE9FE4),
    warn = Color(0xFFE9B45A), danger = Color(0xFFF2B8B5), onDanger = Color(0xFF601410), dangerSoft = Color(0xFF8C1D18), onDangerSoft = Color(0xFFF9DEDC),
) else StatusColors(
    injectable = Color(0xFF4F74C0), oral = Color(0xFFB7802F), support = Color(0xFF7C8882), peptide = Color(0xFF8F68BF),
    warn = Color(0xFF9A5B00), danger = Color(0xFFB3261E), onDanger = Color.White, dangerSoft = Color(0xFFF9DEDC), onDangerSoft = Color(0xFF410E0B),
)

private data class StatusColors(
    val injectable: Color, val oral: Color, val support: Color, val peptide: Color, val warn: Color,
    val danger: Color, val onDanger: Color, val dangerSoft: Color, val onDangerSoft: Color,
)

private fun build(n: Neutrals, a: Accents, dark: Boolean): TrackerColors {
    val s = statusColors(dark)
    return TrackerColors(
        bg = Color(n.bg), surface = Color(n.surface), surface2 = Color(n.surface2), line = Color(n.line), line2 = Color(n.line2),
        ink = Color(n.ink), muted = Color(n.muted), body2 = Color(n.body2), outline = Color(n.outline),
        accent = Color(a.accent), onAccent = Color(a.onAccent), accentSoft = Color(a.soft), accentText = Color(a.text),
        accentMid = Color(a.mid), warn = s.warn, band = Color(a.band),
        injectable = s.injectable, oral = s.oral, support = s.support, peptide = s.peptide,
        danger = s.danger, onDanger = s.onDanger, dangerSoft = s.dangerSoft, onDangerSoft = s.onDangerSoft,
        dark = dark,
    )
}

/**
 * Tokens for a fixed [palette] ([Palette.DYNAMIC] falls back to Sage; use [dynamicTrackerColors] for the wallpaper).
 * With [pureBlack], dark mode moves every surface one step darker onto a true black background.
 */
fun trackerColors(palette: Palette, dark: Boolean, pureBlack: Boolean = false): TrackerColors {
    val scheme = schemes[palette] ?: schemes.getValue(Palette.SAGE)
    val (n, a) = if (dark) scheme.dark else scheme.light
    val colors = build(n, a, dark)
    return if (dark && pureBlack) colors.onBlack() else colors
}

private fun TrackerColors.onBlack() = copy(bg = Color.Black, surface = bg, surface2 = surface, line2 = lerp(line2, Color.Black, 0.3f), band = lerp(band, Color.Black, 0.3f))

/** Tokens from a Material You scheme (wallpaper colours), keeping the app's status colours. */
fun dynamicTrackerColors(scheme: ColorScheme, dark: Boolean, pureBlack: Boolean = false): TrackerColors {
    val s = statusColors(dark)
    val colors = TrackerColors(
        bg = if (dark) scheme.surface else scheme.surfaceContainer,
        surface = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest,
        surface2 = scheme.surfaceContainerHighest, line = scheme.outlineVariant, line2 = scheme.surfaceContainerHigh,
        ink = scheme.onSurface, muted = scheme.onSurfaceVariant, body2 = lerp(scheme.onSurface, scheme.onSurfaceVariant, 0.5f),
        outline = scheme.outline, accent = scheme.primary, onAccent = scheme.onPrimary, accentSoft = scheme.primaryContainer,
        accentText = if (dark) scheme.primary else scheme.onPrimaryContainer, accentMid = scheme.inversePrimary,
        warn = s.warn, band = scheme.surfaceContainerHigh,
        injectable = s.injectable, oral = s.oral, support = s.support, peptide = s.peptide,
        danger = s.danger, onDanger = s.onDanger, dangerSoft = s.dangerSoft, onDangerSoft = s.onDangerSoft,
        dark = dark,
    )
    return if (dark && pureBlack) colors.onBlack() else colors
}
