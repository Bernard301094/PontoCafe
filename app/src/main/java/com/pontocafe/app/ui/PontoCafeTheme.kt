package com.pontocafe.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pontocafe.app.R

/**
 * Plus Jakarta Sans, a família do design system, empacotada no APK.
 *
 * É a fonte variável oficial (um único arquivo com o eixo `wght` de 200 a 800),
 * e não um conjunto de estáticas: pesa ~176 KB para todos os pesos que o design
 * usa, contra cinco arquivos separados. Cada peso abaixo fixa o eixo no valor
 * exato pedido pelo DESIGN.md.
 *
 * Vai empacotada em vez de baixada do Google Fonts de propósito: o totem fica
 * num corredor de fábrica e não se pode assumir nem rede nem Play Services nele
 * — o mesmo raciocínio que já levou o modelo de voz para dentro do APK.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.plus_jakarta_sans_italic else R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val PlusJakartaSans = FontFamily(
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
    jakarta(800),
    jakarta(400, italic = true),
    jakarta(600, italic = true),
)

/**
 * A paleta da marca, igual à do painel web.
 *
 * Os valores são os mesmos que o `tailwind.config` do painel declara — a escala
 * `coffee`, o `amberAccent` e o `warmCream`. Não é coincidência que se pareçam:
 * são literalmente os mesmos hexadecimais, para que o totem no corredor e o
 * painel no balcão não pareçam dois produtos.
 *
 * O que havia antes era a paleta do DESIGN.md: neutros frios, levemente
 * azulados. Ela não estava errada em si, estava errada *aqui* -- o canvas era
 * `#F9F9FF`, um branco com tinta azul, e ao lado do painel a app lia-se fria e
 * clínica, que é o oposto de um ritual de café.
 */
object PontoCafeBrand {
    // A escala `coffee` do painel, do mais claro ao mais escuro.
    val coffee50 = Color(0xFFFDF8F5)
    val coffee100 = Color(0xFFF7EBE1)
    val coffee200 = Color(0xFFEBD2BF)
    val coffee300 = Color(0xFFDDB396)
    val coffee400 = Color(0xFFC7906E)
    val coffee500 = Color(0xFFA46B47)
    val coffee600 = Color(0xFF845033)
    val coffee700 = Color(0xFF683D26)
    val coffee800 = Color(0xFF4F2E1C)
    val coffee900 = Color(0xFF341D12)
    val coffee950 = Color(0xFF1E0F09)

    /** O âmbar da marca: o selo, a etapa, o realce. */
    val amberAccent = Color(0xFFD97706)

    /** Creme quente -- o canvas de todo o app (surface/background). */
    val warmCream = Color(0xFFFAF7F2)

    // Neutros quentes para texto secundário e bordas. São os `stone` do painel:
    // cinzas com fundo quente, e não os azulados que o Material dá por omissão.
    val stone200 = Color(0xFFE7E5E4)
    val stone400 = Color(0xFFA8A29E)
    val stone600 = Color(0xFF57534E)

    /** Espresso escuro -- fundo do modo restrito/quiosque. */
    val deepEspresso = coffee950

    /** Verde esmeralda -- estados verificados/ativos e sincronismo (tertiary). */
    val emeraldSync = Color(0xFF006C49)

    /** Vermelho de violação -- mesma família do erro fiscal (error). */
    val crimsonSpoofAlert = Color(0xFFBA1A1A)
}

/**
 * Tokens legados mantidos para compatibilidade com as telas existentes.
 *
 * Eles deixaram de representar "vidro escuro" e agora são neutros translúcidos
 * que funcionam sobre os esquemas claro e escuro do Material 3. Novos componentes
 * devem preferir MaterialTheme.colorScheme.surfaceContainer* diretamente.
 *
 * As tinturas usam o âmbar claro (#F0B27A, o `inverse-primary` da paleta)
 * e não o âmbar primário: sobre fundo quase preto o primário tostado some, e o
 * âmbar claro é justamente o papel que o design reserva a superfícies escuras.
 */
object PontoCafePremium {
    val backgroundTop = PontoCafeBrand.coffee900
    val backgroundMid = PontoCafeBrand.coffee950
    val backgroundBottom = Color(0xFF150A05)
    val glass = Color(0x12F0B27A)
    val glassStrong = Color(0x1EF0B27A)
    val glassSoft = Color(0x14F0B27A)
    val border = Color(0x35F0B27A)
    val borderSoft = Color(0x24F0B27A)
    val glow = Color(0xFFF0B27A)
    val glowSoft = Color(0x24F0B27A)
    val ice = Color(0xFF4EDEA3)
    val textPrimary = PontoCafeBrand.coffee50
    val textSecondary = PontoCafeBrand.coffee300
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
    background = PontoCafeBrand.coffee950,
    onBackground = PontoCafeBrand.coffee50,
    surface = PontoCafeBrand.coffee950,
    onSurface = PontoCafeBrand.coffee50,
    surfaceVariant = Color(0xFF4A3225),
    onSurfaceVariant = PontoCafeBrand.coffee300,
    surfaceDim = PontoCafeBrand.coffee950,
    surfaceBright = Color(0xFF45291A),
    surfaceContainerLowest = Color(0xFF150A05),
    surfaceContainerLow = Color(0xFF26150D),
    surfaceContainer = Color(0xFF2C1911),
    surfaceContainerHigh = Color(0xFF3A2116),
    surfaceContainerHighest = Color(0xFF47291B),
    surfaceTint = Color(0xFFF0B27A),
    outline = PontoCafeBrand.stone400,
    outlineVariant = Color(0xFF4A3225),
    inverseSurface = PontoCafeBrand.coffee50,
    inverseOnSurface = PontoCafeBrand.coffee900,
    inversePrimary = PontoCafeBrand.coffee900,
    scrim = Color(0xFF000000),
    // Iguais aos do esquema claro: é o que "fixed" quer dizer.
    primaryFixed = Color(0xFFFDEBD3),
    primaryFixedDim = Color(0xFFF5C481),
    onPrimaryFixed = PontoCafeBrand.coffee950,
    onPrimaryFixedVariant = PontoCafeBrand.coffee700,
    secondaryFixed = PontoCafeBrand.coffee100,
    secondaryFixedDim = PontoCafeBrand.coffee200,
    onSecondaryFixed = PontoCafeBrand.coffee950,
    onSecondaryFixedVariant = PontoCafeBrand.coffee800,
    tertiaryFixed = Color(0xFF6FFBBE),
    tertiaryFixedDim = Color(0xFF4EDEA3),
    onTertiaryFixed = Color(0xFF002113),
    onTertiaryFixedVariant = Color(0xFF005236),
)

