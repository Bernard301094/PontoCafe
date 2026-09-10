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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Constantes literais da paleta do design system "Ponto Café". Use estes valores
 * quando um componente precisa da cor exata da marca (vinheta do quiosque,
 * glow biométrico) em vez de um papel semântico do Material 3.
 *
 * A paleta sintetiza clareza corporativa (neutros frios, levemente azulados) com
 * o calor tátil do ritual do café (âmbar profundo tostado).
 */
object PontoCafeBrand {
    /** Espresso escuro -- fundo do modo restrito/quiosque (on-primary-fixed). */
    val deepEspresso = Color(0xFF2F1500)

    /** Âmbar tostado -- cor operacional primária da marca (primary). */
    val tonalAmber = Color(0xFF8D4B00)

    /** Canvas claro levemente azulado do app (surface/background). */
    val softCreamSurface = Color(0xFFF9F9FF)

    /** Azul-ardósia profundo -- texto sobre o canvas claro (on-surface). */
    val darkSlate = Color(0xFF141B2B)

    /** Verde esmeralda -- estados verificados/ativos e sincronismo (tertiary). */
    val emeraldSync = Color(0xFF006C49)

    /** Vermelho de violação/spoof -- mesma família do erro fiscal (error). */
    val crimsonSpoofAlert = Color(0xFFBA1A1A)
}

/**
 * Tokens legados mantidos para compatibilidade com as telas existentes.
 *
 * Eles deixaram de representar "vidro escuro" e agora são neutros translúcidos
 * que funcionam sobre os esquemas claro e escuro do Material 3. Novos componentes
 * devem preferir MaterialTheme.colorScheme.surfaceContainer* diretamente.
 *
 * As tinturas usam o âmbar claro (#FFB77D, o `inverse-primary` do design system)
 * e não o âmbar primário: sobre fundo quase preto o primário tostado some, e o
 * âmbar claro é justamente o papel que o design reserva a superfícies escuras.
 */
object PontoCafePremium {
    val backgroundTop = PontoCafeBrand.deepEspresso
    val backgroundMid = Color(0xFF241000)
    val backgroundBottom = Color(0xFF1A0B00)
    val glass = Color(0x12FFB77D)
    val glassStrong = Color(0x1EFFB77D)
    val glassSoft = Color(0x14FFB77D)
    val border = Color(0x35FFB77D)
    val borderSoft = Color(0x24FFB77D)
    val glow = Color(0xFFFFB77D)
    val glowSoft = Color(0x24FFB77D)
    val ice = Color(0xFF4EDEA3)
    val textPrimary = PontoCafeBrand.softCreamSurface
    val textSecondary = Color(0xFFDBC2B0)
}

// Contraparte escura do design system. O design entregue especifica só o modo
// claro; este esquema deriva dele pela lógica tonal do Material 3 (o papel
// "fixed"/"fixed-dim" de cada matiz vira o acento no escuro) para que o app
// continue legível quando o sistema está em tema escuro.
private val PontoCafeDarkColors = darkColorScheme(
    primary = Color(0xFFFFB77D),
    onPrimary = Color(0xFF4B2500),
    primaryContainer = Color(0xFF6E3900),
    onPrimaryContainer = Color(0xFFFFDCC3),
    secondary = Color(0xFFE3BEB8),
    onSecondary = Color(0xFF422B27),
    secondaryContainer = Color(0xFF5B403C),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFF4EDEA3),
    onTertiary = Color(0xFF003826),
    tertiaryContainer = Color(0xFF005236),
    onTertiaryContainer = Color(0xFF6FFBBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F141D),
    onBackground = Color(0xFFDFE2F4),
    surface = Color(0xFF0F141D),
    onSurface = Color(0xFFDFE2F4),
    surfaceVariant = Color(0xFF444B5C),
    onSurfaceVariant = Color(0xFFDBC2B0),
    surfaceDim = Color(0xFF0F141D),
    surfaceBright = Color(0xFF353A45),
    surfaceContainerLowest = Color(0xFF090E17),
    surfaceContainerLow = Color(0xFF171C26),
    surfaceContainer = Color(0xFF1B202A),
    surfaceContainerHigh = Color(0xFF262B35),
    surfaceContainerHighest = Color(0xFF313640),
    surfaceTint = Color(0xFFFFB77D),
    outline = Color(0xFFA28D7C),
    outlineVariant = Color(0xFF554336),
    inverseSurface = Color(0xFFDFE2F4),
    inverseOnSurface = Color(0xFF293040),
    inversePrimary = PontoCafeBrand.tonalAmber,
    scrim = Color(0xFF000000),
    // Iguais aos do esquema claro: é o que "fixed" quer dizer.
    primaryFixed = Color(0xFFFFDCC3),
    primaryFixedDim = Color(0xFFFFB77D),
    onPrimaryFixed = Color(0xFF2F1500),
    onPrimaryFixedVariant = Color(0xFF6E3900),
    secondaryFixed = Color(0xFFFFDAD4),
    secondaryFixedDim = Color(0xFFE3BEB8),
    onSecondaryFixed = Color(0xFF2B1613),
    onSecondaryFixedVariant = Color(0xFF5B403C),
    tertiaryFixed = Color(0xFF6FFBBE),
    tertiaryFixedDim = Color(0xFF4EDEA3),
    onTertiaryFixed = Color(0xFF002113),
    onTertiaryFixedVariant = Color(0xFF005236),
)

