package org.sparc.studycircle.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.sparc.studycircle.core.model.SparcVisualTheme

data class SparcPalette(
    val theme: SparcVisualTheme,
    val isDark: Boolean,
    val useHeroHeader: Boolean,
    val bg: Color,
    val readerPaper: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val primaryEnd: Color,
    val primarySoft: Color,
    val primarySoftText: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val dockBg: Color,
    val dockBorder: Color,
    val dockInactive: Color
) {
    val primaryGradient: Brush
        get() = Brush.linearGradient(listOf(primary, primaryEnd))
}

val CobaltSaasPalette = SparcPalette(
    theme = SparcVisualTheme.COBALT_SAAS,
    isDark = false,
    useHeroHeader = false,
    bg = Color(0xFFF6F8FC),
    readerPaper = Color(0xFFFCFBF8),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0),
    text = Color(0xFF0F172A),
    muted = Color(0xFF64748B),
    primary = Color(0xFF2563EB),
    primaryEnd = Color(0xFF1D4ED8),
    primarySoft = Color(0xFFDBEAFE),
    primarySoftText = Color(0xFF1E40AF),
    accent = Color(0xFFF59E0B),
    accentSoft = Color(0xFFFEF3C7),
    accentText = Color(0xFF92400E),
    dockBg = Color(0xF5FFFFFF),
    dockBorder = Color(0xFFE2E8F0),
    dockInactive = Color(0xFF64748B)
)

val MidnightGlassPalette = SparcPalette(
    theme = SparcVisualTheme.MIDNIGHT_GLASS,
    isDark = true,
    useHeroHeader = false,
    bg = Color(0xFF090D16),
    readerPaper = Color(0xFF0E1525),
    card = Color(0xFF111827),
    cardAlt = Color(0xFF1E293B),
    border = Color(0xFF1F2937),
    text = Color(0xFFF8FAFC),
    muted = Color(0xFF94A3B8),
    primary = Color(0xFF10B981),
    primaryEnd = Color(0xFF059669),
    primarySoft = Color(0xFF064E3B),
    primarySoftText = Color(0xFF34D399),
    accent = Color(0xFFF97316),
    accentSoft = Color(0xFF431407),
    accentText = Color(0xFFFB923C),
    dockBg = Color(0xEB111827),
    dockBorder = Color(0xFF334155),
    dockInactive = Color(0xFF94A3B8)
)

val OceanHeroPalette = SparcPalette(
    theme = SparcVisualTheme.OCEAN_HERO,
    isDark = false,
    useHeroHeader = true,
    bg = Color(0xFFF2F6F9),
    readerPaper = Color(0xFFFAF9F5),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFE6F4F1),
    border = Color(0xFFDCE6EE),
    text = Color(0xFF0F172A),
    muted = Color(0xFF5B6B7F),
    primary = Color(0xFF0D9488),
    primaryEnd = Color(0xFF0284C7),
    primarySoft = Color(0xFFCCFBF1),
    primarySoftText = Color(0xFF115E59),
    accent = Color(0xFFF43F5E),
    accentSoft = Color(0xFFFFE4E6),
    accentText = Color(0xFFBE123C),
    dockBg = Color(0xEB0F172A),
    dockBorder = Color(0xFF1E293B),
    dockInactive = Color(0xFF94A3B8)
)

fun paletteFor(theme: SparcVisualTheme): SparcPalette = when (theme) {
    SparcVisualTheme.COBALT_SAAS -> CobaltSaasPalette
    SparcVisualTheme.MIDNIGHT_GLASS -> MidnightGlassPalette
    SparcVisualTheme.OCEAN_HERO -> OceanHeroPalette
}

val LocalSparcPalette = staticCompositionLocalOf { CobaltSaasPalette }

// Shared status colors
val SparcCrimson = Color(0xFFEF4444)
val SparcCrimsonContainer = Color(0xFFFEE2E2)
val SparcHighlightYellow = Color(0xFFFDE68A)
val SparcOliveBadge = Color(0xFF059669)

private val SparcTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun SparcTheme(
    visualTheme: SparcVisualTheme = SparcVisualTheme.COBALT_SAAS,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(visualTheme)
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primarySoft,
            onPrimaryContainer = palette.primarySoftText,
            secondary = palette.accent,
            secondaryContainer = palette.accentSoft,
            onSecondaryContainer = palette.accentText,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = SparcCrimson
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primarySoft,
            onPrimaryContainer = palette.primarySoftText,
            secondary = palette.accent,
            secondaryContainer = palette.accentSoft,
            onSecondaryContainer = palette.accentText,
            background = palette.bg,
            onBackground = palette.text,
            surface = palette.card,
            onSurface = palette.text,
            surfaceVariant = palette.cardAlt,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            error = SparcCrimson
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = if (palette.useHeroHeader) palette.primary.toArgb() else palette.card.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !palette.isDark && !palette.useHeroHeader
        }
    }

    CompositionLocalProvider(LocalSparcPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SparcTypography,
            content = content
        )
    }
}
