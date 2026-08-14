package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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


enum class AccountProfile(val label: String) {
    SUPERVISOR("Supervisor"),
    ADMIN("Administrador"),
}

data class NewAccountInput(
    val nome: String,
    val email: String,
    val senha: String,
    val perfil: AccountProfile,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAccountForm(
    carregando: Boolean = false,
    onSubmit: (NewAccountInput) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var perfil by remember { mutableStateOf(AccountProfile.SUPERVISOR) }
    var perfilAberto by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Cadastrar conta", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome") },
            singleLine = true,
            enabled = !carregando,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("E-mail") },
            singleLine = true,
            enabled = !carregando,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Senha") },
            singleLine = true,
            enabled = !carregando,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text("Mínimo de 10 caracteres") },
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmarSenha,
            onValueChange = { confirmarSenha = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirmar senha") },
            singleLine = true,
            enabled = !carregando,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = perfilAberto,
            onExpandedChange = { if (!carregando) perfilAberto = !perfilAberto },
        ) {
            OutlinedTextField(
                value = perfil.label,
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                label = { Text("Perfil") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = perfilAberto) },
                enabled = !carregando,
            )
            ExposedDropdownMenu(
                expanded = perfilAberto,
                onDismissRequest = { perfilAberto = false },
            ) {
                AccountProfile.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            perfil = option
                            perfilAberto = false
                        },
                    )
                }
            }
        }

        erro?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                erro = when {
                    nome.trim().length < 2 -> "Informe o nome da pessoa."
                    !email.contains('@') -> "Informe um e-mail válido."
                    senha.length < 10 -> "A senha deve ter pelo menos 10 caracteres."
                    senha != confirmarSenha -> "As senhas não coincidem."
                    else -> null
                }
                if (erro == null) {
                    onSubmit(
                        NewAccountInput(
                            nome = nome.trim(),
                            email = email.trim().lowercase(),
                            senha = senha,
                            perfil = perfil,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !carregando,
        ) {
            Text(if (carregando) "Cadastrando..." else "Cadastrar conta")
        }
    }
}
