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
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.AdminTestPauseStore
import com.pontocafe.app.data.PausaSupervisor
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
    var pauseFilter by remember { mutableStateOf(OperationalPauseFilter.TODOS) }
    var selectedPause by remember { mutableStateOf<OperationalPauseItem?>(null) }

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

    selectedPause?.let { item ->
        OperationalPauseDetailDialog(
            item = item,
            onDismiss = { selectedPause = null },
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
    val overdue = state.pausasAtivas.count {
        supervisorOperationSeconds(it, nowSnapshot) > it.limiteSegundos
    }
    val operationItems = remember(state.pausasAtivas, testPause, state.ultimaAtualizacaoAoVivoEmMillis) {
        buildOperationalPauseItems(
            realPauses = state.pausasAtivas,
            testPause = testPause,
            nowMillis = System.currentTimeMillis(),
        )
    }
    val filteredItems = remember(operationItems, pauseFilter, state.ultimaAtualizacaoAoVivoEmMillis) {
        filterOperationalPauseItems(
            items = operationItems,
            filter = pauseFilter,
            nowMillis = System.currentTimeMillis(),
        )
    }

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
                            state.pausasAtivas.isEmpty() -> "Operação sem pausas reais abertas"
                            else -> "${state.pausasAtivas.size} pessoa(s) em pausa agora"
                        },
                        supportingText = when {
                            overdue > 0 -> "Os casos acima do limite aparecem primeiro no painel operacional."
                            state.pausasAtivas.isEmpty() -> "Quando alguém sair para o café, o registro aparecerá automaticamente aqui."
                            else -> "A lista prioriza excedidos, depois quem está a menos de 2 minutos do limite."
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

                item("active-title") {
                    SectionTitle(
                        "Pessoas no café agora",
                        "Painel compacto para acompanhar muitas pausas sem perder os casos prioritários.",
                    )
                }

                item("active-overview") {
                    OperationalPauseOverview(
                        realPauses = state.pausasAtivas,
                        items = operationItems,
                        filter = pauseFilter,
                        onFilterChange = { pauseFilter = it },
                    )
                }

                if (filteredItems.isEmpty()) {
                    item("active-empty-${pauseFilter.name}") {
                        PcEmptyState(
                            title = when (pauseFilter) {
                                OperationalPauseFilter.TODOS -> "Nenhuma pausa aberta"
                                OperationalPauseFilter.ATENCAO -> "Ninguém em atenção agora"
                                OperationalPauseFilter.EXCEDIDOS -> "Nenhuma pausa excedida"
                            },
                            supportingText = when (pauseFilter) {
                                OperationalPauseFilter.TODOS -> "A lista será atualizada automaticamente quando houver uma nova saída."
                                OperationalPauseFilter.ATENCAO -> "Aqui aparecem pessoas com até 2 minutos restantes antes do limite."
                                OperationalPauseFilter.EXCEDIDOS -> "Os casos acima do limite aparecerão aqui automaticamente."
                            },
                            icon = Icons.Default.Coffee,
                        )
                    }
                } else {
                    items(filteredItems, key = { "compact-${it.pause.id}-${it.isTest}" }) { item ->
                        OperationalPauseCompactCard(
                            item = item,
                            onClick = { selectedPause = item },
                        )
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

private fun supervisorOperationSeconds(pause: PausaSupervisor, nowMillis: Long): Int {
    val base = pause.tempoSegundos ?: pause.duracaoSegundos ?: 0
    if (pause.fimLocal != null || pause.clienteAtualizadoEmMillis <= 0L) return base
    val extra = ((nowMillis - pause.clienteAtualizadoEmMillis) / 1_000L)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    return (base.toLong() + extra).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
