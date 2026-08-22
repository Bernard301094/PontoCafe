package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AdminDevice
import com.pontocafe.app.data.SecureAdminDeviceActivationTokenStore
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class DeviceDangerAction { DEACTIVATE, DELETE, ROTATE }

private data class DeviceFact(
    val label: String,
    val value: String,
    val supportingText: String,
    val tone: PontoCafeTone = PontoCafeTone.NEUTRAL,
)

@Composable
fun AdminDevicesScreenV2(
    viewModel: AdminDeviceViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state = viewModel.state
    val activationTokenStore = remember(context) {
        SecureAdminDeviceActivationTokenStore(context.applicationContext)
    }
    var activationTokens by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var configurePin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun createDevice() {
        val pinValid = !configurePin || (pin.length in 4..12 && pin == confirmPin)
        if (state.carregando || name.trim().length < 2 || !pinValid) return
        focusManager.clearFocus()
        viewModel.criarDispositivo(name, pin.takeIf { configurePin })
    }

    // Somente tokens de ativação ainda pendentes ficam recuperáveis no aparelho
    // do Administrador. Assim que o Ponto ativa ou é bloqueado, o segredo local é
    // apagado. O bearer de sessão do Ponto nunca é copiado para esta área.
    LaunchedEffect(state.dispositivos) {
        val pendingIds = state.dispositivos
            .asSequence()
            .filter { it.ativo && it.statusAtivacao != "ATIVADO" }
            .map { it.id }
            .toSet()
        activationTokenStore.reconcile(pendingIds)
        activationTokens = pendingIds.mapNotNull { deviceId ->
            activationTokenStore.read(deviceId)?.let { deviceId to it }
        }.toMap()
    }

    LaunchedEffect(state.tokenGerado, state.tokenDeviceId) {
        val token = state.tokenGerado ?: return@LaunchedEffect
        val deviceId = state.tokenDeviceId ?: return@LaunchedEffect
        activationTokenStore.save(deviceId, token)
        activationTokens = activationTokens + (deviceId to token)
    }

    LaunchedEffect(state.tokenGerado, state.tokenRotacionado) {
        if (state.tokenGerado != null && !state.tokenRotacionado) {
            name = ""
            configurePin = false
            pin = ""
            confirmPin = ""
            showCreate = false
        }
    }

    state.tokenGerado?.let { token ->
        val clipboard = LocalClipboardManager.current
        var tokenCopied by remember(token) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(if (state.tokenRotacionado) "Novo token de ativação" else "Dispositivo criado")
            },
            text = {
                PcDialogBody {
                    Text(state.tokenDeviceName ?: "Dispositivo", style = MaterialTheme.typography.titleSmall)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                token,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(PontoCafeSpacing.md),
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    PcStateBanner(
                        title = "Disponível também no cartão",
                        supportingText = "Enquanto este aparelho aguardar ativação, este token ficará cifrado neste dispositivo de Administração e poderá ser copiado novamente pelo cartão. Depois da ativação, a credencial do Ponto não é recuperável por segurança.",
                        tone = PontoCafeTone.INFO,
                    )
                    if (tokenCopied) {
                        Text(
                            "Token copiado. Insira-o no novo aparelho para concluir a ativação.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalPontoCafeSemanticColors.current.success,
                        )
                    }
                }
            },
            confirmButton = {
                PcPrimaryButton(
                    text = if (tokenCopied) "Copiar novamente" else "Copiar token",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        clipboard.setText(AnnotatedString(token))
                        tokenCopied = true
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = viewModel::limparToken) {
                    Text("Fechar")
                }
            },
        )
    }

    PontoCafeResponsivePage(maxContentWidth = 920.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.lg,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item(key = "header") {
                    PontoCafeScreenHeader(
                        title = "Dispositivos",
                        onBack = onBack,
                        backLabel = "Gestão",
                        eyebrow = "Segurança do Ponto",
                    )
                }

                item(key = "health") { DeviceHealthOverviewV2(viewModel) }

                state.mensagem?.let { message ->
                    item(key = "message") {
                        PcStateBanner(
                            title = "Alteração concluída",
                            supportingText = message,
                            tone = PontoCafeTone.SUCCESS,
                        )
                    }
                }

                state.erro?.let { error ->
                    item(key = "error") {
                        PcStateBanner(
                            title = "Não foi possível concluir",
                            supportingText = error,
                            tone = PontoCafeTone.DANGER,
                        )
                    }
                }

                item(key = "actions-title") {
                    SectionTitle(
                        "Ações",
                        "Cadastre um terminal ou atualize imediatamente o estado dos aparelhos autorizados.",
                    )
                }

                item(key = "actions") {
                    if (responsive.isCompact || responsive.usesLargeText) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcPrimaryButton(
                                text = if (showCreate) "Fechar cadastro" else "Novo dispositivo",
                                icon = Icons.Default.Add,
                                onClick = { showCreate = !showCreate },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.carregando,
                            )
                            PcSecondaryButton(
                                text = "Atualizar",
                                icon = Icons.Default.Refresh,
                                onClick = viewModel::carregar,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.carregando,
                                loading = state.carregando,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcPrimaryButton(
                                text = if (showCreate) "Fechar cadastro" else "Novo dispositivo",
                                icon = Icons.Default.Add,
                                onClick = { showCreate = !showCreate },
                                modifier = Modifier.weight(1f),
                                enabled = !state.carregando,
                            )
                            PcSecondaryButton(
                                text = "Atualizar",
                                icon = Icons.Default.Refresh,
                                onClick = viewModel::carregar,
                                modifier = Modifier.weight(1f),
                                enabled = !state.carregando,
                                loading = state.carregando,
                            )
                        }
                    }
                }

                if (showCreate) {
                    item(key = "create") {
                        PcSectionSurface {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                SectionTitle(
                                    "Cadastrar aparelho",
                                    "Use um nome fácil de identificar. O PIN é opcional e pode ser definido agora ou depois.",
                                )
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it.take(120) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome do dispositivo") },
                                    placeholder = { Text("Ex.: Tablet · Linha 01") },
                                    singleLine = true,
                                    enabled = !state.carregando,
                                    shape = MaterialTheme.shapes.large,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    ),
                                )

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(PontoCafeSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Text(
                                                "Configurar PIN agora",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                if (configurePin) {
                                                    "O PIN será exclusivo deste aparelho."
                                                } else {
                                                    "Sem PIN, sair do modo Ponto exigirá login autenticado de Administrador ou Supervisor."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = configurePin,
                                            onCheckedChange = { enabled ->
                                                configurePin = enabled
                                                if (!enabled) {
                                                    pin = ""
                                                    confirmPin = ""
                                                }
                                            },
                                            enabled = !state.carregando,
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = configurePin) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                                    ) {
                                        SecurePinFieldV2(
                                            label = "PIN de desbloqueio",
                                            value = pin,
                                            enabled = !state.carregando,
                                            imeAction = ImeAction.Next,
                                            keyboardActions = KeyboardActions(
                                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                            ),
                                            onValueChange = { pin = it },
                                        )
                                        SecurePinFieldV2(
                                            label = "Confirmar PIN",
                                            value = confirmPin,
                                            enabled = !state.carregando,
                                            imeAction = ImeAction.Done,
                                            keyboardActions = KeyboardActions(onDone = { createDevice() }),
                                            isError = pin.isNotBlank() && confirmPin.isNotBlank() && pin != confirmPin,
                                            onValueChange = { confirmPin = it },
                                        )
                                        if (pin.isNotBlank() && confirmPin.isNotBlank() && pin != confirmPin) {
                                            Text(
                                                "Os PINs não coincidem.",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }

                                PcPrimaryButton(
                                    text = "Cadastrar e gerar token",
                                    onClick = ::createDevice,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = name.trim().length >= 2 &&
                                        (!configurePin || (pin.length in 4..12 && pin == confirmPin)),
                                    loading = state.carregando,
                                )
                            }
                        }
                    }
                }

                item(key = "devices-title") {
                    SectionTitle(
                        "Aparelhos cadastrados",
                        "${state.dispositivos.count { it.ativo }} com acesso · ${state.dispositivos.count { !it.ativo }} bloqueado(s). As ações principais ficam disponíveis diretamente em cada cartão.",
                    )
                }

                if (state.dispositivos.isEmpty() && !state.carregando) {
                    item(key = "empty") {
                        PcEmptyState(
                            title = "Nenhum dispositivo cadastrado",
                            supportingText = "Cadastre o primeiro terminal para gerar o token de ativação.",
                            icon = Icons.Default.Devices,
                        )
                    }
                } else {
                    items(state.dispositivos, key = { "device-v2-${it.id}" }) { device ->
                        DeviceCardV2(
                            viewModel = viewModel,
                            device = device,
                            activationToken = activationTokens[device.id],
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
private fun DeviceHealthOverviewV2(viewModel: AdminDeviceViewModel) {
    val state = viewModel.state
    val healthy = state.health?.let { it.status == "ok" && it.banco == "ok" } == true

    PcHeroCard(
        title = if (healthy) "Dispositivos protegidos" else "Verifique a conexão do sistema",
        supportingText = buildString {
            append("App ${BuildConfig.VERSION_NAME} · servidor ${if (healthy) "online" else "sem confirmação"}")
            state.appStatus?.let { append(" · versão atual ${it.latestAndroidVersion}") }
        },
        icon = if (healthy) Icons.Default.Security else Icons.Default.Devices,
        tone = if (healthy) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
    )
}

@Composable
private fun DeviceCardV2(
    viewModel: AdminDeviceViewModel,
    device: AdminDevice,
    activationToken: String?,
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember(device.id) { mutableStateOf(false) }
    var newName by remember(device.id, device.nome) { mutableStateOf(device.nome) }
    var newPin by remember(device.id) { mutableStateOf("") }
    var confirmPin by remember(device.id) { mutableStateOf("") }
    var dangerAction by remember(device.id) { mutableStateOf<DeviceDangerAction?>(null) }
    val telemetryRecent = isDeviceTimeRecent(device.telemetriaEm, maxAgeHours = 36)
    val updateAvailable = isVersionOlder(
        installed = device.appVersion,
        latest = viewModel.state.appStatus?.latestAndroidVersion,
    )
    val activationLabel = when (device.statusAtivacao) {
        "ATIVADO" -> "Ativado"
        "INATIVO" -> "Bloqueado"
        else -> "Aguardando ativação"
    }
    val statusLabel = when {
        !device.ativo -> "Bloqueado"
        device.alertaSaude -> "Requer atenção"
        device.statusAtivacao != "ATIVADO" -> "Aguardando ativação"
        updateAvailable -> "Atualização disponível"
        !telemetryRecent -> "Sem telemetria recente"
        else -> "Operacional"
    }
    val statusTone = when {
        !device.ativo -> PontoCafeTone.NEUTRAL
        device.alertaSaude -> PontoCafeTone.DANGER
        device.statusAtivacao != "ATIVADO" || updateAvailable || !telemetryRecent -> PontoCafeTone.WARNING
        else -> PontoCafeTone.SUCCESS
    }
    val deviceFacts = listOf(
        DeviceFact(
            label = "Ativação",
            value = activationLabel,
            supportingText = when (device.statusAtivacao) {
                "ATIVADO" -> "Concluída em ${formatDeviceTime(device.ativadoEm)}"
                "INATIVO" -> "Acesso ao Ponto bloqueado pelo Administrador"
                else -> "Use o token mostrado abaixo no aparelho"
            },
            tone = when (device.statusAtivacao) {
                "ATIVADO" -> PontoCafeTone.SUCCESS
                "INATIVO" -> PontoCafeTone.NEUTRAL
                else -> PontoCafeTone.WARNING
            },
        ),
        DeviceFact(
            label = "Aplicativo",
            value = device.appVersion?.let { "Versão $it" } ?: "Não informada",
            supportingText = if (updateAvailable) {
                "Versão mais recente: ${viewModel.state.appStatus?.latestAndroidVersion}"
            } else {
                listOfNotNull(device.deviceModel, device.androidVersion?.let { "Android $it" })
                    .joinToString(" · ")
                    .ifBlank { "Modelo e Android ainda não informados" }
            },
            tone = if (updateAvailable) PontoCafeTone.WARNING else PontoCafeTone.INFO,
        ),
        DeviceFact(
            label = "Última atividade",
            value = formatDeviceTime(device.ultimoAcessoEm),
            supportingText = if (telemetryRecent) "Telemetria recebida recentemente" else "Sem telemetria nas últimas 36 h",
            tone = if (telemetryRecent) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
        ),
        DeviceFact(
            label = "Saúde e segurança",
            value = when {
                device.alertaSaude -> "Verificar aparelho"
                device.pinConfigurado -> "PIN configurado"
                else -> "Login obrigatório"
            },
            supportingText = buildString {
                append(
                    if (device.pinConfigurado) {
                        "Saída do modo Ponto protegida por PIN"
                    } else {
                        "Sem PIN: saída do modo Ponto exige login autenticado"
                    },
                )
                if (device.crashCount > 0 || device.stallCount > 0) {
                    append(" · ${device.crashCount} falha(s) · ${device.stallCount} travamento(s)")
                }
            },
            tone = if (device.alertaSaude) PontoCafeTone.WARNING else if (device.pinConfigurado) PontoCafeTone.SUCCESS else PontoCafeTone.INFO,
        ),
    )

    fun savePin() {
        if (viewModel.state.carregando || newPin.length !in 4..12 || newPin != confirmPin) return
        focusManager.clearFocus()
        viewModel.alterarPin(device, newPin)
    }

    LaunchedEffect(viewModel.state.pinAtualizadoDeviceId) {
        if (viewModel.state.pinAtualizadoDeviceId == device.id) {
            newPin = ""
            confirmPin = ""
        }
    }

    dangerAction?.let { action ->
        val title = when (action) {
            DeviceDangerAction.DEACTIVATE -> "Bloquear acesso deste dispositivo?"
            DeviceDangerAction.DELETE -> "Excluir dispositivo da gestão?"
            DeviceDangerAction.ROTATE -> if (device.ativo) "Gerar um novo token?" else "Reativar com um novo token?"
        }
        val text = when (action) {
            DeviceDangerAction.DEACTIVATE ->
                "O aparelho deixará de registrar pontos imediatamente. Para voltar a usá-lo, será necessário gerar e informar um novo token de ativação."
            DeviceDangerAction.DELETE ->
                "O dispositivo será removido desta lista. Se ele já tiver registros de ponto, o histórico será preservado para auditoria em vez de ser destruído."
            DeviceDangerAction.ROTATE ->
                "A credencial anterior será revogada e um novo token de 10 caracteres será gerado para este dispositivo."
        }
        AlertDialog(
            onDismissRequest = { dangerAction = null },
            title = { Text(title) },
            text = {
                PcDialogBody {
                    Text(text)
                    PcStateBanner(
                        title = "Ação administrativa",
                        supportingText = "A alteração será registrada na auditoria do sistema.",
                        tone = PontoCafeTone.WARNING,
                    )
                }
            },
            confirmButton = {
                PcDangerButton(
                    text = when (action) {
                        DeviceDangerAction.DEACTIVATE -> "Bloquear acesso"
                        DeviceDangerAction.DELETE -> "Excluir dispositivo"
                        DeviceDangerAction.ROTATE -> if (device.ativo) "Revogar e gerar novo" else "Reativar e gerar token"
                    },
                    onClick = {
                        when (action) {
                            DeviceDangerAction.DEACTIVATE -> viewModel.desativar(device)
                            DeviceDangerAction.DELETE -> viewModel.excluirPermanentemente(device)
                            DeviceDangerAction.ROTATE -> viewModel.rotacionarToken(device)
                        }
                        dangerAction = null
                    },
                    loading = viewModel.state.carregando,
                )
            },
            dismissButton = {
                TextButton(onClick = { dangerAction = null }) { Text("Cancelar") }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = statusLabel },
        colors = CardDefaults.cardColors(
            containerColor = if (device.alertaSaude) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            1.dp,
            if (device.alertaSaude) MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        device.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "ID ${device.id.take(8)} · ${if (device.ativo) "acesso permitido" else "acesso bloqueado"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(statusLabel, statusTone)
            }

            DeviceFactsPanel(deviceFacts)
            DeviceTokenPanel(device = device, activationToken = activationToken)

            SectionTitle(
                "Gerenciar dispositivo",
                "Editar, bloquear ou excluir ficam disponíveis diretamente neste cartão.",
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stack = maxWidth < 620.dp || LocalDensity.current.fontScale >= 1.3f
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        PcTonalButton(
                            text = if (expanded) "Fechar edição" else "Editar dispositivo",
                            icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.state.carregando,
                        )
                        PcSecondaryButton(
                            text = if (device.ativo) "Bloquear acesso" else "Reativar com novo token",
                            onClick = {
                                dangerAction = if (device.ativo) DeviceDangerAction.DEACTIVATE else DeviceDangerAction.ROTATE
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.state.carregando,
                            contentColor = if (device.ativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        PcSecondaryButton(
                            text = "Excluir dispositivo",
                            onClick = { dangerAction = DeviceDangerAction.DELETE },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.state.carregando,
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        PcTonalButton(
                            text = if (expanded) "Fechar edição" else "Editar",
                            icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            onClick = { expanded = !expanded },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.state.carregando,
                        )
                        PcSecondaryButton(
                            text = if (device.ativo) "Bloquear" else "Reativar",
                            onClick = {
                                dangerAction = if (device.ativo) DeviceDangerAction.DEACTIVATE else DeviceDangerAction.ROTATE
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.state.carregando,
                            contentColor = if (device.ativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        PcSecondaryButton(
                            text = "Excluir",
                            onClick = { dangerAction = DeviceDangerAction.DELETE },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.state.carregando,
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                ) {
                    SectionTitle(
                        "Identificação",
                        "O nome pode ser alterado sem modificar o ID do dispositivo.",
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome do aparelho") },
                        singleLine = true,
                        enabled = !viewModel.state.carregando,
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (newName.trim().length >= 2 && newName.trim() != device.nome) {
                                viewModel.renomear(device, newName)
                            }
                        }),
                    )
                    PcSecondaryButton(
                        text = "Salvar nome",
                        onClick = { viewModel.renomear(device, newName) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.state.carregando && newName.trim().length >= 2 && newName.trim() != device.nome,
                    )

                    SectionTitle(
                        "PIN de desbloqueio",
                        if (device.ativo) {
                            "Defina um novo PIN para este terminal."
                        } else {
                            "Você pode preparar um novo PIN mesmo com o acesso bloqueado."
                        },
                    )
                    SecurePinFieldV2(
                        label = "Novo PIN",
                        value = newPin,
                        enabled = !viewModel.state.carregando,
                        imeAction = ImeAction.Next,
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                        onValueChange = { newPin = it },
                    )
                    SecurePinFieldV2(
                        label = "Confirmar novo PIN",
                        value = confirmPin,
                        enabled = !viewModel.state.carregando,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { savePin() }),
                        isError = newPin.isNotBlank() && confirmPin.isNotBlank() && newPin != confirmPin,
                        onValueChange = { confirmPin = it },
                    )
                    if (newPin.isNotBlank() && confirmPin.isNotBlank() && newPin != confirmPin) {
                        Text(
                            "Os PINs não coincidem.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    PcSecondaryButton(
                        text = if (device.pinConfigurado) "Alterar PIN" else "Definir PIN",
                        onClick = ::savePin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.state.carregando && newPin.length in 4..12 && newPin == confirmPin,
                    )

                    SectionTitle(
                        "Token e ativação",
                        "Gerar um novo token revoga a credencial anterior e exige nova ativação no aparelho.",
                    )
                    PcSecondaryButton(
                        text = "Gerar novo token",
                        onClick = { dangerAction = DeviceDangerAction.ROTATE },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.state.carregando,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceTokenPanel(
    device: AdminDevice,
    activationToken: String?,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(device.id, activationToken) { mutableStateOf(false) }
    val pending = device.ativo && device.statusAtivacao != "ATIVADO"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = when {
            activationToken != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            pending -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Text(
                "Token do dispositivo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                activationToken != null -> {
                    SelectionContainer {
                        Text(
                            activationToken,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "Token de ativação pendente. Use este código de 10 caracteres no aparelho correspondente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PcSecondaryButton(
                        text = if (copied) "Token copiado" else "Copiar token",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString(activationToken))
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                pending -> {
                    Text(
                        "Token de ativação não disponível neste aparelho",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Por segurança, tokens antigos não podem ser reconstruídos a partir do banco. Gere um novo token neste cartão para substituir o anterior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !device.ativo -> {
                    Text(
                        "Acesso bloqueado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "A credencial anterior não pode registrar pontos. Reative o aparelho gerando um novo token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        "Credencial ativa protegida no aparelho",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Depois da ativação, o Ponto troca o código de 10 caracteres por uma credencial longa que fica somente no dispositivo. Ela não é exibida no Admin; para substituí-la, gere um novo token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceFactsPanel(facts: List<DeviceFact>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PontoCafeSpacing.sm),
        ) {
            val columns = if (maxWidth >= 560.dp && LocalDensity.current.fontScale < 1.3f) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                facts.chunked(columns).forEach { rowFacts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowFacts.forEach { fact -> DeviceFactCell(fact, Modifier.weight(1f)) }
                        repeat(columns - rowFacts.size) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceFactCell(fact: DeviceFact, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(PontoCafeSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            fact.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusPill(fact.value, fact.tone)
        Text(
            fact.supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isVersionOlder(installed: String?, latest: String?): Boolean {
    fun parts(value: String?): List<Int>? {
        val normalized = value?.trim()?.takeIf { it.matches(Regex("^\\d+\\.\\d+\\.\\d+$")) } ?: return null
        return normalized.split('.').map(String::toInt)
    }
    val current = parts(installed) ?: return false
    val target = parts(latest) ?: return false
    return current.zip(target).firstOrNull { (left, right) -> left != right }
        ?.let { (left, right) -> left < right }
        ?: false
}

private fun parseDeviceInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim()
        .replace(' ', 'T')
        .let { raw -> Regex("([+-]\\d{2})$").replace(raw) { match -> "${match.groupValues[1]}:00" } }
    return runCatching { OffsetDateTime.parse(normalized).toInstant() }
        .recoverCatching { Instant.parse(normalized) }
        .getOrNull()
}

private fun isDeviceTimeRecent(
    value: String?,
    maxAgeHours: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    val instant = parseDeviceInstant(value) ?: return false
    val age = nowMillis - instant.toEpochMilli()
    return age in 0..(maxAgeHours * 60L * 60L * 1_000L)
}

private fun formatDeviceTime(value: String?): String {
    val instant = parseDeviceInstant(value) ?: return "Ainda não registrada"
    return instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))
}

@Composable
private fun SecurePinFieldV2(
    label: String,
    value: String,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    var visible by remember(label) { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(12)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text("4 a 12 números") },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (visible) KeyboardType.Number else KeyboardType.NumberPassword,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        isError = isError,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar PIN" else "Mostrar PIN",
                )
            }
        },
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
    )
}
