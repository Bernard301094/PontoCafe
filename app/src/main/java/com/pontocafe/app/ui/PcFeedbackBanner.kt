package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Feedback compacto e animado para ações administrativas e de autenticação.
 * A mensagem entra em uma live region para que TalkBack também receba a mudança
 * de estado sem depender apenas de cor ou movimento.
 *
 * Única implementação canônica: este componente já existiu duplicado (uma
 * segunda `fun PcFeedbackBanner` vivia em `OperationalUxComponents.kt`, sem
 * tokens de movimento e sem `autoDismissMillis`), e a resolução de sobrecarga
 * do Kotlin escolhia uma ou outra silenciosamente conforme os parâmetros
 * passados em cada chamada. `autoDismissMillis` existe aqui como superconjunto
 * das duas versões anteriores para que nenhum chamador precise mudar.
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
        enter = fadeIn(tween(PontoCafeMotion.Standard)) + scaleIn(
            animationSpec = tween(PontoCafeMotion.Standard),
            initialScale = 0.98f,
        ),
        exit = fadeOut(tween(PontoCafeMotion.Quick)) + scaleOut(
            animationSpec = tween(PontoCafeMotion.Quick),
            targetScale = 0.98f,
        ),
    ) {
        val text = message ?: return@AnimatedVisibility
        val semantic = LocalPontoCafeSemanticColors.current
        val (container, content, icon) = when (tone) {
            PontoCafeTone.SUCCESS -> Triple(semantic.successContainer, semantic.onSuccessContainer, Icons.Default.CheckCircle)
            PontoCafeTone.WARNING -> Triple(semantic.warningContainer, semantic.onWarningContainer, Icons.Default.Warning)
            PontoCafeTone.DANGER -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Default.Warning)
            PontoCafeTone.INFO -> Triple(semantic.infoContainer, semantic.onInfoContainer, Icons.Default.Info)
            PontoCafeTone.NEUTRAL -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Info)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = if (tone == PontoCafeTone.DANGER) LiveRegionMode.Assertive else LiveRegionMode.Polite
                    stateDescription = when (tone) {
                        PontoCafeTone.SUCCESS -> "Sucesso"
                        PontoCafeTone.WARNING -> "Atenção"
                        PontoCafeTone.DANGER -> "Erro"
                        PontoCafeTone.INFO -> "Informação"
                        PontoCafeTone.NEUTRAL -> "Aviso"
                    }
                },
            shape = MaterialTheme.shapes.medium,
            color = container,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (tone) {
                            PontoCafeTone.SUCCESS -> "Concluído"
                            PontoCafeTone.WARNING -> "Atenção"
                            PontoCafeTone.DANGER -> "Não foi possível concluir"
                            PontoCafeTone.INFO -> "Informação"
                            PontoCafeTone.NEUTRAL -> "Aviso"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                    )
                    Text(text, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.9f))
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar aviso", tint = content)
                    }
                }
            }
        }
    }
}
