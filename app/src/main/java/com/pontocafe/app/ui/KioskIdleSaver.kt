package com.pontocafe.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Tempo sem rosto e sem toque antes de entrar em repouso.
 *
 * Dois minutos é um meio-termo deliberado: curto o bastante para que o quiosque
 * passe a maior parte de um turno ocioso com a câmera desligada, e longo o
 * bastante para não apagar na cara de quem está lendo o comprovante ou hesitando
 * na frente do aparelho.
 */
const val KIOSK_IDLE_TIMEOUT_MILLIS = 120_000L

/**
 * Brilho da tela em repouso, em 0f..1f. Não é zero de propósito: com a tela
 * totalmente apagada ninguém descobre que basta tocar, e o quiosque parece
 * quebrado. Isto é o mínimo que ainda deixa a mensagem legível de longe.
 */
private const val KIOSK_IDLE_BRIGHTNESS = 0.02f

/**
 * Repouso do quiosque.
 *
 * O ponto do repouso é a CÂMERA, não a tela. A prévia mais o ML Kit rodando em
 * PERFORMANCE_MODE_ACCURATE são o consumo dominante desta tela — baixar só o
 * brilho economizaria a menor parte do gasto. Por isso quem chama deve parar de
 * compor o FaceCameraPreview enquanto [idle] for true: o DisposableEffect dele
 * desvincula o provider e encerra o executor de análise.
 *
 * A consequência assumida é que, em repouso, o aparelho não enxerga ninguém — não
 * há como acordar por presença sem manter ligado justamente o que se quer
 * desligar. Acorda com toque, e o aviso na tela diz isso com todas as letras.
 */
@Composable
fun KioskIdleSaver(
    idle: Boolean,
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // O brilho é atributo da janela, não do composable: precisa ser restaurado no
    // onDispose, senão sair do quiosque com o app em repouso deixa o aparelho
    // inteiro escuro até alguém mexer nas configurações.
    DisposableEffect(context, idle) {
        val activity = context as? Activity
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (idle) {
                    KIOSK_IDLE_BRIGHTNESS
                } else {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
        onDispose {
            if (window != null && previous != null) {
                window.attributes = window.attributes.apply { screenBrightness = previous }
            }
        }
    }

    AnimatedVisibility(
        visible = idle,
        enter = fadeIn(tween(PontoCafeMotion.Standard)),
        exit = fadeOut(tween(PontoCafeMotion.Standard)),
        modifier = modifier,
    ) {
        val breathe by rememberInfiniteTransition(label = "kiosk-idle").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = PontoCafeMotion.StandardEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "kiosk-idle-breathe",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    // Sem ripple e sem indicação: a tela inteira é o botão, um
                    // círculo de toque no meio do nada só confundiria.
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onWake,
                )
                .semantics {
                    contentDescription = "Ponto Café em repouso. Toque na tela para bater o ponto."
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(32.dp)
                    .graphicsLayer { alpha = breathe },
            ) {
                Text(
                    "Toque para bater o ponto",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "A câmera está desligada para poupar bateria",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Conta o tempo ocioso e devolve se o quiosque deve dormir.
 *
 * Qualquer rosto visível, operação em andamento ou toque zera a contagem — e é
 * por isso que [activityToken] existe: quem chama passa um valor que muda a cada
 * sinal de vida (contagem de rostos, ciclo de leitura, instante do último toque),
 * e a contagem recomeça sozinha.
 *
 * [enabled] false congela o relógio sem dormir: serve para nunca adormecer no
 * meio de um reconhecimento ou com um comprovante na tela.
 */
@Composable
fun rememberKioskIdleState(
    enabled: Boolean,
    activityToken: Any?,
    timeoutMillis: Long = KIOSK_IDLE_TIMEOUT_MILLIS,
): Boolean {
    var idle by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, activityToken, timeoutMillis) {
        if (!enabled) {
            idle = false
            return@LaunchedEffect
        }
        idle = false
        delay(timeoutMillis)
        idle = true
    }

    return idle
}
