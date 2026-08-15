package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
            .padding(horizontal = PontoCafeSpacing.xl, vertical = PontoCafeSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
    ) {
        PontoCafeScreenHeader(title = "Ativar dispositivo", eyebrow = "Configuração inicial")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(PontoCafeSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text("Vincule este aparelho ao Ponto Café", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "O código curto é usado uma única vez. Depois, o Android guarda uma credencial longa e protegida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            SectionTitle("Código de ativação", "Gerado pelo Administrador na gestão de dispositivos.")
            OutlinedTextField(
                value = token,
                onValueChange = { value -> token = value.filter { it.isLetterOrDigit() }.take(10) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de 10 caracteres") },
                supportingText = { Text("${token.length}/10") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
            )
            Button(
                onClick = { viewModel.configurarDispositivo(token) },
                modifier = Modifier.fillMaxWidth(),
                enabled = token.length == 10 && !viewModel.state.carregando,
            ) {
                Text(if (viewModel.state.carregando) "Ativando..." else "Ativar este aparelho")
            }
        }

        MessageCard(viewModel)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                Text("Precisa administrar a instalação?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A área administrativa continua disponível mesmo antes deste aparelho ser ativado como Ponto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onAdminClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir área administrativa")
                }
            }
        }
    }
}
