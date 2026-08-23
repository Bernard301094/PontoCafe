package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AccountRegistrationDraftState
import java.security.SecureRandom

enum class AccountProfile(
    val label: String,
    val supervisor: Boolean,
    val shift: String? = null,
) {
    SUPERVISOR_A("Supervisor", true, "A"),
    SUPERVISOR_B("Supervisor", true, "B"),
    SUPERVISOR_C("Supervisor", true, "C"),
    SUPERVISOR_D("Supervisor", true, "D"),
    ADMIN("Administrador", false, null),
    ;

    companion object {
        fun supervisorForShift(shift: String): AccountProfile = when (shift.uppercase()) {
            "B" -> SUPERVISOR_B
            "C" -> SUPERVISOR_C
            "D" -> SUPERVISOR_D
            else -> SUPERVISOR_A
        }
    }
}

data class NewAccountInput(
    val nome: String,
    val email: String,
    val senha: String,
    val perfil: AccountProfile,
)

private const val TEMP_PASSWORD_LENGTH = 16
private const val TEMP_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val TEMP_LOWER = "abcdefghijkmnopqrstuvwxyz"
private const val TEMP_DIGITS = "23456789"
private const val TEMP_SYMBOLS = "!@#$%*-_"

private fun generateTemporaryPassword(random: SecureRandom): String {
    val all = TEMP_UPPER + TEMP_LOWER + TEMP_DIGITS + TEMP_SYMBOLS
    val chars = mutableListOf(
        TEMP_UPPER[random.nextInt(TEMP_UPPER.length)],
        TEMP_LOWER[random.nextInt(TEMP_LOWER.length)],
        TEMP_DIGITS[random.nextInt(TEMP_DIGITS.length)],
        TEMP_SYMBOLS[random.nextInt(TEMP_SYMBOLS.length)],
    )
    while (chars.size < TEMP_PASSWORD_LENGTH) chars += all[random.nextInt(all.length)]
    for (index in chars.lastIndex downTo 1) {
        val swap = random.nextInt(index + 1)
        val current = chars[index]
        chars[index] = chars[swap]
        chars[swap] = current
    }
    return chars.joinToString("")
}

