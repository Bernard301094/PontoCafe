package com.pontocafe.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.ComprovantePonto
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.TipoComprovantePonto
import kotlinx.coroutines.delay

private const val POINT_RECEIPT_VISIBLE_MILLIS = 2_000L
private const val POINT_BLOCKED_VISIBLE_MILLIS = 2_000L

/**
 * Host contínuo do Ponto. A câmera permanece montada durante reconhecimento,
 * autorização, avisos e comprovante. Não existe mais confirmação manual de
 * identidade: depois da validação biométrica, a ação segura é executada
 * automaticamente. Isso evita o antigo fluxo "É você?" e mantém a decisão
 * autoritativa no backend.
 */
@Composable
fun PontoFlowHost(
    viewModel: PontoCafeViewModel,
    hasAdminSession: Boolean,
    hasSupervisorSession: Boolean,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
    onLoginModeClick: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val state = viewModel.state
    val identificacao = state.identificacao

    // Brilho somente desta janela. Não modifica a preferência global do Android
    // e não exige WRITE_SETTINGS. Ao sair do Ponto, restauramos exatamente o
    // valor anterior (-1f também é preservado quando o sistema controlava).
    DisposableEffect(activity) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
        if (window != null) {
            val params = window.attributes
            params.screenBrightness = 1.0f
            window.attributes = params
        }
        onDispose {
            if (window != null && previousBrightness != null) {
                val params = window.attributes
                params.screenBrightness = previousBrightness
                window.attributes = params
            }
        }
    }

    // A antiga tela "É você?" foi removida do fluxo. Quando o servidor já
    // confirmou a identidade e a ação é permitida, registramos automaticamente.
    // Casos bloqueados são tratados abaixo como aviso terminal e casos fora do
    // horário continuam abrindo somente a autorização do Supervisor.
    LaunchedEffect(identificacao?.verificacaoToken, state.needsAuthorization) {
        val atual = identificacao ?: return@LaunchedEffect
        if (state.needsAuthorization || atual.acaoSugerida == "BLOQUEADO") {
            return@LaunchedEffect
        }
        viewModel.confirmarIdentidade()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaceKioskScreen(
            viewModel = viewModel,
            hasAdminSession = hasAdminSession,
            hasSupervisorSession = hasSupervisorSession,
            onAdminClick = onAdminClick,
            onSupervisorClick = onSupervisorClick,
            onLoginModeClick = onLoginModeClick,
        )

        when {
            state.comprovante != null -> FastPointReceiptOverlay(
                viewModel = viewModel,
                comprovante = state.comprovante,
            )

            state.needsAuthorization -> OpaquePontoOverlay {
                AuthorizationScreen(viewModel)
            }

            identificacao?.acaoSugerida == "BLOQUEADO" -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao.colaborador?.nome,
                mensagem = identificacao.mensagem
                    ?: "Esta pausa já foi utilizada hoje. É permitida apenas uma pausa por período.",
                repeatedPause = identificacao.motivo == "PAUSA_PERIODO_JA_UTILIZADA",
            )

            identificacao != null && !state.erro.isNullOrBlank() -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao.colaborador?.nome,
                mensagem = state.erro,
                repeatedPause = state.erro.contains("já registrou esta pausa", ignoreCase = true) ||
                    state.erro.contains("já utilizada", ignoreCase = true),
            )
        }
    }
}

@Composable
private fun OpaquePontoOverlay(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}

@Composable
private fun FastPointBlockedOverlay(
    viewModel: PontoCafeViewModel,
    nome: String?,
    mensagem: String,
    repeatedPause: Boolean,
) {
    val view = LocalView.current

    LaunchedEffect(nome, mensagem, repeatedPause) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        delay(POINT_BLOCKED_VISIBLE_MILLIS)
        viewModel.rejeitarIdentidade()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF160B08))
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )

            Text(
                text = if (repeatedPause) "PAUSA JÁ UTILIZADA" else "REGISTRO NÃO REALIZADO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White,
            )

            if (!nome.isNullOrBlank()) {
                Text(
                    text = nome,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                )
            }

            Text(
                text = mensagem,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.84f),
            )

            Text(
                text = "Nenhum novo ponto foi registrado",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.94f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FastPointReceiptOverlay(
    viewModel: PontoCafeViewModel,
    comprovante: ComprovantePonto,
) {
    val view = LocalView.current
    val start = comprovante.tipo == TipoComprovantePonto.INICIO
    val warning = !start && comprovante.excedeuLimite

    LaunchedEffect(comprovante) {
        // Feedback tátil é complementar. Uma falha do dispositivo ao vibrar não
        // pode impedir o encerramento automático do comprovante.
        runCatching {
            view.performHapticFeedback(
                if (warning) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONFIRM,
            )
        }

        // O comprovante nunca depende do último faceCount para sair da tela.
        // Dois segundos são suficientes para confirmar visualmente o registro
        // sem interromper desnecessariamente a fila do Ponto.
        delay(POINT_RECEIPT_VISIBLE_MILLIS)
        viewModel.concluirComprovante()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04110E))
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (warning) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )

            Text(
                text = if (start) "SAÍDA REGISTRADA" else "RETORNO REGISTRADO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White,
            )

            Text(
                text = comprovante.nome,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.White,
            )

            Text(
                text = if (start) {
                    "${comprovante.horarioRegistrado}  ·  retorne até ${comprovante.retornoAte ?: "--:--"}"
                } else {
                    "${comprovante.horarioRegistrado}  ·  duração ${viewModel.formatarTempo(comprovante.duracaoSegundos ?: 0)}"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.82f),
            )

            if (comprovante.pendenteSincronizacao) {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Salvo com segurança neste aparelho",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (comprovante.foraHorario) {
                Text(
                    "Liberação do Supervisor validada",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (warning) {
                Text(
                    "Registro confirmado · limite excedido",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                "Próxima pessoa em instantes",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.94f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
