package com.pontocafe.app.ui

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.PausaSupervisor
import kotlinx.coroutines.delay

private enum class SupervisorLiveAlertType { SAIDA, RETORNO, EXCESSO, MISTO }

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
    agoraEmMillis: Long,
): SupervisorLiveAlert? {
    val context = LocalContext.current
    var baseline by remember { mutableStateOf<Map<String, PausaSupervisor>?>(null) }
    var overdueBaseline by remember { mutableStateOf<Set<String>>(emptySet()) }
    var alert by remember { mutableStateOf<SupervisorLiveAlert?>(null) }

    LaunchedEffect(pausasAtivas, enabled, agoraEmMillis) {
        if (!enabled) {
            baseline = null
            overdueBaseline = emptySet()
            alert = null
            return@LaunchedEffect
        }

        val atual = pausasAtivas.associateBy { it.id }
        val excessosAtuais = atual.values
            .filter { tempoAtualSupervisor(it, agoraEmMillis) > it.limiteSegundos }
            .mapTo(mutableSetOf()) { it.id }
        val anterior = baseline

        if (anterior == null) {
            // O primeiro carregamento apenas estabelece a referência para não tocar
            // alertas antigos assim que o Supervisor abre o painel.
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

        alert = novoAlerta
        emitSupervisorLiveAlert(context, novoAlerta.type)
    }

    LaunchedEffect(alert?.id) {
        val currentId = alert?.id ?: return@LaunchedEffect
        delay(8_000)
        if (alert?.id == currentId) alert = null
    }

    return alert
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
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

private fun emitSupervisorLiveAlert(context: Context, type: String) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.play()
    }

    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = when (type) {
            SupervisorLiveAlertType.RETORNO.name -> longArrayOf(0, 90, 70, 90)
            SupervisorLiveAlertType.EXCESSO.name -> longArrayOf(0, 260, 100, 260, 100, 420)
            SupervisorLiveAlertType.MISTO.name -> longArrayOf(0, 140, 70, 140, 70, 140)
            else -> longArrayOf(0, 180, 90, 260)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
