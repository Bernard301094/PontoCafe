package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.OperationalAlertHistoryStore
import com.pontocafe.app.data.PausaSupervisor
import java.time.LocalDate
import kotlinx.coroutines.delay

internal enum class SupervisorLiveAlertType {
    SAIDA,
    RETORNO,
    PROXIMO_LIMITE,
    CRITICO,
    EXCESSO,
    MISTO,
}

private const val TRANSIENT_ALERT_DURATION_MILLIS = 8_000L
private const val CURRENT_DAY_REFRESH_MILLIS = 10_000L
internal const val SUPERVISOR_LIVE_ALERT_WARNING_THRESHOLD_SECONDS = 60
internal const val SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS = 15

data class SupervisorLiveAlert(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
)

/**
 * Pure alert-priority selection, shared by the on-screen banner
 * ([rememberSupervisorLiveActivityAlert]) and [com.pontocafe.app.SupervisorViewModel]'s
 * tab-independent background monitor, so both agree on which single alert
 * wins when several conditions change in the same polling cycle. Each caller
 * owns its own baseline/diffing state independently; only the priority
 * ordering and message wording live here.
 */
internal fun selectSupervisorLiveAlert(
    novas: List<PausaSupervisor>,
    retornos: List<PausaSupervisor>,
    novosExcessos: List<PausaSupervisor>,
    novosCriticos: List<PausaSupervisor>,
    novosAvisos: List<PausaSupervisor>,
    alertId: Long,
): SupervisorLiveAlert? {
    if (
        novas.isEmpty() && retornos.isEmpty() && novosExcessos.isEmpty() &&
        novosCriticos.isEmpty() && novosAvisos.isEmpty()
    ) return null

    return when {
        novosExcessos.isNotEmpty() -> {
            val nomes = nomesParaAlerta(novosExcessos)
            SupervisorLiveAlert(
                id = alertId,
                type = SupervisorLiveAlertType.EXCESSO.name,
                title = if (novosExcessos.size == 1) "Limite de pausa excedido" else "${novosExcessos.size} pausas acima do limite",
                message = if (novosExcessos.size == 1) {
                    "$nomes ultrapassou o limite e ainda não registrou o retorno."
                } else {
                    "$nomes ultrapassaram o limite e ainda não registraram o retorno."
                },
            )
        }
        novosCriticos.isNotEmpty() -> {
            val nomes = nomesParaAlerta(novosCriticos)
            SupervisorLiveAlert(
                id = alertId,
                type = SupervisorLiveAlertType.CRITICO.name,
                title = if (novosCriticos.size == 1) "Retorno necessário em até 15 s" else "${novosCriticos.size} pausas em estado crítico",
                message = if (novosCriticos.size == 1) {
                    "$nomes está a poucos segundos do limite da pausa."
                } else {
                    "$nomes estão a poucos segundos do limite da pausa."
                },
            )
        }
        novosAvisos.isNotEmpty() -> {
            val nomes = nomesParaAlerta(novosAvisos)
            SupervisorLiveAlert(
                id = alertId,
                type = SupervisorLiveAlertType.PROXIMO_LIMITE.name,
                title = if (novosAvisos.size == 1) "Pausa próxima do limite" else "${novosAvisos.size} pausas próximas do limite",
                message = if (novosAvisos.size == 1) {
                    "$nomes tem menos de 1 minuto para registrar o retorno."
                } else {
                    "$nomes têm menos de 1 minuto para registrar o retorno."
                },
            )
        }
        novas.isNotEmpty() && retornos.isEmpty() -> {
            val nomes = nomesParaAlerta(novas)
            SupervisorLiveAlert(
                id = alertId,
                type = SupervisorLiveAlertType.SAIDA.name,
                title = if (novas.size == 1) "Saída para o café" else "${novas.size} saídas para o café",
                message = if (novas.size == 1) "$nomes bateu o ponto e saiu para o café." else "$nomes bateram o ponto e saíram para o café.",
            )
        }
        retornos.isNotEmpty() && novas.isEmpty() -> {
            val nomes = nomesParaAlerta(retornos)
            SupervisorLiveAlert(
                id = alertId,
                type = SupervisorLiveAlertType.RETORNO.name,
                title = if (retornos.size == 1) "Retorno do café" else "${retornos.size} retornos do café",
                message = if (retornos.size == 1) "$nomes bateu o ponto de retorno." else "$nomes bateram o ponto de retorno.",
            )
        }
        else -> SupervisorLiveAlert(
            id = alertId,
            type = SupervisorLiveAlertType.MISTO.name,
            title = "Movimentação no Ponto Café",
            message = "${novas.size} saída(s) e ${retornos.size} retorno(s) detectado(s).",
        )
    }
}

