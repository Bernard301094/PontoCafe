package com.pontocafe.app.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminUserDetailScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    val user = state.selecionado ?: return
    var novaSenha by remember(user.id) { mutableStateOf("") }
    var confirmar by remember(user.id) { mutableStateOf("") }
    var erroLocal by remember(user.id) { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember(user.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PontoCafeScreenHeader(
            title = "Gerenciar conta",
            onBack = viewModel::voltarHome,
            backLabel = "Painel",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InitialAvatar(user.nome)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfilePill(user.perfil)
                        StatusPill(if (user.ativo) "Ativo" else "Desativado", user.ativo)
                    }
                }
            }
        }

        AdminFeedback(viewModel)

        SectionTitle(
            title = "Acesso",
            subtitle = "Controle o status e o nível de permissão desta conta.",
        )

        Button(
            onClick = { viewModel.alterarAtivo(user) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando,
        ) {
            Text(if (user.ativo) "Desativar conta" else "Reativar conta")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.alterarPerfil(user, "SUPERVISOR") },
                modifier = Modifier.weight(1f),
                enabled = !state.carregando && user.perfil != "SUPERVISOR",
            ) { Text("Tornar Supervisor") }
            OutlinedButton(
                onClick = { viewModel.alterarPerfil(user, "ADMIN") },
                modifier = Modifier.weight(1f),
                enabled = !state.carregando && user.perfil != "ADMIN",
            ) { Text("Tornar Admin") }
        }

        SectionTitle(
            title = "Redefinir senha",
            subtitle = "Ao redefinir a senha, as sessões atuais desta conta serão encerradas.",
        )
        OutlinedTextField(
            value = novaSenha,
            onValueChange = { novaSenha = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nova senha") },
            supportingText = { Text("Mínimo de 10 caracteres") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = confirmar,
            onValueChange = { confirmar = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirmar nova senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        erroLocal?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Button(
            onClick = {
                erroLocal = when {
                    novaSenha.length < 10 -> "A nova senha deve ter pelo menos 10 caracteres."
                    novaSenha != confirmar -> "As senhas não coincidem."
                    else -> null
                }
                if (erroLocal == null) viewModel.redefinirSenha(user.id, novaSenha)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando,
        ) {
            Text("Redefinir senha")
        }

        SectionTitle("Zona de risco")
        if (confirmarExclusao) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Excluir conta definitivamente?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        "Esta ação é permanente. A conta e suas sessões de acesso serão removidas.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { confirmarExclusao = false },
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancelar") }
                        Button(
                            onClick = { viewModel.excluirUsuario(user) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.carregando,
                        ) { Text("Excluir") }
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { confirmarExclusao = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            ) {
                Text("Excluir conta")
            }
        }
    }
}
