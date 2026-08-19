package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AdminDevice

private enum class DeviceDangerAction { DEACTIVATE, DELETE, ROTATE }

@Composable
fun AdminDevicesScreenV2(
    viewModel: AdminDeviceViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.tokenGerado, state.tokenRotacionado) {
        if (state.tokenGerado != null && !state.tokenRotacionado) {
            name = ""
            pin = ""
            confirmPin = ""
            showCreate = false
        }
    }

    state.tokenGerado?.let { token ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(if (state.tokenRotacionado) "Novo código de ativação" else "Dispositivo criado")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Text(state.tokenDeviceName ?: "Dispositivo", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(token, style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        "Copie este código de 10 caracteres agora. Por segurança, ele é exibido somente neste momento.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::limparToken) { Text("Já copiei") }
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

                state.tokenGerado?.let { token ->
                    item(key = "token") {
                        PcSectionSurface {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                StatusPill("Código exibido uma única vez", PontoCafeTone.WARNING)
                                Text(
                                    if (state.tokenRotacionado) "Novo código de ativação" else "Dispositivo criado",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    state.tokenDeviceName ?: "Dispositivo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SelectionContainer {
                                    Text(
                                        token,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                PcSecondaryButton(
                                    text = "Já copiei",
                                    onClick = viewModel::limparToken,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                item(key = "actions-title") {
                    SectionTitle(
                        "Ações",
                        "Cadastre um novo terminal ou atualize a lista de aparelhos autorizados.",
                    )
                }

                item(key = "actions") {
                    if (responsive.isCompact) {
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
                                )
                                SecurePinFieldV2("PIN de desbloqueio", pin, enabled = !state.carregando) { pin = it }
                                SecurePinFieldV2("Confirmar PIN", confirmPin, enabled = !state.carregando) { confirmPin = it }
                                if (pin.isNotBlank() && confirmPin.isNotBlank() && pin != confirmPin) {
                                    Text(
                                        "Os PINs não coincidem.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                PcPrimaryButton(
                                    text = if (state.carregando) "Gerando código…" else "Cadastrar e gerar código",
                                    onClick = { viewModel.criarDispositivo(name, pin) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.carregando && name.trim().length >= 2 && pin.length in 4..12 && pin == confirmPin,
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
                            supportingText = "Cadastre o primeiro terminal para gerar um código de ativação.",
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
    var expanded by remember(device.id) { mutableStateOf(false) }
    var newName by remember(device.id, device.nome) { mutableStateOf(device.nome) }
    var newPin by remember(device.id) { mutableStateOf("") }
    var confirmPin by remember(device.id) { mutableStateOf("") }
    var dangerAction by remember(device.id) { mutableStateOf<DeviceDangerAction?>(null) }

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
            DeviceDangerAction.ROTATE -> "O token instalado deixará de funcionar e um novo código de ativação será gerado."
        }
        AlertDialog(
            onDismissRequest = { dangerAction = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            DeviceDangerAction.DEACTIVATE -> viewModel.desativar(device)
                            DeviceDangerAction.DELETE -> viewModel.excluirPermanentemente(device)
                            DeviceDangerAction.ROTATE -> viewModel.rotacionarToken(device)
                        }
                        dangerAction = null
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { dangerAction = null }) { Text("Cancelar") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                        "ID ${device.id.take(8)}${device.ultimoAcessoEm?.let { " · atividade ${it.take(16).replace('T', ' ')}" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    if (device.ativo) "Ativo" else "Inativo",
                    if (device.ativo) PontoCafeTone.SUCCESS else PontoCafeTone.NEUTRAL,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                StatusPill(
                    if (device.pinConfigurado) "PIN configurado" else "PIN pendente",
                    if (device.pinConfigurado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                )
            }

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
                        SecurePinFieldV2("Novo PIN", newPin, enabled = !viewModel.state.carregando) { newPin = it }
                        SecurePinFieldV2("Confirmar novo PIN", confirmPin, enabled = !viewModel.state.carregando) { confirmPin = it }
                        if (newPin.isNotBlank() && confirmPin.isNotBlank() && newPin != confirmPin) {
                            Text(
                                "Os PINs não coincidem.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PcSecondaryButton(
                            text = if (device.pinConfigurado) "Alterar PIN" else "Definir PIN",
                            onClick = { viewModel.alterarPin(device, newPin) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.state.carregando && newPin.length in 4..12 && newPin == confirmPin,
                        )
                    }

                    SectionTitle(
                        "Ações do aparelho",
                        "Tokens e estado de ativação afetam o acesso deste dispositivo ao Ponto Café.",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
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
                        )
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
private fun SecurePinFieldV2(
    label: String,
    value: String,
    enabled: Boolean = true,
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
        ),
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
