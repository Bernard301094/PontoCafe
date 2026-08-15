package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
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
                Button(onClick = viewModel::limparToken) {
                    Text("Já copiei")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
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
                Card(colors = CardDefaults.cardColors(containerColor = LocalPontoCafeSemanticColors.current.successContainer)) {
                    Text(
                        message,
                        modifier = Modifier.padding(PontoCafeSpacing.md),
                        color = LocalPontoCafeSemanticColors.current.onSuccessContainer,
                    )
                }
            }
        }
        state.erro?.let { error ->
            item(key = "error") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        error,
                        modifier = Modifier.padding(PontoCafeSpacing.md),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        state.tokenGerado?.let { token ->
            item(key = "token") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LocalPontoCafeSemanticColors.current.infoContainer),
                    border = BorderStroke(1.dp, LocalPontoCafeSemanticColors.current.info.copy(alpha = 0.25f)),
                ) {
                    Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        Text(
                            if (state.tokenRotacionado) "Novo código de ativação" else "Dispositivo criado",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalPontoCafeSemanticColors.current.onInfoContainer,
                        )
                        Text(
                            state.tokenDeviceName ?: "Dispositivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalPontoCafeSemanticColors.current.onInfoContainer,
                        )
                        SelectionContainer {
                            Text(
                                token,
                                style = MaterialTheme.typography.headlineMedium,
                                color = LocalPontoCafeSemanticColors.current.onInfoContainer,
                            )
                        }
                        Text(
                            "Copie agora. O código de 10 caracteres é exibido uma única vez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalPontoCafeSemanticColors.current.onInfoContainer,
                        )
                        OutlinedButton(onClick = viewModel::limparToken, modifier = Modifier.fillMaxWidth()) {
                            Text("Já copiei")
                        }
                    }
                }
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Button(
                    onClick = { showCreate = !showCreate },
                    modifier = Modifier.weight(1f),
                    enabled = !state.carregando,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(if (showCreate) " Fechar cadastro" else " Novo dispositivo")
                }
                OutlinedButton(
                    onClick = viewModel::carregar,
                    modifier = Modifier.weight(1f),
                    enabled = !state.carregando,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" Atualizar")
                }
            }
        }

        if (showCreate) {
            item(key = "create") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        SectionTitle("Cadastrar aparelho", "Defina um nome reconhecível e um PIN individual.")
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(120) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nome do dispositivo") },
                            placeholder = { Text("Ex.: Galaxy A55 · Produção") },
                            singleLine = true,
                            enabled = !state.carregando,
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
                        Button(
                            onClick = { viewModel.criarDispositivo(name, pin) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.carregando && name.trim().length >= 2 && pin.length in 4..12 && pin == confirmPin,
                        ) {
                            Text(if (state.carregando) "Gerando código…" else "Cadastrar e gerar código")
                        }
                        Text(
                            "Os dados só serán limpos depois que o servidor confirmar a criação do dispositivo.",
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
                "${state.dispositivos.count { it.ativo }} ativo(s) · ${state.dispositivos.count { !it.ativo }} inativo(s)",
            )
        }

        if (state.dispositivos.isEmpty() && !state.carregando) {
            item(key = "empty") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        "Nenhum dispositivo cadastrado.",
                        modifier = Modifier.padding(PontoCafeSpacing.md),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.dispositivos, key = { "device-v2-${it.id}" }) { device ->
                DeviceCardV2(viewModel, device)
            }
        }
    }
}

@Composable
private fun DeviceHealthOverviewV2(viewModel: AdminDeviceViewModel) {
    val state = viewModel.state
    val healthy = state.health?.let { it.status == "ok" && it.banco == "ok" } == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Saúde do sistema", style = MaterialTheme.typography.titleMedium)
                Text(
                    "App ${BuildConfig.VERSION_NAME} · servidor ${if (healthy) "online" else "sem confirmação"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.appStatus?.let {
                    Text(
                        "Última ${it.latestAndroidVersion} · mínima ${it.minimumAndroidVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusPill(
                if (healthy) "Online" else "Verificar",
                if (healthy) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
            )
        }
    }
}

@Composable
private fun DeviceCardV2(
    viewModel: AdminDeviceViewModel,
    device: AdminDevice,
) {
    var newName by remember(device.id, device.nome) { mutableStateOf(device.nome) }
    var newPin by remember(device.id) { mutableStateOf("") }
    var confirmPin by remember(device.id) { mutableStateOf("") }
    var dangerAction by remember(device.id) { mutableStateOf<DeviceDangerAction?>(null) }

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
            dismissButton = { TextButton(onClick = { dangerAction = null }) { Text("Cancelar") } },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(device.nome, style = MaterialTheme.typography.titleMedium)
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

            StatusPill(
                if (device.pinConfigurado) "PIN configurado" else "PIN pendente",
                if (device.pinConfigurado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
            )

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do aparelho") },
                singleLine = true,
                enabled = !viewModel.state.carregando,
            )
            OutlinedButton(
                onClick = { viewModel.renomear(device, newName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.state.carregando && newName.trim().length >= 2 && newName.trim() != device.nome,
            ) { Text("Salvar nome") }

            if (device.ativo) {
                SecurePinFieldV2("Novo PIN", newPin, enabled = !viewModel.state.carregando) { newPin = it }
                SecurePinFieldV2("Confirmar novo PIN", confirmPin, enabled = !viewModel.state.carregando) { confirmPin = it }
                OutlinedButton(
                    onClick = {
                        viewModel.alterarPin(device, newPin)
                        newPin = ""
                        confirmPin = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.state.carregando && newPin.length in 4..12 && newPin == confirmPin,
                ) { Text(if (device.pinConfigurado) "Alterar PIN" else "Definir PIN") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                OutlinedButton(
                    onClick = { dangerAction = DeviceDangerAction.ROTATE },
                    modifier = Modifier.weight(1f),
                    enabled = !viewModel.state.carregando,
                ) { Text("Novo token") }
                OutlinedButton(
                    onClick = { dangerAction = DeviceDangerAction.DEACTIVATE },
                    modifier = Modifier.weight(1f),
                    enabled = device.ativo && !viewModel.state.carregando,
                ) { Text("Desativar") }
            }
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
        keyboardOptions = KeyboardOptions(keyboardType = if (visible) KeyboardType.Number else KeyboardType.NumberPassword),
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
    )
}
