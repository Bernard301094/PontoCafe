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

@Immutable
data class PontoCafeResponsiveInfo(
    val availableWidth: Dp,
    val pagePadding: Dp,
    val windowSizeClass: PontoCafeWindowSizeClass,
    val isNarrow: Boolean,
    val isCompact: Boolean,
    val isMedium: Boolean,
    val isExpanded: Boolean,
) {
    val isPhone: Boolean get() = windowSizeClass == PontoCafeWindowSizeClass.COMPACT
    val supportsTwoColumns: Boolean get() = windowSizeClass != PontoCafeWindowSizeClass.COMPACT
}

fun pontoCafeWindowSizeClass(width: Dp): PontoCafeWindowSizeClass = when {
    width < 600.dp -> PontoCafeWindowSizeClass.COMPACT
    width < 840.dp -> PontoCafeWindowSizeClass.MEDIUM
    else -> PontoCafeWindowSizeClass.EXPANDED
}

@Composable
fun PontoCafeResponsivePage(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 960.dp,
    content: @Composable (PontoCafeResponsiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth
        val sizeClass = pontoCafeWindowSizeClass(width)
        val info = PontoCafeResponsiveInfo(
            availableWidth = width,
            pagePadding = when (sizeClass) {
                PontoCafeWindowSizeClass.COMPACT -> if (width < 360.dp) 12.dp else 16.dp
                PontoCafeWindowSizeClass.MEDIUM,
                PontoCafeWindowSizeClass.EXPANDED -> 24.dp
            },
            windowSizeClass = sizeClass,
            // Sub-breakpoint exclusivamente para componentes muito densos.
            // A decisão principal de layout deve usar windowSizeClass/isCompact.
            isNarrow = width < 480.dp,
            isCompact = sizeClass == PontoCafeWindowSizeClass.COMPACT,
            isMedium = sizeClass == PontoCafeWindowSizeClass.MEDIUM,
            isExpanded = sizeClass == PontoCafeWindowSizeClass.EXPANDED,
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
