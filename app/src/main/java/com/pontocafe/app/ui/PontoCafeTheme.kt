package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PontoCafePremium {
    val backgroundTop = Color(0xFF0A1512)
    val backgroundMid = Color(0xFF07100E)
    val backgroundBottom = Color(0xFF040806)
    val glass = Color(0xE6111A18)
    val glassStrong = Color(0xF015211D)
    val glassSoft = Color(0xCC172520)
    val border = Color(0x3D9CE7D0)
    val borderSoft = Color(0x244E6B62)
    val glow = Color(0xFF8DE6C8)
    val glowSoft = Color(0x338DE6C8)
    val ice = Color(0xFFA8D8FF)
    val textPrimary = Color(0xFFF2F7F5)
    val textSecondary = Color(0xFFB5C4BE)
}

private val PremiumColors = darkColorScheme(
    primary = Color(0xFF8DE6C8),
    onPrimary = Color(0xFF062A21),
    primaryContainer = Color(0xCC163C32),
    onPrimaryContainer = Color(0xFFD6FFF2),
    secondary = Color(0xFFB7CBC4),
    onSecondary = Color(0xFF162A24),
    secondaryContainer = Color(0xCC263934),
    onSecondaryContainer = Color(0xFFE1EEE9),
    tertiary = Color(0xFFA8D8FF),
    onTertiary = Color(0xFF0C2D45),
    tertiaryContainer = Color(0xCC203E53),
    onTertiaryContainer = Color(0xFFE0F1FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xCC7B1F1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Transparent,
    onBackground = PontoCafePremium.textPrimary,
    surface = PontoCafePremium.glass,
    onSurface = PontoCafePremium.textPrimary,
    surfaceVariant = PontoCafePremium.glassSoft,
    onSurfaceVariant = PontoCafePremium.textSecondary,
    surfaceDim = Color(0xFF06100D),
    surfaceBright = Color(0xFF253631),
    surfaceContainerLowest = Color(0xB3070E0C),
    surfaceContainerLow = Color(0xCC0D1714),
    surfaceContainer = Color(0xE6121D19),
    surfaceContainerHigh = Color(0xF0182420),
    surfaceContainerHighest = Color(0xFF1E2B27),
    surfaceTint = Color(0xFF8DE6C8),
    outline = Color(0xFF51645D),
    outlineVariant = PontoCafePremium.borderSoft,
    inverseSurface = Color(0xFFE5ECE9),
    inverseOnSurface = Color(0xFF1D2A26),
    scrim = Color(0xCC000000),
)

@Immutable
data class PontoCafeSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

private val PremiumSemanticColors = PontoCafeSemanticColors(
    success = Color(0xFF7BE0B7),
    successContainer = Color(0xCC153B30),
    onSuccessContainer = Color(0xFFD2F9E9),
    warning = Color(0xFFFFD27D),
    warningContainer = Color(0xCC46351A),
    onWarningContainer = Color(0xFFFFEDC9),
    info = Color(0xFFA8D8FF),
    infoContainer = Color(0xCC203B50),
    onInfoContainer = Color(0xFFE0F1FF),
)

val LocalPontoCafeSemanticColors = staticCompositionLocalOf { PremiumSemanticColors }

object PontoCafeSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val PontoCafeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val PontoCafeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 37.sp,
        lineHeight = 43.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.55).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun PontoCafeAppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PontoCafePremium.backgroundTop,
                        PontoCafePremium.backgroundMid,
                        PontoCafePremium.backgroundBottom,
                    ),
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun PontoCafeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPontoCafeSemanticColors provides PremiumSemanticColors) {
        MaterialTheme(
            colorScheme = PremiumColors,
            typography = PontoCafeTypography,
            shapes = PontoCafeShapes,
        ) {
            PontoCafeAppBackground(content = content)
        }
    }
}