// Esquema claro -- transcrição literal dos tokens do design system "Ponto Café".
private val PontoCafeLightColors = lightColorScheme(
    // O primário é o `coffee-900`, e não o âmbar, porque é ele que veste os
    // botões cheios -- e no painel todo botão de ação principal é
    // `bg-coffee-900 text-white`. O âmbar entra como realce, nos papéis "fixed".
    primary = PontoCafeBrand.coffee900,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PontoCafeBrand.coffee800,
    onPrimaryContainer = PontoCafeBrand.coffee100,
    secondary = PontoCafeBrand.coffee600,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = PontoCafeBrand.coffee100,
    onSecondaryContainer = PontoCafeBrand.coffee800,
    // O verde fica: não é cor de marca, é estado. Trocá-lo por âmbar faria
    // "sincronizado" e "em pausa" deixarem de se distinguir num relance.
    tertiary = PontoCafeBrand.emeraldSync,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF00A572),
    onTertiaryContainer = Color(0xFF00311F),
    error = PontoCafeBrand.crimsonSpoofAlert,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = PontoCafeBrand.warmCream,
    onBackground = PontoCafeBrand.coffee950,
    surface = PontoCafeBrand.warmCream,
    onSurface = PontoCafeBrand.coffee950,
    surfaceVariant = PontoCafeBrand.coffee100,
    onSurfaceVariant = PontoCafeBrand.stone600,
    surfaceDim = PontoCafeBrand.coffee200,
    surfaceBright = Color(0xFFFFFFFF),
    // A rampa de superfícies sobe do branco para o café claro. O cartão do
    // design é branco sobre o creme -- é esse contraste que o faz existir.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = PontoCafeBrand.coffee50,
    surfaceContainer = PontoCafeBrand.coffee100,
    surfaceContainerHigh = Color(0xFFF1E0D0),
    surfaceContainerHighest = PontoCafeBrand.coffee200,
    surfaceTint = PontoCafeBrand.amberAccent,
    outline = PontoCafeBrand.stone400,
    outlineVariant = PontoCafeBrand.stone200,
    inverseSurface = PontoCafeBrand.coffee900,
    inverseOnSurface = PontoCafeBrand.coffee50,
    inversePrimary = Color(0xFFF0B27A),
    scrim = Color(0xFF000000),
    // Papéis "fixed": por definição do Material 3 valem o mesmo no claro e no
    // escuro. É deles que saem as pílulas de estado -- e é aqui que o âmbar da
    // marca aparece, como no painel, onde a etapa é `bg-amber-50 text-coffee-700`.
    primaryFixed = Color(0xFFFDEBD3),
    primaryFixedDim = Color(0xFFF5C481),
    onPrimaryFixed = PontoCafeBrand.coffee950,
    onPrimaryFixedVariant = PontoCafeBrand.coffee700,
    secondaryFixed = PontoCafeBrand.coffee100,
    secondaryFixedDim = PontoCafeBrand.coffee200,
    onSecondaryFixed = PontoCafeBrand.coffee950,
    onSecondaryFixedVariant = PontoCafeBrand.coffee800,
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

// O padrão acompanha o app, que é sempre claro. Só vale para composables fora
// de PontoCafeTheme (previews soltos); dentro do tema o valor vem de lá.
val LocalPontoCafeSemanticColors = staticCompositionLocalOf { LightSemanticColors }

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
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.03).em,
    ),
    displayMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.025).em,
    ),
    // display-lg
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).em,
    ),
    // headline-lg
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em,
    ),
    // headline-md
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.015).em,
    ),
    // headline-sm
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    ),
    // headline-sm responde também pelo titleLarge: o design não tem um degrau
    // "title" separado -- os títulos de seção usam o mesmo corpo de 18sp.
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em,
    ),
    // label-lg
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    ),
    // body-lg
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.em,
    ),
    // body-md
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.em,
    ),
    // body-sm
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.em,
    ),
    // label-lg
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    ),
    // label-md
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    ),
    // label-sm -- micro-tags e cabeçalhos em caixa alta dentro de pílulas.
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.04.em,
    ),
)

@Composable
fun PontoCafeAppBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
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

/**
 * O Ponto Café é sempre claro, mesmo com o aparelho no tema escuro.
 *
 * Não é preferência estética: o design system entregue especifica só o modo
 * claro, e o app roda em totem de corredor e em aparelhos de supervisão que
 * precisam mostrar a mesma coisa lado a lado. Deixar o tema do sistema decidir
 * fazia dois aparelhos na mesma mesa exibirem paletas diferentes do mesmo turno.
 *
 * O parâmetro continua existindo — o esquema escuro está pronto e é só passar
 * `darkTheme = true` para voltar a usá-lo.
 */
@Composable
fun PontoCafeTheme(
    darkTheme: Boolean = false,
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
