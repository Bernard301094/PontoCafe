package com.pontocafe.app.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.pontocafe.app.AdminViewModel

@Composable
fun FirstAdminSetupScreen(viewModel: AdminViewModel, onClose: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var chave by remember { mutableStateOf("") }
    var erroLocal by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    val state = viewModel.state

    fun validateAccount(): String? = when {
        nome.trim().length < 2 -> "Informe o nome do administrador."
        !email.contains('@') -> "Informe um e-mail válido."
        senha.length < 10 -> "A senha deve ter pelo menos 10 caracteres."
        senha != confirmar -> "As senhas não coincidem."
        else -> null
    }

    fun advanceToSecurity() {
        erroLocal = validateAccount()
        if (erroLocal == null) {
            focusManager.clearFocus()
            step = 1
        }
    }

    fun createAdministrator() {
        erroLocal = when {
            chave.length < 16 -> "Informe a chave de instalação válida."
            else -> validateAccount()
        }
        if (erroLocal == null && !state.carregando && state.instalacaoConfigurada) {
            focusManager.clearFocus()
            viewModel.criarPrimeiroAdmin(nome, email, senha, chave)
        }
    }

    PontoCafeResponsivePage(maxContentWidth = PontoCafeDimensions.formContentWidth) { responsive ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
    ) {
        PontoCafeScreenHeader(
            title = "Primeiro administrador",
            onBack = onClose,
            backLabel = "Ponto Café",
            eyebrow = "Configuração inicial",
        )

        PcHeroCard(
            title = if (step == 0) "Crie a conta principal" else "Proteja a instalação",
            supportingText = if (step == 0) {
                "Esta conta terá acesso completo a pessoas, dispositivos, regras e auditoria."
            } else {
                "A chave de instalação confirma que somente uma pessoa autorizada pode concluir o primeiro cadastro."
            },
            icon = if (step == 0) Icons.Default.AdminPanelSettings else Icons.Default.Security,
            tone = PontoCafeTone.INFO,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            StepPill("1", "Conta", active = step == 0, completed = step > 0, modifier = Modifier.weight(1f))
            StepPill("2", "Segurança", active = step == 1, completed = false, modifier = Modifier.weight(1f))
        }

        if (!state.instalacaoConfigurada) {
            PcStateBanner(
                title = "Servidor ainda não preparado",
                supportingText = "A chave segura de instalação precisa ser configurada no backend antes de concluir este cadastro.",
                tone = PontoCafeTone.WARNING,
            )
        }

        when (step) {
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it; erroLocal = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome") },
                        singleLine = true,
                        enabled = !state.carregando,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; erroLocal = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-mail") },
                        singleLine = true,
                        enabled = !state.carregando,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                    )
                    SecurePasswordField(
                        value = senha,
                        onValueChange = { senha = it; erroLocal = null },
                        label = "Senha",
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
                        onValueChange = { confirmar = it; erroLocal = null },
                        label = "Confirmar senha",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { advanceToSecurity() }),
                    )
                }

                erroLocal?.let {
                    PcFeedbackBanner(it, PontoCafeTone.DANGER, onDismiss = { erroLocal = null })
                }

                PcPrimaryButton(
                    text = "Continuar",
                    onClick = ::advanceToSecurity,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.carregando,
                )
            }

            else -> {
                PcKeyValueCard(
                    title = "Conta que será criada",
                    rows = listOf(
                        "Nome" to nome.trim(),
                        "E-mail" to email.trim().lowercase(),
                        "Perfil" to "Administrador",
                    ),
                )

                SecurePasswordField(
                    value = chave,
                    onValueChange = { chave = it; erroLocal = null },
                    label = "Chave de instalação",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.carregando,
                    supportingText = "Fornecida na configuração segura do servidor",
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { createAdministrator() }),
                )

                erroLocal?.let {
                    PcFeedbackBanner(it, PontoCafeTone.DANGER, onDismiss = { erroLocal = null })
                }
                AdminFeedback(viewModel)

                PcPrimaryButton(
                    text = "Criar administrador",
                    onClick = ::createAdministrator,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.instalacaoConfigurada,
                    loading = state.carregando,
                )
                PcSecondaryButton(
                    text = "Voltar e revisar conta",
                    onClick = { step = 0; erroLocal = null },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.carregando,
                )
            }
        }

        PcSecondaryButton(
            text = "Voltar ao Ponto Café",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando,
        )
    }
    }
}

@Composable
private fun StepPill(
    number: String,
    label: String,
    active: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    PcStateBanner(
        title = "${if (completed) "✓" else number} · $label",
        supportingText = when {
            completed -> "Concluído"
            active -> "Etapa atual"
            else -> "Próxima etapa"
        },
        tone = when {
            completed -> PontoCafeTone.SUCCESS
            active -> PontoCafeTone.INFO
            else -> PontoCafeTone.NEUTRAL
        },
        modifier = modifier,
    )
}
