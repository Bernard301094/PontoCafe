package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

/**
 * Tokens legados mantidos para compatibilidade com as telas existentes.
 *
 * Eles deixaram de representar "vidro escuro" e agora são neutros translúcidos
 * que funcionam sobre os esquemas claro e escuro do Material 3. Novos componentes
 * devem preferir MaterialTheme.colorScheme.surfaceContainer* diretamente.
 */
object PontoCafePremium {
    val backgroundTop = Color(0xFF0A100E)
    val backgroundMid = Color(0xFF0B0F0E)
    val backgroundBottom = Color(0xFF080B0A)
    val glass = Color(0x12758A82)
    val glassStrong = Color(0x1E758A82)
    val glassSoft = Color(0x14758A82)
    val border = Color(0x357A9188)
    val borderSoft = Color(0x247A9188)
    val glow = Color(0xFF55D6B2)
    val glowSoft = Color(0x2455D6B2)
    val ice = Color(0xFF8AC7FF)
    val textPrimary = Color(0xFFF3F8F5)
    val textSecondary = Color(0xFFB9C7C1)
}

private val PontoCafeDarkColors = darkColorScheme(
    primary = Color(0xFF72DCBC),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF165143),
    onPrimaryContainer = Color(0xFFB0F2DD),
    secondary = Color(0xFFB5CCC4),
    onSecondary = Color(0xFF20352F),
    secondaryContainer = Color(0xFF354B44),
    onSecondaryContainer = Color(0xFFD1E8DF),
    tertiary = Color(0xFFA5CDFF),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF174B75),
    onTertiaryContainer = Color(0xFFD3E5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1210),
    onBackground = Color(0xFFE4EAE6),
    surface = Color(0xFF0D1210),
    onSurface = Color(0xFFE4EAE6),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C3),
    surfaceDim = Color(0xFF0D1210),
    surfaceBright = Color(0xFF333A37),
    surfaceContainerLowest = Color(0xFF080D0B),
    surfaceContainerLow = Color(0xFF151A18),
    surfaceContainer = Color(0xFF191F1C),
    surfaceContainerHigh = Color(0xFF232925),
    surfaceContainerHighest = Color(0xFF2D332F),
    surfaceTint = Color(0xFF72DCBC),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFE4EAE6),
    inverseOnSurface = Color(0xFF2A302D),
    scrim = Color(0xFF000000),
)

private val PontoCafeLightColors = lightColorScheme(
    primary = Color(0xFF006B56),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9AF2D5),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DE),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFF35618D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1E4FF),
    onTertiaryContainer = Color(0xFF001D35),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181D1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181D1B),
    surfaceVariant = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceDim = Color(0xFFD7DBD8),
    surfaceBright = Color(0xFFF7FAF8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBEFEC),
    surfaceContainerHigh = Color(0xFFE5E9E6),
    surfaceContainerHighest = Color(0xFFDCE2DE),
    surfaceTint = Color(0xFF006B56),
    outline = Color(0xFF6F7974),
    outlineVariant = Color(0xFFBEC9C3),
    inverseSurface = Color(0xFF2D312F),
    inverseOnSurface = Color(0xFFEFF1EF),
    scrim = Color(0xFF000000),
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

private val DarkSemanticColors = PontoCafeSemanticColors(
    success = Color(0xFF72DCBC),
    successContainer = Color(0xFF164D40),
    onSuccessContainer = Color(0xFFB0F2DD),
    warning = Color(0xFFFFC867),
    warningContainer = Color(0xFF55431E),
    onWarningContainer = Color(0xFFFFEAB8),
    info = Color(0xFFA5CDFF),
    infoContainer = Color(0xFF244A6E),
    onInfoContainer = Color(0xFFD3E5FF),
)

private val LightSemanticColors = PontoCafeSemanticColors(
    success = Color(0xFF006B56),
    successContainer = Color(0xFF9AF2D5),
    onSuccessContainer = Color(0xFF002019),
    warning = Color(0xFF795900),
    warningContainer = Color(0xFFFFE08A),
    onWarningContainer = Color(0xFF251A00),
    info = Color(0xFF35618D),
    infoContainer = Color(0xFFD1E4FF),
    onInfoContainer = Color(0xFF001D35),
)

val LocalPontoCafeSemanticColors = staticCompositionLocalOf { DarkSemanticColors }

object PontoCafeSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
}

object PontoCafeDimensions {
    val minimumTouchTarget = 48.dp
    val compactContentWidth = 560.dp
    val formContentWidth = 760.dp
    val detailContentWidth = 920.dp
    val dashboardContentWidth = 1_180.dp
    val dialogMaxWidth = 560.dp
}

private val PontoCafeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val PontoCafeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun PontoCafeAppBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val wash = if (darkTheme) {
        listOf(
            colors.surfaceContainerLow,
            colors.background,
            colors.background,
        )
    } else {
        listOf(
            colors.primaryContainer.copy(alpha = 0.16f),
            colors.background,
            colors.background,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(wash)),
    ) {
        content()
    }
}

@Composable
fun PontoCafeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    CompositionLocalProvider(LocalPontoCafeSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) PontoCafeDarkColors else PontoCafeLightColors,
            typography = PontoCafeTypography,
            shapes = PontoCafeShapes,
        ) {
            PontoCafeAppBackground(darkTheme = darkTheme, content = content)
        }
    }
}
