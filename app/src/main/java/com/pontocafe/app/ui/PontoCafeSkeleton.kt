package com.pontocafe.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
private fun rememberSkeletonAlpha(): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = PontoCafeMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
    alpha: State<Float>,
) {
    // A opacidade é lida DENTRO do graphicsLayer, e não durante a composição.
    // Lida fora, cada frame do brilho recompunha a árvore inteira do esqueleto
    // -- justamente enquanto a tela ainda está a buscar dados e a CPU faz falta.
    val base = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = 0.06f + 0.08f * alpha.value }
            .background(color = base, shape = shape),
    )
}

@Composable
private fun PontoCafeSkeletonRowContent(
    modifier: Modifier,
    compact: Boolean,
    alpha: State<Float>,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
        border = androidx.compose.foundation.BorderStroke(1.dp, PontoCafePremium.borderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (compact) 12.dp else 15.dp),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            SkeletonBlock(
                modifier = Modifier.size(if (compact) 42.dp else 48.dp),
                shape = CircleShape,
                alpha = alpha,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(if (compact) 0.64f else 0.72f)
                        .height(15.dp),
                    alpha = alpha,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .height(11.dp),
                    alpha = alpha,
                )
                if (!compact) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(88.dp)
                            .height(24.dp),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonMetricStrip(alpha: State<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        repeat(2) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
                border = androidx.compose.foundation.BorderStroke(1.dp, PontoCafePremium.borderSoft),
            ) {
                Column(
                    modifier = Modifier.padding(PontoCafeSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    SkeletonBlock(Modifier.width(52.dp).height(26.dp), alpha = alpha)
                    SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(11.dp), alpha = alpha)
                }
            }
        }
    }
}

@Composable
fun PontoCafeListSkeletonScreen(
    title: String,
    eyebrow: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    rows: Int = 5,
    showMetrics: Boolean = true,
) {
    val sharedAlpha = rememberSkeletonAlpha()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = "Carregando $title"
            },
        contentPadding = PaddingValues(
            start = PontoCafeSpacing.lg,
            end = PontoCafeSpacing.lg,
            top = PontoCafeSpacing.md,
            bottom = PontoCafeSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("skeleton-header") {
            PontoCafeScreenHeader(
                title = title,
                eyebrow = eyebrow,
                onBack = onBack,
            )
        }
        if (showMetrics) {
            item("skeleton-metrics") { SkeletonMetricStrip(sharedAlpha) }
        }
        item("skeleton-section") {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SkeletonBlock(Modifier.width(156.dp).height(20.dp), alpha = sharedAlpha)
                SkeletonBlock(Modifier.fillMaxWidth(0.62f).height(11.dp), alpha = sharedAlpha)
            }
        }
        items(rows, key = { "skeleton-row-$it" }) {
            PontoCafeSkeletonRowContent(
                modifier = Modifier,
                compact = false,
                alpha = sharedAlpha,
            )
        }
    }
}

/**
 * Esqueleto curto para blocos dentro de uma tela já desenhada.
 *
 * Vivia em `LoadingSkeleton.kt` com uma segunda implementação do mesmo brilho —
 * outra `rememberInfiniteTransition`, outra curva, outro intervalo de opacidade.
 * Duas telas a carregar lado a lado pulsavam fora de fase, e o defeito de
 * recomposição por frame estava duplicado. Passa a usar o mesmo brilho de
 * [PontoCafeListSkeletonScreen].
 */
@Composable
fun PontoCafeLoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
) {
    val alpha = rememberSkeletonAlpha()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = "Carregando conteúdo"
            },
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        SkeletonBlock(
            modifier = Modifier.width(150.dp).height(22.dp),
            shape = RoundedCornerShape(8.dp),
            alpha = alpha,
        )
        repeat(rows.coerceIn(1, 6)) {
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp),
                alpha = alpha,
            )
        }
    }
}