// Esquema claro -- transcrição literal dos tokens do design system "Ponto Café".
private val PontoCafeLightColors = lightColorScheme(
    primary = PontoCafeBrand.tonalAmber,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB15F00),
    onPrimaryContainer = Color(0xFFFFFBFF),
    secondary = Color(0xFF745853),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFED7D0),
    onSecondaryContainer = Color(0xFF795C57),
    tertiary = PontoCafeBrand.emeraldSync,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF00A572),
    onTertiaryContainer = Color(0xFF00311F),
    error = PontoCafeBrand.crimsonSpoofAlert,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = PontoCafeBrand.softCreamSurface,
    onBackground = PontoCafeBrand.darkSlate,
    surface = PontoCafeBrand.softCreamSurface,
    onSurface = PontoCafeBrand.darkSlate,
    surfaceVariant = Color(0xFFDCE2F7),
    onSurfaceVariant = Color(0xFF554336),
    surfaceDim = Color(0xFFD3DAEF),
    surfaceBright = PontoCafeBrand.softCreamSurface,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3FF),
    surfaceContainer = Color(0xFFE9EDFF),
    surfaceContainerHigh = Color(0xFFE1E8FD),
    surfaceContainerHighest = Color(0xFFDCE2F7),
    surfaceTint = Color(0xFF904D00),
    outline = Color(0xFF887364),
    outlineVariant = Color(0xFFDBC2B0),
    inverseSurface = Color(0xFF293040),
    inverseOnSurface = Color(0xFFEDF0FF),
    inversePrimary = Color(0xFFFFB77D),
    scrim = Color(0xFF000000),
    // Papéis "fixed": por definição do Material 3 valem o mesmo no claro e no
    // escuro. O design usa muito -- é deles que saem as pílulas de estado
    // (âmbar claro para etapa, verde claro para "ativo/operacional").
    primaryFixed = Color(0xFFFFDCC3),
    primaryFixedDim = Color(0xFFFFB77D),
    onPrimaryFixed = Color(0xFF2F1500),
    onPrimaryFixedVariant = Color(0xFF6E3900),
    secondaryFixed = Color(0xFFFFDAD4),
    secondaryFixedDim = Color(0xFFE3BEB8),
    onSecondaryFixed = Color(0xFF2B1613),
    onSecondaryFixedVariant = Color(0xFF5B403C),
    tertiaryFixed = Color(0xFF6FFBBE),
    tertiaryFixedDim = Color(0xFF4EDEA3),
    onTertiaryFixed = Color(0xFF002113),
    onTertiaryFixedVariant = Color(0xFF005236),
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
    // Esmeralda no papel "fixed-dim" do design: sobre fundo escuro é ele, e não
    // o tertiary #006C49 do modo claro, que mantém o contraste acima de 4.5:1.
    success = Color(0xFF4EDEA3),
    successContainer = Color(0xFF005236),
    onSuccessContainer = Color(0xFF6FFBBE),
    // Amarelo-ouro mais puro: com o primary em âmbar tostado, o warning precisa
    // de um matiz distante o bastante para não parecer a mesma cor da marca.
    warning = Color(0xFFFFD54D),
    warningContainer = Color(0xFF6B5300),
    onWarningContainer = Color(0xFFFFE9A6),
    info = Color(0xFFA5CDFF),
    infoContainer = Color(0xFF244A6E),
    onInfoContainer = Color(0xFFD3E5FF),
    // Coral claro em vez do crimson literal da marca: no escuro o #BA1A1A não
    // alcança contraste de texto, e este tom fica perceptivelmente mais quente
    // que o error (#FFB4AB), preservando a distinção entre erro de formulário
    // e ocorrência crítica de jornada.
    critical = Color(0xFFFF8A80),
    criticalContainer = Color(0xFF5C231D),
    onCriticalContainer = Color(0xFFFFDAD3),
)

