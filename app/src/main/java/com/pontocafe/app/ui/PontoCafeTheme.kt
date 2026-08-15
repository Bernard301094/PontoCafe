package com.pontocafe.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PontoCafeLightColors = lightColorScheme(
    primary = Color(0xFF0F6B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEDE4),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DF),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2E8FC),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF9FBF9),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF404945),
    outline = Color(0xFF707975),
)

private val PontoCafeDarkColors = darkColorScheme(
    primary = Color(0xFF96D7C4),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFFB2F3DF),
    secondary = Color(0xFFB2CCC3),
    onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCDE9DF),
    tertiary = Color(0xFFA7CDDF),
    onTertiary = Color(0xFF0A3544),
    tertiaryContainer = Color(0xFF254C5C),
    onTertiaryContainer = Color(0xFFC2E8FC),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDFE4E1),
    surface = Color(0xFF111816),
    onSurface = Color(0xFFDFE4E1),
    surfaceVariant = Color(0xFF404945),
    onSurfaceVariant = Color(0xFFBFC9C4),
)

private val PontoCafeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun PontoCafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) PontoCafeDarkColors else PontoCafeLightColors,
        shapes = PontoCafeShapes,
        content = content,
    )
}
