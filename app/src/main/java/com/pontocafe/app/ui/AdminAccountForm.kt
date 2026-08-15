package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun AdminAccountForm(
    carregando: Boolean = false,
    initialProfile: AccountProfile = AccountProfile.SUPERVISOR,
    showHeader: Boolean = true,
    onSubmit: (NewAccountInput) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var perfil by remember(initialProfile) { mutableStateOf(initialProfile) }
    var erro by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showHeader) {
            PontoCafeHeader("Nova conta de acesso")
            Text(
                "Crie uma conta para Supervisor ou Administrador. Isto é diferente do cadastro de colaboradores que batem o ponto.",
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }

        SectionTitle(
            title = "Perfil de acesso",
            subtitle = "Supervisor é o perfil indicado para acompanhar pausas, colaboradores e biometria.",
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileChoiceCard(
                title = "Supervisor",
                description = "Pausas, colaboradores, biometria e autorizações.",
                selected = perfil == AccountProfile.SUPERVISOR,
                onClick = { perfil = AccountProfile.SUPERVISOR },
                modifier = Modifier.weight(1f),
                enabled = !carregando,
            )
            ProfileChoiceCard(
                title = "Administrador",
                description = "Controle total do sistema, acessos e dispositivos.",
                selected = perfil == AccountProfile.ADMIN,
                onClick = { perfil = AccountProfile.ADMIN },
                modifier = Modifier.weight(1f),
                enabled = !carregando,
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("Informações da conta")
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome completo") },
            placeholder = { Text("Ex.: nome e sobrenome") },
            singleLine = true,
            enabled = !carregando,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("E-mail de acesso") },
            singleLine = true,
            enabled = !carregando,
        )

        Spacer(Modifier.height(22.dp))
        SectionTitle("Segurança", "A senha será usada para iniciar sessão neste perfil.")
        Spacer(Modifier.height(10.dp))

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
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirmarSenha,
            onValueChange = { confirmarSenha = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirmar senha") },
            singleLine = true,
            enabled = !carregando,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                when {
                    confirmarSenha.isBlank() -> Text("Repita a senha")
                    senha == confirmarSenha -> Text("As senhas coincidem")
                    else -> Text("As senhas ainda não coincidem")
                }
            },
        )

        erro?.let {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
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
            Text(
                if (carregando) {
                    "Cadastrando..."
                } else if (perfil == AccountProfile.SUPERVISOR) {
                    "Criar conta de Supervisor"
                } else {
                    "Criar conta de Administrador"
                },
            )
        }
    }
}

@Composable
private fun ProfileChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (selected) "Selecionado" else "Selecionar",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
