package com.mycompany.synapse.ui.theme

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.game.Domain

/** Designsprache „Biolumineszenz“: tiefes Nachtblau, jeder Bereich hat seine eigene Leuchtfarbe. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val stroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val bad: Color,
    val good: Color,
    val gridLine: Color,
    private val domains: List<Color>,
    /** Spielfarben (Matrix, Waage, Schulte) – gut unterscheidbar. */
    val gameColors: List<Color>,
    /** Stroop-Farben passend zu [stroopNames]. */
    val stroopColors: List<Color>,
    val heatRamp: List<Color>,
    val stroopNames: List<String> = listOf("ROT", "BLAU", "GRÜN", "GELB", "LILA"),
) {
    fun domain(d: Domain): Color = domains[d.ordinal]

    /** Farbschwäche-Modus: Farben, die auch bei Rot-Grün-Schwäche klar unterscheidbar sind (geprüft). */
    fun colorblindSafe(): Palette = if (dark) copy(
        stroopNames = listOf("BLAU", "ORANGE", "GELB", "WEISS", "LILA"),
        stroopColors = listOf(Color(0xFF60A5FA), Color(0xFFF97316), Color(0xFFFDE047), Color(0xFFE5E7EB), Color(0xFFA855F7)),
        gameColors = listOf(Color(0xFF60A5FA), Color(0xFFF97316), Color(0xFFFDE047), Color(0xFFE5E7EB), Color(0xFFA855F7), Color(0xFF94A3B8)),
    ) else copy(
        stroopNames = listOf("BLAU", "ORANGE", "SCHWARZ", "LILA"),
        stroopColors = listOf(Color(0xFF1D4ED8), Color(0xFFEA580C), Color(0xFF111827), Color(0xFFA855F7)),
        gameColors = listOf(Color(0xFF1D4ED8), Color(0xFFEA580C), Color(0xFF111827), Color(0xFFA855F7), Color(0xFFCA8A04), Color(0xFF64748B)),
    )
}

val DarkPalette = Palette(
    dark = true,
    bgTop = Color(0xFF0B1130),
    bgBottom = Color(0xFF05070F),
    surface = Color(0xFF141B3D).copy(alpha = 0.72f),
    surfaceStrong = Color(0xFF1B2450),
    stroke = Color(0x33A5B4FC),
    textPrimary = Color(0xFFEEF2FF),
    textSecondary = Color(0xFFB4BCDF),
    textMuted = Color(0xFF7D86AE),
    bad = Color(0xFFF87171),
    good = Color(0xFF4ADE80),
    gridLine = Color(0x22A5B4FC),
    domains = listOf(
        Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFFBBF24),
        Color(0xFF34D399), Color(0xFFE879F9), Color(0xFFFB923C),
    ),
    gameColors = listOf(Color(0xFF22D3EE), Color(0xFFFBBF24), Color(0xFFE879F9), Color(0xFF4ADE80), Color(0xFFF87171), Color(0xFFA78BFA)),
    stroopColors = listOf(Color(0xFFF87171), Color(0xFF60A5FA), Color(0xFF4ADE80), Color(0xFFFACC15), Color(0xFFC084FC)),
    heatRamp = listOf(Color(0xFF161E42), Color(0xFF0E4A63), Color(0xFF117A8F), Color(0xFF19A9BF), Color(0xFF22D3EE)),
)

val LightPalette = Palette(
    dark = false,
    bgTop = Color(0xFFF3F5FF),
    bgBottom = Color(0xFFE6EAF8),
    surface = Color(0xFFFFFFFF).copy(alpha = 0.86f),
    surfaceStrong = Color(0xFFFFFFFF),
    stroke = Color(0x1F1E2A5A),
    textPrimary = Color(0xFF111735),
    textSecondary = Color(0xFF454D70),
    textMuted = Color(0xFF6F7799),
    bad = Color(0xFFDC2626),
    good = Color(0xFF15803D),
    gridLine = Color(0x1A1E2A5A),
    domains = listOf(
        Color(0xFF0891B2), Color(0xFF7C3AED), Color(0xFFCA8A04),
        Color(0xFF15803D), Color(0xFFDB2777), Color(0xFFEA580C),
    ),
    gameColors = listOf(Color(0xFF0891B2), Color(0xFFCA8A04), Color(0xFFDB2777), Color(0xFF15803D), Color(0xFFDC2626), Color(0xFF7C3AED)),
    stroopColors = listOf(Color(0xFFDC2626), Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFCA8A04), Color(0xFF9333EA)),
    heatRamp = listOf(Color(0xFFE3E8F5), Color(0xFFB3E4EE), Color(0xFF6CC7DA), Color(0xFF1FA2BF), Color(0xFF0E7490)),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

object Synapse {
    val colors: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

@Composable
fun isAppDark(themeMode: Int): Boolean = when (themeMode) {
    1 -> true
    2 -> false
    else -> isSystemInDarkTheme()
}

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

@Composable
fun SynapseTheme(dark: Boolean, colorblind: Boolean = false, content: @Composable () -> Unit) {
    val base = if (dark) DarkPalette else LightPalette
    val palette = if (colorblind) base.colorblindSafe() else base
    val scheme = if (dark) darkColorScheme(
        primary = palette.domain(Domain.RECHNEN),
        secondary = palette.domain(Domain.LOGIK),
        background = palette.bgBottom,
        surface = palette.surfaceStrong,
        onSurface = palette.textPrimary,
        onBackground = palette.textPrimary,
        error = palette.bad,
    ) else lightColorScheme(
        primary = palette.domain(Domain.RECHNEN),
        secondary = palette.domain(Domain.LOGIK),
        background = palette.bgBottom,
        surface = palette.surfaceStrong,
        onSurface = palette.textPrimary,
        onBackground = palette.textPrimary,
        error = palette.bad,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
