package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun DeviceSetupScreen(viewModel: PontoCafeViewModel, onAdminClick: () -> Unit = {}) {
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PontoCafeHeader("Configuração inicial do dispositivo")
        Text(
            "Informe o código de ativação de 10 caracteres gerado pelo Administrador. Depois da ativação, o aparelho recebe uma credencial longa e protegida.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { value ->
                        token = value.filter { it.isLetterOrDigit() }.take(10)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Código de ativação") },
                    supportingText = {
                        Text("${token.length}/10 · letras maiúsculas, minúsculas e números")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.configurarDispositivo(token) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = token.length == 10 && !viewModel.state.carregando,
                ) {
                    Text(if (viewModel.state.carregando) "Ativando..." else "Ativar dispositivo")
                }
            }
        }

        MessageCard(viewModel)

        OutlinedButton(onClick = onAdminClick, modifier = Modifier.fillMaxWidth()) {
            Text("Entrar na área administrativa")
        }
    }
}
