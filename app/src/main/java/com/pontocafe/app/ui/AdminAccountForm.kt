package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
            val stackProfiles = pontoCafeWindowSizeClass(maxWidth) == PontoCafeWindowSizeClass.COMPACT && maxWidth < 430.dp
            if (stackProfiles) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    ProfileChoiceCard(
                        title = "Supervisor",
                        description = "Pausas, colaboradores, biometria e autorizações.",
                        selected = perfil == AccountProfile.SUPERVISOR,
                        onClick = { draftState.update(draft.copy(perfil = AccountProfile.SUPERVISOR.name, erroLocal = null)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !carregando,
                    )
                    ProfileChoiceCard(
                        title = "Administrador",
                        description = "Controle total, acessos, configurações e dispositivos.",
                        selected = perfil == AccountProfile.ADMIN,
                        onClick = { draftState.update(draft.copy(perfil = AccountProfile.ADMIN.name, erroLocal = null)) },
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
                        onClick = { draftState.update(draft.copy(perfil = AccountProfile.SUPERVISOR.name, erroLocal = null)) },
                        modifier = Modifier.weight(1f),
                        enabled = !carregando,
                    )
                    ProfileChoiceCard(
                        title = "Administrador",
                        description = "Controle total, acessos, configurações e dispositivos.",
                        selected = perfil == AccountProfile.ADMIN,
                        onClick = { draftState.update(draft.copy(perfil = AccountProfile.ADMIN.name, erroLocal = null)) },
                        modifier = Modifier.weight(1f),
                        enabled = !carregando,
                    )
                }
            }
        }

        PcSectionSurface {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                Text("Informações da conta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = draft.nome,
                    onValueChange = { draftState.update(draft.copy(nome = it, erroLocal = null)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome completo") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    supportingText = { Text("Nome da pessoa que utilizará este acesso.") },
                    singleLine = true,
                    enabled = !carregando,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = draft.email,
                    onValueChange = { draftState.update(draft.copy(email = it, erroLocal = null)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail de acesso") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    supportingText = { Text("Este e-mail será usado para iniciar sessão.") },
                    singleLine = true,
                    enabled = !carregando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
            }
        }

        PcSectionSurface {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text("Segurança", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Defina uma senha forte para proteger este perfil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecurePasswordField(
                    value = draft.senha,
                    onValueChange = { draftState.update(draft.copy(senha = it, erroLocal = null)) },
                    label = "Senha",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !carregando,
                    supportingText = "Mínimo de 10 caracteres",
                )
                PasswordRequirement("Pelo menos 10 caracteres", passwordLongEnough)
                PasswordRequirement("Conter letras e números", passwordHasLetter && passwordHasDigit)
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
                PasswordRequirement("Confirmação igual à senha", passwordsMatch)
            }
        }

        PcFeedbackBanner(
            message = draft.erroLocal,
            tone = PontoCafeTone.DANGER,
            onDismiss = { draftState.setValidationError(null) },
        )

        PcSectionSurface {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text(
                    completionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (validationError == null) LocalPontoCafeSemanticColors.current.success
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PcPrimaryButton(
                    text = if (carregando) {
                        "Criando conta…"
                    } else if (perfil == AccountProfile.SUPERVISOR) {
                        "Criar conta de Supervisor"
                    } else {
                        "Criar conta de Administrador"
                    },
                    icon = Icons.Default.AdminPanelSettings,
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
                )
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
                text = if (selected) "Selecionado" else "Selecionar",
                tone = if (selected) PontoCafeTone.SUCCESS else PontoCafeTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun PasswordRequirement(text: String, fulfilled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (fulfilled) LocalPontoCafeSemanticColors.current.success
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (fulfilled) LocalPontoCafeSemanticColors.current.success
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
