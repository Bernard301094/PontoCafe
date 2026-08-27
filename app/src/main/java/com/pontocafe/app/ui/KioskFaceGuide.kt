package com.pontocafe.app.ui

import com.pontocafe.app.camera.FaceGuideGeometry
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val FACE_GUIDE_READY_STABILITY_MILLIS = 180L

/**
 * Histerese na descida. Sem ela, um único frame fora da política já pintava o guia
 * de vermelho enquanto o verde exigia 180 ms — assimetria que, somada ao jitter de
 * pose do ML Kit em PERFORMANCE_MODE_FAST, produzia piscada verde/vermelho contínua.
 */
private const val FACE_GUIDE_LOST_STABILITY_MILLIS = 420L

private const val PARTICLE_BURST_MILLIS = 700
private const val REJECT_SHAKE_MILLIS = 420
private const val PARTICLE_COUNT = 10
private const val LOCK_RIPPLE_MILLIS = 620

/**
 * Largura/altura da caixa do guia. Rosto é mais alto que largo, então o alvo também
 * precisa ser — ver a derivação em FaceGuideGeometry.
 *
 * FaceKioskScreen inverte estas duas constantes para transformar a altura de oval
 * exigida pela política na largura passada em guideWidth. Elas são o único contrato
 * entre a tela e o desenho: mudar o desenho sem mudá-las reintroduz exatamente o bug
 * que esta correção resolveu.
 */
internal const val KIOSK_GUIDE_CANVAS_ASPECT = FaceGuideGeometry.GUIDE_OVAL_ASPECT

/** Quanto da caixa o oval ocupa; a sobra é respiro para o traço e o glow. */
internal const val KIOSK_GUIDE_OVAL_FILL = 0.96f

/**
 * Reticula biométrica ambiente. Âmbar pulsante enquanto procura/posiciona o rosto,
 * anel esmeralda com explosão de partículas no instante de um reconhecimento bem
 * sucedido (matchCelebration), e anel rubi com tremor curto para spoof/rejeição
 * (rejected). A histerese de estabilidade do rosto (stablePositioned) é a mesma
 * lógica anti-flicker de antes — não foi alterada, só o desenho mudou de cantos
 * retangulares para um anel circular.
 */
