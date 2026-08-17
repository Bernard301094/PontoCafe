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

@Immutable
data class PontoCafeResponsiveInfo(
    val availableWidth: Dp,
    val pagePadding: Dp,
    val isNarrow: Boolean,
    val isCompact: Boolean,
    val isMedium: Boolean,
    val isExpanded: Boolean,
)

@Composable
fun PontoCafeResponsivePage(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 960.dp,
    content: @Composable (PontoCafeResponsiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = maxWidth
        val info = PontoCafeResponsiveInfo(
            availableWidth = width,
            pagePadding = when {
                width < 360.dp -> 12.dp
                width < 600.dp -> 16.dp
                else -> 24.dp
            },
            isNarrow = width < 360.dp,
            isCompact = width < 600.dp,
            isMedium = width >= 600.dp && width < 840.dp,
            isExpanded = width >= 840.dp,
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
