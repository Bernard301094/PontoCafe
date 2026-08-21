package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.notifications.SupervisorAlertNotifier
import java.time.LocalDate
import kotlinx.coroutines.delay

private enum class SupervisorLiveAlertType { SAIDA, RETORNO, EXCESSO, MISTO }

private const val TRANSIENT_ALERT_DURATION_MILLIS = 8_000L
private const val CURRENT_DAY_REFRESH_MILLIS = 10_000L

data class SupervisorLiveAlert(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
)

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
    var baseline by remember { mutableStateOf<Map<String, PausaSupervisor>?>(null) }
    var overdueBaseline by remember { mutableStateOf<Set<String>>(emptySet()) }
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
            transientAlert = null
            return@LaunchedEffect
        }

        val atual = pausasAtivas.associateBy { it.id }
        val excessosAtuais = atual.values
            .filter { tempoAtualSupervisor(it, agoraEmMillis) > it.limiteSegundos }
            .mapTo(mutableSetOf()) { it.id }
        val anterior = baseline

        if (anterior == null) {
            baseline = atual
            overdueBaseline = excessosAtuais
            return@LaunchedEffect
        }

        val novas = atual.filterKeys { it !in anterior }.values.toList()
        val retornos = anterior.filterKeys { it !in atual }.values.toList()
        val novosExcessos = excessosAtuais
            .filter { it !in overdueBaseline }
            .mapNotNull(atual::get)

        baseline = atual
        overdueBaseline = excessosAtuais

        if (novas.isEmpty() && retornos.isEmpty() && novosExcessos.isEmpty()) return@LaunchedEffect

        val novoAlerta = when {
            novosExcessos.isNotEmpty() -> {
                val nomes = nomesParaAlerta(novosExcessos)
                SupervisorLiveAlert(
                    id = System.nanoTime(),
                    type = SupervisorLiveAlertType.EXCESSO.name,
                    title = if (novosExcessos.size == 1) "Limite de pausa atingido" else "${novosExcessos.size} pausas acima do limite",
                    message = if (novosExcessos.size == 1) {
                        "$nomes atingiu o limite e ainda não registrou o retorno."
                    } else {
                        "$nomes atingiram o limite e ainda não registraram o retorno."
                    },
                )
            }
            novas.isNotEmpty() && retornos.isEmpty() -> {
                val nomes = nomesParaAlerta(novas)
                SupervisorLiveAlert(
                    id = System.nanoTime(),
                    type = SupervisorLiveAlertType.SAIDA.name,
                    title = if (novas.size == 1) "Saída para o café" else "${novas.size} saídas para o café",
                    message = if (novas.size == 1) "$nomes bateu o ponto e saiu para o café." else "$nomes bateram o ponto e saíram para o café.",
                )
            }
            retornos.isNotEmpty() && novas.isEmpty() -> {
                val nomes = nomesParaAlerta(retornos)
                SupervisorLiveAlert(
                    id = System.nanoTime(),
                    type = SupervisorLiveAlertType.RETORNO.name,
                    title = if (retornos.size == 1) "Retorno do café" else "${retornos.size} retornos do café",
                    message = if (retornos.size == 1) "$nomes bateu o ponto de retorno." else "$nomes bateram o ponto de retorno.",
                )
            }
            else -> {
                SupervisorLiveAlert(
                    id = System.nanoTime(),
                    type = SupervisorLiveAlertType.MISTO.name,
                    title = "Movimentação no Ponto Café",
                    message = "${novas.size} saída(s) e ${retornos.size} retorno(s) detectado(s).",
                )
            }
        }

        transientAlert = novoAlerta
        SupervisorAlertNotifier.notify(
            context = context,
            eventType = novoAlerta.type,
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
    val containerColor = when (alert.type) {
        SupervisorLiveAlertType.EXCESSO.name -> MaterialTheme.colorScheme.errorContainer
        SupervisorLiveAlertType.RETORNO.name -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (alert.type) {
        SupervisorLiveAlertType.EXCESSO.name -> MaterialTheme.colorScheme.onErrorContainer
        SupervisorLiveAlertType.RETORNO.name -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

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
        Column(modifier = Modifier.padding(14.dp)) {
            Text(alert.title, fontWeight = FontWeight.Bold)
            Text(
                alert.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 3.dp),
            )
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

private fun tempoAtualSupervisor(pausa: PausaSupervisor, agoraEmMillis: Long): Int {
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
