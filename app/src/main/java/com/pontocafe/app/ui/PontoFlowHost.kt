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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.pontocafe.app.data.LocalCompletedPause
import com.pontocafe.app.data.SecurePontoOfflineStore
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

private const val POINT_RECEIPT_VISIBLE_MILLIS = 3_000L
private const val POINT_BLOCKED_VISIBLE_MILLIS = 2_000L
private const val USED_BREAK_WARNING_VISIBLE_MILLIS = 5_000L
private val PONTO_TIMEZONE: ZoneId = ZoneId.of("America/Fortaleza")

/**
 * Host contínuo do Ponto. A câmera permanece montada durante reconhecimento,
 * avisos e comprovante. Não existe confirmação manual de identidade nem tela
 * de código temporário no Ponto: depois da validação biométrica, o backend
 * decide se registra ou bloqueia a tentativa.
 *
 * A UI também consulta o histórico local cifrado de pausas concluídas. Essa
 * segunda barreira é somente de bloqueio: ela nunca autoriza um registro. Isso
 * garante que um Worker antigo não transforme uma folga já usada em um aviso
 * genérico de "fora do horário".
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

    // Uma nova instância é criada para cada ciclo/identificação relevante para
    // evitar ler um cachedSnapshot anterior ao retorno que acabou de ser salvo
    // pelo ViewModel em outra instância do SecurePontoOfflineStore.
    val localHistoryStore = remember(
        identificacao?.verificacaoToken,
        state.scanCycle,
        state.needsAuthorization,
    ) {
        SecurePontoOfflineStore(context.applicationContext)
    }

    val localCompletedBreak = identificacao?.colaborador?.id?.let { colaboradorId ->
        findRelevantCompletedBreak(
            store = localHistoryStore,
            collaboratorId = colaboradorId,
        )
    }
    val serverSaysUsedBreak = identificacao?.motivo == "PAUSA_PERIODO_JA_UTILIZADA"
    val usedBreakDetected = serverSaysUsedBreak || localCompletedBreak != null
    val resolvedUsedBreakMessage = when {
        localCompletedBreak != null -> localUsedBreakMessage(localCompletedBreak)
        serverSaysUsedBreak -> normalizeUsedBreakMessage(
            identificacao?.mensagem ?: "Você já utilizou sua folga deste período hoje.",
        )
        else -> null
    }

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

    LaunchedEffect(
        identificacao?.verificacaoToken,
        state.needsAuthorization,
        usedBreakDetected,
    ) {
        val atual = identificacao ?: return@LaunchedEffect
        // Folga concluída hoje é um estado terminal. Não deixamos o fluxo cair
        // em confirmarIdentidade(), porque Workers antigos transformavam isso em
        // needsAuthorization/fora do horário e encurtavam o aviso para 2 s.
        if (usedBreakDetected || state.needsAuthorization || atual.acaoSugerida == "BLOQUEADO") {
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

            // Prioridade máxima: se o servidor OU o histórico local seguro sabe
            // que esta folga já terminou hoje, nunca mostramos FORA DO HORÁRIO.
            usedBreakDetected && identificacao != null -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao.colaborador?.nome,
                mensagem = resolvedUsedBreakMessage
                    ?: "Você já utilizou sua folga deste período hoje.",
                repeatedPause = true,
            )

            identificacao?.acaoSugerida == "BLOQUEADO" -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao.colaborador?.nome,
                mensagem = identificacao.mensagem ?: "Nenhum ponto foi registrado.",
                repeatedPause = false,
            )

            state.needsAuthorization -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao?.colaborador?.nome,
                mensagem = identificacao?.mensagem
                    ?: "Fora do horário permitido. Nenhum ponto foi registrado.",
                repeatedPause = false,
            )

            identificacao != null && !state.erro.isNullOrBlank() -> {
                val errorMessage = state.erro.orEmpty()
                FastPointBlockedOverlay(
                    viewModel = viewModel,
                    nome = identificacao.colaborador?.nome,
                    mensagem = errorMessage,
                    repeatedPause = errorMessage.contains("já registrou esta pausa", ignoreCase = true) ||
                        errorMessage.contains("já utilizada", ignoreCase = true) ||
                        errorMessage.contains("folga", ignoreCase = true),
                )
            }
        }
    }
}

/**
 * Descobre qual período é relevante neste instante. Dentro da janela usa a
 * própria regra ativa. Fora dela usa a janela mais próxima. Exemplo: depois
 * do fim da janela da tarde, TARDE continua sendo a referência e uma folga de
 * tarde concluída hoje tem prioridade sobre o aviso genérico de fora do horário.
 */
