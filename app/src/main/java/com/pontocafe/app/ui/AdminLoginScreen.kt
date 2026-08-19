package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.SecureAdminSessionStore

@Composable
fun AdminLoginScreen(viewModel: AdminViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val sessionStore = remember(context) {
        SecureAdminSessionStore(context.applicationContext, "admin")
    }
    var email by remember { mutableStateOf(sessionStore.loginEmailSuggestion()) }
    var senha by remember { mutableStateOf("") }
    val state = viewModel.state

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
            title = "Administrador",
            onBack = onClose,
            backLabel = "Ponto Café",
            eyebrow = "Acesso protegido",
        )

        PcHeroCard(
            title = "Controle administrativo",
            supportingText = "Contas, dispositivos, regras, auditoria e segurança em uma área protegida.",
            icon = Icons.Default.AdminPanelSettings,
            tone = PontoCafeTone.INFO,
        )

        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-mail") },
                singleLine = true,
                enabled = !state.carregando,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            SecurePasswordField(
                value = senha,
                onValueChange = { senha = it },
                label = "Senha",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            )
            AdminFeedback(viewModel)
            Button(
                onClick = {
                    sessionStore.prepareLogin(email, "ADMIN")
                    viewModel.login(email, senha)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && senha.length >= 10 && !state.carregando,
            ) {
                Text(if (state.carregando) "Entrando…" else "Entrar com segurança")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                "Depois do login, esta conta ficará disponível no seletor deste aparelho. O token é cifrado pelo Android Keystore e a senha nunca é armazenada.",
                modifier = Modifier.padding(PontoCafeSpacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AdminFeedback(viewModel: AdminViewModel) {
    val state = viewModel.state
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
        PcFeedbackBanner(
            message = state.erro,
            tone = PontoCafeTone.DANGER,
            onDismiss = viewModel::limparFeedback,
        )
        PcFeedbackBanner(
            message = state.mensagem,
            tone = PontoCafeTone.INFO,
            onDismiss = viewModel::limparFeedback,
            autoDismissMillis = 4_000L,
        )
    }
}
