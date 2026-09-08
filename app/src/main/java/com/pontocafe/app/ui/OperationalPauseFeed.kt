@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.AdminTestPause
import com.pontocafe.app.data.PausaSupervisor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private const val OPERATIONAL_ATTENTION_SECONDS = 120
private const val OPERATIONAL_WARNING_SECONDS = 60
private const val OPERATIONAL_CRITICAL_SECONDS = 15

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
    sector: String? = null,
): List<OperationalPauseItem> {
    val byStatus = when (filter) {
        OperationalPauseFilter.TODOS -> items
        OperationalPauseFilter.ATENCAO -> items.filter {
            val elapsed = operationalPauseElapsed(it.pause, nowMillis)
            val remaining = it.pause.limiteEfetivoSegundos - elapsed
            elapsed <= it.pause.limiteEfetivoSegundos && remaining <= OPERATIONAL_ATTENTION_SECONDS
        }
        OperationalPauseFilter.EXCEDIDOS -> items.filter {
            operationalPauseElapsed(it.pause, nowMillis) > it.pause.limiteEfetivoSegundos
        }
    }
    return if (sector == null) byStatus else byStatus.filter { it.pause.setor == sector }
}

@Composable
fun OperationalPauseOverview(
    realPauses: List<PausaSupervisor>,
    items: List<OperationalPauseItem>,
    filter: OperationalPauseFilter,
    onFilterChange: (OperationalPauseFilter) -> Unit,
    sectorFilter: String? = null,
    onSectorFilterChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val realOverdue = realPauses.count { operationalPauseElapsed(it, now) > it.limiteEfetivoSegundos }
    val realCritical = realPauses.count {
        val remaining = it.limiteEfetivoSegundos - operationalPauseElapsed(it, now)
        remaining in 0..OPERATIONAL_CRITICAL_SECONDS
    }
    val realAttention = realPauses.count {
        val elapsed = operationalPauseElapsed(it, now)
        val remaining = it.limiteEfetivoSegundos - elapsed
        elapsed <= it.limiteEfetivoSegundos && remaining <= OPERATIONAL_ATTENTION_SECONDS
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
                            realCritical > 0 -> "$realCritical em estado crítico"
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
                        realCritical > 0 -> "CRÍTICO"
                        realAttention > 0 -> "ATENÇÃO"
                        realPauses.isNotEmpty() -> "OK"
                        else -> "LIVRE"
                    },
                    tone = when {
                        realOverdue > 0 -> PontoCafeTone.DANGER
                        realCritical > 0 || realAttention > 0 -> PontoCafeTone.WARNING
                        realPauses.isNotEmpty() -> PontoCafeTone.SUCCESS
                        else -> PontoCafeTone.NEUTRAL
                    },
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                contentPadding = PaddingValues(end = PontoCafeSpacing.xs),
            ) {
                OperationalPauseFilter.entries.forEach { option ->
                    val count = filterOperationalPauseItems(items, option, now, sectorFilter).size
                    item(key = option.name) {
                        FilterChip(
                            selected = filter == option,
                            onClick = { onFilterChange(option) },
                            label = { Text("${option.label} $count") },
                        )
                    }
                }
            }

            val sectors = remember(items) {
                items.mapNotNull { it.pause.setor?.takeIf(String::isNotBlank) }.distinct().sorted()
            }
            if (sectors.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    contentPadding = PaddingValues(end = PontoCafeSpacing.xs),
                ) {
                    item(key = "sector-all") {
                        FilterChip(
                            selected = sectorFilter == null,
                            onClick = { onSectorFilterChange(null) },
                            label = { Text("Todos os setores") },
                        )
                    }
                    sectors.forEach { sector ->
                        item(key = "sector-$sector") {
                            FilterChip(
                                selected = sectorFilter == sector,
                                onClick = { onSectorFilterChange(sector) },
                                label = { Text(sector) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * O relógio da operação, um só para toda a lista.
 *
 * Cada cartão mantinha o seu: com vinte pessoas em pausa eram vinte corrotinas
 * e vinte recomposições por segundo, todas a calcular o mesmo instante. Aqui
 * uma corrotina acorda alinhada à viragem do segundo -- e não a cada 1000 ms,
 * que iria derivando -- e todos os cartões leem o mesmo valor.
 *
 * Quem não fornecer o relógio recebe um que não anda: é melhor um cartão
 * parado, e visivelmente parado, do que vinte relógios a competir.
 */
val LocalOperationalNow = staticCompositionLocalOf<State<Long>> {
    mutableLongStateOf(System.currentTimeMillis())
}

@Composable
fun OperationalClockProvider(content: @Composable () -> Unit) {
    // Preso ao ciclo de vida: um relógio a acordar de segundo a segundo com o
    // app em segundo plano é trabalho para desenhar uma tela que ninguém vê.
    val lifecycleOwner = LocalLifecycleOwner.current
    val agora = produceState(System.currentTimeMillis(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val instante = System.currentTimeMillis()
                value = instante
                delay(1_000L - instante % 1_000L)
            }
        }
    }
    CompositionLocalProvider(LocalOperationalNow provides agora) { content() }
}

@Composable
fun OperationalPauseCompactCard(
    item: OperationalPauseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseManually: (() -> Unit)? = null,
) {
    val pause = item.pause
    val relogio = LocalOperationalNow.current
    val now = relogio.value

    val elapsed = operationalPauseElapsed(pause, now)
    val remaining = (pause.limiteEfetivoSegundos - elapsed).coerceAtLeast(0)
    val overdue = elapsed > pause.limiteEfetivoSegundos
    val critical = !overdue && remaining <= OPERATIONAL_CRITICAL_SECONDS
    val warning = !overdue && !critical && remaining <= OPERATIONAL_WARNING_SECONDS
    val attention = !overdue && !critical && !warning && remaining <= OPERATIONAL_ATTENTION_SECONDS
    val progress = if (pause.limiteEfetivoSegundos <= 0) 1f else {
        (elapsed.toFloat() / pause.limiteEfetivoSegundos.toFloat()).coerceIn(0f, 1f)
    }
    val semantic = LocalPontoCafeSemanticColors.current
    val semanticColor = when {
        overdue -> semantic.critical
        critical || warning || attention -> semantic.warning
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .motionScale(active = critical, activeScale = 1.01f)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = when {
                    overdue -> "Pausa acima do limite"
                    critical -> "Pausa crítica, menos de quinze segundos para retornar"
                    warning -> "Pausa com menos de um minuto para retornar"
                    attention -> "Pausa próxima do limite"
                    else -> "Pausa dentro do limite"
                }
            },
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = when {
                overdue -> semantic.criticalContainer
                critical || warning -> semantic.warningContainer
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
                // O arco em volta do avatar diz quanto resta sem obrigar a ler
                // um número: cheio no início, some conforme o tempo passa, e
                // vira crítico antes de estourar. É desenhado com o valor lido
                // dentro do drawBehind -- o traço muda a cada segundo sem que o
                // cartão recomponha.
                Box(
                    modifier = Modifier.drawBehind {
                        val decorridoAgora = operationalPauseElapsed(pause, relogio.value)
                        val fracao = if (pause.limiteEfetivoSegundos <= 0) {
                            1f
                        } else {
                            (decorridoAgora.toFloat() / pause.limiteEfetivoSegundos).coerceIn(0f, 1f)
                        }
                        val traco = 3.dp.toPx()
                        val folga = traco * 1.6f
                        drawArc(
                            color = semanticColor.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(-folga, -folga),
                            size = Size(size.width + folga * 2, size.height + folga * 2),
                            style = Stroke(width = traco, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = semanticColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (1f - fracao),
                            useCenter = false,
                            topLeft = Offset(-folga, -folga),
                            size = Size(size.width + folga * 2, size.height + folga * 2),
                            style = Stroke(width = traco, cap = StrokeCap.Round),
                        )
                    },
                ) {
                    CollaboratorAvatar(name = pause.nome)
                }
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
                        text = "Saída ${pause.inicioLocal} · limite ${formatOperationalDuration(pause.limiteSegundos)}" +
                            if (pause.carenciaSegundos > 0) " + ${pause.carenciaSegundos}s de tolerância" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(58.dp),
                            color = semanticColor,
                            trackColor = semanticColor.copy(alpha = 0.14f),
                            strokeWidth = 4.dp,
                        )
                        Text(
                            text = formatOperationalDuration(elapsed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = semanticColor,
                        )
                    }
                    StatusPill(
                        modifier = Modifier.padding(top = 4.dp),
                        text = when {
                            overdue -> "Excedido +${formatOperationalDuration(elapsed - pause.limiteEfetivoSegundos)}"
                            critical -> "Crítico · ${formatOperationalDuration(remaining)}"
                            warning -> "Restam ${formatOperationalDuration(remaining)}"
                            attention -> "Atenção · ${formatOperationalDuration(remaining)}"
                            else -> "OK · ${formatOperationalDuration(remaining)}"
                        },
                        tone = when {
                            overdue -> PontoCafeTone.DANGER
                            critical || warning || attention -> PontoCafeTone.WARNING
                            else -> PontoCafeTone.SUCCESS
                        },
                    )
                    if (onCloseManually != null && !item.isTest) {
                        TextButton(
                            onClick = onCloseManually,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Registrar retorno",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ManualPauseCloseReasons = listOf(
    "Código não funcionou no quiosque",
    "Esqueceu de marcar o retorno",
    "Outro",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualPauseCloseDialog(
    item: OperationalPauseItem,
    loading: Boolean,
    onConfirm: (motivo: String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null,
) {
    val pause = item.pause
    var motivoRapido by remember(pause.id) { mutableStateOf<String?>(null) }
    var outroMotivo by remember(pause.id) { mutableStateOf("") }
    val motivoFinal = if (motivoRapido == "Outro") outroMotivo.trim() else motivoRapido.orEmpty()
    val podeConfirmar = motivoFinal.length >= 2

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Registrar retorno manualmente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Text(
                    text = "${pause.nome} · saída às ${pause.inicioLocal}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Use apenas quando a pessoa já retornou, mas o retorno não chegou a ser registrado no quiosque.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Uma LazyRow cortava "Esqueceu de marcar o retorno" na borda da
                // tela: a pessoa via "Esqueceu de r" e nao havia nada indicando
                // que era rolavel. Em um dialogo com tres opcoes fixas, quebrar
                // em linhas mostra todas de uma vez.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ManualPauseCloseReasons.forEach { reason ->
                        FilterChip(
                            selected = motivoRapido == reason,
                            onClick = {
                                motivoRapido = reason
                                if (reason != "Outro") outroMotivo = ""
                            },
                            label = { Text(reason) },
                        )
                    }
                }
                if (motivoRapido == "Outro") {
                    OutlinedTextField(
                        value = outroMotivo,
                        onValueChange = { outroMotivo = it.take(300) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descreva o motivo") },
                        minLines = 2,
                        maxLines = 4,
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(motivoFinal) },
                enabled = podeConfirmar && !loading,
            ) {
                Text(if (loading) "Registrando…" else "Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
fun OperationalPauseDetailDialog(
    item: OperationalPauseItem,
    onDismiss: () -> Unit,
) {
    val pause = item.pause
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos
        ?: operationalPauseElapsed(pause, System.currentTimeMillis())
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteEfetivoSegundos)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PcBottomSheetContent {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                CollaboratorAvatar(
                    name = pause.nome,
                    avatarSize = 56.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
                ) {
                    Text(
                        text = pause.nome,
                        modifier = Modifier.semantics { heading() },
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
        clienteAtualizadoEmMillis = startedAtMillis,
    )
}

private fun operationalPausePriority(pause: PausaSupervisor, nowMillis: Long): Int {
    val elapsed = operationalPauseElapsed(pause, nowMillis)
    val remaining = pause.limiteEfetivoSegundos - elapsed
    return when {
        elapsed > pause.limiteEfetivoSegundos -> 0
        remaining <= OPERATIONAL_CRITICAL_SECONDS -> 1
        remaining <= OPERATIONAL_WARNING_SECONDS -> 2
        remaining <= OPERATIONAL_ATTENTION_SECONDS -> 3
        else -> 4
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
    // Os minutos nunca transbordavam para horas, entao uma pausa aberta desde
    // ontem aparecia como "1876:48" — que parecia 1876 horas e na verdade eram
    // 31 h. Em um cartao de atencao, um numero que ninguem consegue ler e pior
    // do que nenhum numero.
    val minutes = safe / 60
    val seconds = safe % 60
    return when {
        minutes >= 1440 -> {
            val days = minutes / 1440
            val hours = (minutes % 1440) / 60
            if (hours == 0) "${days}d" else "${days}d ${hours}h"
        }
        minutes >= 60 -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0) "${hours}h" else "${hours}h ${rest}min"
        }
        else -> "%02d:%02d".format(minutes, seconds)
    }
}
