package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AdminDevice

@Composable
fun AdminDevicesScreen(
    viewModel: AdminDeviceViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    var nome by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmarPin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PontoCafeHeader("Dispositivos e segurança")
            Text(
                "Administre o PIN, nome e credenciais de cada aparelho que funciona como Ponto Café.",
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SystemHealthCard(viewModel)
        }

        state.mensagem?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        state.erro?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionTitle("Novo dispositivo", "Crie o aparelho já com um PIN individual.")
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome do dispositivo") },
                        placeholder = { Text("Ex.: Galaxy A55 · Produção") },
                        singleLine = true,
                    )
                    PinField("PIN de desbloqueio", pin) { pin = it }
                    PinField("Confirmar PIN", confirmarPin) { confirmarPin = it }
                    Button(
                        onClick = {
                            viewModel.criarDispositivo(nome, pin)
                            pin = ""
                            confirmarPin = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando && nome.trim().length >= 2 && pin.length in 4..12 && pin == confirmarPin,
                    ) {
                        Text("Cadastrar e gerar código de ativação")
                    }
                }
            }
        }

        state.tokenGerado?.let { token ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.tokenRotacionado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (state.tokenRotacionado) "Novo código após revogação" else "Código de ativação",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Dispositivo: ${state.tokenDeviceName ?: "—"}")
                        if (state.tokenRotacionado) {
                            Text("O token instalado anteriormente já foi revogado. O aparelho precisa ser ativado novamente com este código.")
                        } else {
                            Text("Copie agora. Este código de 10 caracteres é exibido uma única vez.")
                        }
                        SelectionContainer {
                            Text(token, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = viewModel::limparToken, modifier = Modifier.fillMaxWidth()) {
                            Text("Já copiei")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(
                "Dispositivos cadastrados",
                "${state.dispositivos.count { it.ativo }} ativo(s) · ${state.dispositivos.count { !it.ativo }} inativo(s)",
            )
        }

        items(state.dispositivos, key = { it.id }) { dispositivo ->
            DeviceSecurityCard(viewModel, dispositivo)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::carregar, modifier = Modifier.weight(1f)) {
                    Text("Atualizar")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Voltar ao painel")
                }
            }
        }
    }
}

@Composable
private fun SystemHealthCard(viewModel: AdminDeviceViewModel) {
    val state = viewModel.state
    val health = state.health
    val version = state.appStatus
    val healthy = health?.let { it.status == "ok" && it.banco == "ok" } == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Saúde do Ponto")
            StatusPill(
                if (healthy) "Servidor e banco online" else "Servidor sem confirmação",
                positive = healthy,
            )
            Text("App instalado · ${BuildConfig.VERSION_NAME}")
            version?.let {
                Text("Versão mais recente · ${it.latestAndroidVersion}")
                Text("Versão mínima aceita · ${it.minimumAndroidVersion}")
            }
        }
    }
}

@Composable
private fun DeviceSecurityCard(
    viewModel: AdminDeviceViewModel,
    dispositivo: AdminDevice,
) {
    var pin by remember(dispositivo.id) { mutableStateOf("") }
    var confirmarPin by remember(dispositivo.id) { mutableStateOf("") }
    var novoNome by remember(dispositivo.id, dispositivo.nome) { mutableStateOf(dispositivo.nome) }
    var confirmarDesativacao by remember(dispositivo.id) { mutableStateOf(false) }
    var confirmarRotacao by remember(dispositivo.id) { mutableStateOf(false) }

    if (confirmarDesativacao) {
        AlertDialog(
            onDismissRequest = { confirmarDesativacao = false },
            title = { Text("Desativar dispositivo?") },
            text = { Text("${dispositivo.nome} perderá imediatamente o acesso ao Ponto Café. O aparelho não poderá registrar novas pausas enquanto estiver desativado.") },
            confirmButton = {
                Button(onClick = { confirmarDesativacao = false; viewModel.desativar(dispositivo) }) { Text("Desativar") }
            },
            dismissButton = { TextButton(onClick = { confirmarDesativacao = false }) { Text("Cancelar") } },
        )
    }

    if (confirmarRotacao) {
        AlertDialog(
            onDismissRequest = { confirmarRotacao = false },
            title = { Text("Revogar token atual?") },
            text = { Text("O token atualmente instalado em ${dispositivo.nome} deixará de funcionar imediatamente. Será gerado um novo código de ativação de 10 caracteres.") },
            confirmButton = {
                Button(onClick = { confirmarRotacao = false; viewModel.rotacionarToken(dispositivo) }) { Text("Revogar e gerar novo") }
            },
            dismissButton = { TextButton(onClick = { confirmarRotacao = false }) { Text("Cancelar") } },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dispositivo.nome, fontWeight = FontWeight.Bold)
                    Text(
                        "ID ${dispositivo.id.take(8)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusPill(if (dispositivo.ativo) "Ativo" else "Inativo", positive = dispositivo.ativo)
            }

            dispositivo.ultimoAcessoEm?.let {
                Text(
                    "Última atividade · ${it.take(16).replace('T', ' ')}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (dispositivo.pinConfigurado) "PIN personalizado configurado" else "PIN personalizado pendente",
                color = if (dispositivo.pinConfigurado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )

            OutlinedTextField(
                value = novoNome,
                onValueChange = { novoNome = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do aparelho") },
                singleLine = true,
                enabled = !viewModel.state.carregando,
            )
            OutlinedButton(
                onClick = { viewModel.renomear(dispositivo, novoNome) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.state.carregando && novoNome.trim().length >= 2 && novoNome.trim() != dispositivo.nome,
            ) { Text("Salvar novo nome") }

            if (dispositivo.ativo) {
                PinField("Novo PIN", pin) { pin = it }
                PinField("Confirmar novo PIN", confirmarPin) { confirmarPin = it }
                Button(
                    onClick = {
                        viewModel.alterarPin(dispositivo, pin)
                        pin = ""
                        confirmarPin = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.state.carregando && pin.length in 4..12 && pin == confirmarPin,
                ) {
                    Text(if (dispositivo.pinConfigurado) "Alterar PIN" else "Definir PIN")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { confirmarRotacao = true },
                    modifier = Modifier.weight(1f),
                    enabled = !viewModel.state.carregando,
                ) { Text("Novo token") }
                OutlinedButton(
                    onClick = { confirmarDesativacao = true },
                    modifier = Modifier.weight(1f),
                    enabled = dispositivo.ativo && !viewModel.state.carregando,
                ) { Text("Desativar") }
            }
        }
    }
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue -> onValueChange(newValue.filter(Char::isDigit).take(12)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text("4 a 12 números") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
    )
}
