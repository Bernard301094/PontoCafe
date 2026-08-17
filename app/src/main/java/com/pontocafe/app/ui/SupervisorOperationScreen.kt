package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.AdminTestPause
import com.pontocafe.app.data.AdminTestPauseStore
import com.pontocafe.app.data.PausaSupervisor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SupervisorOperationScreen(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val state = viewModel.state
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val testPause by AdminTestPauseStore.active.collectAsState()
    val testAsPause = remember(testPause) { testPause?.toSupervisorPause() }
    var selectedPause by remember { mutableStateOf<PausaSupervisor?>(null) }
    var selectedPauseIsTest by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.atualizarPausasAoVivoSilencioso()
            viewModel.atualizarUltimoRetornoSilencioso()
            launch {
                while (true) {
                    delay(5_000)
                    viewModel.atualizarPausasAoVivoSilencioso()
                }
            }
            launch {
                while (true) {
                    delay(10_000)
                    viewModel.atualizarUltimoRetornoSilencioso()
                }
            }
        }
    }

    selectedPause?.let { pause ->
        SupervisorPauseDetailDialog(
            pause = pause,
            isTest = selectedPauseIsTest,
            onDismiss = {
                selectedPause = null
                selectedPauseIsTest = false
            },
        )
    }

    val alert = rememberSupervisorLiveActivityAlert(
        pausasAtivas = state.pausasAtivas,
        enabled = state.ultimaAtualizacaoAoVivoEmMillis != null,
        latestReturn = state.ultimoRetorno,
    )
    val pendingFaces = remember(state.colaboradores) {
        state.colaboradores.filter { !it.rostoCadastrado }.sortedBy { it.nome.lowercase() }
    }
    val nowSnapshot = System.currentTimeMillis()
    val orderedPausas = remember(state.pausasAtivas, state.ultimaAtualizacaoAoVivoEmMillis) {
        state.pausasAtivas.sortedWith(
            compareByDescending<PausaSupervisor> { supervisorOperationSeconds(it, nowSnapshot) > it.limiteSegundos }
                .thenByDescending { supervisorOperationSeconds(it, nowSnapshot) },
        )
    }
    val overdue = orderedPausas.count { supervisorOperationSeconds(it, nowSnapshot) > it.limiteSegundos }
    val hasAnyVisualPause = orderedPausas.isNotEmpty() || testAsPause != null

    PontoCafeResponsivePage(maxContentWidth = 960.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.lg,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item("header") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        PontoCafeScreenHeader(
                            title = "Visão geral",
                            eyebrow = "Supervisor",
                        )
                        PcSecondaryButton(
                            text = "Voltar ao Ponto",
                            onClick = onClose,
                            modifier = if (responsive.isCompact) Modifier.fillMaxWidth() else Modifier,
                        )
                    }
                }

                item("hero") {
                    PcHeroCard(
                        title = when {
                            overdue > 0 -> "$overdue pausa(s) exigem atenção"
                            state.pausasAtivas.isEmpty() -> "Operação sem pausas abertas"
                            else -> "${state.pausasAtivas.size} pessoa(s) em pausa agora"
                        },
                        supportingText = when {
                            overdue > 0 -> "Os casos acima do limite aparecem primeiro e podem ser abertos para ver todos os dados."
                            state.pausasAtivas.isEmpty() -> "Quando uma pessoa sair para o café, o registro aparecerá automaticamente aqui."
                            else -> "Acompanhe saída, tempo decorrido e limite sem precisar atualizar manualmente."
                        },
                        icon = if (overdue > 0) Icons.Default.Timer else Icons.Default.Coffee,
                        tone = if (overdue > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                    )
                }

                item("connection") {
                    SupervisorConnectionBanner(
                        connectionOk = state.conexaoAoVivoOk,
                        lastUpdateMillis = state.ultimaAtualizacaoAoVivoEmMillis,
                    )
                }

                alert?.let { currentAlert ->
                    item("activity-${currentAlert.id}") {
                        SupervisorLiveActivityAlertBanner(currentAlert)
                    }
                }

                item("metrics") {
                    if (responsive.isCompact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = state.pausasAtivas.size.toString(),
                                label = "Em pausa",
                                icon = Icons.Default.Coffee,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = overdue.toString(),
                                label = "Acima do limite",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f),
                                attention = overdue > 0,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = state.pausasAtivas.size.toString(),
                                label = "Em pausa agora",
                                icon = Icons.Default.Coffee,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = overdue.toString(),
                                label = "Acima do limite",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f),
                                attention = overdue > 0,
                            )
                            PcMetricTile(
                                value = pendingFaces.size.toString(),
                                label = "Rostos pendentes",
                                icon = Icons.Default.Face,
                                modifier = Modifier.weight(1f),
                                attention = pendingFaces.isNotEmpty(),
                            )
                        }
                    }
                }

                item("actions-title") {
                    SectionTitle(
                        "Ações rápidas",
                        "Acesse as tarefas do Supervisor sem misturar acompanhamento com configuração.",
                    )
                }

                item("actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        PcActionTile(
                            title = "Autorizar exceção",
                            supportingText = "Liberar uma pausa fora da janela normal",
                            icon = Icons.Default.Coffee,
                            onClick = viewModel::abrirAutorizacao,
                        )
                        PcActionTile(
                            title = "Histórico por data",
                            supportingText = "Escolher um dia no calendário e abrir cada registro",
                            icon = Icons.Default.CalendarMonth,
                            onClick = { viewModel.abrirHistorico() },
                        )
                        PcActionTile(
                            title = "Pessoas e biometria",
                            supportingText = "Cadastrar, atualizar ou excluir colaboradores e rostos",
                            icon = Icons.Default.PersonSearch,
                            onClick = viewModel::abrirColaboradores,
                        )
                    }
                }

                if (state.erro != null && !state.conexaoAoVivoOk) {
                    item("error") {
                        OperationalAlertCard(
                            title = "Exibindo os últimos dados disponíveis",
                            text = state.erro ?: "A conexão será atualizada automaticamente.",
                            actionLabel = "Tentar agora",
                            onClick = viewModel::atualizarAoVivo,
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }

                item("active-title") {
                    SectionTitle(
                        "Pessoas no café agora",
                        when {
                            orderedPausas.isNotEmpty() && testAsPause != null ->
                                "Pausas reais e uma simulação TESTE. O teste usa o mesmo cartão, mas não altera métricas nem histórico."
                            orderedPausas.isNotEmpty() ->
                                "Toque em qualquer pessoa para ver horários, limite, setor e situação completa."
                            testAsPause != null ->
                                "A simulação TESTE usa exatamente o mesmo visual de uma pausa real e não é salva no sistema."
                            else -> "Nenhuma pausa real em andamento."
                        },
                    )
                }

                testAsPause?.let { pause ->
                    item("test-${pause.id}") {
                        SupervisorOperationPauseCard(
                            pause = pause,
                            isTest = true,
                            onClick = {
                                selectedPause = pause
                                selectedPauseIsTest = true
                            },
                        )
                    }
                }

                if (orderedPausas.isEmpty() && testAsPause == null) {
                    item("empty") {
                        PcEmptyState(
                            title = "Nenhuma pausa aberta",
                            supportingText = "A lista será atualizada automaticamente quando houver uma nova saída.",
                            icon = Icons.Default.Groups,
                        )
                    }
                } else {
                    items(orderedPausas, key = { "operation-${it.id}" }) { pause ->
                        SupervisorOperationPauseCard(
                            pause = pause,
                            onClick = {
                                selectedPause = pause
                                selectedPauseIsTest = false
                            },
                        )
                    }
                }

                if (pendingFaces.isNotEmpty()) {
                    item("pending") {
                        PcActionTile(
                            title = "${pendingFaces.size} biometria(s) pendente(s)",
                            supportingText = "Abra Pessoas para concluir os registros faciais.",
                            icon = Icons.Default.Face,
                            onClick = viewModel::abrirColaboradores,
                        )
                    }
                }

                item("refresh") {
                    PcSecondaryButton(
                        text = if (state.carregando) "Atualizando…" else "Atualizar agora",
                        icon = Icons.Default.Refresh,
                        onClick = viewModel::atualizarAoVivo,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando,
                    )
                }

                if (!state.sessaoAdministrativa) {
                    item("logout") {
                        PcSecondaryButton(
                            text = "Encerrar sessão de Supervisor",
                            onClick = viewModel::sair,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            PcScrollToTopFab(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md),
            )
        }
    }
}

@Composable
private fun SupervisorConnectionBanner(
    connectionOk: Boolean,
    lastUpdateMillis: Long?,
) {
    var now by remember(lastUpdateMillis) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastUpdateMillis, connectionOk) {
        if (lastUpdateMillis == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val seconds = lastUpdateMillis?.let { ((now - it) / 1_000L).coerceAtLeast(0L) }
    PcStateBanner(
        title = when {
            !connectionOk -> "Conexão instável"
            seconds == null -> "Conectando"
            seconds < 10 -> "Dados sincronizados"
            else -> "Última atualização há ${seconds}s"
        },
        supportingText = if (connectionOk) {
            "Pausas atualizadas a cada 5 segundos e último retorno a cada 10 segundos."
        } else {
            "Os últimos dados válidos permanecem na tela enquanto o sistema reconecta."
        },
        tone = if (connectionOk) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
    )
}

@Composable
private fun SupervisorOperationPauseCard(
    pause: PausaSupervisor,
    isTest: Boolean = false,
    onClick: () -> Unit,
) {
    var now by remember(pause.id, pause.clienteAtualizadoEmMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(pause.id, pause.clienteAtualizadoEmMillis) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val elapsed = supervisorOperationSeconds(pause, now)
    val overdue = elapsed > pause.limiteSegundos
    val remaining = (pause.limiteSegundos - elapsed).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(pause.nome)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    Text(
                        pause.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isTest) {
                        StatusPill(text = "TESTE", tone = PontoCafeTone.INFO)
                    }
                }
                Text(
                    listOfNotNull(pause.setor, pause.periodo.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Saída ${pause.inicioLocal} · limite ${formatSupervisorDuration(pause.limiteSegundos)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isTest) {
                    Text(
                        "Simulação local · não salva histórico, auditoria ou métricas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatSupervisorDuration(elapsed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                StatusPill(
                    text = if (overdue) "Excedido" else "Restam ${formatSupervisorDuration(remaining)}",
                    tone = if (overdue) PontoCafeTone.DANGER else PontoCafeTone.SUCCESS,
                )
            }
        }
    }
}

@Composable
private fun SupervisorPauseDetailDialog(
    pause: PausaSupervisor,
    isTest: Boolean = false,
    onDismiss: () -> Unit,
) {
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos ?: supervisorOperationSeconds(pause, System.currentTimeMillis())
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteSegundos)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pause.nome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                if (isTest) {
                    PcStateBanner(
                        title = "TESTE · Não salvo no sistema",
                        supportingText = "Esta pausa existe somente para visualizar a experiência do Supervisor.",
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
                        "Duração" to formatSupervisorDuration(duration),
                        "Limite" to formatSupervisorDuration(pause.limiteSegundos),
                        "Situação" to if (exceeded) "Acima do limite" else "Dentro do limite",
                        "Fora do horário" to if (pause.foraHorario) "Sim" else "Não",
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

private fun AdminTestPause.toSupervisorPause(): PausaSupervisor {
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

private fun supervisorOperationSeconds(pause: PausaSupervisor, nowMillis: Long): Int {
    val base = pause.tempoSegundos ?: pause.duracaoSegundos ?: 0
    if (pause.fimLocal != null || pause.clienteAtualizadoEmMillis <= 0L) return base
    val extra = ((nowMillis - pause.clienteAtualizadoEmMillis) / 1_000L)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    return (base.toLong() + extra).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun formatSupervisorDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatSupervisorInstant(millis: Long): String = runCatching {
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrDefault("—")
