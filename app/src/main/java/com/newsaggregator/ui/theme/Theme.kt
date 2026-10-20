package com.newsaggregator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Full glass token set from the original styles.xml, expressed for Compose. */
data class NewsColors(
    val bg: Color,
    val glassBg: Color,
    val glassBgStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val cardBg: Color,
    val divider: Color,
    val accent: Color,
    val glassPlate: Color,
    val isLight: Boolean
)

val DarkNewsColors = NewsColors(
    bg = BgDark,
    glassBg = GlassBg,
    glassBgStrong = GlassBgStrong,
    glassBorder = GlassBorder,
    glassHighlight = GlassHighlight,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    cardBg = CardBgDark,
    divider = DividerDark,
    accent = ColorAccent,
    glassPlate = GlassPlateDark,
    isLight = false
)

val LightNewsColors = NewsColors(
    bg = BgDarkLight,
    glassBg = GlassBgLight,
    glassBgStrong = Color.White,
    glassBorder = GlassBorderLight,
    glassHighlight = GlassHighlightLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    cardBg = CardBgLight,
    divider = DividerLight,
    accent = ColorAccent,
    glassPlate = GlassPlateLight,
    isLight = true
)

val LocalNewsColors = staticCompositionLocalOf { DarkNewsColors }

/**
 * fontSizeScale scales ALL text sizes (settings font-size pref: 1.0f / 1.15f / 1.3f).
 */
@Composable
fun NewsFlowTheme(
    lightTheme: Boolean = false,
    fontSizeScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colors = if (lightTheme) LightNewsColors else DarkNewsColors
    val scheme = if (lightTheme) lightColorScheme(
        primary = ColorAccent,
        background = BgDarkLight,
        surface = BgDarkLight,
        onBackground = TextPrimaryLight,
        onSurface = TextPrimaryLight
    ) else darkColorScheme(
        primary = ColorAccent,
        background = BgDark,
        surface = BgDark,
        onBackground = TextPrimaryDark,
        onSurface = TextPrimaryDark
    )

    val typography = Typography(
        headlineSmall = TextStyle(fontSize = (18 * fontSizeScale).sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = (15 * fontSizeScale).sp, fontWeight = FontWeight.Bold),
        titleSmall = TextStyle(fontSize = (13 * fontSizeScale).sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = (16 * fontSizeScale).sp),
        bodyMedium = TextStyle(fontSize = (14 * fontSizeScale).sp),
        bodySmall = TextStyle(fontSize = (11 * fontSizeScale).sp),
        labelLarge = TextStyle(fontSize = (12 * fontSizeScale).sp, fontWeight = FontWeight.Bold),
        labelMedium = TextStyle(fontSize = (10 * fontSizeScale).sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = (9 * fontSizeScale).sp)
    )

    CompositionLocalProvider(LocalNewsColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content
        )
    }
}

/** Default body font family (system default like the original app). */
val AppFontFamily: FontFamily = FontFamily.Default
