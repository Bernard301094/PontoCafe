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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = PontoCafeMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return alpha
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
    alpha: Float,
) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f + 0.08f * alpha),
            shape = shape,
        ),
    )
}

@Composable
private fun PontoCafeSkeletonRowContent(
    modifier: Modifier,
    compact: Boolean,
    alpha: Float,
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
fun PontoCafeSkeletonRow(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val alpha = rememberSkeletonAlpha()
    PontoCafeSkeletonRowContent(
        modifier = modifier,
        compact = compact,
        alpha = alpha,
    )
}

@Composable
private fun SkeletonMetricStrip(alpha: Float) {
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
