package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun PontoCafeResponsivePage(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 960.dp,
    content: @Composable (PontoCafeResponsiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        val sizeClass = pontoCafeWindowSizeClass(width)
        val heightClass = pontoCafeWindowHeightClass(height)
        val fontScale = LocalDensity.current.fontScale
        val info = PontoCafeResponsiveInfo(
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
