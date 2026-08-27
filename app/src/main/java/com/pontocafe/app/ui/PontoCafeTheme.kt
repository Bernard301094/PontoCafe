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
 * Constantes literais da paleta "Warm Espresso & Slate". Use estes valores
 * quando um componente precisa da cor exata da marca (vinheta do quiosque,
 * glow biométrico) em vez de um papel semântico do Material 3.
 */
object PontoCafeBrand {
    val deepEspresso = Color(0xFF1E120B)
    val tonalAmber = Color(0xFFD97706)
    val softCreamSurface = Color(0xFFF9F6F0)
    val darkSlate = Color(0xFF121417)
    val emeraldSync = Color(0xFF059669)
    val crimsonSpoofAlert = Color(0xFFDC2626)
}

/**
 * Tokens legados mantidos para compatibilidade com as telas existentes.
 *
 * Eles deixaram de representar "vidro escuro" e agora são neutros translúcidos
 * que funcionam sobre os esquemas claro e escuro do Material 3. Novos componentes
 * devem preferir MaterialTheme.colorScheme.surfaceContainer* diretamente.
 */
object PontoCafePremium {
    val backgroundTop = PontoCafeBrand.deepEspresso
    val backgroundMid = Color(0xFF190F09)
    val backgroundBottom = Color(0xFF120B06)
    val glass = Color(0x12D97706)
    val glassStrong = Color(0x1ED97706)
    val glassSoft = Color(0x14D97706)
    val border = Color(0x35D97706)
    val borderSoft = Color(0x24D97706)
    val glow = PontoCafeBrand.tonalAmber
    val glowSoft = Color(0x24D97706)
    val ice = PontoCafeBrand.emeraldSync
    val textPrimary = PontoCafeBrand.softCreamSurface
    val textSecondary = Color(0xFFC9BEB2)
}

private val PontoCafeDarkColors = darkColorScheme(
    primary = PontoCafeBrand.tonalAmber,
    onPrimary = Color(0xFF2E1500),
    primaryContainer = Color(0xFF5C3A0C),
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFDCC1AA),
    onSecondary = Color(0xFF3D2E1F),
    secondaryContainer = Color(0xFF554434),
    onSecondaryContainer = Color(0xFFF5DEC9),
    tertiary = Color(0xFFB1D399),
    onTertiary = Color(0xFF1F3710),
    tertiaryContainer = Color(0xFF354E25),
    onTertiaryContainer = Color(0xFFCDEDB4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = PontoCafeBrand.darkSlate,
    onBackground = Color(0xFFEAE1D9),
    surface = PontoCafeBrand.darkSlate,
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD3C4B4),
    surfaceDim = PontoCafeBrand.darkSlate,
    surfaceBright = Color(0xFF3A3D42),
    surfaceContainerLowest = Color(0xFF0A0B0D),
    surfaceContainerLow = Color(0xFF191B1E),
    surfaceContainer = Color(0xFF1D2023),
    surfaceContainerHigh = Color(0xFF272A2E),
    surfaceContainerHighest = Color(0xFF323539),
    surfaceTint = PontoCafeBrand.tonalAmber,
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF4F4539),
    inverseSurface = Color(0xFFEAE1D9),
    inverseOnSurface = Color(0xFF34302A),
    scrim = Color(0xFF000000),
)