@Composable
fun rememberSupervisorLiveActivityAlert(
    pausasAtivas: List<PausaSupervisor>,
    enabled: Boolean,
    latestReturn: PausaSupervisor? = null,
): SupervisorLiveAlert? = rememberSupervisorLiveActivityAlert(
    pausasAtivas = pausasAtivas,
    enabled = enabled,
    agoraEmMillis = System.currentTimeMillis(),
    latestReturn = latestReturn,
)

@Composable
fun rememberSupervisorLiveActivityAlert(
    pausasAtivas: List<PausaSupervisor>,
    enabled: Boolean,
    agoraEmMillis: Long,
    latestReturn: PausaSupervisor? = null,
): SupervisorLiveAlert? {
    val context = LocalContext.current
    val accessibilityManager = LocalAccessibilityManager.current
    val historyStore = remember(context) { OperationalAlertHistoryStore(context.applicationContext) }
    var baseline by remember { mutableStateOf<Map<String, PausaSupervisor>?>(null) }
    var overdueBaseline by remember { mutableStateOf<Set<String>>(emptySet()) }
    var warningBaseline by remember { mutableStateOf<Set<String>>(emptySet()) }
    var criticalBaseline by remember { mutableStateOf<Set<String>>(emptySet()) }
    var transientAlert by remember { mutableStateOf<SupervisorLiveAlert?>(null) }
    var currentDay by remember { mutableStateOf(LocalDate.now().toString()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(CURRENT_DAY_REFRESH_MILLIS)
            currentDay = LocalDate.now().toString()
        }
    }

    val latestReturnAlert = remember(latestReturn?.id, latestReturn?.fimLocal, latestReturn?.data, currentDay) {
        latestReturn
            ?.takeIf { retorno -> retorno.data == currentDay }
            ?.toPersistentReturnAlert()
    }

    LaunchedEffect(pausasAtivas, enabled, agoraEmMillis) {
        if (!enabled) {
            baseline = null
            overdueBaseline = emptySet()
            warningBaseline = emptySet()
            criticalBaseline = emptySet()
            transientAlert = null
            return@LaunchedEffect
        }

        val atual = pausasAtivas.associateBy { it.id }
        val excessosAtuais = atual.values
            .filter { tempoAtualSupervisor(it, agoraEmMillis) > it.limiteSegundos }
            .mapTo(mutableSetOf()) { it.id }
        val criticosAtuais = atual.values
            .filter {
                val remaining = it.limiteSegundos - tempoAtualSupervisor(it, agoraEmMillis)
                remaining in 0..SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS
            }
            .mapTo(mutableSetOf()) { it.id }
        val avisosAtuais = atual.values
            .filter {
                val remaining = it.limiteSegundos - tempoAtualSupervisor(it, agoraEmMillis)
                remaining in (SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS + 1)..SUPERVISOR_LIVE_ALERT_WARNING_THRESHOLD_SECONDS
            }
            .mapTo(mutableSetOf()) { it.id }
        val anterior = baseline

        if (anterior == null) {
            baseline = atual
            overdueBaseline = excessosAtuais
            warningBaseline = avisosAtuais
            criticalBaseline = criticosAtuais
            return@LaunchedEffect
        }

        val novas = atual.filterKeys { it !in anterior }.values.toList()
        val retornos = anterior.filterKeys { it !in atual }.values.toList()
        val novosExcessos = excessosAtuais
            .filter { it !in overdueBaseline }
            .mapNotNull(atual::get)
        val novosCriticos = criticosAtuais
            .filter { it !in criticalBaseline }
            .mapNotNull(atual::get)
        val novosAvisos = avisosAtuais
            .filter { it !in warningBaseline }
            .mapNotNull(atual::get)

        baseline = atual
        overdueBaseline = excessosAtuais
        warningBaseline = avisosAtuais
        criticalBaseline = criticosAtuais

        val novoAlerta = selectSupervisorLiveAlert(
            novas = novas,
            retornos = retornos,
            novosExcessos = novosExcessos,
            novosCriticos = novosCriticos,
            novosAvisos = novosAvisos,
            alertId = System.nanoTime(),
        ) ?: return@LaunchedEffect

        transientAlert = novoAlerta
        // Recorded here for the on-screen history panel only. System
        // notification delivery is owned exclusively by
        // SupervisorViewModel.startLiveAlertMonitoring(), which keeps running
        // across Supervisor tabs instead of only while this composable (the
        // "Ao Vivo" tab specifically) is on screen — see that function for
        // the single source of truth on "was this event notified".
        historyStore.record(
            id = novoAlerta.id,
            type = novoAlerta.type,
            title = novoAlerta.title,
            message = novoAlerta.message,
        )
    }

    LaunchedEffect(transientAlert?.id) {
        val currentId = transientAlert?.id ?: return@LaunchedEffect
        delay(
            accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = TRANSIENT_ALERT_DURATION_MILLIS,
                containsIcons = false,
                containsText = true,
                containsControls = false,
            ) ?: TRANSIENT_ALERT_DURATION_MILLIS,
        )
        if (transientAlert?.id == currentId) transientAlert = null
    }

    // Eventos novos têm prioridade por alguns segundos. Depois disso, somente
    // o último retorno DO DIA ATUAL pode permanecer no painel de Operação.
    return transientAlert ?: latestReturnAlert
}

