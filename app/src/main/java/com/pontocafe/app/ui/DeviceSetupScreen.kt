package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

private fun looksLikeActivationToken(text: String): Boolean =
    text.length == 10 && text.all { it.isLetterOrDigit() }

@Composable
fun DeviceSetupScreen(
    viewModel: PontoCafeViewModel,
    onAdminClick: () -> Unit = {},
    onSupervisorClick: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    var token by rememberSaveable { mutableStateOf("") }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }
    val loading = viewModel.state.carregando

    // Sugestão de colar só aparece se o conteúdo da área de transferência já
    // parece um código de ativação válido (10 caracteres alfanuméricos) — não
    // preenche sozinho, é sempre uma sugestão que a pessoa confirma com um toque.
    LaunchedEffect(Unit) {
        val clipped = clipboard.getText()?.text?.trim()?.uppercase()
        if (clipped != null && looksLikeActivationToken(clipped) && clipped != token) {
            clipboardSuggestion = clipped
        }
    }

    fun activate() {
        if (token.length != 10 || loading) return
        focusManager.clearFocus()
        viewModel.configurarDispositivo(token)
    }

    PontoCafeResponsivePage(maxContentWidth = 900.dp) { responsive ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = responsive.pagePadding, vertical = PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(
                if (responsive.useCompactVerticalLayout) PontoCafeSpacing.md else PontoCafeSpacing.lg,
            ),
        ) {
        PontoCafeScreenHeader(title = "Ativar dispositivo", eyebrow = "Configuração inicial")

        val guidancePane: @Composable () -> Unit = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(PontoCafeSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Vincule este aparelho ao Ponto Café",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        "O código curto é usado uma única vez. Depois, o Android guarda uma credencial longa e protegida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                    HorizontalDividerTint()
                    GuidanceStep(1, "Abra Administrador › Dispositivos neste ou em outro aparelho já ativado.")
                    GuidanceStep(2, "Gere um código de ativação de 10 caracteres para este aparelho.")
                    GuidanceStep(3, "Digite (ou cole) o código ao lado e toque em Ativar.")
                }
            }
        }

        val formPane: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                SectionTitle("Código de ativação", "Gerado pelo Administrador na gestão de dispositivos.")

                clipboardSuggestion?.let { suggestion ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Código copiado detectado: $suggestion",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            TextButton(onClick = {
                                token = suggestion
                                clipboardSuggestion = null
                            }) {
                                Text("Colar")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { value ->
                        token = value.filter { it.isLetterOrDigit() }.take(10)
                        if (token == clipboardSuggestion) clipboardSuggestion = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Código de 10 caracteres") },
                    supportingText = { Text("${token.length}/10") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { activate() }),
                    singleLine = true,
                )
                PcPrimaryButton(
                    text = "Ativar este aparelho",
                    onClick = ::activate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = token.length == 10,
                    loading = loading,
                )

                MessageCard(viewModel)
            }
        }

        if (responsive.supportsTwoColumns) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                Box(modifier = Modifier.weight(1f)) { guidancePane() }
                Box(modifier = Modifier.weight(1f)) { formPane() }
            }
        } else {
            guidancePane()
            formPane()
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                Modifier.padding(PontoCafeSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Text("Acesso de gestão", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Administrador e Supervisor podem entrar mesmo antes deste aparelho ser ativado como Ponto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PcSecondaryButton(
                    text = "Entrar como Administrador",
                    onClick = onAdminClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                PcSecondaryButton(
                    text = "Entrar como Supervisor",
                    onClick = onSupervisorClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    }
}

@Composable
private fun HorizontalDividerTint() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f))
}

@Composable
private fun GuidanceStep(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
    }
}
