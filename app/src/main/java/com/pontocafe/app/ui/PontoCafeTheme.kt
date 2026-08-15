package com.pontocafe.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEFE9),
    onPrimaryContainer = Color(0xFF0A382F),
    secondary = Color(0xFF52645E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7EEEB),
    onSecondaryContainer = Color(0xFF283631),
    tertiary = Color(0xFF315E7C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEAF4),
    onTertiaryContainer = Color(0xFF15384E),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF6E1610),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF18211E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18211E),
    surfaceVariant = Color(0xFFEEF2F0),
    onSurfaceVariant = Color(0xFF66706C),
    outline = Color(0xFFD0D8D4),
    outlineVariant = Color(0xFFE3E8E6),
    inverseSurface = Color(0xFF25312D),
    inverseOnSurface = Color(0xFFF4F7F5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD4C2),
    onPrimary = Color(0xFF043A30),
    primaryContainer = Color(0xFF164F43),
    onPrimaryContainer = Color(0xFFD5F5EC),
    secondary = Color(0xFFB8C7C1),
    onSecondary = Color(0xFF26342F),
    secondaryContainer = Color(0xFF35443F),
    onSecondaryContainer = Color(0xFFE4ECE8),
    tertiary = Color(0xFFA8CDE4),
    onTertiary = Color(0xFF17394D),
    tertiaryContainer = Color(0xFF294F67),
    onTertiaryContainer = Color(0xFFDCECF6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101513),
    onBackground = Color(0xFFE3E9E6),
    surface = Color(0xFF171D1B),
    onSurface = Color(0xFFE3E9E6),
    surfaceVariant = Color(0xFF222A27),
    onSurfaceVariant = Color(0xFFB9C4BF),
    outline = Color(0xFF53615C),
    outlineVariant = Color(0xFF303A36),
    inverseSurface = Color(0xFFE3E9E6),
    inverseOnSurface = Color(0xFF26302C),
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

private val LightSemanticColors = PontoCafeSemanticColors(
    success = Color(0xFF17845E),
    successContainer = Color(0xFFDDF4EA),
    onSuccessContainer = Color(0xFF0C4B35),
    warning = Color(0xFFA96108),
    warningContainer = Color(0xFFFFEBCB),
    onWarningContainer = Color(0xFF633B08),
    info = Color(0xFF315E7C),
    infoContainer = Color(0xFFE1EEF7),
    onInfoContainer = Color(0xFF1C435D),
)

private val DarkSemanticColors = PontoCafeSemanticColors(
    success = Color(0xFF75D5AD),
    successContainer = Color(0xFF173F32),
    onSuccessContainer = Color(0xFFC8F5E2),
    warning = Color(0xFFF7C66C),
    warningContainer = Color(0xFF493716),
    onWarningContainer = Color(0xFFFFEBC4),
    info = Color(0xFFA8CDE4),
    infoContainer = Color(0xFF223D4E),
    onInfoContainer = Color(0xFFDDEFFA),
)

val LocalPontoCafeSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object PontoCafeSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object PontoCafeMotion {
    const val fast = 160
    const val normal = 260
    const val emphasized = 420
}

private val PontoCafeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val PontoCafeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.2).sp,
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
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

@Composable
fun PontoCafeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPontoCafeSemanticColors provides if (dark) DarkSemanticColors else LightSemanticColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = PontoCafeTypography,
            shapes = PontoCafeShapes,
            content = content,
        )
    }
}
