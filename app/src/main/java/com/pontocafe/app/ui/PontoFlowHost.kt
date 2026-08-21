package com.pontocafe.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

private enum class PointBlockReason {
    DAILY_EXHAUSTED,
    PERIOD_USED,
    OUTSIDE_WINDOW,
    GENERIC,
}

/**
 * Host contínuo do Ponto. A câmera permanece montada durante reconhecimento,
 * avisos e comprovante. Não existe confirmação manual de identidade nem tela
 * de código temporário no Ponto: depois da validação biométrica, o backend
 * decide se registra ou bloqueia a tentativa.
 *
 * A UI também consulta o histórico local cifrado de pausas concluídas. Essa
 * segunda barreira é somente de bloqueio: ela nunca autoriza um registro.
 * Quando MANHA e TARDE já terminaram hoje, esse estado 2/2 é terminal e tem
 * prioridade absoluta sobre qualquer aviso de horário.
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

    val localHistoryStore = remember(
        identificacao?.verificacaoToken,
        state.scanCycle,
        state.needsAuthorization,
    ) {
        SecurePontoOfflineStore(context.applicationContext)
    }

    val collaboratorId = identificacao?.colaborador?.id
    val localMorningBreak = collaboratorId?.let {
        localHistoryStore.completedPauseToday(it, "MANHA")
    }
    val localAfternoonBreak = collaboratorId?.let {
        localHistoryStore.completedPauseToday(it, "TARDE")
    }
    val localDayExhausted = localMorningBreak != null && localAfternoonBreak != null

    val localCompletedBreak = if (collaboratorId != null && !localDayExhausted) {
        findRelevantCompletedBreak(
            store = localHistoryStore,
            collaboratorId = collaboratorId,
        )
    } else {
        null
    }

    val serverDayExhausted = identificacao?.motivo == "PAUSAS_DO_DIA_JA_UTILIZADAS"
    val serverSaysUsedBreak = identificacao?.motivo == "PAUSA_PERIODO_JA_UTILIZADA"
    val dayExhausted = serverDayExhausted || localDayExhausted
    val usedBreakDetected = dayExhausted || serverSaysUsedBreak || localCompletedBreak != null

    val exhaustedDayMessage = when {
        serverDayExhausted && !identificacao?.mensagem.isNullOrBlank() -> identificacao?.mensagem
        localDayExhausted -> localDailyExhaustedMessage(localMorningBreak!!, localAfternoonBreak!!)
        else -> null
    }

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

            dayExhausted -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao?.colaborador?.nome,
                mensagem = exhaustedDayMessage
                    ?: "Pausas de hoje já utilizadas (2/2). Não há mais pausa disponível para hoje.",
                reason = PointBlockReason.DAILY_EXHAUSTED,
            )

            serverSaysUsedBreak || localCompletedBreak != null ->
                FastPointBlockedOverlay(
                    viewModel = viewModel,
                    nome = identificacao?.colaborador?.nome,
                    mensagem = resolvedUsedBreakMessage
                        ?: "Você já utilizou sua folga deste período hoje.",
                    reason = PointBlockReason.PERIOD_USED,
                )

            identificacao?.acaoSugerida == "BLOQUEADO" -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao.colaborador?.nome,
                mensagem = identificacao.mensagem ?: "Nenhum ponto foi registrado.",
                reason = PointBlockReason.GENERIC,
            )

            state.needsAuthorization -> FastPointBlockedOverlay(
                viewModel = viewModel,
                nome = identificacao?.colaborador?.nome,
                mensagem = identificacao?.mensagem
                    ?: "Fora do horário permitido. Nenhum ponto foi registrado.",
                reason = PointBlockReason.OUTSIDE_WINDOW,
            )

            identificacao != null && !state.erro.isNullOrBlank() -> {
                val errorMessage = state.erro.orEmpty()
                val reason = when {
                    errorMessage.contains("2/2", ignoreCase = true) ->
                        PointBlockReason.DAILY_EXHAUSTED
                    errorMessage.contains("já registrou esta pausa", ignoreCase = true) ||
                        errorMessage.contains("já utilizada", ignoreCase = true) ||
                        errorMessage.contains("folga", ignoreCase = true) ->
                        PointBlockReason.PERIOD_USED
                    errorMessage.contains("fora do horário", ignoreCase = true) ->
                        PointBlockReason.OUTSIDE_WINDOW
                    else -> PointBlockReason.GENERIC
                }

                FastPointBlockedOverlay(
                    viewModel = viewModel,
                    nome = identificacao.colaborador?.nome,
                    mensagem = errorMessage,
                    reason = reason,
                )
            }
        }
    }
}

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

private fun localDailyExhaustedMessage(
    morning: LocalCompletedPause,
    afternoon: LocalCompletedPause,
): String = "Pausas de hoje já utilizadas (2/2). " +
    "Manhã: ${morning.inicioLocal}–${morning.fimLocal} · " +
    "Tarde: ${afternoon.inicioLocal}–${afternoon.fimLocal}. " +
    "Não há mais pausa disponível para hoje."

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
    reason: PointBlockReason,
) {
    val view = LocalView.current
    val accessibilityManager = LocalAccessibilityManager.current
    val repeatedPause = reason == PointBlockReason.DAILY_EXHAUSTED ||
        reason == PointBlockReason.PERIOD_USED
    val mensagemExibida = if (reason == PointBlockReason.PERIOD_USED) {
        normalizeUsedBreakMessage(mensagem)
    } else {
        mensagem
    }

    val title = when (reason) {
        PointBlockReason.DAILY_EXHAUSTED -> "Pausas do dia já utilizadas"
        PointBlockReason.PERIOD_USED -> "Folga já utilizada"
        PointBlockReason.OUTSIDE_WINDOW -> "Fora do horário permitido"
        PointBlockReason.GENERIC -> "Registro não realizado"
    }
    val supporting = when (reason) {
        PointBlockReason.DAILY_EXHAUSTED -> "Não há mais pausa disponível para hoje."
        PointBlockReason.PERIOD_USED -> "Nenhum novo registro foi criado para este período."
        PointBlockReason.OUTSIDE_WINDOW -> "Solicite uma liberação ao Supervisor quando aplicável."
        PointBlockReason.GENERIC -> "Nenhum novo ponto foi registrado."
    }

    LaunchedEffect(nome, mensagemExibida, reason) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT)
        }
        val baseTimeout = if (repeatedPause) {
            USED_BREAK_WARNING_VISIBLE_MILLIS
        } else {
            POINT_BLOCKED_VISIBLE_MILLIS
        }
        delay(
            accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = baseTimeout,
                containsIcons = true,
                containsText = true,
                containsControls = false,
            ) ?: baseTimeout,
        )
        viewModel.rejeitarIdentidade()
    }

    PointFeedbackBackdrop(
        accent = Color(0xFFFFC867),
        background = Color(0xFF160B08),
    ) { compactFeedback ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Assertive
                    stateDescription = listOfNotNull(title, nome, mensagemExibida, supporting)
                        .joinToString(". ")
                },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF51D1714),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = if (compactFeedback) 340.dp else 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (compactFeedback) 16.dp else 24.dp,
                        vertical = if (compactFeedback) 16.dp else 26.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(if (compactFeedback) 52.dp else 72.dp),
                    shape = CircleShape,
                    color = Color(0xFFFFC867).copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFFFC867).copy(alpha = 0.24f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (reason == PointBlockReason.OUTSIDE_WINDOW) {
                                Icons.Default.Schedule
                            } else {
                                Icons.Default.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(if (compactFeedback) 26.dp else 34.dp),
                            tint = Color(0xFFFFC867),
                        )
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (!nome.isNullOrBlank()) {
                    Text(
                        text = nome,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.055f),
                ) {
                    Text(
                        text = mensagemExibida,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.84f),
                    )
                }

                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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
    val accessibilityManager = LocalAccessibilityManager.current
    val start = comprovante.tipo == TipoComprovantePonto.INICIO
    val warning = !start && comprovante.excedeuLimite

    LaunchedEffect(comprovante) {
        runCatching {
            view.performHapticFeedback(
                if (warning) HapticFeedbackConstantsCompat.REJECT else HapticFeedbackConstantsCompat.CONFIRM,
            )
        }

        delay(
            accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = POINT_RECEIPT_VISIBLE_MILLIS,
                containsIcons = true,
                containsText = true,
                containsControls = false,
            ) ?: POINT_RECEIPT_VISIBLE_MILLIS,
        )
        viewModel.concluirComprovante()
    }

    val accent = if (warning) Color(0xFFFFC867) else Color(0xFF72DCBC)
    val title = if (start) "Pausa iniciada" else "Retorno registrado"
    val detail = if (start) {
        "${comprovante.horarioRegistrado} · retorne até ${comprovante.retornoAte ?: "--:--"}"
    } else {
        "${comprovante.horarioRegistrado} · duração ${viewModel.formatarTempo(comprovante.duracaoSegundos ?: 0)}"
    }

    PointFeedbackBackdrop(
        accent = accent,
        background = Color(0xFF04110E),
    ) { compactFeedback ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Assertive
                    stateDescription = "$title. ${comprovante.nome}. $detail"
                },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF5121C19),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = if (compactFeedback) 340.dp else 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (compactFeedback) 16.dp else 24.dp,
                        vertical = if (compactFeedback) 16.dp else 26.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(if (compactFeedback) 52.dp else 72.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (warning) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(if (compactFeedback) 27.dp else 36.dp),
                            tint = accent,
                        )
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = comprovante.nome,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.055f),
                ) {
                    Text(
                        text = detail,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }

                when {
                    comprovante.pendenteSincronizacao -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Salvo com segurança neste aparelho",
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.76f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    warning -> {
                        Text(
                            "Registro confirmado · limite excedido",
                            color = accent,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    comprovante.foraHorario -> {
                        Text(
                            "Registro validado pelo fluxo autorizado",
                            color = accent,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    else -> {
                        Text(
                            "Registro confirmado",
                            color = accent,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Text(
                    "Próxima pessoa em instantes",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PointFeedbackBackdrop(
    accent: Color,
    background: Color,
    content: @Composable (compactFeedback: Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compactFeedback = maxHeight < 560.dp || LocalDensity.current.fontScale >= 1.6f
        val glowSize = minOf(maxWidth, maxHeight).times(0.72f).coerceAtMost(320.dp)
        Box(
            modifier = Modifier
                .size(glowSize)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        content(compactFeedback)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
