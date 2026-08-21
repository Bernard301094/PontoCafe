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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AdminDevice
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
    val focusManager = LocalFocusManager.current
    val state = viewModel.state
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun createDevice() {
        if (state.carregando || name.trim().length < 2 || pin.length !in 4..12 || pin != confirmPin) return
        focusManager.clearFocus()
        viewModel.criarDispositivo(name, pin)
    }

    LaunchedEffect(state.tokenGerado, state.tokenRotacionado) {
        if (state.tokenGerado != null && !state.tokenRotacionado) {
            name = ""
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
                        title = "Visível somente agora",
                        supportingText = "Salve este token de 10 caracteres antes de fechar. Depois, ele não poderá ser consultado novamente.",
                        tone = PontoCafeTone.WARNING,
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
                    Text("Já salvei · fechar")
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
                        "Cadastre um novo terminal ou atualize a lista de aparelhos autorizados.",
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
                                    "Use um nome fácil de identificar e defina um PIN exclusivo deste dispositivo.",
                                )
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it.take(120) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome do dispositivo") },
                                    placeholder = { Text("Ex.: Galaxy A55 · Produção") },
                                    singleLine = true,
                                    enabled = !state.carregando,
                                    shape = MaterialTheme.shapes.large,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    ),
                                )
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
                                PcPrimaryButton(
                                    text = "Cadastrar e gerar token",
                                    onClick = ::createDevice,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = name.trim().length >= 2 && pin.length in 4..12 && pin == confirmPin,
                                    loading = state.carregando,
                                )
                                Text(
                                    "Os campos só são limpos depois que o servidor confirma a criação.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item(key = "devices-title") {
                    SectionTitle(
                        "Aparelhos cadastrados",
                        "${state.dispositivos.count { it.ativo }} ativo(s) · ${state.dispositivos.count { !it.ativo }} inativo(s). Toque em Configurar somente quando precisar alterar um aparelho.",
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
                        DeviceCardV2(viewModel, device)
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
            state.appStatus?.let {
                append(" · versão atual ${it.latestAndroidVersion}")
            }
        },
        icon = if (healthy) Icons.Default.Security else Icons.Default.Devices,
        tone = if (healthy) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
    )
}

