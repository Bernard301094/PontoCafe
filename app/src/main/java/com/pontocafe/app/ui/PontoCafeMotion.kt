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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

/**
 * Física do toque, no lugar de curvas de Bézier.
 *
 * Uma mola descreve a intenção ("comprime e volta") em vez de uma duração fixa,
 * e o Compose interrompe-a a meio sem salto quando o dedo sai antes do fim —
 * um `tween` de 150 ms interrompido dá o degrau que se via ao tocar depressa
 * duas vezes no mesmo botão.
 *
 * A compressão e o regresso usam molas diferentes de propósito. Descer tem de
 * ser imediato: é a confirmação de que o toque foi registado, e uma mola macia
 * aqui faz o botão parecer lento. Subir pode ser elástico, porque já é só
 * acabamento.
 */
object PontoSprings {
    /** Descida do toque: rígida, sem oscilação — o dedo tem de sentir na hora. */
    val PressDown: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** Regresso do toque: o ressalto elástico que dá o carácter tátil. */
    val PressRelease: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Cartões e painéis a entrar ou a mudar de tamanho: firme, sem ressalto. */
    val Surface: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/**
 * Escalas de compressão por tipo de alvo.
 *
 * Um botão de lista comprime pouco — a linha inteira mexer-se distrai. Uma
 * tecla de teclado comprime mais, porque é o único sinal de que o dígito
 * entrou, e o dedo tapa o número enquanto o toca.
 */
object PontoPressScale {
    const val Surface = 0.98f
    const val Button = 0.96f
    const val Key = 0.92f
}

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

/**
 * Compressão tátil de um alvo tocável.
 *
 * Devolve só o número; quem chama aplica-o com [pontoPressScale], que escreve
 * em `graphicsLayer`. Isso mantém a animação inteira na fase de desenho: o
 * RenderThread trata da transformação e nem a medição nem o layout voltam a
 * correr enquanto o dedo está em baixo. Animar `padding` ou `size` para o mesmo
 * efeito recriaria o layout a cada frame.
 */
@Composable
fun rememberPontoPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = PontoPressScale.Button,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) PontoSprings.PressDown else PontoSprings.PressRelease,
        label = "ponto-press-scale",
    )
    return scale
}

/**
 * Aplica a escala na fase de desenho.
 *
 * `graphicsLayer` sem lambda receberia o valor por parâmetro e invalidaria a
 * composição a cada frame; a versão com lambda lê o valor dentro do bloco de
 * desenho, e só o desenho volta a correr.
 */
fun Modifier.pontoPressScale(scale: () -> Float): Modifier = graphicsLayer {
    val value = scale()
    scaleX = value
    scaleY = value
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
