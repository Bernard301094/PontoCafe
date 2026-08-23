package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import java.security.SecureRandom

private const val RESET_TEMP_LENGTH = 16
private const val RESET_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val RESET_LOWER = "abcdefghijkmnopqrstuvwxyz"
private const val RESET_DIGITS = "23456789"
private const val RESET_SYMBOLS = "!@#$%*-_"

private fun generateResetTemporaryPassword(random: SecureRandom): String {
    val all = RESET_UPPER + RESET_LOWER + RESET_DIGITS + RESET_SYMBOLS
    val chars = mutableListOf(
        RESET_UPPER[random.nextInt(RESET_UPPER.length)],
        RESET_LOWER[random.nextInt(RESET_LOWER.length)],
        RESET_DIGITS[random.nextInt(RESET_DIGITS.length)],
        RESET_SYMBOLS[random.nextInt(RESET_SYMBOLS.length)],
    )
    while (chars.size < RESET_TEMP_LENGTH) chars += all[random.nextInt(all.length)]
    for (index in chars.lastIndex downTo 1) {
        val swap = random.nextInt(index + 1)
        val value = chars[index]
        chars[index] = chars[swap]
        chars[swap] = value
    }
    return chars.joinToString("")
}

@Composable
fun AdminUserDetailScreen(viewModel: AdminViewModel) {
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val secureRandom = remember { SecureRandom() }
    val state = viewModel.state
    val user = state.selecionado ?: return
    val supervisor = user.perfil.equals("SUPERVISOR", ignoreCase = true)

    var novaSenha by remember(user.id) { mutableStateOf("") }
    var confirmar by remember(user.id) { mutableStateOf("") }
    var erroLocal by remember(user.id) { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember(user.id) { mutableStateOf(false) }
    var senhaTemporariaPendente by remember(user.id) { mutableStateOf<String?>(null) }
    var senhaTemporariaConfirmada by remember(user.id) { mutableStateOf<String?>(null) }
    var aguardandoResetSupervisor by remember(user.id) { mutableStateOf(false) }

    LaunchedEffect(state.mensagem, aguardandoResetSupervisor) {
        if (aguardandoResetSupervisor && state.mensagem?.contains("Senha redefinida", ignoreCase = true) == true) {
            senhaTemporariaConfirmada = senhaTemporariaPendente
            aguardandoResetSupervisor = false
        }
    }
    LaunchedEffect(state.erro, aguardandoResetSupervisor) {
        if (aguardandoResetSupervisor && state.erro != null) aguardandoResetSupervisor = false
    }

    fun resetAdminPassword() {
        erroLocal = when {
            novaSenha.length < 10 -> "A nova senha deve ter pelo menos 10 caracteres."
            novaSenha.none(Char::isLetter) || novaSenha.none(Char::isDigit) -> "A senha deve combinar letras e números."
            novaSenha != confirmar -> "As senhas não coincidem."
            else -> null
        }
        if (erroLocal == null && !state.carregando) {
            focusManager.clearFocus()
            viewModel.redefinirSenha(user.id, novaSenha)
        }
    }

    fun generateSupervisorReset() {
        if (state.carregando) return
        senhaTemporariaPendente = generateResetTemporaryPassword(secureRandom)
        senhaTemporariaConfirmada = null
        erroLocal = null
    }

    fun applySupervisorReset() {
        val temporary = senhaTemporariaPendente ?: return
        if (state.carregando) return
        aguardandoResetSupervisor = true
        viewModel.redefinirSenha(user.id, temporary)
    }

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
            subtitle = "Controle o status desta conta. Alterações importantes encerram sessões quando necessário.",
        )

        if (user.ativo) {
            PcSecondaryButton(
                text = "Desativar conta",
                onClick = { viewModel.alterarAtivo(user) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
                contentColor = MaterialTheme.colorScheme.error,
            )
        } else {
            PcPrimaryButton(
                text = "Reativar conta",
                onClick = { viewModel.alterarAtivo(user) },
                modifier = Modifier.fillMaxWidth(),
                loading = state.carregando,
            )
        }

        if (supervisor) {
            PcSecondaryButton(
                text = "Tornar Administrador",
                onClick = { viewModel.alterarPerfil(user, "ADMIN") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            )
        } else {
            PcFeedbackBanner(
                message = "Para criar um Supervisor, use “Nova conta” e informe obrigatoriamente o turno A, B, C ou D. Isso garante o novo fluxo de senha temporária.",
                tone = PontoCafeTone.INFO,
            )
        }

        SectionTitle(
            title = if (supervisor) "Redefinir acesso do Supervisor" else "Redefinir senha",
            subtitle = if (supervisor) {
                "Uma nova senha temporária será aplicada, todas as sessões atuais serão encerradas e a troca será obrigatória no próximo login."
            } else {
                "Ao redefinir a senha, as sessões atuais desta conta serão encerradas."
            },
        )

        if (supervisor) {
            PcPrimaryButton(
                text = if (senhaTemporariaPendente == null) "Gerar nova senha temporária" else "Gerar outra senha temporária",
                icon = Icons.Default.Key,
                onClick = ::generateSupervisorReset,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            )

            AnimatedVisibility(
                visible = senhaTemporariaPendente != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val temporary = senhaTemporariaPendente.orEmpty()
                PcSectionSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        Text(
                            if (senhaTemporariaConfirmada == temporary) "Nova senha temporária aplicada" else "Senha temporária pronta para aplicar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            temporary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        PcSecondaryButton(
                            text = "Copiar senha",
                            icon = Icons.Default.ContentCopy,
                            onClick = { clipboard.setText(AnnotatedString(temporary)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (senhaTemporariaConfirmada == temporary) {
                            PcFeedbackBanner(
                                message = "Senha temporária aplicada com sucesso. Entregue-a ao Supervisor; no próximo login ele será obrigado a criar uma senha pessoal.",
                                tone = PontoCafeTone.SUCCESS,
                            )
                        } else {
                            PcFeedbackBanner(
                                message = "Ainda não compartilhe esta senha. Primeiro aplique a alteração no servidor.",
                                tone = PontoCafeTone.WARNING,
                            )
                            PcPrimaryButton(
                                text = "Aplicar senha e encerrar sessões",
                                onClick = ::applySupervisorReset,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = temporary.isNotBlank(),
                                loading = state.carregando && aguardandoResetSupervisor,
                            )
                        }
                    }
                }
            }
        } else {
            SecurePasswordField(
                value = novaSenha,
                onValueChange = { novaSenha = it; erroLocal = null },
                label = "Nova senha",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
                supportingText = "Mínimo de 10 caracteres, com letras e números",
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            SecurePasswordField(
                value = confirmar,
                onValueChange = { confirmar = it; erroLocal = null },
                label = "Confirmar nova senha",
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { resetAdminPassword() }),
                isError = confirmar.isNotBlank() && novaSenha != confirmar,
            )
            PcFeedbackBanner(
                message = erroLocal,
                tone = PontoCafeTone.DANGER,
                onDismiss = { erroLocal = null },
            )
            PcPrimaryButton(
                text = "Redefinir senha",
                onClick = ::resetAdminPassword,
                modifier = Modifier.fillMaxWidth(),
                enabled = novaSenha.length >= 10 && novaSenha == confirmar,
                loading = state.carregando,
            )
        }

        SectionTitle("Zona de risco")
        PcFeedbackBanner(
            message = "Excluir uma conta remove definitivamente o acesso e as sessões dessa pessoa. Esta ação não pode ser desfeita.",
            tone = PontoCafeTone.WARNING,
        )

        AnimatedVisibility(
            visible = confirmarExclusao,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Excluir conta definitivamente?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Text(
                        "A conta de ${user.nome} e todas as sessões de acesso serão removidas. O histórico operacional independente da conta permanece preservado.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    PcFormActions(
                        primaryText = "Excluir conta",
                        onPrimary = { viewModel.excluirUsuario(user) },
                        primaryEnabled = !state.carregando,
                        primaryLoading = state.carregando,
                        primaryDanger = true,
                        secondaryText = "Cancelar",
                        onSecondary = { confirmarExclusao = false },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !confirmarExclusao,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PcDangerButton(
                text = "Excluir conta",
                icon = Icons.Default.DeleteForever,
                onClick = { confirmarExclusao = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            )
        }
    }
}
