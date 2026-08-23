package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class PontoConnectivityFeedbackType {
    OFFLINE,
    RECOVERED,
    SYNCING,
    SYNCED,
}

private data class PontoConnectivityFeedbackMessage(
    val id: Long,
    val type: PontoConnectivityFeedbackType,
    val title: String,
    val detail: String,
)

private const val CONNECTIVITY_FEEDBACK_VISIBLE_MILLIS = 3_600L

/**
 * Feedback transitório de conectividade do Ponto.
 *
 * A tela principal já mantém os indicadores persistentes de modo offline e da
 * fila pendente. Este componente comunica somente transições importantes:
 * perda de conexão, retorno da conexão, sincronização e fila concluída. Não
 * altera o mecanismo de sincronização, a fila offline ou regras do Ponto.
 */
@Composable
internal fun PontoConnectivityFeedback(
    modoOffline: Boolean,
    sincronizandoPendencias: Boolean,
    eventosPendentes: Int,
    modifier: Modifier = Modifier,
) {
    var previousOffline by remember { mutableStateOf<Boolean?>(null) }
    var previousPending by remember { mutableIntStateOf(eventosPendentes) }
    var message by remember { mutableStateOf<PontoConnectivityFeedbackMessage?>(null) }

    LaunchedEffect(modoOffline, sincronizandoPendencias, eventosPendentes) {
        val wasOffline = previousOffline
        val oldPending = previousPending

        val next = when {
            wasOffline != null && modoOffline && wasOffline != true ->
                PontoConnectivityFeedbackMessage(
                    id = System.nanoTime(),
                    type = PontoConnectivityFeedbackType.OFFLINE,
                    title = "Sem conexão com o servidor",
                    detail = "O Ponto continua protegido no modo offline.",
                )

            wasOffline == true && !modoOffline ->
                PontoConnectivityFeedbackMessage(
                    id = System.nanoTime(),
                    type = PontoConnectivityFeedbackType.RECOVERED,
                    title = "Conexão restabelecida",
                    detail = if (eventosPendentes > 0) {
                        "$eventosPendentes registro(s) aguardando sincronização."
                    } else {
                        "O aparelho voltou a operar online."
                    },
                )

            !modoOffline && sincronizandoPendencias && eventosPendentes > 0 ->
                PontoConnectivityFeedbackMessage(
                    id = System.nanoTime(),
                    type = PontoConnectivityFeedbackType.SYNCING,
                    title = "Sincronizando registros",
                    detail = "$eventosPendentes registro(s) protegido(s) ainda pendente(s).",
                )

            wasOffline == false && !modoOffline && oldPending > 0 && eventosPendentes == 0 ->
                PontoConnectivityFeedbackMessage(
                    id = System.nanoTime(),
                    type = PontoConnectivityFeedbackType.SYNCED,
                    title = "Tudo sincronizado",
                    detail = "A fila offline deste aparelho foi concluída.",
                )

            else -> null
        }

        previousOffline = modoOffline
        previousPending = eventosPendentes
        if (next != null) message = next
    }

    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(CONNECTIVITY_FEEDBACK_VISIBLE_MILLIS)
        if (message?.id == id) message = null
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(tween(PontoCafeMotion.Standard)) + slideInVertically(
            animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
            initialOffsetY = { -it },
        ),
        exit = fadeOut(tween(PontoCafeMotion.Quick)) + slideOutVertically(
            animationSpec = tween(PontoCafeMotion.Quick),
            targetOffsetY = { -it },
        ),
    ) {
        val current = message ?: return@AnimatedVisibility
        val semantic = LocalPontoCafeSemanticColors.current
        val (container, content, icon): Triple<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color, ImageVector> =
            when (current.type) {
                PontoConnectivityFeedbackType.OFFLINE -> Triple(
                    semantic.warningContainer,
                    semantic.onWarningContainer,
                    Icons.Default.CloudOff,
                )
                PontoConnectivityFeedbackType.RECOVERED,
                PontoConnectivityFeedbackType.SYNCED -> Triple(
                    semantic.successContainer,
                    semantic.onSuccessContainer,
                    Icons.Default.CloudDone,
                )
                PontoConnectivityFeedbackType.SYNCING -> Triple(
                    semantic.infoContainer,
                    semantic.onInfoContainer,
                    Icons.Default.Sync,
                )
            }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = "${current.title}. ${current.detail}"
                },
            shape = MaterialTheme.shapes.large,
            color = container,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(21.dp))
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                    Text(
                        current.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.86f),
                    )
                }
            }
        }
    }
}
