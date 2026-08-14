package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
fun FirstAdminSetupScreen(viewModel: AdminViewModel, onClose: () -> Unit) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var chave by remember { mutableStateOf("") }
    var erroLocal by remember { mutableStateOf<String?>(null) }
    val state = viewModel.state

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Configurar primeiro administrador")
        Text("Esse cadastro só funciona enquanto ainda não existir nenhuma conta no sistema.")

        if (!state.instalacaoConfigurada) {
            Text("O servidor ainda precisa receber FIRST_ADMIN_SETUP_KEY antes de concluir este cadastro.")
        }

        OutlinedTextField(nome, { nome = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true)
        OutlinedTextField(
            senha,
            { senha = it },
            Modifier.fillMaxWidth(),
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            confirmar,
            { confirmar = it },
            Modifier.fillMaxWidth(),
            label = { Text("Confirmar senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            chave,
            { chave = it },
            Modifier.fillMaxWidth(),
            label = { Text("Chave de instalação") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        erroLocal?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        AdminFeedback(viewModel)

        Button(
            onClick = {
                erroLocal = when {
                    nome.trim().length < 2 -> "Informe o nome."
                    !email.contains('@') -> "Informe um e-mail válido."
                    senha.length < 10 -> "A senha deve ter pelo menos 10 caracteres."
                    senha != confirmar -> "As senhas não coincidem."
                    chave.length < 16 -> "Informe a chave de instalação."
                    else -> null
                }
                if (erroLocal == null) viewModel.criarPrimeiroAdmin(nome, email, senha, chave)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando && state.instalacaoConfigurada,
        ) {
            Text(if (state.carregando) "Criando..." else "Criar primeiro ADMIN")
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao Ponto Café")
        }
    }
}