private fun findRelevantCompletedBreak(
    store: SecurePontoOfflineStore,
    collaboratorId: String,
): LocalCompletedPause? {
    val snapshot = store.snapshot()
    if (snapshot.regras.isEmpty()) return null

    val now = ZonedDateTime.now(PONTO_TIMEZONE)
    val nowSeconds = now.toLocalTime().toSecondOfDay()

    val referencePeriod = snapshot.regras.mapNotNull { rule ->
        runCatching {
            val startSeconds = LocalTime.parse(rule.inicio).toSecondOfDay()
            val endSeconds = LocalTime.parse(rule.fim).toSecondOfDay()
            val distance = when {
                nowSeconds < startSeconds -> startSeconds - nowSeconds
                nowSeconds >= endSeconds -> nowSeconds - endSeconds
                else -> 0
            }
            rule.periodo to distance
        }.getOrNull()
    }.minByOrNull { (_, distance) -> distance }?.first ?: return null

    return store.completedPauseToday(collaboratorId, referencePeriod)
}

private fun localUsedBreakMessage(completed: LocalCompletedPause): String {
    val periodo = if (completed.periodo == "MANHA") "manhã" else "tarde"
    val minutos = completed.duracaoSegundos / 60
    val segundos = completed.duracaoSegundos % 60
    val duracao = when {
        minutos <= 0 -> "${segundos} s"
        segundos > 0 -> "${minutos} min ${segundos} s"
        else -> "${minutos} min"
    }
    return "Você já utilizou sua folga da $periodo hoje. " +
        "Saída: ${completed.inicioLocal} · Retorno: ${completed.fimLocal} · Duração: $duracao."
}

@Composable
private fun FastPointBlockedOverlay(
    viewModel: PontoCafeViewModel,
    nome: String?,
    mensagem: String,
    repeatedPause: Boolean,
) {
    val view = LocalView.current
    val mensagemExibida = if (repeatedPause) normalizeUsedBreakMessage(mensagem) else mensagem

    LaunchedEffect(nome, mensagemExibida, repeatedPause) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        delay(
            if (repeatedPause) USED_BREAK_WARNING_VISIBLE_MILLIS
            else POINT_BLOCKED_VISIBLE_MILLIS,
        )
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
                text = if (repeatedPause) "FOLGA JÁ UTILIZADA" else "REGISTRO NÃO REALIZADO",
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
                text = mensagemExibida,
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

/**
 * Normaliza qualquer resposta antiga do backend para a linguagem atual da UI.
 * Mantém os detalhes de saída/retorno/duração quando estiverem disponíveis.
 */
private fun normalizeUsedBreakMessage(original: String): String {
    if (original.startsWith("Você já utilizou sua folga", ignoreCase = true)) {
        return original
    }

    val periodo = when {
        original.contains("manhã", ignoreCase = true) -> " da manhã"
        original.contains("tarde", ignoreCase = true) -> " da tarde"
        else -> " deste período"
    }

    val saidaIndex = original.indexOf("Saída:", ignoreCase = true)
    val detalhes = if (saidaIndex >= 0) {
        " " + original.substring(saidaIndex).trim()
    } else {
        ""
    }

    return "Você já utilizou sua folga$periodo hoje.$detalhes"
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
        runCatching {
            view.performHapticFeedback(
                if (warning) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.CONFIRM,
            )
        }

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
                    "Registro validado",
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
