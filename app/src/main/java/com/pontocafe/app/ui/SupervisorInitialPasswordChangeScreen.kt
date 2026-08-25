package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.SupervisorPasswordChangeRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SupervisorInitialPasswordChangeScreen(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var completed by remember { mutableStateOf(false) }

    val longEnough = newPassword.length >= 10
    val hasLetter = newPassword.any(Char::isLetter)
    val hasDigit = newPassword.any(Char::isDigit)
    val matches = confirmation.isNotBlank() && newPassword == confirmation
    val valid = longEnough && hasLetter && hasDigit && matches

    LaunchedEffect(completed) {
        if (!completed) return@LaunchedEffect
        delay(650)
        viewModel.atualizarAoVivo()
    }

    PontoCafeResponsivePage(maxContentWidth = PontoCafeDimensions.compactContentWidth) { responsive ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = responsive.pagePadding,
                end = responsive.pagePadding,
                top = PontoCafeSpacing.lg,
                bottom = PontoCafeSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            item("header") {
                PontoCafeScreenHeader(
                    title = "Crie sua nova senha",
                    eyebrow = "Primeiro acesso · Supervisor",
                )
            }

            item("notice") {
                PcSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Troca obrigatória de segurança",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "A senha recebida do Administrador é temporária. Defina uma senha pessoal antes de acessar a operação.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PcFeedbackBanner(
                            message = "Depois da troca, a senha temporária deixa de funcionar e outras sessões desta conta são encerradas.",
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }
            }

            item("form") {
                PcSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        SecurePasswordField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                localError = null
                                SupervisorPasswordChangeRuntime.dismissError()
                            },
                            label = "Nova senha",
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !SupervisorPasswordChangeRuntime.submitting && !completed,
                            supportingText = "Mínimo de 10 caracteres, com letras e números",
                            imeAction = ImeAction.Next,
                        )
                        PasswordRule("Pelo menos 10 caracteres", longEnough)
                        PasswordRule("Contém letra", hasLetter)
                        PasswordRule("Contém número", hasDigit)
                        SecurePasswordField(
                            value = confirmation,
                            onValueChange = {
                                confirmation = it
                                localError = null
                                SupervisorPasswordChangeRuntime.dismissError()
                            },
                            label = "Confirmar nova senha",
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !SupervisorPasswordChangeRuntime.submitting && !completed,
                            isError = confirmation.isNotBlank() && !matches,
                            supportingText = if (confirmation.isBlank() || matches) null else "As senhas não coincidem",
                            imeAction = ImeAction.Done,
                        )
                    }
                }
            }

            item("feedback") {
                AnimatedVisibility(
                    visible = completed,
                    enter = fadeIn() + scaleIn(initialScale = 0.98f),
                    exit = fadeOut() + scaleOut(targetScale = 0.98f),
                ) {
                    PcFeedbackBanner(
                        message = "Senha atualizada com sucesso. Abrindo o perfil de Supervisor…",
                        tone = PontoCafeTone.SUCCESS,
                    )
                }
                if (!completed) {
                    PcFeedbackBanner(
                        message = localError ?: SupervisorPasswordChangeRuntime.error,
                        tone = PontoCafeTone.DANGER,
                        onDismiss = {
                            localError = null
                            SupervisorPasswordChangeRuntime.dismissError()
                        },
                    )
                }
            }

            item("actions") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    PcPrimaryButton(
                        text = if (completed) "Senha atualizada" else "Salvar nova senha",
                        icon = if (completed) Icons.Default.CheckCircle else Icons.Default.LockReset,
                        onClick = {
                            if (!valid) {
                                localError = "Revise os requisitos e confirme a mesma senha."
                                return@PcPrimaryButton
                            }
                            scope.launch {
                                val ok = SupervisorPasswordChangeRuntime.submit(newPassword)
                                if (ok) {
                                    newPassword = ""
                                    confirmation = ""
                                    completed = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = valid && !completed,
                        loading = SupervisorPasswordChangeRuntime.submitting,
                    )
                    PcSecondaryButton(
                        text = "Sair desta conta",
                        onClick = {
                            newPassword = ""
                            confirmation = ""
                            viewModel.sair()
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !SupervisorPasswordChangeRuntime.submitting,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordRule(text: String, ok: Boolean) {
    Text(
        text = if (ok) "✓ $text" else "• $text",
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) LocalPontoCafeSemanticColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
