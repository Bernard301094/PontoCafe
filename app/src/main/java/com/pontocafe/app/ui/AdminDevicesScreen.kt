package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Dispositivos e PIN")
        Text(
            "Cada dispositivo pode ter um PIN diferente para sair do modo Ponto. O PIN nunca é exibido depois de salvo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Novo dispositivo", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do dispositivo") },
                    placeholder = { Text("Ex.: Galaxy A55 Produção") },
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
                    Text("Gerar token e cadastrar dispositivo")
                }
            }
        }

        state.tokenGerado?.let { token ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Token de ${state.tokenDeviceName ?: "dispositivo"}", fontWeight = FontWeight.SemiBold)
                    Text("Copie agora. O token não será mostrado novamente.")
                    SelectionContainer {
                        Text(token, style = MaterialTheme.typography.headlineSmall)
                    }
                    OutlinedButton(
                        onClick = viewModel::limparToken,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Já copiei")
                    }
                }
            }
        }

        Text("Dispositivos cadastrados", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.dispositivos, key = { it.id }) { dispositivo ->
                DevicePinCard(viewModel, dispositivo)
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao painel")
        }
    }
}

@Composable
private fun DevicePinCard(
    viewModel: AdminDeviceViewModel,
    dispositivo: AdminDevice,
) {
    var pin by remember(dispositivo.id) { mutableStateOf("") }
    var confirmarPin by remember(dispositivo.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(dispositivo.nome, fontWeight = FontWeight.SemiBold)
            Text(
                "ID ${dispositivo.id.take(8)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when {
                    !dispositivo.ativo -> "Dispositivo inativo"
                    dispositivo.pinConfigurado -> "PIN personalizado configurado"
                    else -> "Usando o PIN padrão atual até que um novo PIN seja definido"
                },
                color = if (dispositivo.pinConfigurado) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            PinField("Novo PIN", pin) { pin = it }
            PinField("Confirmar novo PIN", confirmarPin) { confirmarPin = it }
            Button(
                onClick = {
                    viewModel.alterarPin(dispositivo, pin)
                    pin = ""
                    confirmarPin = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = dispositivo.ativo && !viewModel.state.carregando && pin.length in 4..12 && pin == confirmarPin,
            ) {
                Text(if (dispositivo.pinConfigurado) "Alterar PIN" else "Definir PIN")
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
