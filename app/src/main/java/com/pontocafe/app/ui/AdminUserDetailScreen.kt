package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminUserDetailScreen(viewModel: AdminViewModel) {
    val focusManager = LocalFocusManager.current
    val state = viewModel.state
    val user = state.selecionado ?: return
    var novaSenha by remember(user.id) { mutableStateOf("") }
    var confirmar by remember(user.id) { mutableStateOf("") }
    var erroLocal by remember(user.id) { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember(user.id) { mutableStateOf(false) }

    fun resetPassword() {
        erroLocal = when {
            novaSenha.length < 10 -> "A nova senha deve ter pelo menos 10 caracteres."
            novaSenha != confirmar -> "As senhas não coincidem."
            else -> null
        }
        if (erroLocal == null && !state.carregando) {
            focusManager.clearFocus()
            viewModel.redefinirSenha(user.id, novaSenha)
        }
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
            subtitle = "Controle o status e o nível de permissão desta conta.",
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

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stack = maxWidth < 480.dp || LocalDensity.current.fontScale >= 1.3f
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    PcSecondaryButton(
                        text = "Tornar Supervisor",
                        onClick = { viewModel.alterarPerfil(user, "SUPERVISOR") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando && user.perfil != "SUPERVISOR",
                    )
                    PcSecondaryButton(
                        text = "Tornar Administrador",
                        onClick = { viewModel.alterarPerfil(user, "ADMIN") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando && user.perfil != "ADMIN",
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    PcSecondaryButton(
                        text = "Tornar Supervisor",
                        onClick = { viewModel.alterarPerfil(user, "SUPERVISOR") },
                        modifier = Modifier.weight(1f),
                        enabled = !state.carregando && user.perfil != "SUPERVISOR",
                    )
                    PcSecondaryButton(
                        text = "Tornar Administrador",
                        onClick = { viewModel.alterarPerfil(user, "ADMIN") },
                        modifier = Modifier.weight(1f),
                        enabled = !state.carregando && user.perfil != "ADMIN",
                    )
                }
            }
        }

        SectionTitle(
            title = "Redefinir senha",
            subtitle = "Ao redefinir a senha, as sessões atuais desta conta serão encerradas.",
        )
        SecurePasswordField(
            value = novaSenha,
            onValueChange = { novaSenha = it },
            label = "Nova senha",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando,
            supportingText = "Mínimo de 10 caracteres",
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        )
        SecurePasswordField(
            value = confirmar,
            onValueChange = { confirmar = it },
            label = "Confirmar nova senha",
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { resetPassword() }),
            isError = confirmar.isNotBlank() && novaSenha != confirmar,
        )
        erroLocal?.let {
            PcFeedbackBanner(it, PontoCafeTone.DANGER, onDismiss = { erroLocal = null })
        }
        PcPrimaryButton(
            text = "Redefinir senha",
            onClick = ::resetPassword,
            modifier = Modifier.fillMaxWidth(),
            enabled = novaSenha.length >= 10 && novaSenha == confirmar,
            loading = state.carregando,
        )

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
