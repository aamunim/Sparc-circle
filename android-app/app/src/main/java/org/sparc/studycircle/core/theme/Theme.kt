package org.sparc.studycircle.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SparcEmerald = Color(0xFF0F5132)
val SparcEmeraldDark = Color(0xFF0A3622)
val SparcEmeraldContainer = Color(0xFFD1E7DD)
val SparcAmber = Color(0xFFB45309)
val SparcAmberContainer = Color(0xFFFEF3C7)
val SparcCrimson = Color(0xFFB91C1C)
val SparcParchment = Color(0xFFF8F7F4)
val SparcSurface = Color(0xFFFFFFFF)
val SparcInk = Color(0xFF0F172A)
val SparcMutedInk = Color(0xFF475569)

private val SparcLightColors = lightColorScheme(
    primary = SparcEmerald,
    onPrimary = Color.White,
    primaryContainer = SparcEmeraldContainer,
    onPrimaryContainer = SparcEmeraldDark,
    secondary = SparcAmber,
    onSecondary = Color.White,
    secondaryContainer = SparcAmberContainer,
    onSecondaryContainer = Color(0xFF78350F),
    background = SparcParchment,
    onBackground = SparcInk,
    surface = SparcSurface,
    onSurface = SparcInk,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SparcMutedInk,
    error = SparcCrimson,
    onError = Color.White
)

private val SparcTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
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
