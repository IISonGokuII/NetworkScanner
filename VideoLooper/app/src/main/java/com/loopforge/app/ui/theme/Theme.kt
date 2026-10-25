package com.loopforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Neon {
    val Night = Color(0xFF07070F)
    val Deep = Color(0xFF0E0E1E)
    val Magenta = Color(0xFFFF3D9A)
    val Violet = Color(0xFF8B5CFF)
    val Cyan = Color(0xFF22E5FF)
    val Mint = Color(0xFF3DFFB5)
    val Amber = Color(0xFFFFC44D)
    val Coral = Color(0xFFFF5C7A)
    val Text = Color(0xFFF4F4FF)
    val TextDim = Color(0xFFA6A8C8)
    val TextFaint = Color(0xFF6E7090)
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x22FFFFFF)
    val Stroke = Color(0x1FFFFFFF)

    val Spectrum = listOf(Magenta, Violet, Cyan)
    val brush: Brush get() = Brush.linearGradient(Spectrum)
    val sweep: Brush get() = Brush.sweepGradient(listOf(Magenta, Violet, Cyan, Magenta))

    /** Traffic-light colour for a seam quality in 0..1. */
    fun quality(q: Float): Color = when {
        q >= 0.85f -> Mint
        q >= 0.6f -> Amber
        else -> Coral
    }
}

private val Colors = darkColorScheme(
    primary = Neon.Violet,
    onPrimary = Color.White,
    secondary = Neon.Cyan,
    tertiary = Neon.Magenta,
    background = Neon.Night,
    onBackground = Neon.Text,
    surface = Neon.Deep,
    onSurface = Neon.Text,
    surfaceVariant = Color(0xFF1A1A2E),
    onSurfaceVariant = Neon.TextDim,
    outline = Neon.Stroke,
)

private val Type = Typography().let { t ->
    val family = FontFamily.SansSerif
    t.copy(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-1.5).sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.sp),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.3.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.8.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp),
    )
}

@Composable
fun LoopForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
