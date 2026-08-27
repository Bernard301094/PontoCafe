package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreenV2(
    viewModel: AdminViewModel,
    onClose: () -> Unit,
    onDevicesClick: () -> Unit,
) {
    val state = viewModel.state
    val summary = state.resumoOperacional
    val collaborators = summary?.colaboradoresAtivos ?: state.colaboradores.size
    val activeSupervisors = summary?.supervisoresAtivos
        ?: state.usuarios.count { it.ativo && it.perfil == "SUPERVISOR" }
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
    val activeAccount = remember(adminSessionStore) { adminSessionStore.activeAccount() }
    val adminDisplayName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: "Administrador"
    val adminLiveRepository = remember(adminSessionStore) { SupervisorApiClient.create(adminSessionStore) }
    val testPause by AdminTestPauseStore.active.collectAsState()

    var livePauses by remember { mutableStateOf<List<PausaSupervisor>>(emptyList()) }
    var livePausesLoaded by remember { mutableStateOf(false) }
    var pauseFilter by remember { mutableStateOf(OperationalPauseFilter.TODOS) }
    var selectedOperationalPause by remember { mutableStateOf<OperationalPauseItem?>(null) }
    var manualClosePause by remember { mutableStateOf<OperationalPauseItem?>(null) }
    var manualCloseLoading by remember { mutableStateOf(false) }
    var manualCloseError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var showAllLive by remember { mutableStateOf(false) }
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
                    .onSuccess { pausas ->
                        livePauses = pausas
                        livePausesLoaded = true
                    }
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
            initialSelectedDateMillis = historyDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showHistoryCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            historyDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
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
                title = {
                    Text(
                        "Escolha a data do histórico",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                },
                headline = null,
                showModeToggle = false,
            )
        }
    }

    selectedOperationalPause?.let { item ->
        OperationalPauseDetailDialog(item, onDismiss = { selectedOperationalPause = null })
    }
    manualClosePause?.let { item ->
        ManualPauseCloseDialog(
            item = item,
            loading = manualCloseLoading,
            errorMessage = manualCloseError,
            onConfirm = { motivo ->
                coroutineScope.launch {
                    manualCloseLoading = true
                    manualCloseError = null
                    runCatching { adminLiveRepository.finalizarPausaManual(item.pause.id, motivo) }
                        .onSuccess {
                            manualCloseLoading = false
                            manualClosePause = null
                            runCatching { adminLiveRepository.pausasAtivas() }.onSuccess { pausas ->
                                livePauses = pausas
                                livePausesLoaded = true
                            }
                        }
                        .onFailure { error ->
                            manualCloseLoading = false
                            manualCloseError = SupervisorRepository.message(error)
                        }
                }
            },
            onDismiss = {
                manualClosePause = null
                manualCloseError = null
            },
        )
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
    val nowMillis = System.currentTimeMillis()
    val operationItems = remember(livePauses, testPause) {
        buildOperationalPauseItems(livePauses, testPause, System.currentTimeMillis())
    }
    val filteredItems = filterOperationalPauseItems(operationItems, pauseFilter, nowMillis)
    val attentionCount = filterOperationalPauseItems(
        operationItems,
        OperationalPauseFilter.ATENCAO,
        nowMillis,
    ).size
    val exceededCount = filterOperationalPauseItems(
        operationItems,
        OperationalPauseFilter.EXCEDIDOS,
        nowMillis,
    ).size

    val sortedHistory = historyPauses.sortedByDescending { it.inicioLocal }
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

    // Zona colorida fixa (saudação + navegação + números-chave) sobre uma
    // folha arredondada rolável -- ver PcHeroPage. Substitui a barra fina +
    // AdminHomeOverviewCard que ficavam soltos no topo da lista.
    PcHeroPage(
        heroContent = {
            PcHeroZoneTopBar(
                title = "Início",
                eyebrow = "Administrador",
                account = activeAccount,
                fallbackName = adminDisplayName,
                onProfileClick = { showAccountSheet = true },
                onBackToPonto = onClose,
            )
            Text(
                // O titulo dizia "Tudo em ordem por aqui" enquanto logo abaixo o
                // painel mostrava "2 Excedidos" e um selo URGENTE. O cabecalho
                // precisa concordar com o que a tela esta mostrando.
                when {
                    !online -> "Conexão ainda não confirmada"
                    exceededCount > 0 -> "$exceededCount pausa(s) excedida(s)"
                    attentionCount > 0 -> "$attentionCount pausa(s) perto do limite"
                    else -> "Tudo em ordem por aqui"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                PcHeroStat(value = "$openPauses", label = "Em pausa", modifier = Modifier.weight(1f))
                PcHeroStat(value = "$attentionCount", label = "Em atenção", modifier = Modifier.weight(1f))
                PcHeroStat(value = "$exceededCount", label = "Excedidos", modifier = Modifier.weight(1f))
                PcHeroStat(value = "$activeDevices", label = "Dispositivos", modifier = Modifier.weight(1f))
            }
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = 1180.dp) { responsive ->
        val livePreviewLimit = when (responsive.windowSizeClass) {
            PontoCafeWindowSizeClass.COMPACT -> 4
            PontoCafeWindowSizeClass.MEDIUM -> 5
            PontoCafeWindowSizeClass.EXPANDED -> 6
        }
        val visibleLive = if (showAllLive) filteredItems else filteredItems.take(livePreviewLimit)
        val historyPreviewLimit = if (responsive.isExpanded) 5 else 3
        val visibleHistory = if (showAllHistory) sortedHistory else sortedHistory.take(historyPreviewLimit)
        val hasPendingConfiguration = pendingFaces > 0 || devicesWithoutPin > 0 || activeSupervisors == 0

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.md,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item("feedback") { AdminFeedback(viewModel) }

                // Sinal de status logo após o resumo -- antes só existia a versão
                // "tudo certo" e ficava no fim da lista, depois do histórico inteiro,
                // onde ninguém rolava até ver. Agora aparece sempre aqui em cima,
                // nos dois sentidos: com ou sem pendência.
                item("configuration-status") {
                    if (hasPendingConfiguration) {
                        val pendingReasons = buildList {
                            if (pendingFaces > 0) add("$pendingFaces pessoa(s) sem biometria cadastrada")
                            if (devicesWithoutPin > 0) add("$devicesWithoutPin dispositivo(s) sem PIN configurado")
                            if (activeSupervisors == 0) add("nenhum supervisor ativo")
                        }
                        PcStateBanner(
                            title = "Configuração pendente",
                            supportingText = pendingReasons.joinToString(" · ").ifEmpty {
                                "Há itens de configuração para revisar."
                            },
                            tone = PontoCafeTone.WARNING,
                        )
                    } else if (collaborators > 0) {
                        PcStateBanner(
                            title = "Configuração em dia",
                            supportingText = "Equipe, biometria, supervisão e dispositivos não apresentam pendências de configuração.",
                            tone = PontoCafeTone.SUCCESS,
                        )
                    }
                }

                item("quick-actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        AdminHomeSectionHeader(
                            title = "Ações rápidas",
                            subtitle = "Acesso direto às tarefas administrativas mais usadas.",
                        )
                        if (responsive.isNarrow || responsive.usesLargeText) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                AdminHomeQuickAction(
                                    title = "Pessoas",
                                    icon = Icons.Default.PersonAdd,
                                    onClick = viewModel::abrirColaboradores,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AdminHomeQuickAction(
                                    title = "Autorizar",
                                    icon = Icons.Default.Coffee,
                                    onClick = viewModel::abrirAutorizacao,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AdminHomeQuickAction(
                                    title = "Dispositivos",
                                    icon = Icons.Default.Devices,
                                    onClick = onDevicesClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            AdminHomeQuickAction(
                                title = "Pessoas",
                                icon = Icons.Default.PersonAdd,
                                onClick = viewModel::abrirColaboradores,
                                modifier = Modifier.weight(1f),
                            )
                            AdminHomeQuickAction(
                                title = "Autorizar",
                                icon = Icons.Default.Coffee,
                                onClick = viewModel::abrirAutorizacao,
                                modifier = Modifier.weight(1f),
                            )
                            AdminHomeQuickAction(
                                title = "Dispositivos",
                                icon = Icons.Default.Devices,
                                onClick = onDevicesClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (responsive.isExpanded && responsive.supportsTwoColumns) {
                    item("desktop-dashboard") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
                            verticalAlignment = Alignment.Top,
                        ) {
                            AdminHomeAttentionPanel(
                                livePauses = livePauses,
                                livePausesLoaded = livePausesLoaded,
                                operationItems = operationItems,
                                pauseFilter = pauseFilter,
                                onFilterChange = {
                                    pauseFilter = it
                                    showAllLive = false
                                },
                                visibleItems = visibleLive,
                                filteredCount = filteredItems.size,
                                showAll = showAllLive,
                                onToggleShowAll = { showAllLive = !showAllLive },
                                onItemClick = { selectedOperationalPause = it },
                                onCloseManually = { manualClosePause = it },
                                modifier = Modifier.weight(1.12f),
                            )

                            AdminHomeReadinessPanel(
                                collaborators = collaborators,
                                registeredFaces = registeredFaces,
                                activeSupervisors = activeSupervisors,
                                pendingFaces = pendingFaces,
                                devicesWithoutPin = devicesWithoutPin,
                                onPeopleClick = viewModel::abrirColaboradores,
                                onDevicesClick = onDevicesClick,
                                onNewSupervisor = viewModel::abrirNovaConta,
                                modifier = Modifier.weight(.88f),
                            )
                        }
                    }
                } else {
                    item("attention") {
                        AdminHomeAttentionPanel(
                            livePauses = livePauses,
                            livePausesLoaded = livePausesLoaded,
                            operationItems = operationItems,
                            pauseFilter = pauseFilter,
                            onFilterChange = {
                                pauseFilter = it
                                showAllLive = false
                            },
                            visibleItems = visibleLive,
                            filteredCount = filteredItems.size,
                            showAll = showAllLive,
                            onToggleShowAll = { showAllLive = !showAllLive },
                            onItemClick = { selectedOperationalPause = it },
                            onCloseManually = { manualClosePause = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    item("readiness") {
                        AdminHomeReadinessPanel(
                            collaborators = collaborators,
                            registeredFaces = registeredFaces,
                            activeSupervisors = activeSupervisors,
                            pendingFaces = pendingFaces,
                            devicesWithoutPin = devicesWithoutPin,
                            onPeopleClick = viewModel::abrirColaboradores,
                            onDevicesClick = onDevicesClick,
                            onNewSupervisor = viewModel::abrirNovaConta,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item("history-header") {
                    if (responsive.isCompact || responsive.usesLargeText) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            AdminHomeSectionHeader(
                                title = if (historyDate == LocalDate.now()) "Histórico de hoje" else "Histórico · $historyDateLabel",
                                subtitle = if (historyLoading) "Carregando registros…" else historySummary,
                            )
                            PcSecondaryButton(
                                text = "Escolher outra data",
                                onClick = { showHistoryCalendar = true },
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.CalendarMonth,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                AdminHomeSectionHeader(
                                    title = if (historyDate == LocalDate.now()) "Histórico de hoje" else "Histórico · $historyDateLabel",
                                    subtitle = if (historyLoading) "Carregando registros…" else historySummary,
                                )
                            }
                            PcSecondaryButton(
                                text = "Escolher data",
                                onClick = { showHistoryCalendar = true },
                                modifier = Modifier.weight(.38f),
                                icon = Icons.Default.CalendarMonth,
                            )
                        }
                    }
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
                    item("history-loading") { PontoCafeLoadingSkeleton(rows = historyPreviewLimit) }
                } else if (historyPauses.isEmpty()) {
                    item("history-empty") {
                        PcEmptyState(
                            title = "Sem registros em $historyDateLabel",
                            supportingText = "Escolha outra data para consultar as pausas registradas.",
                            icon = Icons.Default.CalendarMonth,
                        )
                    }
                } else {
                    items(visibleHistory, key = { "admin-home-history-${it.id}" }) { pause ->
                        HistoryPauseCard(
                            pause = pause,
                            onClick = { selectedHistoryPause = pause },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (historyPauses.size > historyPreviewLimit) {
                        item("history-toggle") {
                            PcSecondaryButton(
                                text = if (showAllHistory) {
                                    "Mostrar somente os mais recentes"
                                } else {
                                    "Ver histórico completo · ${historyPauses.size}"
                                },
                                onClick = { showAllHistory = !showAllHistory },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            PcScrollToTopFab(
                listState,
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md),
            )
        }
    }
    }
}

@Composable
private fun AdminHomeQuickAction(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressScale = rememberPcPressScale(interactionSource)
    Card(
        modifier = modifier.pcPressScale(pressScale),
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PontoCafeSpacing.xs, vertical = PontoCafeSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(PontoCafeSpacing.xs).size(19.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AdminHomeAttentionPanel(
    livePauses: List<PausaSupervisor>,
    livePausesLoaded: Boolean,
    operationItems: List<OperationalPauseItem>,
    pauseFilter: OperationalPauseFilter,
    onFilterChange: (OperationalPauseFilter) -> Unit,
    visibleItems: List<OperationalPauseItem>,
    filteredCount: Int,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onItemClick: (OperationalPauseItem) -> Unit,
    onCloseManually: (OperationalPauseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            AdminHomeSectionHeader(
                title = "Centro de atenção",
                subtitle = "Excedidos e pausas próximas do limite aparecem primeiro.",
            )
            OperationalPauseOverview(livePauses, operationItems, pauseFilter, onFilterChange)

            if (visibleItems.isEmpty()) {
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
            } else {
                visibleItems.forEach { item ->
                    OperationalPauseCompactCard(
                        item,
                        onClick = { onItemClick(item) },
                        onCloseManually = { onCloseManually(item) },
                    )
                }
                if (filteredCount > visibleItems.size || showAll) {
                    PcSecondaryButton(
                        text = if (showAll) "Mostrar menos" else "Ver todas · $filteredCount",
                        onClick = onToggleShowAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminHomeReadinessPanel(
    collaborators: Int,
    registeredFaces: Int,
    activeSupervisors: Int,
    pendingFaces: Int,
    devicesWithoutPin: Int,
    onPeopleClick: () -> Unit,
    onDevicesClick: () -> Unit,
    onNewSupervisor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            AdminHomeSectionHeader(
                title = "Equipe e configuração",
                subtitle = "Só aparecem aqui itens que precisam de preparação ou acompanhamento.",
            )

            if (collaborators > 0) {
                ThinProgressSummary(
                    registeredFaces,
                    collaborators,
                    "Reconhecimento facial",
                    "$registeredFaces de $collaborators colaboradores com rosto cadastrado",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                AdminHomeMiniStat(
                    value = collaborators.toString(),
                    label = "Equipe",
                    icon = Icons.Default.Groups,
                    modifier = Modifier.weight(1f),
                )
                AdminHomeMiniStat(
                    value = activeSupervisors.toString(),
                    label = "Supervisores",
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f),
                )
            }

            if (pendingFaces > 0) {
                OperationalAlertCard(
                    "$pendingFaces rosto(s) aguardando cadastro",
                    "Esses colaboradores ainda não conseguem utilizar reconhecimento facial.",
                    "Abrir Pessoas",
                    onPeopleClick,
                    PontoCafeTone.WARNING,
                )
            }
            if (devicesWithoutPin > 0) {
                OperationalAlertCard(
                    "$devicesWithoutPin dispositivo(s) sem PIN próprio",
                    "Defina um PIN individual para cada ponto.",
                    "Gerenciar dispositivos",
                    onDevicesClick,
                    PontoCafeTone.WARNING,
                )
            }
            if (activeSupervisors == 0) {
                OperationalAlertCard(
                    "Nenhum Supervisor ativo",
                    "Cadastre uma conta de Supervisor para acompanhamento e autorizações.",
                    "Cadastrar Supervisor",
                    onNewSupervisor,
                    PontoCafeTone.INFO,
                )
            }
            if (pendingFaces == 0 && devicesWithoutPin == 0 && activeSupervisors > 0) {
                PcStateBanner(
                    title = "Tudo pronto para operar",
                    supportingText = "Não há pendências de biometria, dispositivo ou supervisão.",
                    tone = PontoCafeTone.SUCCESS,
                )
            }
        }
    }
}

@Composable
private fun AdminHomeMiniStat(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminHomeSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
