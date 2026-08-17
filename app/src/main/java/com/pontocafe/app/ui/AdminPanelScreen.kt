package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AdminTestPauseStore
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SupervisorApiClient
import kotlinx.coroutines.delay

@Composable
fun AdminPanelScreen(
    viewModel: AdminViewModel,
    onClose: () -> Unit,
    onDevicesClick: () -> Unit,
) {
    val state = viewModel.state
    val summary = state.resumoOperacional
    val collaborators = summary?.colaboradoresAtivos ?: state.colaboradores.size
    val activeSupervisors = summary?.supervisoresAtivos ?: state.usuarios.count { it.ativo && it.perfil == "SUPERVISOR" }
    val pendingFaces = summary?.rostosPendentes ?: state.colaboradores.count { !it.rostoCadastrado }
    val registeredFaces = (collaborators - pendingFaces).coerceAtLeast(0)
    val activeDevices = summary?.dispositivosAtivos ?: 0
    val devicesWithoutPin = summary?.dispositivosSemPin ?: 0
    val online = state.erro == null

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val adminSessionStore = remember(context) {
        SecureAdminSessionStore(context.applicationContext, "admin")
    }
    val adminDisplayName = remember(adminSessionStore) {
        adminSessionStore.activeAccount()?.name?.takeIf { it.isNotBlank() } ?: "Administrador"
    }
    val adminLiveRepository = remember(adminSessionStore) {
        SupervisorApiClient.create(adminSessionStore)
    }
    val testPause by AdminTestPauseStore.active.collectAsState()
    var livePauses by remember { mutableStateOf<List<PausaSupervisor>>(emptyList()) }
    var livePausesLoaded by remember { mutableStateOf(false) }
    var pauseFilter by remember { mutableStateOf(OperationalPauseFilter.TODOS) }
    var selectedPause by remember { mutableStateOf<OperationalPauseItem?>(null) }

    LaunchedEffect(lifecycleOwner, adminLiveRepository) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { adminLiveRepository.pausasAtivas() }
                    .onSuccess { pausas ->
                        livePauses = pausas
                        livePausesLoaded = true
                    }
                delay(5_000)
            }
        }
    }

    selectedPause?.let { item ->
        OperationalPauseDetailDialog(
            item = item,
            onDismiss = { selectedPause = null },
        )
    }

    val openPauses = if (livePausesLoaded) livePauses.size else (summary?.pausasAbertas ?: 0)
    val operationItems = remember(livePauses, testPause) {
        buildOperationalPauseItems(
            realPauses = livePauses,
            testPause = testPause,
            nowMillis = System.currentTimeMillis(),
        )
    }
    val filteredItems = remember(operationItems, pauseFilter, livePauses) {
        filterOperationalPauseItems(
            items = operationItems,
            filter = pauseFilter,
            nowMillis = System.currentTimeMillis(),
        )
    }

    PontoCafeResponsivePage(maxContentWidth = 1080.dp) { responsive ->
        val compactDashboard = responsive.availableWidth < 720.dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = responsive.pagePadding),
                contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PontoCafeScreenHeader(
                                title = "Visão geral",
                                eyebrow = "Administrador",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        PcSecondaryButton(
                            text = "Voltar ao Ponto",
                            onClick = onClose,
                            modifier = if (responsive.isCompact) Modifier.fillMaxWidth() else Modifier,
                        )
                    }
                }

                item(key = "feedback") { AdminFeedback(viewModel) }

                item(key = "hero") {
                    PcHeroCard(
                        title = if (online) "Operação normal" else "Operando com dados disponíveis",
                        supportingText = if (online) {
                            "$activeDevices dispositivo(s) ativo(s) · $openPauses pessoa(s) em pausa agora"
                        } else {
                            "A conexão ainda não foi confirmada. Os dados locais permanecem visíveis."
                        },
                        icon = if (online) Icons.Default.Security else Icons.Default.Devices,
                        tone = if (online) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }

                item(key = "metrics-title") {
                    SectionTitle("Operação", "Indicadores principais do Ponto Café neste momento.")
                }

                item(key = "metrics") {
                    if (compactDashboard) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                PcMetricTile(
                                    value = collaborators.toString(),
                                    label = "Colaboradores",
                                    icon = Icons.Default.Groups,
                                    modifier = Modifier.weight(1f),
                                )
                                PcMetricTile(
                                    value = openPauses.toString(),
                                    label = "Em pausa",
                                    icon = Icons.Default.Coffee,
                                    modifier = Modifier.weight(1f),
                                    attention = openPauses > 0,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                PcMetricTile(
                                    value = registeredFaces.toString(),
                                    label = "Biometrias prontas",
                                    icon = Icons.Default.Face,
                                    modifier = Modifier.weight(1f),
                                )
                                PcMetricTile(
                                    value = activeDevices.toString(),
                                    label = "Dispositivos",
                                    icon = Icons.Default.Devices,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = collaborators.toString(),
                                label = "Colaboradores",
                                icon = Icons.Default.Groups,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = openPauses.toString(),
                                label = "Em pausa agora",
                                icon = Icons.Default.Coffee,
                                modifier = Modifier.weight(1f),
                                attention = openPauses > 0,
                            )
                            PcMetricTile(
                                value = registeredFaces.toString(),
                                label = "Biometrias prontas",
                                icon = Icons.Default.Face,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = activeDevices.toString(),
                                label = "Dispositivos",
                                icon = Icons.Default.Devices,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "live-title") {
                    SectionTitle(
                        "Pessoas no café agora",
                        "O mesmo painel operacional do Supervisor, com prioridade automática por risco.",
                    )
                }

                item(key = "live-overview") {
                    OperationalPauseOverview(
                        realPauses = livePauses,
                        items = operationItems,
                        filter = pauseFilter,
                        onFilterChange = { pauseFilter = it },
                    )
                }

                if (filteredItems.isEmpty()) {
                    item(key = "live-empty-${pauseFilter.name}") {
                        PcEmptyState(
                            title = when (pauseFilter) {
                                OperationalPauseFilter.TODOS -> if (livePausesLoaded) "Nenhuma pausa aberta" else "Carregando pausas"
                                OperationalPauseFilter.ATENCAO -> "Ninguém em atenção agora"
                                OperationalPauseFilter.EXCEDIDOS -> "Nenhuma pausa excedida"
                            },
                            supportingText = when (pauseFilter) {
                                OperationalPauseFilter.TODOS -> if (livePausesLoaded) {
                                    "As novas saídas aparecerão automaticamente a cada atualização."
                                } else {
                                    "Consultando o painel operacional com a sessão administrativa."
                                }
                                OperationalPauseFilter.ATENCAO -> "Aqui aparecem pessoas com até 2 minutos restantes antes do limite."
                                OperationalPauseFilter.EXCEDIDOS -> "Os casos acima do limite aparecerão aqui automaticamente."
                            },
                            icon = Icons.Default.Coffee,
                        )
                    }
                } else {
                    items(filteredItems, key = { "admin-live-${it.pause.id}-${it.isTest}" }) { item ->
                        OperationalPauseCompactCard(
                            item = item,
                            onClick = { selectedPause = item },
                        )
                    }
                }

                if (collaborators > 0) {
                    item(key = "face-progress") {
                        ThinProgressSummary(
                            completed = registeredFaces,
                            total = collaborators,
                            title = "Reconhecimento facial",
                            detail = "$registeredFaces de $collaborators colaboradores com rosto cadastrado",
                        )
                    }
                }

                if (pendingFaces > 0 || devicesWithoutPin > 0 || activeSupervisors == 0) {
                    item(key = "pending-title") {
                        SectionTitle("Pendências", "Ações que exigem configuração ou acompanhamento.")
                    }
                }

                if (pendingFaces > 0) {
                    item(key = "pending-faces") {
                        OperationalAlertCard(
                            title = "$pendingFaces rosto(s) aguardando cadastro",
                            text = "Esses colaboradores ainda não conseguem utilizar reconhecimento facial.",
                            actionLabel = "Abrir Pessoas",
                            onClick = viewModel::abrirColaboradores,
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }

                if (devicesWithoutPin > 0) {
                    item(key = "devices-without-pin") {
                        OperationalAlertCard(
                            title = "$devicesWithoutPin dispositivo(s) sem PIN próprio",
                            text = "Defina um PIN individual para cada ponto e reduza dependência do código legado.",
                            actionLabel = "Gerenciar dispositivos",
                            onClick = onDevicesClick,
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }

                if (activeSupervisors == 0) {
                    item(key = "no-supervisor") {
                        OperationalAlertCard(
                            title = "Nenhum Supervisor ativo",
                            text = "Cadastre uma conta de Supervisor para acompanhamento e autorizações.",
                            actionLabel = "Cadastrar Supervisor",
                            onClick = viewModel::abrirNovaConta,
                            tone = PontoCafeTone.INFO,
                        )
                    }
                }

                item(key = "quick-title") {
                    SectionTitle("Ações rápidas", "Atalhos para as tarefas administrativas mais usadas.")
                }

                item(key = "quick-actions") {
                    if (compactDashboard) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcActionTile(
                                title = "Pessoas e acessos",
                                supportingText = "Cadastrar, editar ou gerenciar biometria",
                                icon = Icons.Default.PersonAdd,
                                onClick = viewModel::abrirColaboradores,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PcActionTile(
                                title = "Autorizar pausa",
                                supportingText = "Liberar uma exceção temporária",
                                icon = Icons.Default.Coffee,
                                onClick = viewModel::abrirAutorizacao,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PcActionTile(
                                title = "Dispositivos",
                                supportingText = "PIN, status e configuração do modo Ponto",
                                icon = Icons.Default.Devices,
                                onClick = onDevicesClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcActionTile(
                                title = "Pessoas e acessos",
                                supportingText = "Equipe e biometria",
                                icon = Icons.Default.PersonAdd,
                                onClick = viewModel::abrirColaboradores,
                                modifier = Modifier.weight(1f),
                            )
                            PcActionTile(
                                title = "Autorizar pausa",
                                supportingText = "Exceção temporária",
                                icon = Icons.Default.Coffee,
                                onClick = viewModel::abrirAutorizacao,
                                modifier = Modifier.weight(1f),
                            )
                            PcActionTile(
                                title = "Dispositivos",
                                supportingText = "PIN e configuração",
                                icon = Icons.Default.Devices,
                                onClick = onDevicesClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item(key = "supervisor-test-title") {
                    SectionTitle(
                        "Teste visual do Supervisor",
                        "Simule uma batida de ponto sem criar registros reais.",
                    )
                }

                item(key = "supervisor-test") {
                    PcSectionSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcStateBanner(
                                title = if (testPause == null) "Nenhum teste ativo" else "TESTE ativo no painel operacional",
                                supportingText = if (testPause == null) {
                                    "A simulação existe somente neste aparelho e desaparece ao reiniciar a app."
                                } else {
                                    "${testPause?.adminName ?: adminDisplayName} aparece acima com exatamente o mesmo cartão de uma pausa real."
                                },
                                tone = if (testPause == null) PontoCafeTone.NEUTRAL else PontoCafeTone.INFO,
                            )
                            Text(
                                "O teste não altera pausas reais, métricas, histórico, fila offline, banco de dados ou auditoria.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (testPause == null) {
                                PcPrimaryButton(
                                    text = "Iniciar teste de ponto",
                                    icon = Icons.Default.Science,
                                    onClick = { AdminTestPauseStore.start(adminDisplayName) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                PcSecondaryButton(
                                    text = "Encerrar teste",
                                    icon = Icons.Default.StopCircle,
                                    onClick = AdminTestPauseStore::stop,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                item(key = "access-context") {
                    PcSectionSurface {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                            Text(
                                "Equipe de acesso",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            Text(
                                "$activeSupervisors Supervisor(es) ativo(s) · ${state.usuarios.count { it.ativo }} conta(s) ativa(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item(key = "logout") {
                    PcSecondaryButton(
                        text = "Encerrar sessão administrativa",
                        onClick = viewModel::logout,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