@Composable
fun AdminAccountForm(
    draftState: AccountRegistrationDraftState,
    carregando: Boolean = false,
    initialProfile: AccountProfile = AccountProfile.SUPERVISOR_A,
    showHeader: Boolean = true,
    onSubmit: (NewAccountInput) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val secureRandom = remember { SecureRandom() }
    val draft = draftState.draft

    val supervisorSelected = draft.perfil.startsWith("SUPERVISOR") || initialProfile.supervisor && draft.perfil.isBlank()
    val selectedShift = draft.perfil.substringAfter("SUPERVISOR_", "A").takeIf { it in setOf("A", "B", "C", "D") } ?: "A"
    val selectedProfile = if (supervisorSelected) AccountProfile.supervisorForShift(selectedShift) else AccountProfile.ADMIN

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
        supervisorSelected && draft.senha.isBlank() -> "Gere a senha temporária para continuar."
        !passwordLongEnough -> if (supervisorSelected) "Gere novamente a senha temporária." else "A senha deve ter pelo menos 10 caracteres."
        !passwordHasLetter || !passwordHasDigit -> "A senha deve combinar letras e números."
        !supervisorSelected && !passwordsMatch -> "As senhas não coincidem."
        else -> null
    }

    fun selectSupervisor(shift: String = selectedShift) {
        val profile = AccountProfile.supervisorForShift(shift)
        // Ao mudar perfil/turno descartamos qualquer senha temporária anterior.
        draftState.update(
            draft.copy(
                perfil = profile.name,
                senha = "",
                confirmarSenha = "",
                erroLocal = null,
            ),
        )
    }

    fun selectAdmin() {
        draftState.update(
            draft.copy(
                perfil = AccountProfile.ADMIN.name,
                senha = "",
                confirmarSenha = "",
                erroLocal = null,
            ),
        )
    }

    fun generatePassword() {
        if (carregando) return
        val generated = generateTemporaryPassword(secureRandom)
        draftState.update(
            draft.copy(
                senha = generated,
                confirmarSenha = generated,
                erroLocal = null,
            ),
        )
    }

    fun submit() {
        draftState.setValidationError(validationError)
        if (validationError == null && !carregando) {
            focusManager.clearFocus()
            draftState.markSubmitted()
            onSubmit(
                NewAccountInput(
                    nome = cleanName,
                    email = cleanEmail.lowercase(),
                    senha = draft.senha,
                    perfil = selectedProfile,
                ),
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        if (showHeader) {
            PontoCafeHeader("Nova conta de acesso")
            Text(
                "Cadastre o acesso e defina o turno operacional quando o perfil for Supervisor.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle(
            title = "Perfil de acesso",
            subtitle = "Supervisor usa senha temporária e troca obrigatória no primeiro acesso.",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            ProfileChoiceCard(
                title = "Supervisor",
                description = "Operação diária · turno obrigatório",
                selected = supervisorSelected,
                onClick = { selectSupervisor() },
                modifier = Modifier.weight(1f),
                enabled = !carregando,
            )
            ProfileChoiceCard(
                title = "Administrador",
                description = "Controle total do sistema",
                selected = !supervisorSelected,
                onClick = ::selectAdmin,
                modifier = Modifier.weight(1f),
                enabled = !carregando,
            )
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
                    singleLine = true,
                    enabled = !carregando,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
                OutlinedTextField(
                    value = draft.email,
                    onValueChange = { draftState.update(draft.copy(email = it, erroLocal = null)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail de acesso") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    enabled = !carregando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                )
            }
        }

        AnimatedVisibility(
            visible = supervisorSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md)) {
                PcSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Text("Turno do Supervisor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                        ) {
                            listOf("A", "B", "C", "D").forEach { shift ->
                                val selected = selectedShift == shift
                                if (selected) {
                                    PcPrimaryButton(
                                        text = "Turno $shift",
                                        onClick = { selectSupervisor(shift) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !carregando,
                                    )
                                } else {
                                    PcSecondaryButton(
                                        text = shift,
                                        onClick = { selectSupervisor(shift) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !carregando,
                                    )
                                }
                            }
                        }
                    }
                }

                PcSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Text("Senha temporária", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "O sistema gera uma senha temporária. O Supervisor será obrigado a substituí-la no primeiro login.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PcPrimaryButton(
                            text = if (draft.senha.isBlank()) "Gerar senha temporária" else "Gerar outra senha",
                            icon = Icons.Default.Key,
                            onClick = ::generatePassword,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !carregando,
                        )

                        AnimatedVisibility(
                            visible = draft.senha.isNotBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(PontoCafeSpacing.md),
                                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                                    ) {
                                        Text("Senha temporária gerada", fontWeight = FontWeight.Bold)
                                        Text(draft.senha, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Copie antes de concluir. Esta senha não será armazenada em texto puro.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        PcSecondaryButton(
                                            text = "Copiar senha temporária",
                                            icon = Icons.Default.ContentCopy,
                                            onClick = { clipboard.setText(AnnotatedString(draft.senha)) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                PcFeedbackBanner(
                                    message = "No primeiro acesso, o Supervisor verá obrigatoriamente a tela para criar uma nova senha.",
                                    tone = PontoCafeTone.WARNING,
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !supervisorSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PcSectionSurface {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Text("Segurança do Administrador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SecurePasswordField(
                        value = draft.senha,
                        onValueChange = { draftState.update(draft.copy(senha = it, erroLocal = null)) },
                        label = "Senha",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !carregando,
                        supportingText = "Mínimo de 10 caracteres, com letras e números",
                        imeAction = ImeAction.Next,
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    )
                    SecurePasswordField(
                        value = draft.confirmarSenha,
                        onValueChange = { draftState.update(draft.copy(confirmarSenha = it, erroLocal = null)) },
                        label = "Confirmar senha",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !carregando,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        isError = draft.confirmarSenha.isNotBlank() && !passwordsMatch,
                    )
                }
            }
        }

        PcFeedbackBanner(
            message = draft.erroLocal,
            tone = PontoCafeTone.DANGER,
            onDismiss = { draftState.setValidationError(null) },
        )

        PcPrimaryButton(
            text = if (supervisorSelected) "Criar Supervisor · Turno $selectedShift" else "Criar Administrador",
            icon = Icons.Default.AdminPanelSettings,
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = validationError == null,
            loading = carregando,
        )
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusPill(
                text = if (selected) "Selecionado" else "Selecionar",
                tone = if (selected) PontoCafeTone.SUCCESS else PontoCafeTone.NEUTRAL,
            )
        }
    }
}
