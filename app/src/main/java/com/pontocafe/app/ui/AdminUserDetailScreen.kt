package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Gerenciar conta")
        Text(user.nome, style = MaterialTheme.typography.headlineSmall)
        Text(user.email)
        Text("Perfil: ${if (user.perfil == "ADMIN") "Administrador" else "Supervisor"}")
        Text("Status: ${if (user.ativo) "Ativo" else "Desativado"}")
        AdminFeedback(viewModel)

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
            ) { Text("Supervisor") }
            OutlinedButton(
                onClick = { viewModel.alterarPerfil(user, "ADMIN") },
                modifier = Modifier.weight(1f),
                enabled = !state.carregando && user.perfil != "ADMIN",
            ) { Text("Administrador") }
        }

        Text("Redefinir senha", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = novaSenha,
            onValueChange = { novaSenha = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nova senha") },
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
        erroLocal?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
            Text("Redefinir senha e encerrar sessões")
        }

        OutlinedButton(onClick = viewModel::voltarHome, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar")
        }
    }
}
