package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin

object PontoCafeMotion {
    // Motion curto por padrão: o app é operacional e precisa responder imediatamente.
    const val Instant = 70
    const val Quick = 90
    const val Standard = 150
    const val Emphasized = 220
    const val Slow = 320

    // Aliases mantidos para compatibilidade com componentes anteriores ao sistema Motion V2.
    const val fast = 100
    const val normal = 160
    const val emphasized = 240

    val StandardEasing = FastOutSlowInEasing
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

fun pontoEnterTransition(): EnterTransition =
    fadeIn(tween(PontoCafeMotion.Standard)) +
        scaleIn(
            animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.EmphasizedEasing),
            initialScale = 0.995f,
        )

fun pontoExitTransition(): ExitTransition =
    fadeOut(tween(PontoCafeMotion.Quick)) +
        scaleOut(
            animationSpec = tween(PontoCafeMotion.Quick),
            targetScale = 0.997f,
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
        animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.EmphasizedEasing),
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

/**
 * Tremor curto de erro: dispara uma vez a cada valor não-nulo distinto de
 * [trigger] (tipicamente o texto de erro de login). Usado em formulários de
 * autenticação para reforçar visualmente uma tentativa recusada, sem alterar
 * nenhum estado de validação — é puramente decorativo.
 */
@Composable
fun Modifier.shakeOnChange(trigger: Any?): Modifier {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(420, easing = LinearEasing))
        }
    }
    return graphicsLayer {
        translationX = if (shake.value < 1f) {
            sin(shake.value * 28f) * 10f * (1f - shake.value)
        } else {
            0f
        }
    }
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
