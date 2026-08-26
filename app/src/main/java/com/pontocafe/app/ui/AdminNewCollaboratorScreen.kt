package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.FormDraftRegistry
import com.pontocafe.app.trackCollaboratorDraftSubmission

private val CollaboratorShiftOptions = listOf("A", "B", "C")

@Composable
fun AdminNewCollaboratorScreen(viewModel: AdminViewModel) {
    val focusManager = LocalFocusManager.current
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
        }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.take(10)
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

    PcHeroPage(
        heroContent = {
            PcHeroZoneScreenHeader(
                title = "Novo colaborador",
                eyebrow = "Administrador",
                onBack = {
                    draftState.reset()
                    viewModel.voltarColaboradores()
                },
                backLabel = "Pessoas",
            )
            Text(
                "Cadastro para reconhecimento facial no modo Ponto",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = 760.dp) { responsive ->
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                CollaboratorBottomActions(
                    enabled = readyToSubmit && !state.carregando,
                    loading = state.carregando,
                    hint = completionHint,
                    horizontalPadding = responsive.pagePadding,
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
                    .imePadding()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.md,
                    bottom = PontoCafeSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                item("context") {
                    PcHeroCard(
                        title = "Cadastro para o modo Ponto",
                        supportingText = "Cadastre somente pessoas que usarão reconhecimento facial para registrar a pausa. Contas de Supervisor e Administrador ficam separadas.",
                        icon = Icons.Default.Face,
                        tone = PontoCafeTone.INFO,
                    )
                }

                item("feedback") { AdminFeedback(viewModel) }

                item("form") {
                    PcSectionSurface {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                Text("Nome completo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = draft.nome,
                                    onValueChange = { draftState.update(draft.copy(nome = it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    supportingText = { Text("Use o nome completo conforme o cadastro da empresa.") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Next,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    ),
                                )
                            }

                            val sectorField = @Composable {
                                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                    Text("Setor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = draft.setor,
                                        onValueChange = { draftState.update(draft.copy(setor = it)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Setor") },
                                        placeholder = { Text("Ex.: Produção") },
                                        leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = null) },
                                        supportingText = { Text("Digite um novo setor ou escolha uma sugestão.") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Words,
                                            imeAction = ImeAction.Done,
                                        ),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    )
                                    if (sectorSuggestions.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
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
                            }

                            val shiftField = @Composable {
                                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                    Text("Turno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Selecione o turno para manter o cadastro padronizado.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                                    ) {
                                        items(CollaboratorShiftOptions, key = { "shift-$it" }) { shift ->
                                            FilterChip(
                                                selected = cleanShift == shift,
                                                onClick = { draftState.update(draft.copy(turno = shift)) },
                                                label = { Text("Turno $shift") },
                                                leadingIcon = if (cleanShift == shift) {
                                                    { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                                } else null,
                                            )
                                        }
                                    }
                                }
                            }

                            // Setor e turno cabem lado a lado em telas médias/expandidas
                            // -- em vez de empilhar dois campos curtos ocupando a largura
                            // toda de um tablet, aproveitamos o espaço horizontal.
                            if (responsive.supportsTwoColumns) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
                                ) {
                                    Box(modifier = Modifier.weight(1f)) { sectorField() }
                                    Box(modifier = Modifier.weight(1f)) { shiftField() }
                                }
                            } else {
                                sectorField()
                                shiftField()
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
private fun CollaboratorBottomActions(
    enabled: Boolean,
    loading: Boolean,
    hint: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onSave: () -> Unit,
    onSupervisor: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = PontoCafeSpacing.sm),
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
                    tint = if (enabled) LocalPontoCafeSemanticColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    hint,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PcPrimaryButton(
                text = "Salvar e cadastrar rosto",
                icon = Icons.Default.Face,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                loading = loading,
            )

            TextButton(
                onClick = onSupervisor,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Cadastrar Supervisor / conta de acesso", modifier = Modifier.padding(start = PontoCafeSpacing.xs))
            }
        }
    }
}
