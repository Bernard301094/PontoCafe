package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

object PontoCafeMotion {
    const val Quick = 140
    const val Standard = 240
    const val Emphasized = 360
    const val Slow = 520

    val StandardEasing = FastOutSlowInEasing
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

fun pontoEnterTransition(): EnterTransition =
    fadeIn(tween(PontoCafeMotion.Standard)) +
        slideInVertically(
            animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
            initialOffsetY = { it / 12 },
        ) +
        scaleIn(
            animationSpec = tween(PontoCafeMotion.Standard),
            initialScale = 0.985f,
        )

fun pontoExitTransition(): ExitTransition =
    fadeOut(tween(PontoCafeMotion.Quick)) +
        slideOutVertically(
            animationSpec = tween(PontoCafeMotion.Standard),
            targetOffsetY = { -it / 18 },
        ) +
        scaleOut(
            animationSpec = tween(PontoCafeMotion.Quick),
            targetScale = 0.99f,
        )

@Composable
fun MotionReveal(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    AnimatedVisibility(
        visible = visible && mounted,
        modifier = modifier,
        enter = pontoEnterTransition(),
        exit = pontoExitTransition(),
    ) {
        content()
    }
}

@Composable
fun animatedMetricValue(value: String): String {
    val numeric = value.toIntOrNull() ?: return value
    val animated by animateIntAsState(
        targetValue = numeric,
        animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
        label = "metric-value",
    )
    return animated.toString()
}

@Composable
fun animatedProgress(target: Float): Float {
    val progress by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.StandardEasing),
        label = "progress",
    )
    return progress
}

@Composable
fun Modifier.motionScale(active: Boolean, activeScale: Float = 1.02f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (active) activeScale else 1f,
        animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.EmphasizedEasing),
        label = "motion-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