private val LightSemanticColors = PontoCafeSemanticColors(
    // Esmeralda do design system (tertiary): como texto/ícone sobre containers
    // claros e sobre o canvas o contraste passa de 4.5:1 sem escurecer o tom.
    success = PontoCafeBrand.emeraldSync,
    successContainer = Color(0xFF6FFBBE),
    onSuccessContainer = Color(0xFF002113),
    warning = Color(0xFF8C6D00),
    warningContainer = Color(0xFFFFE18C),
    onWarningContainer = Color(0xFF2B2000),
    info = Color(0xFF35618D),
    infoContainer = Color(0xFFD1E4FF),
    onInfoContainer = Color(0xFF001D35),
    // Mesmo vermelho do erro fiscal; a distinção com o erro de formulário fica
    // no container, deliberadamente mais terracota que o errorContainer rosado.
    critical = PontoCafeBrand.crimsonSpoofAlert,
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

// Geometria "Rounded (nível 2)" do design system: retângulos amigáveis para os
// containers e pílula completa reservada a botões/chips (aplicada por componente,
// via CircleShape, e não pela escala global).
//
// O degrau de 4px do design não entra na escala: o Material 3 amarra os campos
// de texto ao slot `extraSmall`, e o design pede 8px justamente para eles. Como
// os únicos outros consumidores de `extraSmall` no app são duas faixas de acento
// de 4dp de largura (que a 8px apenas viram cápsula, coerente com a linguagem de
// pílula do design), 8dp é o valor que serve à superfície que realmente aparece.
private val PontoCafeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// Escala tipográfica do design system mapeada nos papéis do Material 3.
// O tracking vai em `em` (não em `sp`) porque é assim que o design o especifica:
// proporcional ao corpo da fonte, e não um valor absoluto por papel.
private val PontoCafeTypography = Typography(
    // display-timer -- contadores de pausa e relógio do totem.
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.03).em,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.025).em,
    ),
    // display-lg
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).em,
    ),
    // headline-lg
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em,
    ),
    // headline-md
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.015).em,
    ),
    // headline-sm
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    ),
    // headline-sm responde também pelo titleLarge: o design não tem um degrau
    // "title" separado -- os títulos de seção usam o mesmo corpo de 18sp.
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em,
    ),
    // label-lg
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    ),
    // body-lg
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.em,
    ),
    // body-md
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.em,
    ),
    // body-sm
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.em,
    ),
    // label-lg
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    ),
    // label-md
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    ),
    // label-sm -- micro-tags e cabeçalhos em caixa alta dentro de pílulas.
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.04.em,
    ),
)

@Composable
fun PontoCafeAppBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (darkTheme) {
                    // No escuro a lavagem vertical sutil separa o topo do corpo
                    // sem introduzir uma borda dura entre eles.
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                colors.surfaceContainerLow,
                                colors.background,
                                colors.background,
                            ),
                        ),
                    )
                } else {
                    // Nível 0 do design system: canvas chapado, sem sombra e sem
                    // gradiente -- a hierarquia vem das superfícies elevadas.
                    Modifier.background(colors.background)
                },
            ),
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
