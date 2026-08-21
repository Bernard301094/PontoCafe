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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun DeviceSetupScreen(
    viewModel: PontoCafeViewModel,
    onAdminClick: () -> Unit = {},
    onSupervisorClick: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var token by rememberSaveable { mutableStateOf("") }
    val loading = viewModel.state.carregando

    fun activate() {
        if (token.length != 10 || loading) return
        focusManager.clearFocus()
        viewModel.configurarDispositivo(token)
    }

    PontoCafeResponsivePage(maxContentWidth = 640.dp) { responsive ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(
                if (responsive.useCompactVerticalLayout) PontoCafeSpacing.md else PontoCafeSpacing.lg,
            ),
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { activate() }),
                singleLine = true,
            )
            PcPrimaryButton(
                text = "Ativar este aparelho",
                onClick = ::activate,
                modifier = Modifier.fillMaxWidth(),
                enabled = token.length == 10,
                loading = loading,
            )
        }

        MessageCard(viewModel)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                Modifier.padding(PontoCafeSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text("Acesso de gestão", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Administrador e Supervisor podem entrar mesmo antes deste aparelho ser ativado como Ponto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PcSecondaryButton(
                    text = "Entrar como Administrador",
                    onClick = onAdminClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                PcSecondaryButton(
                    text = "Entrar como Supervisor",
                    onClick = onSupervisorClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    }
}
