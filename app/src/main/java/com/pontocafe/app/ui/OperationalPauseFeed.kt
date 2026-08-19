@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.AdminTestPause
import com.pontocafe.app.data.PausaSupervisor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private const val OPERATIONAL_ATTENTION_SECONDS = 120

enum class OperationalPauseFilter(val label: String) {
    TODOS("Todos"),
    ATENCAO("Atenção"),
    EXCEDIDOS("Excedidos"),
}

data class OperationalPauseItem(
    val pause: PausaSupervisor,
    val isTest: Boolean = false,
)

fun buildOperationalPauseItems(
    realPauses: List<PausaSupervisor>,
    testPause: AdminTestPause?,
    nowMillis: Long = System.currentTimeMillis(),
): List<OperationalPauseItem> {
    val items = buildList {
        addAll(realPauses.map { OperationalPauseItem(it, isTest = false) })
        testPause?.let { add(OperationalPauseItem(it.toOperationalPause(), isTest = true)) }
    }
    return items.sortedWith(
        compareBy<OperationalPauseItem> { operationalPausePriority(it.pause, nowMillis) }
            .thenByDescending { operationalPauseElapsed(it.pause, nowMillis) },
    )
}

fun filterOperationalPauseItems(
    items: List<OperationalPauseItem>,
    filter: OperationalPauseFilter,
    nowMillis: Long = System.currentTimeMillis(),
): List<OperationalPauseItem> = when (filter) {
    OperationalPauseFilter.TODOS -> items
    OperationalPauseFilter.ATENCAO -> items.filter {
        val elapsed = operationalPauseElapsed(it.pause, nowMillis)
        val remaining = it.pause.limiteSegundos - elapsed
        elapsed <= it.pause.limiteSegundos && remaining <= OPERATIONAL_ATTENTION_SECONDS
    }
    OperationalPauseFilter.EXCEDIDOS -> items.filter {
        operationalPauseElapsed(it.pause, nowMillis) > it.pause.limiteSegundos
    }
}