private val PontoCafeLightColors = lightColorScheme(
    primary = PontoCafeBrand.tonalAmber,
    onPrimary = Color(0xFF2E1500),
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF6F5B4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5DEC9),
    onSecondaryContainer = Color(0xFF251A0E),
    tertiary = Color(0xFF4B6B3A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEDB4),
    onTertiaryContainer = Color(0xFF0F2004),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = PontoCafeBrand.softCreamSurface,
    onBackground = Color(0xFF1F1B17),
    surface = PontoCafeBrand.softCreamSurface,
    onSurface = Color(0xFF1F1B17),
    surfaceVariant = Color(0xFFF0E0D0),
    onSurfaceVariant = Color(0xFF4F4539),
    surfaceDim = Color(0xFFE2D5C8),
    surfaceBright = PontoCafeBrand.softCreamSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF1E8),
    surfaceContainer = Color(0xFFF5EAE0),
    surfaceContainerHigh = Color(0xFFEFE4D8),
    surfaceContainerHighest = Color(0xFFE9DFD2),
    surfaceTint = PontoCafeBrand.tonalAmber,
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B4),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF8EEE4),
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
    // Antes o "crítico" (pausa excedida) caía direto em MaterialTheme.colorScheme.error
    // — a cor genérica de erro de formulário do Material, sem relação de matiz/croma
    // com success/warning/info. Agora é a quarta cor da mesma família semântica.
    val critical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
)

// Visível no módulo (não privada) porque o kiosco (FaceKioskScreen/PontoFlowHost/
// KioskFaceGuide) força fundo escuro sempre, independente do tema do sistema --
// ele precisa dos valores fixos de "escuro", não do CompositionLocal que segue
// o tema ambiente.
internal val DarkSemanticColors = PontoCafeSemanticColors(
    // Emerald Sync — usado direto (mesmo hex do token de marca), o contraste
    // contra o fundo escuro (Dark Slate) já passa de 4.5:1.
    success = PontoCafeBrand.emeraldSync,
    successContainer = Color(0xFF0B3B30),
    onSuccessContainer = Color(0xFFB0F2DD),
    // Deslocado para amarelo-ouro mais puro (antes um dourado acastanhado) --
    // com o novo primary em tom café/âmbar, warning precisava de matiz mais
    // distante para não parecer a mesma cor da marca.
    warning = Color(0xFFFFD54D),
    warningContainer = Color(0xFF6B5300),
    onWarningContainer = Color(0xFFFFE9A6),
    info = Color(0xFFA5CDFF),
    infoContainer = Color(0xFF244A6E),
    onInfoContainer = Color(0xFFD3E5FF),
    // Crimson Spoof Alert — usado direto; em texto/rótulo grande (labelMedium+
    // SemiBold) o contraste contra o fundo escuro passa no limiar de "large text".
    critical = PontoCafeBrand.crimsonSpoofAlert,
    criticalContainer = Color(0xFF5C231D),
    onCriticalContainer = Color(0xFFFFDAD3),
)

private val LightSemanticColors = PontoCafeSemanticColors(
    // Emerald Sync — como texto/ícone sobre containers claros e sobre o fundo
    // Soft Cream o contraste passa de 4.5:1 sem precisar escurecer o tom.
    success = PontoCafeBrand.emeraldSync,
    successContainer = Color(0xFFA7F3D0),
    onSuccessContainer = Color(0xFF002019),
    warning = Color(0xFF8C6D00),
    warningContainer = Color(0xFFFFE18C),
    onWarningContainer = Color(0xFF2B2000),
    info = Color(0xFF35618D),
    infoContainer = Color(0xFFD1E4FF),
    onInfoContainer = Color(0xFF001D35),
    // Crimson Spoof Alert — usado direto; como texto sobre Soft Cream o
    // contraste fica em ~4.5:1 (limiar de texto normal AA).
    critical = PontoCafeBrand.crimsonSpoofAlert,
    // Antes 0xFFFFDAD3 -- praticamente idêntico ao errorContainer padrão do
    // Material (0xFFFFDAD6), o que anulava a distinção que este token existe
    // para fazer. Um tom mais pêssego/terracota, na mesma vivacidade dos
    // containers vizinhos (success/warning/info), fica perceptivelmente
    // diferente do vermelho-rosado de erro.
    criticalContainer = Color(0xFFFFB4A0),
    onCriticalContainer = Color(0xFF410F08),
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
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val PontoCafeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
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
