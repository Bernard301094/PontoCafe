package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pontocafe.app.FormDraftRegistry
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.trackCollaboratorDraftSubmission

private val SupervisorShiftOptions = listOf("A", "B", "C")

@Composable
fun SupervisorNewCollaboratorPersistentScreen(viewModel: SupervisorViewModel) {
    val focusManager = LocalFocusManager.current
    val state = viewModel.state
    val draftState = remember(viewModel) { FormDraftRegistry.supervisorCollaborator(viewModel) }
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
            addAll(state.colaboradores.mapNotNull { it.setor?.trim() }.filter { it.isNotBlank() })
        }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.take(10)
    }
    val cleanName = draft.nome.trim()
    val cleanSector = draft.setor.trim()
    val cleanShift = draft.turno.trim().uppercase()
    val ready = cleanName.length >= 2 && cleanSector.isNotBlank() && cleanShift in SupervisorShiftOptions

    PcHeroPage(
        heroContent = {
            PcHeroZoneScreenHeader(
                title = "Novo colaborador",
                eyebrow = "Supervisor",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(
                start = responsive.pagePadding,
                end = responsive.pagePadding,
                top = PontoCafeSpacing.md,
                bottom = PontoCafeSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            item("hero") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    PcHeroCard(
                        title = "Cadastro para o modo Ponto",
                        supportingText = "Depois de salvar os dados, a câmera abrirá automaticamente para registrar as 5 amostras faciais.",
                        icon = Icons.Default.Face,
                        tone = PontoCafeTone.INFO,
                    )
                    UpcomingEnrollmentPreview()
                    if (cleanName.isNotBlank() || cleanSector.isNotBlank()) {
                        StatusPill("Rascunho salvo neste aparelho", PontoCafeTone.NEUTRAL)
                    }
                }
            }

            item("feedback") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    PcFeedbackBanner(
                        message = state.erro,
                        tone = PontoCafeTone.DANGER,
                        onDismiss = viewModel::limparAviso,
                    )
                    PcFeedbackBanner(
                        message = state.mensagem,
                        tone = PontoCafeTone.INFO,
                        onDismiss = viewModel::limparAviso,
                        autoDismissMillis = 4_000L,
                    )
                }
            }

            item("form") {
                PcSectionSurface {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
                    ) {
                        OutlinedTextField(
                            value = draft.nome,
                            onValueChange = { draftState.update(draft.copy(nome = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nome completo") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                        )
                        val sectorField = @Composable {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                OutlinedTextField(
                                    value = draft.setor,
                                    onValueChange = { draftState.update(draft.copy(setor = it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Setor") },
                                    leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                )
                                if (sectorSuggestions.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                                        items(sectorSuggestions, key = { "supervisor-sector-$it" }) { sector ->
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
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                                ) {
                                    items(SupervisorShiftOptions, key = { "supervisor-shift-$it" }) { shift ->
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

            item("status") {
                PcStateBanner(
                    title = if (ready) "Pronto para salvar" else "Complete os dados",
                    supportingText = when {
                        cleanName.length < 2 -> "Informe o nome completo."
                        cleanSector.isBlank() -> "Informe ou selecione o setor."
                        cleanShift !in SupervisorShiftOptions -> "Selecione o turno."
                        else -> "Ao continuar, abriremos o cadastro facial."
                    },
                    tone = if (ready) PontoCafeTone.SUCCESS else PontoCafeTone.NEUTRAL,
                )
            }

            item("save") {
                PcPrimaryButton(
                    text = "Salvar e cadastrar rosto",
                    icon = Icons.Default.Face,
                    onClick = {
                        draftState.markSubmitted()
                        viewModel.trackCollaboratorDraftSubmission(draftState)
                        viewModel.criarColaborador(cleanName, cleanSector, cleanShift)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ready,
                    loading = state.carregando,
                )
            }

            item("cancel") {
                PcSecondaryButton(
                    text = "Cancelar",
                    onClick = {
                        draftState.reset()
                        viewModel.voltarColaboradores()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.carregando,
                )
            }
        }
    }
    }
}
