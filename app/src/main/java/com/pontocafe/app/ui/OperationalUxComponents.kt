package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ação de retorno rápido para o início de listas longas. O botão só aparece
 * depois que o usuário realmente se afastou do topo, evitando ruído visual.
 */
@Composable
fun PcScrollToTopFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val visible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 4 ||
                (listState.firstVisibleItemIndex > 0 && listState.firstVisibleItemScrollOffset > 320)
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        SmallFloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.semantics { contentDescription = "Voltar ao topo" },
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
        }
    }
}

/**
 * Bloco Material 3 para informações técnicas/administrativas. Em celulares,
 * valores longos passam para baixo do rótulo em vez de ficarem espremidos.
 */
@Composable
fun PcKeyValueCard(
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    PcSectionSurface(modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stacked = pontoCafeWindowSizeClass(maxWidth) == PontoCafeWindowSizeClass.COMPACT
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                rows.forEach { (label, value) ->
                    if (stacked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feedback operacional comum. Mensagens de sucesso/informação podem sumir
 * sozinhas; erros ficam persistentes até serem dispensados ou substituídos.
 */
@Composable
fun PcFeedbackBanner(
    message: String?,
    tone: PontoCafeTone,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    autoDismissMillis: Long? = null,
) {
    LaunchedEffect(message, autoDismissMillis) {
        if (!message.isNullOrBlank() && autoDismissMillis != null) {
            delay(autoDismissMillis)
            onDismiss?.invoke()
        }
    }

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (message.isNullOrBlank()) return@AnimatedVisibility
        val semantic = LocalPontoCafeSemanticColors.current
        val (container, content) = when (tone) {
            PontoCafeTone.SUCCESS -> semantic.successContainer to semantic.onSuccessContainer
            PontoCafeTone.WARNING -> semantic.warningContainer to semantic.onWarningContainer
            PontoCafeTone.INFO -> semantic.infoContainer to semantic.onInfoContainer
            PontoCafeTone.DANGER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = PontoCafeSpacing.md,
                    top = PontoCafeSpacing.sm,
                    bottom = PontoCafeSpacing.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
                ) {
                    Text(
                        text = when (tone) {
                            PontoCafeTone.SUCCESS -> "Concluído"
                            PontoCafeTone.WARNING -> "Atenção"
                            PontoCafeTone.INFO -> "Informação"
                            PontoCafeTone.DANGER -> "Não foi possível concluir"
                            PontoCafeTone.NEUTRAL -> "Aviso"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar aviso")
                    }
                }
            }
        }
    }
}