@Composable
internal fun KioskFaceGuide(
    active: Boolean,
    faceDetected: Boolean,
    warning: Boolean,
    positioned: Boolean,
    recognitionReady: Boolean,
    guideWidth: Dp,
    modifier: Modifier = Modifier,
    turnProgress: Float = 0f,
    matchCelebration: Boolean = false,
    rejected: Boolean = false,
) {
    var stablePositioned by remember { mutableStateOf(false) }

    LaunchedEffect(active, faceDetected, warning, positioned) {
        if (!active || !faceDetected || warning) {
            stablePositioned = false
        } else if (positioned) {
            delay(FACE_GUIDE_READY_STABILITY_MILLIS)
            stablePositioned = true
        } else {
            delay(FACE_GUIDE_LOST_STABILITY_MILLIS)
            stablePositioned = false
        }
    }

    val detecting = active && (!faceDetected || !stablePositioned) && !warning
    val targetColor = when {
        !active -> Color.White.copy(alpha = 0.32f)
        warning -> DarkSemanticColors.critical
        detecting -> PontoCafeBrand.tonalAmber
        else -> DarkSemanticColors.success
    }
    val guideColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            PontoCafeMotion.Standard,
            easing = PontoCafeMotion.EmphasizedEasing,
        ),
        label = "kiosk-guide-color",
    )

    val pulseTransition = rememberInfiniteTransition(label = "kiosk-reticle")
    val ambientPulse by pulseTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (detecting) 1100 else 650, easing = PontoCafeMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kiosk-reticle-pulse",
    )

    // Varredura: um arco curto girando pelo perímetro do oval enquanto procura.
    //
    // O pulso de tamanho foi removido de propósito (animar o raio fazia o alvo
    // mentir sobre o que FaceCapturePolicy aceita), mas isso deixou a retícula
    // parada, só trocando de cor. Movimento que NÃO é o contorno do alvo resolve
    // as duas coisas: o oval fica cravado no tamanho certo e a tela mostra que
    // está trabalhando.
    val sweepAngle by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (detecting) 1900 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "kiosk-reticle-sweep",
    )

    // Anel de confirmação: ao travar a posição, uma onda sai do oval para fora e
    // some. Dispara uma vez por borda de subida, não fica em loop.
    val lockProgress = remember { Animatable(1f) }
    LaunchedEffect(stablePositioned) {
        if (stablePositioned) {
            lockProgress.snapTo(0f)
            lockProgress.animateTo(1f, tween(LOCK_RIPPLE_MILLIS, easing = PontoCafeMotion.EmphasizedEasing))
        }
    }

    // Explosão de partículas: dispara uma vez a cada borda de subida de
    // matchCelebration, sem reiniciar a máquina de estados de reconhecimento.
    val burstProgress = remember { Animatable(0f) }
    LaunchedEffect(matchCelebration) {
        if (matchCelebration) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(1f, tween(PARTICLE_BURST_MILLIS, easing = LinearEasing))
        }
    }

    // Tremor curto: dispara uma vez a cada borda de subida de rejected.
    val shakeProgress = remember { Animatable(0f) }
    LaunchedEffect(rejected) {
        if (rejected) {
            shakeProgress.snapTo(0f)
            shakeProgress.animateTo(1f, tween(REJECT_SHAKE_MILLIS, easing = LinearEasing))
        }
    }
    val shakeOffsetPx = if (shakeProgress.value in 0f..1f && shakeProgress.value < 1f) {
        val decay = 1f - shakeProgress.value
        (sin(shakeProgress.value * 28f) * 10f * decay)
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .width(guideWidth)
            .aspectRatio(KIOSK_GUIDE_CANVAS_ASPECT)
            .semantics {
                contentDescription = when {
                    !active -> "Reticula biométrica indisponível"
                    rejected || warning -> "Reticula rubi: verificação recusada, tente novamente"
                    matchCelebration -> "Reticula esmeralda: reconhecimento confirmado"
                    detecting -> "Reticula âmbar pulsando: procurando e posicionando o rosto"
                    recognitionReady -> "Reticula esmeralda pulsando: rosto pronto para reconhecimento"
                    else -> "Reticula esmeralda: posição correta"
                }
            },
    ) {
        val cx = size.width / 2f + shakeOffsetPx
        val cy = size.height / 2f

        // O oval É o alvo de enquadramento, e seu tamanho vem da política via
        // FaceGuideGeometry — por isso a geometria aqui é FIXA. O pulso ambiente
        // passou a viver só no brilho e na opacidade: animar o raio, como o anel
        // circular fazia, variava o alvo em -14% e voltava a mentir sobre o tamanho
        // que FaceCapturePolicy aceita.
        //
        // Também deixou de ser um círculo. Rosto é mais alto que largo; um círculo
        // com a altura correta precisaria ser mais largo que a tela (ver a aritmética
        // em FaceGuideGeometry). O oval de KIOSK_GUIDE_CANVAS_ASPECT cabe.
        val ovalWidth = size.width * KIOSK_GUIDE_OVAL_FILL
        val ovalHeight = size.height * KIOSK_GUIDE_OVAL_FILL
        val alive = detecting || recognitionReady
        val pulseFactor = if (alive) ambientPulse else 1f
        val ringStroke = (if (stablePositioned || recognitionReady || warning) 4.4.dp else 3.4.dp).toPx()

        // Glow externo suave, mais forte quando o anel está "vivo" (detectando ou pronto).
        val glowRadius = maxOf(ovalWidth, ovalHeight) / 2f * (1.28f + (pulseFactor - 0.86f))
        val glowAlpha = (if (alive || warning) 0.28f else 0.14f) * pulseFactor
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    guideColor.copy(alpha = glowAlpha),
                    guideColor.copy(alpha = 0f),
                ),
                center = Offset(cx, cy),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(cx, cy),
        )

        val ovalTopLeft = Offset(cx - ovalWidth / 2f, cy - ovalHeight / 2f)

        // Onda de confirmação saindo para fora do alvo. Fora do alvo de propósito:
        // por dentro competiria visualmente com o contorno que a pessoa usa para
        // se enquadrar.
        if (lockProgress.value < 1f) {
            val t = lockProgress.value
            val spread = 1f + t * 0.22f
            drawOval(
                color = guideColor.copy(alpha = (1f - t) * 0.55f),
                topLeft = Offset(cx - ovalWidth * spread / 2f, cy - ovalHeight * spread / 2f),
                size = Size(ovalWidth * spread, ovalHeight * spread),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Varredura sobre o próprio contorno enquanto procura/posiciona.
        if (detecting || recognitionReady) {
            val tail = if (detecting) 84f else 52f
            repeat(4) { step ->
                val fade = 1f - step / 4f
                drawArc(
                    color = guideColor.copy(alpha = 0.5f * fade * fade),
                    startAngle = sweepAngle + step * (tail / 4f),
                    sweepAngle = tail / 4f + 1.5f,
                    useCenter = false,
                    topLeft = ovalTopLeft,
                    size = Size(ovalWidth, ovalHeight),
                    style = Stroke(width = ringStroke * 1.7f, cap = StrokeCap.Round),
                )
            }
        }

        // Oval principal.
        drawOval(
            color = guideColor.copy(alpha = guideColor.alpha * pulseFactor),
            topLeft = Offset(cx - ovalWidth / 2f, cy - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round),
        )

        // Anel de progresso do desafio de virar o rosto: cresce de rubi a esmeralda
        // conforme os frames estáveis se acumulam, em vez de só alternar de cor no final.
        if (turnProgress > 0f) {
            val progress = turnProgress.coerceIn(0f, 1f)
            val ringColor = lerp(DarkSemanticColors.critical, DarkSemanticColors.success, progress)
            val challengeWidth = ovalWidth * 0.88f
            val challengeHeight = ovalHeight * 0.88f
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(cx - challengeWidth / 2f, cy - challengeHeight / 2f),
                size = Size(challengeWidth, challengeHeight),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Explosão de partículas na confirmação do match, agora seguindo o oval.
        if (matchCelebration && burstProgress.value < 1f) {
            val progress = burstProgress.value
            val particleAlpha = (1f - progress).coerceIn(0f, 1f)
            val travel = 0.15f + progress * 0.95f
            repeat(PARTICLE_COUNT) { index ->
                val angle = (360f / PARTICLE_COUNT) * index
                val radians = Math.toRadians(angle.toDouble())
                val px = cx + (cos(radians) * (ovalWidth / 2f * travel)).toFloat()
                val py = cy + (sin(radians) * (ovalHeight / 2f * travel)).toFloat()
                drawCircle(
                    color = DarkSemanticColors.success.copy(alpha = particleAlpha),
                    radius = (3.4.dp.toPx()) * (1f - progress * 0.5f),
                    center = Offset(px, py),
                )
            }
        }
    }
}
