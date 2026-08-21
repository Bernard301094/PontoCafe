package com.pontocafe.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.AdminTestPauseStore
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.notifications.SupervisorAlertNotifier
import com.pontocafe.app.notifications.SupervisorNotificationAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIVE_PAUSES_REFRESH_MILLIS = 15_000L
private const val LAST_RETURN_REFRESH_MILLIS = 30_000L

@Composable
fun SupervisorOperationScreen(viewModel: SupervisorViewModel, onClose: () -> Unit) {
    val state = viewModel.state
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val testPause by AdminTestPauseStore.active.collectAsState()
    var pauseFilter by rememberSaveable { mutableStateOf(OperationalPauseFilter.TODOS) }
    var selectedPause by remember { mutableStateOf<OperationalPauseItem?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val sessionStore = remember(context, state.sessaoAdministrativa) {
        SecureAdminSessionStore(context.applicationContext, if (state.sessaoAdministrativa) "admin" else "supervisor")
    }
    val activeAccount = remember(sessionStore, state.sessaoAdministrativa) { sessionStore.activeAccount() }
    val accountProfileLabel = if (state.sessaoAdministrativa) "Administrador" else "Supervisor"
    val accountFallbackName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: accountProfileLabel
    val appContext = context.applicationContext
    var notificationAvailability by remember {
        mutableStateOf<SupervisorNotificationAvailability?>(null)
    }
    var notificationPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
        notificationAvailability = SupervisorAlertNotifier.availability(appContext)
    }

    LaunchedEffect(appContext) {
        SupervisorAlertNotifier.ensureChannel(appContext)
        notificationAvailability = SupervisorAlertNotifier.availability(appContext)
    }

    DisposableEffect(lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAvailability = SupervisorAlertNotifier.availability(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.atualizarPausasAoVivoSilencioso()
            viewModel.atualizarUltimoRetornoSilencioso()
            launch {
                while (true) {
                    delay(LIVE_PAUSES_REFRESH_MILLIS)
                    viewModel.atualizarPausasAoVivoSilencioso()
                }
            }
            launch {
                while (true) {
                    delay(LAST_RETURN_REFRESH_MILLIS)
                    viewModel.atualizarUltimoRetornoSilencioso()
                }
            }
        }
    }

    selectedPause?.let { item ->
        OperationalPauseDetailDialog(item = item, onDismiss = { selectedPause = null })
    }
    if (showAccountSheet) {
        PcAccountProfileSheet(
            account = activeAccount,
            fallbackName = accountFallbackName,
            profileLabel = accountProfileLabel,
            onDismiss = { showAccountSheet = false },
            onLogout = if (state.sessaoAdministrativa) {
                null
            } else {
                {
                    showAccountSheet = false
                    viewModel.sair()
                }
            },
        )
    }

    val alert = rememberSupervisorLiveActivityAlert(
        pausasAtivas = state.pausasAtivas,
        enabled = state.ultimaAtualizacaoAoVivoEmMillis != null,
        latestReturn = state.ultimoRetorno,
    )
    val notificationPrompt = when (notificationAvailability) {
        SupervisorNotificationAvailability.PERMISSION_REQUIRED -> Triple(
            "Ative os alertas do Supervisor",
            "O Android precisa da sua permissão para mostrar e vibrar nos novos eventos.",
            if (notificationPermissionDenied) "Abrir ajustes" else "Permitir notificações",
        )
        SupervisorNotificationAvailability.APP_DISABLED -> Triple(
            "Notificações desativadas no Android",
            "Os eventos continuam no painel, mas o sistema não pode avisar fora dele.",
            "Abrir ajustes",
        )
        SupervisorNotificationAvailability.CHANNEL_DISABLED -> Triple(
            "Canal de alertas desativado",
            "Ative o canal de saídas, retornos e excessos nos ajustes do sistema.",
            "Revisar canal",
        )
        SupervisorNotificationAvailability.VIBRATION_DISABLED -> Triple(
            "Vibração dos alertas desativada",
            "As notificações serão exibidas sem vibrar, conforme a configuração atual do canal.",
            "Revisar canal",
        )
        SupervisorNotificationAvailability.ENABLED,
        null -> null
    }
    val notificationAction = {
        val availability = notificationAvailability
        if (
            availability == SupervisorNotificationAvailability.PERMISSION_REQUIRED &&
            !notificationPermissionDenied &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SupervisorAlertNotifier.openSettings(
                context = appContext,
                channelSpecific = availability == SupervisorNotificationAvailability.CHANNEL_DISABLED ||
                    availability == SupervisorNotificationAvailability.VIBRATION_DISABLED,
            )
        }
    }
    val pendingFaces = remember(state.colaboradores) { state.colaboradores.filter { !it.rostoCadastrado }.sortedBy { it.nome.lowercase() } }
    val nowSnapshot = System.currentTimeMillis()
    val overdue = state.pausasAtivas.count { supervisorOperationSeconds(it, nowSnapshot) > it.limiteSegundos }
    val operationItems = remember(state.pausasAtivas, testPause, state.ultimaAtualizacaoAoVivoEmMillis) {
        buildOperationalPauseItems(state.pausasAtivas, testPause, System.currentTimeMillis())
    }
    val filteredItems = remember(operationItems, pauseFilter, state.ultimaAtualizacaoAoVivoEmMillis) {
        filterOperationalPauseItems(operationItems, pauseFilter, System.currentTimeMillis())
    }

    PontoCafeResponsivePage(maxContentWidth = 960.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(start = responsive.pagePadding, end = responsive.pagePadding, top = PontoCafeSpacing.md, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                item("header") {
                    PcAreaTopBar(
                        title = "Operação",
                        eyebrow = accountProfileLabel,
                        account = activeAccount,
                        fallbackName = accountFallbackName,
                        onProfileClick = { showAccountSheet = true },
                        onBackToPonto = onClose,
                    )
                }
                item("connection") { SupervisorConnectionBanner(state.conexaoAoVivoOk, state.ultimaAtualizacaoAoVivoEmMillis) }
                notificationPrompt?.let { (title, text, actionLabel) ->
                    item("notification-${notificationAvailability?.name}") {
                        OperationalAlertCard(
                            title = title,
                            text = text,
                            actionLabel = actionLabel,
                            onClick = notificationAction,
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }
                alert?.let { currentAlert -> item("activity-${currentAlert.id}") { SupervisorLiveActivityAlertBanner(currentAlert) } }
                item("attention") {
                    OperationalPauseOverview(
                        realPauses = state.pausasAtivas,
                        items = operationItems,
                        filter = pauseFilter,
                        onFilterChange = { pauseFilter = it },
                    )
                }
                item("metrics") {
                    if (responsive.usesVeryLargeText) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcMetricTile(state.pausasAtivas.size.toString(), "Em pausa", Icons.Default.Coffee, Modifier.fillMaxWidth())
                            PcMetricTile(overdue.toString(), "Acima do limite", Icons.Default.Timer, Modifier.fillMaxWidth(), attention = overdue > 0)
                            PcMetricTile(pendingFaces.size.toString(), "Rostos pendentes", Icons.Default.Face, Modifier.fillMaxWidth(), attention = pendingFaces.isNotEmpty())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcMetricTile(state.pausasAtivas.size.toString(), "Em pausa", Icons.Default.Coffee, Modifier.weight(1f))
                            PcMetricTile(overdue.toString(), "Acima do limite", Icons.Default.Timer, Modifier.weight(1f), attention = overdue > 0)
                            if (!responsive.isCompact) {
                                PcMetricTile(pendingFaces.size.toString(), "Rostos pendentes", Icons.Default.Face, Modifier.weight(1f), attention = pendingFaces.isNotEmpty())
                            }
                        }
                    }
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
                        OperationalPauseCompactCard(item = item, onClick = { selectedPause = item })
                    }
                }
                item("actions-title") { SectionTitle("Ações rápidas", "Tarefas mais usadas durante a operação.") }
                item("actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        PcActionTile("Autorizar exceção", "Liberar uma pausa fora da janela normal", Icons.Default.Coffee, viewModel::abrirAutorizacao)
                        PcActionTile("Histórico por data", "Escolher um dia e abrir cada registro", Icons.Default.CalendarMonth, { viewModel.abrirHistorico() })
                        PcActionTile("Pessoas e biometria", "Cadastrar, atualizar ou excluir colaboradores e rostos", Icons.Default.PersonSearch, viewModel::abrirColaboradores)
                    }
                }
                if (state.erro != null && !state.conexaoAoVivoOk) {
                    item("error") {
                        OperationalAlertCard("Exibindo os últimos dados disponíveis", state.erro ?: "A conexão será atualizada automaticamente.", "Tentar agora", viewModel::atualizarAoVivo, PontoCafeTone.WARNING)
                    }
                }
                if (pendingFaces.isNotEmpty()) {
                    item("pending") { PcActionTile("${pendingFaces.size} biometria(s) pendente(s)", "Abra Pessoas para concluir os registros faciais.", Icons.Default.Face, viewModel::abrirColaboradores) }
                }
                item("refresh") {
                    PcSecondaryButton(
                        text = "Atualizar agora",
                        onClick = viewModel::atualizarAoVivo,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Refresh,
                        loading = state.carregando,
                    )
                }
            }
            PcScrollToTopFab(listState, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md))
        }
    }
}

@Composable
private fun SupervisorConnectionBanner(connectionOk: Boolean, lastUpdateMillis: Long?) {
    var now by remember(lastUpdateMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastUpdateMillis, connectionOk) {
        if (lastUpdateMillis == null) return@LaunchedEffect
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val seconds = lastUpdateMillis?.let { ((now - it) / 1_000L).coerceAtLeast(0L) }
    PcStateBanner(
        title = when {
            !connectionOk -> "Conexão instável"
            seconds == null -> "Conectando"
            seconds < 10 -> "Dados sincronizados"
            else -> "Última atualização há ${seconds}s"
        },
        supportingText = if (connectionOk) "Atualização automática ativa." else "Os últimos dados válidos permanecem visíveis enquanto o sistema reconecta.",
        tone = if (connectionOk) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
    )
}

private fun supervisorOperationSeconds(pause: PausaSupervisor, nowMillis: Long): Int {
    val base = pause.tempoSegundos ?: pause.duracaoSegundos ?: 0
    if (pause.fimLocal != null || pause.clienteAtualizadoEmMillis <= 0L) return base
    val extra = ((nowMillis - pause.clienteAtualizadoEmMillis) / 1_000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong())
    return (base.toLong() + extra).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
