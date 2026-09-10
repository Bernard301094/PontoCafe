package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            modifier = Modifier
                .size(PontoCafeDimensions.minimumTouchTarget)
                .semantics { contentDescription = "Voltar ao topo" },
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
            val stacked = pontoCafeWindowSizeClass(maxWidth) == PontoCafeWindowSizeClass.COMPACT ||
                LocalDensity.current.fontScale >= 1.3f
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
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

// PcFeedbackBanner used to be duplicated here with a second, independently
// implemented signature (no PontoCafeMotion, plain fadeIn/fadeOut). It now
// has a single canonical implementation in PcFeedbackBanner.kt, which already
// includes autoDismissMillis as a superset of what this file's version added.
