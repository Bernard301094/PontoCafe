package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corpo único para folhas extensas. Mantém as ações alcançáveis em paisagem e
 * limita a largura em tablets sem impor um tamanho fixo ao telefone.
 */
@Composable
fun PcBottomSheetContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = maxContentWidth)
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(
                    start = PontoCafeSpacing.lg,
                    end = PontoCafeSpacing.lg,
                    bottom = PontoCafeSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            content = content,
        )
    }
}

/** Conteúdo rolável para o slot `text` de diálogos Material 3 extensos. */
@Composable
fun PcDialogBody(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        content = content,
    )
}

/**
 * Ações de formulário que ficam lado a lado somente quando texto e largura
 * realmente comportam esse arranjo. Em fonte ampliada, a ação primária volta a
 * ocupar uma linha inteira.
 */
@Composable
fun PcFormActions(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
    primaryDanger: Boolean = false,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryEnabled: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stack = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.3f
        val secondary: (@Composable (Modifier) -> Unit)? = if (secondaryText != null && onSecondary != null) {
            { buttonModifier ->
                PcSecondaryButton(
                    text = secondaryText,
                    onClick = onSecondary,
                    enabled = secondaryEnabled && !primaryLoading,
                    modifier = buttonModifier,
                )
            }
        } else {
            null
        }

        if (stack) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                if (primaryDanger) {
                    PcDangerButton(
                        text = primaryText,
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        loading = primaryLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PcPrimaryButton(
                        text = primaryText,
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        loading = primaryLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                secondary?.invoke(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondary?.invoke(Modifier.weight(1f))
                if (primaryDanger) {
                    PcDangerButton(
                        text = primaryText,
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        loading = primaryLoading,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PcPrimaryButton(
                        text = primaryText,
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        loading = primaryLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
