package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Política única de largura para toda a interface. Evita que cada tela invente
 * breakpoints diferentes e volte a comprimir textos em aparelhos como o A55.
 */
enum class PontoCafeWindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * A altura precisa ser tratada separadamente da largura. Um telefone em paisagem
 * pode ter largura de tablet e, ainda assim, pouquíssimo espaço vertical útil.
 */
enum class PontoCafeWindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class PontoCafeResponsiveInfo(
    val availableWidth: Dp,
    val availableHeight: Dp,
    val pagePadding: Dp,
    val windowSizeClass: PontoCafeWindowSizeClass,
    val windowHeightClass: PontoCafeWindowHeightClass,
    val isNarrow: Boolean,
    val isCompact: Boolean,
    val isMedium: Boolean,
    val isExpanded: Boolean,
    val isCompactHeight: Boolean,
    val isLandscape: Boolean,
    val fontScale: Float,
    val isLargeScreen: Boolean,
    val isExtraLargeScreen: Boolean,
) {
    val isPhone: Boolean get() = windowSizeClass == PontoCafeWindowSizeClass.COMPACT
    val usesLargeText: Boolean get() = fontScale >= 1.3f
    val usesVeryLargeText: Boolean get() = fontScale >= 1.6f
    val isShortLandscape: Boolean get() = isLandscape && availableHeight < 600.dp
    val useCompactVerticalLayout: Boolean get() = isCompactHeight || isShortLandscape

    // Em fonte ampliada, duas colunas estreitas deixam de ser realmente úteis.
    val supportsTwoColumns: Boolean
        get() = windowSizeClass != PontoCafeWindowSizeClass.COMPACT && !usesVeryLargeText
}

fun pontoCafeWindowSizeClass(width: Dp): PontoCafeWindowSizeClass = when {
    width < 600.dp -> PontoCafeWindowSizeClass.COMPACT
    width < 840.dp -> PontoCafeWindowSizeClass.MEDIUM
    else -> PontoCafeWindowSizeClass.EXPANDED
}

fun pontoCafeWindowHeightClass(height: Dp): PontoCafeWindowHeightClass = when {
    height < 480.dp -> PontoCafeWindowHeightClass.COMPACT
    height < 900.dp -> PontoCafeWindowHeightClass.MEDIUM
    else -> PontoCafeWindowHeightClass.EXPANDED
}

/**
 * Fonte única dos valores derivados. Existe para que [PontoCafeResponsivePage] e
 * [PontoCafeResponsiveOverlayScreen] não possam divergir nos breakpoints.
 */
private fun buildResponsiveInfo(width: Dp, height: Dp, fontScale: Float): PontoCafeResponsiveInfo {
    val sizeClass = pontoCafeWindowSizeClass(width)
    val heightClass = pontoCafeWindowHeightClass(height)
    return PontoCafeResponsiveInfo(
        availableWidth = width,
        availableHeight = height,
        pagePadding = when (sizeClass) {
            PontoCafeWindowSizeClass.COMPACT -> if (width < 360.dp) 12.dp else 16.dp
            PontoCafeWindowSizeClass.MEDIUM,
            PontoCafeWindowSizeClass.EXPANDED -> 24.dp
        },
        windowSizeClass = sizeClass,
        windowHeightClass = heightClass,
        // Sub-breakpoint exclusivamente para componentes muito densos.
        // A decisão principal de layout deve usar windowSizeClass/isCompact.
        isNarrow = width < 480.dp,
        isCompact = sizeClass == PontoCafeWindowSizeClass.COMPACT,
        isMedium = sizeClass == PontoCafeWindowSizeClass.MEDIUM,
        isExpanded = sizeClass == PontoCafeWindowSizeClass.EXPANDED,
        isCompactHeight = heightClass == PontoCafeWindowHeightClass.COMPACT,
        isLandscape = width > height,
        fontScale = fontScale,
        // Breakpoints grandes complementam as três classes canônicas sem
        // quebrar os `when` existentes que dependem delas.
        isLargeScreen = width >= 1_200.dp,
        isExtraLargeScreen = width >= 1_600.dp,
    )
}

/**
 * A mesma informação, sem envolver nada.
 *
 * [PontoCafeResponsivePage] e [PontoCafeResponsiveOverlayScreen] resolvem o tamanho
 * a partir das restrições do pai. Isso é o correto, mas obriga a tela inteira a
 * virar filha de um novo composable — reindentar centenas de linhas só para ler
 * `pagePadding`. Em telas que já ocupam a janela toda, o tamanho da janela e as
 * restrições do pai são a mesma coisa, e ler da configuração evita esse custo.
 *
 * Só vale para raízes de tela. Dentro de um painel, uma coluna de tablet ou um
 * diálogo, isto devolve o tamanho da JANELA e não o do espaço realmente
 * disponível — nesses casos use um dos dois composables acima.
 */
@Composable
fun rememberPontoCafeResponsiveInfo(): PontoCafeResponsiveInfo {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, fontScale) {
        buildResponsiveInfo(
            width = configuration.screenWidthDp.dp,
            height = configuration.screenHeightDp.dp,
            fontScale = fontScale,
        )
    }
}

/**
 * A mesma informação de [PontoCafeResponsivePage], sem a caixa que limita a largura.
 *
 * Telas de câmera (quiosque, cadastro biométrico) são full-bleed por natureza: a
 * prévia ocupa a tela inteira e os controles são overlays alinhados às bordas.
 * Passá-las por [PontoCafeResponsivePage] centraria a câmera numa caixa de 960 dp.
 *
 * Sem esta variante cada uma dessas telas reinventava `maxHeight < 480.dp` por
 * conta própria — exatamente o que o cabeçalho deste arquivo pede para evitar, e o
 * motivo de elas terem ficado de fora do sistema até agora. O `content` recebe o
 * escopo do BoxWithConstraints, então `Modifier.align(...)` continua disponível.
 */
@Composable
fun PontoCafeResponsiveOverlayScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(PontoCafeResponsiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        content(buildResponsiveInfo(maxWidth, maxHeight, LocalDensity.current.fontScale))
    }
}

@Composable
fun PontoCafeResponsivePage(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 960.dp,
    content: @Composable (PontoCafeResponsiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val info = buildResponsiveInfo(maxWidth, maxHeight, LocalDensity.current.fontScale)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth(),
            ) {
                content(info)
            }
        }
    }
}
