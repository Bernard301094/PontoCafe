@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AdminTestPauseStore
import com.pontocafe.app.data.CoffeeRuleV2
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.domain.PontoCafeRules
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private enum class ManagementRuleTimeTarget { START, END }

private data class CoffeeWindowState(
    val label: String,
    val supportingText: String,
    val tone: PontoCafeTone,
)

private data class ManagementAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun AdminManagementScreenV3(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
    onDevicesClick: () -> Unit,
    onSyncClick: () -> Unit,
    onKioskClick: () -> Unit,
    onClose: () -> Unit,
) {
    val reliability = reliabilityViewModel.state
    val context = LocalContext.current
    val adminSessionStore = remember(context) {
        SecureAdminSessionStore(context.applicationContext, "admin")
    }
    val activeAccount = remember(adminSessionStore) { adminSessionStore.activeAccount() }
    val adminName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: "Administrador"
    val testPause by AdminTestPauseStore.active.collectAsState()
    val durationSummary = remember(reliability.rules) {
        val configured = reliability.rules
            .map { PontoCafeRules.formatDuration(it.limiteSegundos) }
            .distinct()
        when (configured.size) {
            0 -> "—"
            1 -> configured.first()
            else -> "Variável"
        }
    }

    var showAccountSheet by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(testPause != null) }
    var showPurgeCacheConfirm by remember { mutableStateOf(false) }

    if (showPurgeCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeCacheConfirm = false },
            title = { Text("Limpar cache local?") },
            text = {
                PcDialogBody {
                    Text(
                        "As listas de pessoas, dispositivos, usuários e regras guardadas em memória neste " +
                            "aparelho serão descartadas. A próxima tela que precisar delas vai buscar " +
                            "direto do servidor. Sessão e fila de sincronização offline não são afetadas.",
                    )
                }
            },
            confirmButton = {
                PcPrimaryButton(
                    text = "Limpar cache",
                    onClick = {
                        viewModel.purgarCacheLocal()
                        showPurgeCacheConfirm = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showPurgeCacheConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    LaunchedEffect(Unit) {
        reliabilityViewModel.loadManagement()
    }

    if (showAccountSheet) {
        PcAccountProfileSheet(
            account = activeAccount,
            fallbackName = adminName,
            profileLabel = "Administrador",
            onDismiss = { showAccountSheet = false },
            onLogout = {
                showAccountSheet = false
                viewModel.logout()
            },
        )
    }

    PcHeroPage(
        heroContent = {
            PcHeroZoneTopBar(
                title = "Gestão",
                eyebrow = "Administrador",
                account = activeAccount,
                fallbackName = adminName,
                onProfileClick = { showAccountSheet = true },
                onBackToPonto = onClose,
            )
            Text(
                "Configuração operacional, segurança e saúde do sistema",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                PcHeroStat(
                    value = if (reliability.rules.isEmpty()) "—" else "${reliability.rules.count { it.ativo }}/${reliability.rules.size}",
                    label = "Períodos ativos",
                    modifier = Modifier.weight(1f),
                )
                PcHeroStat(
                    value = if (reliabilityViewModel.faceModelReady) "Pronta" else "Atenção",
                    label = "Biometria local",
                    modifier = Modifier.weight(1f),
                )
                PcHeroStat(value = durationSummary, label = "Tempo configurado", modifier = Modifier.weight(1f))
            }
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = 1180.dp) { responsive ->
        val operationActions = remember(onDevicesClick, onSyncClick, onKioskClick, viewModel) {
            listOf(
                ManagementAction(
                    title = "Dispositivos",
                    subtitle = "PIN, tokens e aparelhos autorizados",
                    icon = Icons.Default.Devices,
                    onClick = onDevicesClick,
                ),
                ManagementAction(
                    title = "Sincronização",
                    subtitle = "Offline, fila local e pendências",
                    icon = Icons.Default.Sync,
                    onClick = onSyncClick,
                ),
                ManagementAction(
                    title = "Autorizações",
                    subtitle = "Exceções de pausa fora do horário",
                    icon = Icons.Default.LockClock,
                    onClick = viewModel::abrirAutorizacao,
                ),
                ManagementAction(
                    title = "Modo terminal",
                    subtitle = "Quiosque, bloqueio e proteção da tela",
                    icon = Icons.Default.Security,
                    onClick = onKioskClick,
                ),
            )
        }
        val reliabilityActions = remember(reliabilityViewModel, viewModel) {
            listOf(
                ManagementAction(
                    title = "Biometria",
                    subtitle = "Modelo, precisão, calibração e retenção",
                    icon = Icons.Default.Fingerprint,
                    onClick = reliabilityViewModel::openBiometricDiagnostics,
                ),
                ManagementAction(
                    title = "Diagnóstico",
                    subtitle = "Servidor, banco de dados e configuração",
                    icon = Icons.Default.HealthAndSafety,
                    onClick = reliabilityViewModel::openSystemDiagnostics,
                ),
                ManagementAction(
                    title = "Auditoria",
                    subtitle = "Rastreabilidade das ações administrativas",
                    icon = Icons.Default.History,
                    onClick = viewModel::abrirAuditoria,
                ),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = responsive.pagePadding,
                end = responsive.pagePadding,
                top = PontoCafeSpacing.md,
                bottom = PontoCafeSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
        ) {
            item("feedback") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    AdminFeedback(viewModel)
                    ReliabilityFeedback(reliabilityViewModel)
                }
            }

            item("operation-title") {
                ManagementSectionHeader(
                    title = "Operação",
                    subtitle = "Acessos usados no dia a dia para manter o Ponto funcionando.",
                )
            }
            item("operation-grid") {
                ManagementActionGrid(
                    actions = operationActions,
                    responsive = responsive,
                )
            }

            item("reliability-title") {
                ManagementSectionHeader(
                    title = "Confiabilidade e controle",
                    subtitle = "Ferramentas técnicas e de rastreabilidade ficam agrupadas aqui.",
                )
            }
            item("reliability-grid") {
                ManagementActionGrid(
                    actions = reliabilityActions,
                    responsive = responsive,
                )
            }

            item("purge-cache") {
                PcSecondaryButton(
                    text = "Limpar cache local",
                    icon = Icons.Default.Sync,
                    onClick = { showPurgeCacheConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item("rules-title") {
                ManagementRulesHeader(reliability.rules)
            }

            if (reliability.rules.isEmpty() && reliability.loading) {
                item("rules-loading") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(PontoCafeSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                        ) {
                            Text("Carregando regras…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Buscando os períodos ativos e a duração configurada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (reliability.rules.isEmpty()) {
                item("rules-empty") {
                    PcEmptyState(
                        title = "Nenhuma regra disponível",
                        supportingText = "Não há períodos de café retornados pelo servidor.",
                        icon = Icons.Default.Timer,
                    )
                }
            } else {
                item("rules-grid") {
                    CoffeeRulesResponsiveLayout(
                        rules = reliability.rules,
                        responsive = responsive,
                        viewModel = reliabilityViewModel,
                    )
                }
            }

            // As outras seções (Operação, Confiabilidade, Regras) têm um
            // ManagementSectionHeader próprio antes do conteúdo -- esta não
            // tinha, o que fazia "Teste operacional" parecer um apêndice solto
            // em vez de uma seção da mesma hierarquia.
            item("test-title") {
                ManagementSectionHeader(
                    title = "Teste operacional",
                    subtitle = "Simule o painel do Supervisor sem gerar registros reais.",
                )
            }

            item("advanced") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAdvanced = !showAdvanced },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(0.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(PontoCafeSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.padding(PontoCafeSpacing.xs).size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (showAdvanced) "Ocultar ferramenta" else "Abrir ferramenta de teste",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Painel visual de INÍCIO/RETORNO simulado.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showAdvanced) "Recolher" else "Expandir",
                                )
                            }
                        }

                        AnimatedVisibility(showAdvanced) {
                            Column(
                                modifier = Modifier.padding(
                                    start = PontoCafeSpacing.md,
                                    end = PontoCafeSpacing.md,
                                    bottom = PontoCafeSpacing.md,
                                ),
                            ) {
                                HorizontalDivider(modifier = Modifier.padding(bottom = PontoCafeSpacing.md))
                                PcAdminVisualTestTool(
                                    testPause = testPause,
                                    adminName = adminName,
                                    onStart = { AdminTestPauseStore.start(adminName) },
                                    onStop = AdminTestPauseStore::stop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}


@Composable
private fun ManagementSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManagementActionGrid(
    actions: List<ManagementAction>,
    responsive: PontoCafeResponsiveInfo,
) {
    val columns = when {
        responsive.usesVeryLargeText -> 1
        responsive.usesLargeText && responsive.isExpanded -> 2
        else -> when (responsive.windowSizeClass) {
            PontoCafeWindowSizeClass.COMPACT -> 1
            PontoCafeWindowSizeClass.MEDIUM -> 2
            PontoCafeWindowSizeClass.EXPANDED -> 3
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
        actions.chunked(columns).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                rowActions.forEach { action ->
                    ManagementActionCard(
                        action = action,
                        compact = responsive.isCompact,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowActions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ManagementActionCard(
    action: ManagementAction,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressScale = rememberPcPressScale(interactionSource)
    Card(
        modifier = modifier.pcPressScale(pressScale),
        onClick = action.onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) PontoCafeSpacing.sm else PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(PontoCafeSpacing.xs).size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
                Text(
                    action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Abrir ${action.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManagementRulesHeader(rules: List<CoffeeRuleV2>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
            Text(
                "Horários e tempo de café",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Horários definem quando a pausa pode começar; tempos de café definem quanto ela pode durar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${rules.count { it.ativo }}/${rules.size} ativos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CoffeeRulesResponsiveLayout(
    rules: List<CoffeeRuleV2>,
    responsive: PontoCafeResponsiveInfo,
    viewModel: AdminReliabilityViewModel,
) {
    val columns = if (responsive.isExpanded && responsive.supportsTwoColumns) 2 else 1
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
        rules.chunked(columns).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                chunk.forEach { rule ->
                    CoffeeRuleEditorV3(
                        viewModel = viewModel,
                        rule = rule,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - chunk.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CoffeeRuleEditorV3(
    viewModel: AdminReliabilityViewModel,
    rule: CoffeeRuleV2,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val initialDuration = PontoCafeRules.splitDuration(rule.limiteSegundos)
    var start by remember(rule) { mutableStateOf(rule.inicio) }
    var end by remember(rule) { mutableStateOf(rule.fim) }
    var minutes by remember(rule) { mutableStateOf(initialDuration.first.toString()) }
    var seconds by remember(rule) { mutableStateOf(initialDuration.second.toString()) }
    var customDuration by remember(rule) {
        mutableStateOf(initialDuration.second != 0 || initialDuration.first !in setOf(10, 12, 15))
    }
    var active by remember(rule) { mutableStateOf(rule.ativo) }
    var localError by remember(rule) { mutableStateOf<String?>(null) }
    var editingTime by remember(rule) { mutableStateOf<ManagementRuleTimeTarget?>(null) }
    var editing by remember(rule) { mutableStateOf(false) }

    val currentMinutes = minutes.toIntOrNull()
    val currentSeconds = seconds.toIntOrNull()
    val dirty = start != rule.inicio ||
        end != rule.fim ||
        active != rule.ativo ||
        currentMinutes != initialDuration.first ||
        currentSeconds != initialDuration.second
    val currentDurationSeconds = if (currentMinutes != null && currentSeconds != null) {
        currentMinutes * 60 + currentSeconds
    } else {
        rule.limiteSegundos
    }
    val windowState = coffeeWindowState(active = active, start = start, end = end)

    fun reset() {
        start = rule.inicio
        end = rule.fim
        minutes = initialDuration.first.toString()
        seconds = initialDuration.second.toString()
        customDuration = initialDuration.second != 0 || initialDuration.first !in setOf(10, 12, 15)
        active = rule.ativo
        localError = null
        editing = false
    }

    fun save() {
        val mins = minutes.toIntOrNull()
        val secs = seconds.toIntOrNull()
        localError = when {
            !start.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário inicial válido."
            !end.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário final válido."
            start >= end -> "O horário final deve ser posterior ao inicial."
            mins == null || secs == null -> "Informe minutos e segundos."
            secs !in 0..59 -> "Os segundos devem ficar entre 0 e 59."
            else -> runCatching {
                PontoCafeRules.durationSeconds(mins, secs)
            }.exceptionOrNull()?.message
        }

        if (localError == null && mins != null && secs != null) {
            focusManager.clearFocus()
            viewModel.saveRule(
                rule.periodo,
                start,
                end,
                PontoCafeRules.durationSeconds(mins, secs),
                active,
            )
        }
    }

    editingTime?.let { target ->
        val source = if (target == ManagementRuleTimeTarget.START) start else end
        val hour = source.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = source.substringAfter(":", "00").toIntOrNull()?.coerceIn(0, 59) ?: 0
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingTime = null },
            title = {
                Text(if (target == ManagementRuleTimeTarget.START) "Horário inicial" else "Horário final")
            },
            text = {
                PcDialogBody {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TimeInput(state = pickerState)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                        if (target == ManagementRuleTimeTarget.START) start = value else end = value
                        editingTime = null
                        localError = null
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { editingTime = null }) { Text("Cancelar") }
            },
        )
    }

    Card(
        modifier = modifier.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (dirty) MaterialTheme.colorScheme.primary.copy(alpha = .48f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.padding(PontoCafeSpacing.xs).size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
                    Text(
                        if (rule.periodo == "MANHA") "Período da manhã" else if (rule.periodo == "TARDE") "Período da tarde" else rule.periodo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Janela de início e limite de duração",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                item {
                    StatusPill(windowState.label, windowState.tone)
                }
                if (dirty) item { StatusPill("Alterações não salvas", PontoCafeTone.INFO) }
            }

            CoffeeRuleSummaryGrid(
                start = start,
                end = end,
                durationSeconds = currentDurationSeconds,
                windowState = windowState,
            )

            PcTonalButton(
                text = if (editing) "Fechar edição" else "Editar período",
                icon = if (editing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                onClick = { editing = !editing },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.state.loading,
            )

            AnimatedVisibility(editing) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md)) {
                    HorizontalDivider()

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(PontoCafeSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Período ativo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (active) "Aceita inícios dentro desta janela." else "Bloqueia novos inícios neste período.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = active,
                                onCheckedChange = {
                                    active = it
                                    localError = null
                                },
                                enabled = !viewModel.state.loading,
                                modifier = Modifier.semantics {
                                    contentDescription = "Ativar período ${rule.periodo.lowercase()}"
                                },
                            )
                        }
                    }

                    SectionTitle(
                        "Horários",
                        "Defina quando este período começa e termina.",
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stack = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.3f
                        if (stack) {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                RuleTimeSelectorV3(
                                    label = "Início da janela",
                                    value = start,
                                    onClick = { editingTime = ManagementRuleTimeTarget.START },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                RuleTimeSelectorV3(
                                    label = "Fim da janela",
                                    value = end,
                                    onClick = { editingTime = ManagementRuleTimeTarget.END },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                RuleTimeSelectorV3(
                                    label = "Início da janela",
                                    value = start,
                                    onClick = { editingTime = ManagementRuleTimeTarget.START },
                                    modifier = Modifier.weight(1f),
                                )
                                RuleTimeSelectorV3(
                                    label = "Fim da janela",
                                    value = end,
                                    onClick = { editingTime = ManagementRuleTimeTarget.END },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    SectionTitle(
                        "Tempo de café",
                        "Limite máximo entre o início e o retorno desta pausa.",
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        items(listOf(10, 12, 15), key = { "duration-$it" }) { preset ->
                            FilterChip(
                                selected = !customDuration && currentMinutes == preset && currentSeconds == 0,
                                onClick = {
                                    minutes = preset.toString()
                                    seconds = "0"
                                    customDuration = false
                                    localError = null
                                },
                                label = { Text("$preset min") },
                            )
                        }
                    }
                    if (!customDuration) {
                        TextButton(onClick = { customDuration = true }) {
                            Text("Definir outro tempo")
                        }
                    }

                    AnimatedVisibility(customDuration) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val stack = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.3f
                            val minutesField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                OutlinedTextField(
                                    value = minutes,
                                    onValueChange = {
                                        minutes = it.filter(Char::isDigit).take(3)
                                        localError = null
                                    },
                                    modifier = fieldModifier,
                                    label = { Text("Minutos") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    ),
                                    singleLine = true,
                                )
                            }
                            val secondsField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                OutlinedTextField(
                                    value = seconds,
                                    onValueChange = {
                                        seconds = it.filter(Char::isDigit).take(2)
                                        localError = null
                                    },
                                    modifier = fieldModifier,
                                    label = { Text("Segundos") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { save() }),
                                    singleLine = true,
                                )
                            }
                            if (stack) {
                                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                    minutesField(Modifier.fillMaxWidth())
                                    secondsField(Modifier.fillMaxWidth())
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                    minutesField(Modifier.weight(1f))
                                    secondsField(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    localError?.let { message ->
                        PcStateBanner(
                            title = "Revise esta regra",
                            supportingText = message,
                            tone = PontoCafeTone.DANGER,
                        )
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stack = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.3f
                        if (stack) {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                PcPrimaryButton(
                                    text = "Salvar alterações",
                                    onClick = ::save,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = dirty,
                                    loading = viewModel.state.loading,
                                )
                                PcSecondaryButton(
                                    text = "Descartar",
                                    onClick = ::reset,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = dirty && !viewModel.state.loading,
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                PcSecondaryButton(
                                    text = "Descartar",
                                    onClick = ::reset,
                                    modifier = Modifier.weight(1f),
                                    enabled = dirty && !viewModel.state.loading,
                                )
                                PcPrimaryButton(
                                    text = "Salvar alterações",
                                    onClick = ::save,
                                    modifier = Modifier.weight(1f),
                                    enabled = dirty,
                                    loading = viewModel.state.loading,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoffeeRuleSummaryGrid(
    start: String,
    end: String,
    durationSeconds: Int,
    windowState: CoffeeWindowState,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = maxWidth < 520.dp || LocalDensity.current.fontScale >= 1.3f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                CoffeeRuleSummaryCard(
                    title = "Horários",
                    value = "$start – $end",
                    supportingText = windowState.supportingText,
                    status = windowState.label,
                    tone = windowState.tone,
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.fillMaxWidth(),
                )
                CoffeeRuleSummaryCard(
                    title = "Tempo de café",
                    value = PontoCafeRules.formatDuration(durationSeconds),
                    supportingText = "Limite máximo entre início e retorno",
                    status = "Limite atual",
                    tone = PontoCafeTone.INFO,
                    icon = Icons.Default.Timer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                CoffeeRuleSummaryCard(
                    title = "Horários",
                    value = "$start – $end",
                    supportingText = windowState.supportingText,
                    status = windowState.label,
                    tone = windowState.tone,
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f),
                )
                CoffeeRuleSummaryCard(
                    title = "Tempo de café",
                    value = PontoCafeRules.formatDuration(durationSeconds),
                    supportingText = "Limite máximo entre início e retorno",
                    status = "Limite atual",
                    tone = PontoCafeTone.INFO,
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CoffeeRuleSummaryCard(
    title: String,
    value: String,
    supportingText: String,
    status: String,
    tone: PontoCafeTone,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(status, tone)
        }
    }
}

private fun coffeeWindowState(
    active: Boolean,
    start: String,
    end: String,
    now: LocalTime = ZonedDateTime.now(ZoneId.of("America/Fortaleza")).toLocalTime(),
): CoffeeWindowState {
    if (!active) {
        return CoffeeWindowState(
            label = "Inativo",
            supportingText = "Esta janela não aceita novos inícios",
            tone = PontoCafeTone.NEUTRAL,
        )
    }
    val startTime = runCatching { LocalTime.parse(start) }.getOrNull()
    val endTime = runCatching { LocalTime.parse(end) }.getOrNull()
    if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
        return CoffeeWindowState(
            label = "Revisar horário",
            supportingText = "O início deve ser anterior ao fim",
            tone = PontoCafeTone.DANGER,
        )
    }
    return when {
        !now.isBefore(startTime) && now.isBefore(endTime) -> CoffeeWindowState(
            label = "Em andamento",
            supportingText = "A hora atual está dentro desta janela",
            tone = PontoCafeTone.SUCCESS,
        )
        now.isBefore(startTime) -> CoffeeWindowState(
            label = "Próximo hoje",
            supportingText = "Começa às $start",
            tone = PontoCafeTone.INFO,
        )
        else -> CoffeeWindowState(
            label = "Encerrado hoje",
            supportingText = "Terminou às $end",
            tone = PontoCafeTone.NEUTRAL,
        )
    }
}

@Composable
private fun RuleTimeSelectorV3(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
