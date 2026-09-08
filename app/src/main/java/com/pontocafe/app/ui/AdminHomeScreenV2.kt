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
import com.pontocafe.app.data.PontoRepositories
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
    // Quantas pessoas estão fora agora. Não é pendência de configuração —
    // é o pulso da operação, e substitui o antigo contador de rostos por cadastrar.
    val emPausaAgora = summary?.codigosEmUso ?: state.colaboradores.count { it.emPausa }
    val codigosPendentes = summary?.codigosPendentes ?: 0
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
    // Vida longa: antes era remember, e o cache morria ao sair da tela.
    val adminLiveRepository = remember(adminSessionStore) { PontoRepositories.supervisor(adminSessionStore) }
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

    // O Início é onde o Supervisor está quando alguém pede café. Carregar as
    // pessoas e os códigos vivos aqui é o que permite emitir sem navegar.
    //
    // E recarregar: sem isto, quem registou o retorno continuaria no cartão até
    // o Supervisor sair e voltar à tela. O primeiro passe é visível (mostra o
    // indicador); os seguintes são silenciosos, para o cartão não piscar de
    // vinte em vinte segundos.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var primeira = true
            while (true) {
                viewModel.carregarAtalhoDeCodigos(silencioso = !primeira)
                primeira = false
                delay(20_000)
            }
        }
    }

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
                    runCatching { adminLiveRepository.finalizarPausaManual(item.pause.colaboradorId, motivo) }
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
    state.codigoEmitido?.let { emitido ->
        PcIssuedCodeDialog(codigo = emitido, onDismiss = viewModel::limparCodigoEmitido)
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
        val hasPendingConfiguration = devicesWithoutPin > 0 || activeSupervisors == 0

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

                // Primeiro item da folha, antes de qualquer painel: emitir um
                // código é o gesto mais frequente do dia e era o único que
                // exigia navegar para outra área. O painel operacional continua
                // logo abaixo -- ele informa, este age.
                item("quick-code") {
                    AccessCodeQuickIssueCard(
                        colaboradores = state.colaboradores,
                        codigosAtivos = state.codigosAtivos,
                        carregando = state.codigosAtalhoCarregando || state.carregando,
                        erro = state.codigosAtalhoErro,
                        maxResultados = if (responsive.isCompact) 4 else 6,
                        onGerar = { pessoa -> viewModel.emitirCodigo(pessoa, null) },
                        onVerTodos = viewModel::abrirCodigos,
                        onTentarNovamente = viewModel::carregarAtalhoDeCodigos,
                    )
                }

                // Pendência de configuração aparece aqui em cima em vez de no fim
                // da lista, depois do histórico inteiro, onde ninguém rolava até
                // ver. O contrário -- "configuração em dia" -- não aparece: o
                // painel de equipe logo abaixo já diz "Tudo pronto para operar",
                // e dois avisos verdes na mesma tela só empurram a operação
                // para baixo.
                if (hasPendingConfiguration) {
                    item("configuration-status") {
                        val pendingReasons = buildList {
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
                    }
                }

                item("quick-actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        AdminHomeSectionHeader(
                            title = "Ir para",
                            subtitle = "As outras áreas da administração.",
                        )
                        if (responsive.isNarrow || responsive.usesLargeText) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                AdminHomeQuickAction(
                                    title = "Pessoas",
                                    icon = Icons.Default.Groups,
                                    onClick = viewModel::abrirColaboradores,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AdminHomeQuickAction(
                                    title = "Novo colaborador",
                                    icon = Icons.Default.PersonAdd,
                                    onClick = viewModel::abrirNovoColaborador,
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
                                icon = Icons.Default.Groups,
                                onClick = viewModel::abrirColaboradores,
                                modifier = Modifier.weight(1f),
                            )
                            AdminHomeQuickAction(
                                title = "Novo colaborador",
                                icon = Icons.Default.PersonAdd,
                                onClick = viewModel::abrirNovoColaborador,
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
                                emPausaAgora = emPausaAgora,
                                activeSupervisors = activeSupervisors,
                                codigosPendentes = codigosPendentes,
                                devicesWithoutPin = devicesWithoutPin,
                                onPeopleClick = viewModel::abrirColaboradores,
                                onCodesClick = viewModel::abrirCodigos,
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
                            emPausaAgora = emPausaAgora,
                            activeSupervisors = activeSupervisors,
                            codigosPendentes = codigosPendentes,
                            devicesWithoutPin = devicesWithoutPin,
                            onPeopleClick = viewModel::abrirColaboradores,
                            onCodesClick = viewModel::abrirCodigos,
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
                OperationalClockProvider {
                    visibleItems.forEach { item ->
                        OperationalPauseCompactCard(
                            item,
                            onClick = { onItemClick(item) },
                            onCloseManually = { onCloseManually(item) },
                        )
                    }
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
    emPausaAgora: Int,
    activeSupervisors: Int,
    codigosPendentes: Int,
    devicesWithoutPin: Int,
    onPeopleClick: () -> Unit,
    onCodesClick: () -> Unit,
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
                    emPausaAgora,
                    collaborators,
                    "Em pausa agora",
                    "$emPausaAgora de $collaborators colaboradores estão no café neste momento",
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
                    onClick = onPeopleClick,
                    modifier = Modifier.weight(1f),
                )
                AdminHomeMiniStat(
                    value = activeSupervisors.toString(),
                    label = "Supervisores",
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f),
                )
            }

            if (codigosPendentes > 0) {
                OperationalAlertCard(
                    "$codigosPendentes código(s) aguardando saída",
                    "Foram emitidos e ainda não foram apresentados no quiosque.",
                    "Abrir códigos",
                    onCodesClick,
                    PontoCafeTone.INFO,
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
                    "Cadastre uma conta de Supervisor para acompanhar a operação e emitir códigos.",
                    "Cadastrar Supervisor",
                    onNewSupervisor,
                    PontoCafeTone.INFO,
                )
            }
            if (devicesWithoutPin == 0 && activeSupervisors > 0) {
                PcStateBanner(
                    title = "Tudo pronto para operar",
                    supportingText = "Não há pendências de dispositivo ou supervisão.",
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
    onClick: (() -> Unit)? = null,
) {
    val semanticsModifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = "$label: $value"
    }
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                // Era o único tile de métrica que ainda escrevia o número cru:
                // os outros três já rolavam, e a diferença aparecia lado a lado.
                Text(animatedMetricValue(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (onClick == null) {
        Surface(
            modifier = semanticsModifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = body,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = semanticsModifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = body,
        )
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
