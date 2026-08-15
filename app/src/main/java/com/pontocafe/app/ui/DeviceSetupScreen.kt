package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
            .padding(24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        PontoCafeHeader("Configuração inicial do dispositivo")
        Spacer(Modifier.height(20.dp))
        Text(
            "Informe o token de 10 caracteres gerado pelo administrador. Ele será armazenado de forma protegida neste aparelho.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { value ->
                token = value.filter { it.isLetterOrDigit() }.take(10)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token do dispositivo") },
            supportingText = {
                Text("${token.length}/10 · letras maiúsculas, minúsculas e números")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.configurarDispositivo(token) },
            modifier = Modifier.fillMaxWidth(),
            enabled = token.length == 10,
        ) {
            Text("Ativar dispositivo")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAdminClick, modifier = Modifier.fillMaxWidth()) {
            Text("Área administrativa")
        }
        Spacer(Modifier.height(16.dp))
        MessageCard(viewModel)
        Spacer(Modifier.weight(1f))
    }
}
