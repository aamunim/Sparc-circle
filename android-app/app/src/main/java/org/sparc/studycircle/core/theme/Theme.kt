package org.sparc.studycircle.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Curated Editorial Scholarly Palette (Approved v2.0 UI Design System)
val SparcEmerald = Color(0xFF0F5132)
val SparcEmeraldDark = Color(0xFF0A3622)
val SparcEmeraldContainer = Color(0xFFD1E7DD)
val SparcOliveBadge = Color(0xFF6B7B1E)

val SparcAmber = Color(0xFFD97706)
val SparcAmberDark = Color(0xFF92400E)
val SparcAmberContainer = Color(0xFFFEF3C7)
val SparcHighlightYellow = Color(0xFFFDE68A)

val SparcCrimson = Color(0xFFB91C1C)
val SparcCrimsonContainer = Color(0xFFFEE2E2)

val SparcParchment = Color(0xFFF8F7F4)
val SparcReaderPaper = Color(0xFFFAF8F4)
val SparcSurface = Color(0xFFFFFFFF)
val SparcCardBorder = Color(0xFFE8E6E1)
val SparcSubtleBg = Color(0xFFF3F4F6)

val SparcInk = Color(0xFF0F172A)
val SparcMutedInk = Color(0xFF64748B)

private val SparcLightColors = lightColorScheme(
    primary = SparcEmerald,
    onPrimary = Color.White,
    primaryContainer = SparcEmeraldContainer,
    onPrimaryContainer = SparcEmeraldDark,
    secondary = SparcAmber,
    onSecondary = Color.White,
    secondaryContainer = SparcAmberContainer,
    onSecondaryContainer = SparcAmberDark,
    background = SparcParchment,
    onBackground = SparcInk,
    surface = SparcSurface,
    onSurface = SparcInk,
    surfaceVariant = SparcSubtleBg,
    onSurfaceVariant = SparcMutedInk,
    outline = SparcCardBorder,
    error = SparcCrimson,
    errorContainer = SparcCrimsonContainer,
    onError = Color.White
)

private val SparcTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
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
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun SparcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SparcLightColors,
        typography = SparcTypography,
        content = content
    )
}