@Composable
private fun DeviceCardV2(
    viewModel: AdminDeviceViewModel,
    device: AdminDevice,
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
        "INATIVO" -> "Inativo"
        else -> "Aguardando ativação"
    }
    val statusLabel = when {
        !device.ativo -> "Inativo"
        device.alertaSaude -> "Requer atenção"
        device.statusAtivacao != "ATIVADO" -> "Aguardando ativação"
        !device.pinConfigurado -> "Configuração pendente"
        updateAvailable -> "Atualização disponível"
        !telemetryRecent -> "Sem telemetria recente"
        else -> "Operacional"
    }
    val statusTone = when {
        !device.ativo -> PontoCafeTone.NEUTRAL
        device.alertaSaude -> PontoCafeTone.DANGER
        device.statusAtivacao != "ATIVADO" || !device.pinConfigurado || updateAvailable || !telemetryRecent ->
            PontoCafeTone.WARNING
        else -> PontoCafeTone.SUCCESS
    }
    val deviceFacts = listOf(
        DeviceFact(
            label = "Ativação",
            value = activationLabel,
            supportingText = when (device.statusAtivacao) {
                "ATIVADO" -> "Concluída em ${formatDeviceTime(device.ativadoEm)}"
                "INATIVO" -> "Acesso ao Ponto revogado"
                else -> "Use o token no novo aparelho"
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
            value = if (device.alertaSaude) "Verificar aparelho" else "Sem alerta recente",
            supportingText = buildString {
                append(if (device.pinConfigurado) "PIN configurado" else "PIN pendente")
                if (device.crashCount > 0 || device.stallCount > 0) {
                    append(" · ${device.crashCount} falha(s) · ${device.stallCount} travamento(s)")
                }
            },
            tone = if (device.alertaSaude || !device.pinConfigurado) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
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
            DeviceDangerAction.DEACTIVATE -> "Desativar dispositivo?"
            DeviceDangerAction.DELETE -> "Excluir permanentemente?"
            DeviceDangerAction.ROTATE -> "Revogar token atual?"
        }
        val text = when (action) {
            DeviceDangerAction.DEACTIVATE -> "O aparelho deixará de registrar pontos até receber uma nova ativação."
            DeviceDangerAction.DELETE -> "A exclusão é definitiva. Se houver histórico de pausas, o servidor bloqueará a operação para preservar a rastreabilidade."
            DeviceDangerAction.ROTATE -> "O token instalado deixará de funcionar e um novo token de ativação será gerado."
        }
        AlertDialog(
            onDismissRequest = { dangerAction = null },
            title = { Text(title) },
            text = {
                PcDialogBody {
                    Text(text)
                    PcStateBanner(
                        title = "Ação restrita ao Administrador",
                        supportingText = "A alteração será registrada na auditoria do sistema.",
                        tone = PontoCafeTone.WARNING,
                    )
                }
            },
            confirmButton = {
                PcDangerButton(
                    text = when (action) {
                        DeviceDangerAction.DEACTIVATE -> "Desativar"
                        DeviceDangerAction.DELETE -> "Excluir"
                        DeviceDangerAction.ROTATE -> "Revogar e gerar novo"
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
            .semantics {
                stateDescription = statusLabel
            },
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
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
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
                        "ID ${device.id.take(8)} · ${if (device.ativo) "acesso ativo" else "acesso inativo"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    statusLabel,
                    statusTone,
                )
            }

            DeviceFactsPanel(deviceFacts)

            PcTonalButton(
                text = if (expanded) "Fechar configuração" else "Configurar aparelho",
                icon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                ) {
                    SectionTitle(
                        "Identificação",
                        "Altere apenas o nome exibido no painel; o ID do aparelho não muda.",
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

                    if (device.ativo) {
                        SectionTitle(
                            "Segurança",
                            if (device.pinConfigurado) "Defina um novo PIN somente quando precisar substituir o atual." else "Este aparelho ainda precisa de um PIN próprio.",
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
                    }

                    SectionTitle(
                        "Ações do aparelho",
                        "Tokens e estado de ativação afetam o acesso deste dispositivo ao Ponto Café.",
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val stackActions = maxWidth < 460.dp || LocalDensity.current.fontScale >= 1.3f
                        if (stackActions) {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                PcSecondaryButton(
                                    text = "Gerar novo token",
                                    onClick = { dangerAction = DeviceDangerAction.ROTATE },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !viewModel.state.carregando,
                                )
                                PcSecondaryButton(
                                    text = "Desativar aparelho",
                                    onClick = { dangerAction = DeviceDangerAction.DEACTIVATE },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = device.ativo && !viewModel.state.carregando,
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                PcSecondaryButton(
                                    text = "Novo token",
                                    onClick = { dangerAction = DeviceDangerAction.ROTATE },
                                    modifier = Modifier.weight(1f),
                                    enabled = !viewModel.state.carregando,
                                )
                                PcSecondaryButton(
                                    text = "Desativar",
                                    onClick = { dangerAction = DeviceDangerAction.DEACTIVATE },
                                    modifier = Modifier.weight(1f),
                                    enabled = device.ativo && !viewModel.state.carregando,
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    PcStateBanner(
                        title = "Zona de risco",
                        supportingText = "Excluir é permanente e pode ser bloqueado pelo servidor quando houver histórico que precise ser preservado.",
                        tone = PontoCafeTone.DANGER,
                    )
                    TextButton(
                        onClick = { dangerAction = DeviceDangerAction.DELETE },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.state.carregando,
                    ) {
                        Text("Excluir permanentemente", color = MaterialTheme.colorScheme.error)
                    }
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
                        rowFacts.forEach { fact ->
                            DeviceFactCell(fact, Modifier.weight(1f))
                        }
                        repeat(columns - rowFacts.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
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
