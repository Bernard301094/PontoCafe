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
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.SecureAdminSessionStore

@Composable
fun SupervisorLoginScreenV2(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val sessionStore = remember(context) {
        SecureAdminSessionStore(context.applicationContext, "supervisor")
    }
    val state = viewModel.state
    var email by remember { mutableStateOf(sessionStore.loginEmailSuggestion()) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PontoCafeSpacing.xl, vertical = PontoCafeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xl),
    ) {
        PontoCafeScreenHeader(
            title = "Supervisor",
            onBack = onClose,
            backLabel = "Ponto Café",
            eyebrow = "Acompanhamento operacional",
        )

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
                Icon(
                    Icons.Default.SupervisorAccount,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(Modifier.weight(1f)) {
                    Text("Operação do café", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Acompanhe pausas, autorizações, colaboradores, biometria e relatórios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-mail") },
                singleLine = true,
                enabled = !state.carregando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            SecurePasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Senha",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            )
            state.erro?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        it,
                        modifier = Modifier.padding(PontoCafeSpacing.sm),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Button(
                onClick = {
                    sessionStore.prepareLogin(email, "SUPERVISOR")
                    viewModel.login(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando && email.isNotBlank() && password.length >= 10,
            ) {
                Text(if (state.carregando) "Entrando..." else "Entrar como Supervisor")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                "Depois do login, esta conta ficará disponível no seletor do aparelho. O token é cifrado e a senha nunca é salva.",
                modifier = Modifier.padding(PontoCafeSpacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
