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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pontocafe.app.data.SupervisorRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
    val adminSessionStore = remember(context) { SecureAdminSessionStore(context.applicationContext, "admin") }
    val activeAccount = remember(adminSessionStore) { adminSessionStore.activeAccount() }
    val adminDisplayName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: "Administrador"
    val adminLiveRepository = remember(adminSessionStore) { SupervisorApiClient.create(adminSessionStore) }
    val testPause by AdminTestPauseStore.active.collectAsState()

    var livePauses by remember { mutableStateOf<List<PausaSupervisor>>(emptyList()) }
    var livePausesLoaded by remember { mutableStateOf(false) }
    var pauseFilter by remember { mutableStateOf(OperationalPauseFilter.TODOS) }
    var selectedOperationalPause by remember { mutableStateOf<OperationalPauseItem?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }

    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var historyPauses by remember { mutableStateOf<List<PausaSupervisor>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(true) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var showHistoryCalendar by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }
    var selectedHistoryPause by remember { mutableStateOf<PausaSupervisor?>(null) }

    LaunchedEffect(lifecycleOwner, adminLiveRepository) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { adminLiveRepository.pausasAtivas() }
                    .onSuccess { pausas -> livePauses = pausas; livePausesLoaded = true }
                delay(5_000)
            }
        }
    }

    LaunchedEffect(historyDate, adminLiveRepository) {
        historyLoading = true
        historyError = null
        runCatching { adminLiveRepository.historico(historyDate.toString()) }
            .onSuccess { pauses ->
                historyPauses = pauses
                historyLoading = false
            }
            .onFailure { error ->
                historyLoading = false
                historyError = SupervisorRepository.message(error)
            }
    }

    if (showHistoryCalendar) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = historyDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showHistoryCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            historyDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            showAllHistory = false
                        }
                        showHistoryCalendar = false
                    },
                ) { Text("Abrir dia") }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryCalendar = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(
                state = pickerState,
                title = { Text("Escolha a data do histórico", modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) },
                headline = null,
                showModeToggle = false,
            )
        }
    }

    selectedOperationalPause?.let { item ->
        OperationalPauseDetailDialog(item, onDismiss = { selectedOperationalPause = null })
    }
    selectedHistoryPause?.let { pause ->
        HistoryPauseDetailDialog(pause = pause, onDismiss = { selectedHistoryPause = null })
    }

    if (showAccountSheet) {
        PcAccountProfileSheet(
            account = activeAccount,
            fallbackName = adminDisplayName,
            profileLabel = "Administrador",
            onDismiss = { showAccountSheet = false },
            onLogout = {
                showAccountSheet = false
                viewModel.logout()
            },
        )
    }

    val openPauses = if (livePausesLoaded) livePauses.size else (summary?.pausasAbertas ?: 0)
    val operationItems = remember(livePauses, testPause) { buildOperationalPauseItems(livePauses, testPause, System.currentTimeMillis()) }
    val filteredItems = remember(operationItems, pauseFilter, livePauses) { filterOperationalPauseItems(operationItems, pauseFilter, System.currentTimeMillis()) }

    val sortedHistory = historyPauses.sortedByDescending { it.inicioLocal }
    val visibleHistory = if (showAllHistory) sortedHistory else sortedHistory.take(3)
    val historyOverLimit = historyPauses.count { pause ->
        val duration = pause.duracaoSegundos ?: pause.tempoSegundos ?: 0
        pause.excedeuLimite ?: (duration > pause.limiteSegundos)
    }
    val historyOutside = historyPauses.count { it.foraHorario }
    val historyDateLabel = historyDate.format(DateTimeFormatter.ofPattern("dd/MM"))
    val historySummary = buildString {
        append(historyPauses.size)
        append(" pausa(s) · ")
        append(historyOverLimit)
        append(" acima do limite")
        if (historyOutside > 0) {
            append(" · ")
            append(historyOutside)
            append(" fora do horário")
        }
    }

    PontoCafeResponsivePage(maxContentWidth = 1080.dp) { responsive ->
        val compactDashboard = responsive.availableWidth < 720.dp
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = responsive.pagePadding),
                contentPadding = PaddingValues(top = PontoCafeSpacing.md, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                item("header") {
                    PcAreaTopBar(
                        title = "Visão geral",
                        eyebrow = "Administrador",
                        account = activeAccount,
                        fallbackName = adminDisplayName,
                        onProfileClick = { showAccountSheet = true },
                        onBackToPonto = onClose,
                    )
                }
                item("feedback") { AdminFeedback(viewModel) }
                item("hero") {
                    PcHeroCard(
                        title = if (online) "Operação normal" else "Operando com dados disponíveis",
                        supportingText = if (online) "$activeDevices dispositivo(s) ativo(s) · $openPauses pessoa(s) em pausa agora" else "A conexão ainda não foi confirmada. Os dados locais permanecem visíveis.",
                        icon = if (online) Icons.Default.Security else Icons.Default.Devices,
                        tone = if (online) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }
                item("metrics-title") { SectionTitle("Agora", "Indicadores essenciais do Ponto Café.") }
                item("metrics") {
                    if (compactDashboard) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                PcMetricTile(collaborators.toString(), "Colaboradores", Icons.Default.Groups, Modifier.weight(1f))
                                PcMetricTile(openPauses.toString(), "Em pausa", Icons.Default.Coffee, Modifier.weight(1f), attention = openPauses > 0)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                PcMetricTile(registeredFaces.toString(), "Biometrias prontas", Icons.Default.Face, Modifier.weight(1f))
                                PcMetricTile(activeDevices.toString(), "Dispositivos", Icons.Default.Devices, Modifier.weight(1f))
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcMetricTile(collaborators.toString(), "Colaboradores", Icons.Default.Groups, Modifier.weight(1f))
                            PcMetricTile(openPauses.toString(), "Em pausa agora", Icons.Default.Coffee, Modifier.weight(1f), attention = openPauses > 0)
                            PcMetricTile(registeredFaces.toString(), "Biometrias prontas", Icons.Default.Face, Modifier.weight(1f))
                            PcMetricTile(activeDevices.toString(), "Dispositivos", Icons.Default.Devices, Modifier.weight(1f))
                        }
                    }
                }
                item("live-title") { SectionTitle("Centro de atenção", "Excedidos e pausas próximas do limite aparecem primeiro.") }
                item("live-overview") { OperationalPauseOverview(livePauses, operationItems, pauseFilter, { pauseFilter = it }) }
                if (filteredItems.isEmpty()) {
                    item("live-empty-${pauseFilter.name}") {
                        PcEmptyState(
                            title = when (pauseFilter) {
                                OperationalPauseFilter.TODOS -> if (livePausesLoaded) "Nenhuma pausa aberta" else "Carregando pausas"
                                OperationalPauseFilter.ATENCAO -> "Ninguém em atenção agora"
                                OperationalPauseFilter.EXCEDIDOS -> "Nenhuma pausa excedida"
                            },
                            supportingText = when (pauseFilter) {
                                OperationalPauseFilter.TODOS -> if (livePausesLoaded) "As novas saídas aparecerão automaticamente." else "Consultando o painel operacional."
                                OperationalPauseFilter.ATENCAO -> "Aqui aparecem pessoas com até 2 minutos restantes."
                                OperationalPauseFilter.EXCEDIDOS -> "Os casos acima do limite aparecerão aqui automaticamente."
                            },
                            icon = Icons.Default.Coffee,
                        )
                    }
                } else {
                    items(filteredItems, key = { "admin-live-${it.pause.id}-${it.isTest}" }) { item ->
                        OperationalPauseCompactCard(item, onClick = { selectedOperationalPause = item })
                    }
                }

                item("history-title") {
                    SectionTitle(
                        "Histórico do Ponto · $historyDateLabel",
                        if (historyLoading) "Carregando registros…" else historySummary,
                    )
                }
                item("history-date") {
                    PcSecondaryButton(
                        text = "Escolher outra data",
                        onClick = { showHistoryCalendar = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.CalendarMonth,
                    )
                }
                if (historyError != null) {
                    item("history-error") {
                        OperationalAlertCard(
                            title = "Não foi possível carregar o histórico",
                            text = historyError ?: "Erro desconhecido",
                            actionLabel = "Tentar novamente",
                            onClick = {
                                val retryDate = historyDate
                                historyDate = retryDate.minusDays(1)
                                historyDate = retryDate
                            },
                            tone = PontoCafeTone.DANGER,
                        )
                    }
                } else if (historyLoading && historyPauses.isEmpty()) {
                    item("history-loading") { PontoCafeLoadingSkeleton(rows = 3) }
                } else if (historyPauses.isEmpty()) {
                    item("history-empty") {
                        PcEmptyState(
                            title = "Sem registros em $historyDateLabel",
                            supportingText = "Escolha outra data para consultar as pausas registradas.",
                            icon = Icons.Default.CalendarMonth,
                        )
                    }
                } else {
                    items(visibleHistory, key = { "admin-history-${it.id}" }) { pause ->
                        HistoryPauseCard(
                            pause = pause,
                            onClick = { selectedHistoryPause = pause },
                        )
                    }
                    if (historyPauses.size > 3) {
                        item("history-toggle") {
                            PcSecondaryButton(
                                text = if (showAllHistory) "Mostrar somente os mais recentes" else "Ver histórico completo · ${historyPauses.size}",
                                onClick = { showAllHistory = !showAllHistory },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (collaborators > 0) {
                    item("face-progress") {
                        ThinProgressSummary(registeredFaces, collaborators, "Reconhecimento facial", "$registeredFaces de $collaborators colaboradores com rosto cadastrado")
                    }
                }
                if (pendingFaces > 0 || devicesWithoutPin > 0 || activeSupervisors == 0) item("pending-title") { SectionTitle("Pendências", "Somente o que exige configuração ou acompanhamento.") }
                if (pendingFaces > 0) {
                    item("pending-faces") { OperationalAlertCard("$pendingFaces rosto(s) aguardando cadastro", "Esses colaboradores ainda não conseguem utilizar reconhecimento facial.", "Abrir Pessoas", viewModel::abrirColaboradores, PontoCafeTone.WARNING) }
                }
                if (devicesWithoutPin > 0) {
                    item("devices-without-pin") { OperationalAlertCard("$devicesWithoutPin dispositivo(s) sem PIN próprio", "Defina um PIN individual para cada ponto.", "Gerenciar dispositivos", onDevicesClick, PontoCafeTone.WARNING) }
                }
                if (activeSupervisors == 0) {
                    item("no-supervisor") { OperationalAlertCard("Nenhum Supervisor ativo", "Cadastre uma conta de Supervisor para acompanhamento e autorizações.", "Cadastrar Supervisor", viewModel::abrirNovaConta, PontoCafeTone.INFO) }
                }
                item("quick-title") { SectionTitle("Ações rápidas", "Atalhos para as tarefas administrativas mais usadas.") }
                item("quick-actions") {
                    if (compactDashboard) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcActionTile("Pessoas e acessos", "Cadastrar, editar ou gerenciar biometria", Icons.Default.PersonAdd, viewModel::abrirColaboradores, Modifier.fillMaxWidth())
                            PcActionTile("Autorizar pausa", "Liberar uma exceção temporária", Icons.Default.Coffee, viewModel::abrirAutorizacao, Modifier.fillMaxWidth())
                            PcActionTile("Dispositivos", "PIN, status e configuração do modo Ponto", Icons.Default.Devices, onDevicesClick, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcActionTile("Pessoas e acessos", "Equipe e biometria", Icons.Default.PersonAdd, viewModel::abrirColaboradores, Modifier.weight(1f))
                            PcActionTile("Autorizar pausa", "Exceção temporária", Icons.Default.Coffee, viewModel::abrirAutorizacao, Modifier.weight(1f))
                            PcActionTile("Dispositivos", "PIN e configuração", Icons.Default.Devices, onDevicesClick, Modifier.weight(1f))
                        }
                    }
                }
            }
            PcScrollToTopFab(listState, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md))
        }
    }
}