@Composable
fun OperationalPauseOverview(
    realPauses: List<PausaSupervisor>,
    items: List<OperationalPauseItem>,
    filter: OperationalPauseFilter,
    onFilterChange: (OperationalPauseFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val realOverdue = realPauses.count { operationalPauseElapsed(it, now) > it.limiteSegundos }
    val realAttention = realPauses.count {
        val elapsed = operationalPauseElapsed(it, now)
        val remaining = it.limiteSegundos - elapsed
        elapsed <= it.limiteSegundos && remaining <= OPERATIONAL_ATTENTION_SECONDS
    }
    val testActive = items.any { it.isTest }

    PcSectionSurface(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = when {
                            realOverdue > 0 -> "$realOverdue exige(m) atenção agora"
                            realAttention > 0 -> "$realAttention próximo(s) do limite"
                            realPauses.isNotEmpty() -> "Operação sob controle"
                            testActive -> "TESTE visual ativo"
                            else -> "Nenhuma pausa aberta"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${realPauses.size} em pausa · $realAttention atenção · $realOverdue excedida(s)" +
                            if (testActive) " · TESTE" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = when {
                        realOverdue > 0 -> "URGENTE"
                        realAttention > 0 -> "ATENÇÃO"
                        realPauses.isNotEmpty() -> "OK"
                        else -> "LIVRE"
                    },
                    tone = when {
                        realOverdue > 0 -> PontoCafeTone.DANGER
                        realAttention > 0 -> PontoCafeTone.WARNING
                        realPauses.isNotEmpty() -> PontoCafeTone.SUCCESS
                        else -> PontoCafeTone.NEUTRAL
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                OperationalPauseFilter.entries.forEach { option ->
                    val count = filterOperationalPauseItems(items, option, now).size
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilterChange(option) },
                        label = { Text("${option.label} $count") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun OperationalPauseCompactCard(
    item: OperationalPauseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pause = item.pause
    var now by remember(pause.id, pause.clienteAtualizadoEmMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(pause.id, pause.clienteAtualizadoEmMillis) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val elapsed = operationalPauseElapsed(pause, now)
    val remaining = (pause.limiteSegundos - elapsed).coerceAtLeast(0)
    val overdue = elapsed > pause.limiteSegundos
    val attention = !overdue && remaining <= OPERATIONAL_ATTENTION_SECONDS
    val progress = if (pause.limiteSegundos <= 0) 1f else {
        (elapsed.toFloat() / pause.limiteSegundos.toFloat()).coerceIn(0f, 1f)
    }
    val semanticColor = when {
        overdue -> MaterialTheme.colorScheme.error
        attention -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = when {
                overdue -> MaterialTheme.colorScheme.errorContainer
                attention -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                CollaboratorAvatar(
                    name = pause.nome,
                    avatarUrl = pause.avatarUrl,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        Text(
                            text = pause.nome,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.isTest) StatusPill(text = "TESTE", tone = PontoCafeTone.INFO)
                    }
                    Text(
                        text = if (item.isTest) {
                            "TESTE · Não salvo no sistema"
                        } else {
                            listOfNotNull(pause.setor, pause.periodo.takeIf { it.isNotBlank() })
                                .joinToString(" · ")
                                .ifBlank { "Pausa em andamento" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Saída ${pause.inicioLocal} · limite ${formatOperationalDuration(pause.limiteSegundos)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatOperationalDuration(elapsed),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = semanticColor,
                    )
                    StatusPill(
                        text = when {
                            overdue -> "Excedido +${formatOperationalDuration(elapsed - pause.limiteSegundos)}"
                            attention -> "Restam ${formatOperationalDuration(remaining)}"
                            else -> "OK · ${formatOperationalDuration(remaining)}"
                        },
                        tone = when {
                            overdue -> PontoCafeTone.DANGER
                            attention -> PontoCafeTone.WARNING
                            else -> PontoCafeTone.SUCCESS
                        },
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = semanticColor,
                trackColor = semanticColor.copy(alpha = 0.14f),
            )
        }
    }
}

@Composable
fun OperationalPauseDetailDialog(
    item: OperationalPauseItem,
    onDismiss: () -> Unit,
) {
    val pause = item.pause
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos
        ?: operationalPauseElapsed(pause, System.currentTimeMillis())
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteSegundos)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = PontoCafeSpacing.lg,
                    end = PontoCafeSpacing.lg,
                    bottom = PontoCafeSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                CollaboratorAvatar(
                    name = pause.nome,
                    avatarUrl = pause.avatarUrl,
                    avatarSize = 56.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
                ) {
                    Text(
                        text = pause.nome,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = listOfNotNull(pause.setor, pause.periodo.takeIf { it.isNotBlank() })
                            .joinToString(" · ")
                            .ifBlank { "Pausa do café" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (item.isTest) {
                PcStateBanner(
                    title = "TESTE · Não salvo no sistema",
                    supportingText = "A simulação usa a mesma visualização de uma pausa real, mas não altera banco, histórico, auditoria ou métricas.",
                    tone = PontoCafeTone.INFO,
                )
            }

            PcKeyValueCard(
                title = "Detalhes da pausa",
                rows = listOf(
                    "Data" to (pause.data ?: "Hoje"),
                    "Período" to pause.periodo,
                    "Setor" to (pause.setor ?: "—"),
                    "Saída" to pause.inicioLocal,
                    "Retorno" to (pause.fimLocal ?: "Ainda em pausa"),
                    "Duração" to formatOperationalDuration(duration),
                    "Limite" to formatOperationalDuration(pause.limiteSegundos),
                    "Situação" to if (exceeded) "Acima do limite" else "Dentro do limite",
                    "Fora do horário" to if (pause.foraHorario) "Sim" else "Não",
                ),
            )

            PcPrimaryButton(
                text = "Fechar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun AdminTestPause.toOperationalPause(): PausaSupervisor {
    val local = Instant.ofEpochMilli(startedAtMillis).atZone(ZoneId.systemDefault())
    return PausaSupervisor(
        id = id,
        periodo = if (local.hour < 12) "MANHA" else "TARDE",
        data = local.toLocalDate().toString(),
        inicioLocal = local.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
        fimLocal = null,
        limiteSegundos = limitSeconds,
        foraHorario = false,
        tempoSegundos = 0,
        duracaoSegundos = null,
        excedeuLimite = null,
        colaboradorId = id,
        nome = adminName,
        setor = "Simulação",
        avatarUrl = null,
        clienteAtualizadoEmMillis = startedAtMillis,
    )
}

private fun operationalPausePriority(pause: PausaSupervisor, nowMillis: Long): Int {
    val elapsed = operationalPauseElapsed(pause, nowMillis)
    val remaining = pause.limiteSegundos - elapsed
    return when {
        elapsed > pause.limiteSegundos -> 0
        remaining <= OPERATIONAL_ATTENTION_SECONDS -> 1
        else -> 2
    }
}

private fun operationalPauseElapsed(pause: PausaSupervisor, nowMillis: Long): Int {
    val base = pause.tempoSegundos ?: pause.duracaoSegundos ?: 0
    if (pause.fimLocal != null || pause.clienteAtualizadoEmMillis <= 0L) return base
    val extra = ((nowMillis - pause.clienteAtualizadoEmMillis) / 1_000L)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    return (base.toLong() + extra).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun formatOperationalDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}