@Composable
fun SupervisorLiveActivityAlertBanner(alert: SupervisorLiveAlert) {
    var visible by remember(alert.id) { mutableStateOf(false) }
    LaunchedEffect(alert.id) { visible = true }

    val containerColor = when (alert.type) {
        SupervisorLiveAlertType.EXCESSO.name -> LocalPontoCafeSemanticColors.current.criticalContainer
        SupervisorLiveAlertType.CRITICO.name,
        SupervisorLiveAlertType.PROXIMO_LIMITE.name -> LocalPontoCafeSemanticColors.current.warningContainer
        SupervisorLiveAlertType.RETORNO.name -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (alert.type) {
        SupervisorLiveAlertType.EXCESSO.name -> LocalPontoCafeSemanticColors.current.onCriticalContainer
        SupervisorLiveAlertType.CRITICO.name,
        SupervisorLiveAlertType.PROXIMO_LIMITE.name -> LocalPontoCafeSemanticColors.current.onWarningContainer
        SupervisorLiveAlertType.RETORNO.name -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = when (alert.type) {
        SupervisorLiveAlertType.EXCESSO.name,
        SupervisorLiveAlertType.CRITICO.name,
        SupervisorLiveAlertType.PROXIMO_LIMITE.name -> Icons.Default.Warning
        SupervisorLiveAlertType.RETORNO.name -> Icons.Default.CheckCircle
        else -> Icons.Default.Coffee
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(PontoCafeMotion.Standard)) + slideInVertically(
            animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
            initialOffsetY = { -it / 3 },
        ),
        exit = fadeOut(tween(PontoCafeMotion.Quick)) + slideOutVertically(
            animationSpec = tween(PontoCafeMotion.Quick),
            targetOffsetY = { -it / 4 },
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    stateDescription = "${alert.title}. ${alert.message}"
                },
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Column {
                    Text(alert.title, fontWeight = FontWeight.Bold)
                    Text(
                        alert.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

private fun PausaSupervisor.toPersistentReturnAlert(): SupervisorLiveAlert {
    val retorno = fimLocal.orEmpty()
    val duracao = duracaoSegundos ?: tempoSegundos
    val duracaoLabel = duracao?.let { " · pausa ${formatAlertDuration(it)}" }.orEmpty()
    val stableId = "$id:$retorno".hashCode().toLong()
    return SupervisorLiveAlert(
        id = stableId,
        type = SupervisorLiveAlertType.RETORNO.name,
        title = "Último retorno registrado",
        message = "$nome retornou às $retorno · saída $inicioLocal$duracaoLabel",
    )
}

private fun formatAlertDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}min ${seconds}s"
        minutes > 0 -> "${minutes}min ${seconds}s"
        else -> "${seconds}s"
    }
}

internal fun tempoAtualSupervisor(pausa: PausaSupervisor, agoraEmMillis: Long): Int {
    val base = pausa.tempoSegundos ?: pausa.duracaoSegundos ?: 0
    if (pausa.fimLocal != null || pausa.clienteAtualizadoEmMillis <= 0L) return base
    val adicional = ((agoraEmMillis - pausa.clienteAtualizadoEmMillis) / 1000L)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    return (base.toLong() + adicional)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun nomesParaAlerta(pausas: List<PausaSupervisor>): String = when (pausas.size) {
    0 -> ""
    1 -> pausas.first().nome
    2 -> "${pausas[0].nome} e ${pausas[1].nome}"
    else -> "${pausas.take(2).joinToString(", ") { it.nome }} e mais ${pausas.size - 2}"
}
