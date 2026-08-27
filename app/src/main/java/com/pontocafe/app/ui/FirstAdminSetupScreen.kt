package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

    PcHeroPage(
        heroContent = {
            PcHeroZoneScreenHeader(
                title = "Primeiro administrador",
                onBack = onClose,
                backLabel = "Ponto Café",
                eyebrow = "Configuração inicial",
            )
            Text(
                if (step == 0) "Etapa 1 de 2 · Crie a conta principal" else "Etapa 2 de 2 · Proteja a instalação",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = PontoCafeDimensions.formContentWidth) { responsive ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
        ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
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

                SetupHorizontalStepper(
                    steps = listOf("Conta", "Segurança"),
                    currentStep = step,
                )
            }
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
                    if (senha.isNotEmpty()) {
                        PasswordStrengthChecklist(senha)
                    }
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

        // CTA fixo acima do teclado — antes rolava junto com o formulário e
        // podia ficar escondido atrás do IME em telas menores.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(modifier = Modifier.padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.md)) {
                if (step == 0) {
                    PcPrimaryButton(
                        text = "Continuar",
                        onClick = ::advanceToSecurity,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.carregando,
                    )
                } else {
                    PcPrimaryButton(
                        text = "Criar administrador",
                        onClick = ::createAdministrator,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.instalacaoConfigurada,
                        loading = state.carregando,
                    )
                }
            }
        }
    }
    }
    }
}

/**
 * Stepper horizontal com os passos reais desta tela (Conta → Segurança). Não
 * inventa uma terceira etapa "Organização" que não existe no fluxo real.
 */
@Composable
private fun SetupHorizontalStepper(
    steps: List<String>,
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, label ->
            val completed = index < currentStep
            val active = index == currentStep
            val dotColor = when {
                completed -> semantic.success
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (completed || active) dotColor else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (!completed && !active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (completed) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (index != steps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = PontoCafeSpacing.xs, vertical = 13.dp),
                    color = if (completed) semantic.success else MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

/**
 * Checklist meramente informativo: só o comprimento mínimo (10) é de fato
 * exigido por validateAccount(). Maiúscula/número/símbolo são sugestões de
 * força, não bloqueiam o botão Continuar — mostrar isso como requisito duro
 * seria enganoso, já que o backend não os valida.
 */
@Composable
private fun PasswordStrengthChecklist(password: String, modifier: Modifier = Modifier) {
    val semantic = LocalPontoCafeSemanticColors.current
    val requirements = listOf(
        "Pelo menos 10 caracteres" to (password.length >= 10),
        "Uma letra maiúscula" to password.any { it.isUpperCase() },
        "Um número" to password.any { it.isDigit() },
        "Um símbolo (ex: !@#$)" to password.any { !it.isLetterOrDigit() },
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        requirements.forEach { (label, met) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (met) "Atendido" else "Ainda não atendido",
                    tint = if (met) semantic.success else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
