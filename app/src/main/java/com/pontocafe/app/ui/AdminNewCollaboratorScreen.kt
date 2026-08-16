package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.FormDraftRegistry
import com.pontocafe.app.trackCollaboratorDraftSubmission

private val CollaboratorShiftOptions = listOf("A", "B", "C")

@Composable
fun AdminNewCollaboratorScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    val draftState = remember(viewModel) { FormDraftRegistry.adminCollaborator(viewModel) }
    val draft = draftState.draft

    LaunchedEffect(Unit) {
        draftState.prepareForDisplay(serverError = state.erro, loading = state.carregando)
    }
    LaunchedEffect(state.erro) {
        if (state.erro != null) draftState.markServerFailure()
    }

    val sectorSuggestions = remember(state.colaboradores, draft.setor) {
        buildList {
            if (draft.setor.isNotBlank()) add(draft.setor.trim())
            addAll(
                state.colaboradores
                    .mapNotNull { it.setor?.trim() }
                    .filter { it.isNotBlank() },
            )
        }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
            .take(10)
    }

    val cleanName = draft.nome.trim()
    val cleanSector = draft.setor.trim()
    val cleanShift = draft.turno.trim().uppercase()
    val readyToSubmit = cleanName.length >= 2 && cleanSector.isNotBlank() && cleanShift in CollaboratorShiftOptions

    val completionHint = when {
        cleanName.length < 2 -> "Informe o nome completo para continuar."
        cleanSector.isBlank() -> "Informe ou selecione o setor."
        cleanShift !in CollaboratorShiftOptions -> "Selecione o turno do colaborador."
        else -> "Tudo pronto. O próximo passo será o cadastro facial."
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            CollaboratorBottomActions(
                enabled = readyToSubmit && !state.carregando,
                loading = state.carregando,
                hint = completionHint,
                onSave = {
                    draftState.markSubmitted()
                    viewModel.trackCollaboratorDraftSubmission(draftState)
                    viewModel.criarColaborador(cleanName, cleanSector, cleanShift)
                },
                onSupervisor = viewModel::abrirNovaConta,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = PontoCafeSpacing.lg,
                end = PontoCafeSpacing.lg,
                top = PontoCafeSpacing.md,
                bottom = PontoCafeSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            item("header") {
                PontoCafeScreenHeader(
                    title = "Novo colaborador",
                    eyebrow = "Ponto Café",
                    onBack = {
                        draftState.reset()
                        viewModel.voltarColaboradores()
                    },
                    backLabel = "Colaboradores",
                )
            }

            item("context") {
                CollaboratorContextCard()
            }

            item("feedback") {
                AdminFeedback(viewModel)
            }

            item("form") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
                    border = BorderStroke(1.dp, PontoCafePremium.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(PontoCafeSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                            Text(
                                "NOME COMPLETO",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedTextField(
                                value = draft.nome,
                                onValueChange = { draftState.update(draft.copy(nome = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Digite o nome completo") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                supportingText = { Text("Use o nome completo conforme o cadastro da empresa.") },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                            Text(
                                "SETOR",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedTextField(
                                value = draft.setor,
                                onValueChange = { draftState.update(draft.copy(setor = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Ex.: Produção") },
                                leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = null) },
                                supportingText = {
                                    Text("Você pode digitar um novo setor ou usar uma sugestão abaixo.")
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            )

                            if (sectorSuggestions.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                                    contentPadding = PaddingValues(top = 2.dp),
                                ) {
                                    items(sectorSuggestions, key = { "sector-$it" }) { sector ->
                                        FilterChip(
                                            selected = cleanSector.equals(sector, ignoreCase = true),
                                            onClick = { draftState.update(draft.copy(setor = sector)) },
                                            label = { Text(sector) },
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                            Text(
                                "TURNO",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Escolha o turno. Isso evita abreviações diferentes no cadastro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                            ) {
                                CollaboratorShiftOptions.forEach { shift ->
                                    ShiftOptionCard(
                                        shift = shift,
                                        selected = cleanShift == shift,
                                        modifier = Modifier.weight(1f),
                                        onClick = { draftState.update(draft.copy(turno = shift)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollaboratorContextCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Cadastro para o modo Ponto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Use esta tela somente para pessoas que vão registrar a pausa por reconhecimento facial.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    "Supervisor e Administrador são contas de acesso, não colaboradores do reconhecimento facial.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShiftOptionCard(
    shift: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else PontoCafePremium.borderSoft,
        ),
        shadowElevation = if (selected) 4.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Turno",
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                shift,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CollaboratorBottomActions(
    enabled: Boolean,
    loading: Boolean,
    hint: String,
    onSave: () -> Unit,
    onSupervisor: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.lg, vertical = PontoCafeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (enabled) LocalPontoCafeSemanticColors.current.success
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    hint,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    if (loading) "Salvando..." else "Salvar e cadastrar rosto",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            TextButton(
                onClick = onSupervisor,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Cadastrar supervisor / conta de acesso",
                    modifier = Modifier.padding(start = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
