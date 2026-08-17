package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AccountRegistrationDraftState

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
    draftState: AccountRegistrationDraftState,
    carregando: Boolean = false,
    initialProfile: AccountProfile = AccountProfile.SUPERVISOR,
    showHeader: Boolean = true,
    onSubmit: (NewAccountInput) -> Unit,
) {
    val draft = draftState.draft
    val perfil = AccountProfile.entries.firstOrNull { it.name == draft.perfil } ?: initialProfile

    val cleanName = draft.nome.trim()
    val cleanEmail = draft.email.trim()
    val emailValid = cleanEmail.contains('@') && cleanEmail.substringAfter('@').contains('.')
    val passwordLongEnough = draft.senha.length >= 10
    val passwordHasLetter = draft.senha.any { it.isLetter() }
    val passwordHasDigit = draft.senha.any { it.isDigit() }
    val passwordsMatch = draft.confirmarSenha.isNotBlank() && draft.senha == draft.confirmarSenha

    val validationError = when {
        cleanName.length < 2 -> "Informe o nome completo da pessoa."
        !emailValid -> "Informe um e-mail de acesso válido."
        !passwordLongEnough -> "A senha deve ter pelo menos 10 caracteres."
        !passwordHasLetter || !passwordHasDigit -> "A senha deve combinar letras e números."
        !passwordsMatch -> "As senhas não coincidem."
        else -> null
    }

    val completionHint = when {
        cleanName.length < 2 -> "Informe o nome completo para continuar."
        cleanEmail.isBlank() -> "Informe o e-mail que será usado para entrar."
        !emailValid -> "Revise o e-mail de acesso."
        draft.senha.isBlank() -> "Crie uma senha para esta conta."
        !passwordLongEnough -> "Use pelo menos 10 caracteres na senha."
        !passwordHasLetter || !passwordHasDigit -> "Inclua letras e números na senha."
        draft.confirmarSenha.isBlank() -> "Confirme a senha."
        !passwordsMatch -> "A confirmação precisa ser igual à senha."
        else -> "Tudo pronto para criar a conta de ${perfil.label}."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        if (showHeader) {
            PontoCafeHeader("Nova conta de acesso")
            Text(
                "Crie uma conta protegida para Supervisor ou Administrador.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle(
            title = "Perfil de acesso",
            subtitle = "Supervisor é recomendado para a operação diária. Administrador possui controle total.",
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackProfiles = maxWidth < 430.dp
            if (stackProfiles) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    ProfileChoiceCard(
                        title = "Supervisor",
                        description = "Pausas, colaboradores, biometria e autorizações.",
                        selected = perfil == AccountProfile.SUPERVISOR,
                        onClick = {
                            draftState.update(
                                draft.copy(perfil = AccountProfile.SUPERVISOR.name, erroLocal = null),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !carregando,
                    )
                    ProfileChoiceCard(
                        title = "Administrador",
                        description = "Controle total, acessos, configurações e dispositivos.",
                        selected = perfil == AccountProfile.ADMIN,
                        onClick = {
                            draftState.update(
                                draft.copy(perfil = AccountProfile.ADMIN.name, erroLocal = null),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !carregando,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    ProfileChoiceCard(
                        title = "Supervisor",
                        description = "Pausas, colaboradores, biometria e autorizações.",
                        selected = perfil == AccountProfile.SUPERVISOR,
                        onClick = {
                            draftState.update(
                                draft.copy(perfil = AccountProfile.SUPERVISOR.name, erroLocal = null),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !carregando,
                    )
                    ProfileChoiceCard(
                        title = "Administrador",
                        description = "Controle total, acessos, configurações e dispositivos.",
                        selected = perfil == AccountProfile.ADMIN,
                        onClick = {
                            draftState.update(
                                draft.copy(perfil = AccountProfile.ADMIN.name, erroLocal = null),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !carregando,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
            border = BorderStroke(1.dp, PontoCafePremium.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        ) {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                Text(
                    "INFORMAÇÕES DA CONTA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedTextField(
                    value = draft.nome,
                    onValueChange = { draftState.update(draft.copy(nome = it, erroLocal = null)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nome completo") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    supportingText = { Text("Nome da pessoa que utilizará este acesso.") },
                    singleLine = true,
                    enabled = !carregando,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )

                OutlinedTextField(
                    value = draft.email,
                    onValueChange = { draftState.update(draft.copy(email = it, erroLocal = null)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("E-mail de acesso") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    supportingText = { Text("Este e-mail será usado para iniciar sessão.") },
                    singleLine = true,
                    enabled = !carregando,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
            border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
        ) {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = PontoCafePremium.glowSoft,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Segurança",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Defina uma senha forte para proteger este perfil.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SecurePasswordField(
                    value = draft.senha,
                    onValueChange = { draftState.update(draft.copy(senha = it, erroLocal = null)) },
                    label = "Senha",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !carregando,
                    supportingText = "Mínimo de 10 caracteres",
                )

                PasswordRequirement(
                    text = "Pelo menos 10 caracteres",
                    fulfilled = passwordLongEnough,
                )
                PasswordRequirement(
                    text = "Conter letras e números",
                    fulfilled = passwordHasLetter && passwordHasDigit,
                )

                SecurePasswordField(
                    value = draft.confirmarSenha,
                    onValueChange = { draftState.update(draft.copy(confirmarSenha = it, erroLocal = null)) },
                    label = "Confirmar senha",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !carregando,
                    supportingText = when {
                        draft.confirmarSenha.isBlank() -> "Repita a mesma senha"
                        passwordsMatch -> "As senhas coincidem"
                        else -> "As senhas ainda não coincidem"
                    },
                )

                PasswordRequirement(
                    text = "Confirmação igual à senha",
                    fulfilled = passwordsMatch,
                )
            }
        }

        draft.erroLocal?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = PontoCafePremium.glassStrong,
            border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
        ) {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text(
                    completionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (validationError == null) {
                        LocalPontoCafeSemanticColors.current.success
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                Button(
                    onClick = {
                        draftState.setValidationError(validationError)
                        if (validationError == null) {
                            draftState.markSubmitted()
                            onSubmit(
                                NewAccountInput(
                                    nome = cleanName,
                                    email = cleanEmail.lowercase(),
                                    senha = draft.senha,
                                    perfil = perfil,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !carregando && validationError == null,
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (carregando) {
                            "Criando conta..."
                        } else if (perfil == AccountProfile.SUPERVISOR) {
                            "Criar conta de Supervisor"
                        } else {
                            "Criar conta de Administrador"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else PontoCafePremium.glassStrong,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else PontoCafePremium.borderSoft,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 5.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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

@Composable
private fun PasswordRequirement(
    text: String,
    fulfilled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (fulfilled) {
                LocalPontoCafeSemanticColors.current.success
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            },
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (fulfilled) {
                LocalPontoCafeSemanticColors.current.success
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
