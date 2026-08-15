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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
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
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminLoginScreen(viewModel: AdminViewModel, onClose: () -> Unit) {
    var email by remember { mutableStateOf("") }
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
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column(Modifier.weight(1f)) {
                    Text("Controle administrativo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Contas, dispositivos, regras, auditoria e segurança.",
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
                onClick = { viewModel.login(email, senha) },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && senha.length >= 10 && !state.carregando,
            ) {
                Text(if (state.carregando) "Entrando..." else "Entrar com segurança")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                "Depois do primeiro login, a sessão pode permanecer aberta e o retorno à área protegida usa biometria ou o bloqueio do próprio celular.",
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
    state.erro?.let {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(it, modifier = Modifier.padding(PontoCafeSpacing.sm), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
    state.mensagem?.let {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LocalPontoCafeSemanticColors.current.infoContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                it,
                modifier = Modifier.padding(PontoCafeSpacing.sm),
                color = LocalPontoCafeSemanticColors.current.onInfoContainer,
            )
        }
    }
}
