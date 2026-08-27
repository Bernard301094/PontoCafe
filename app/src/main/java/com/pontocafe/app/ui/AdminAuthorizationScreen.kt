package com.pontocafe.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.ManualPunchResult
import com.pontocafe.app.ManualPunchType
import com.pontocafe.app.data.Colaborador

private enum class AdminAuthorizationMode { EXCECAO, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuthorizationScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var modo by rememberSaveable { mutableStateOf(AdminAuthorizationMode.EXCECAO) }
    var selecionado by remember { mutableStateOf<Colaborador?>(null) }
    var busca by rememberSaveable { mutableStateOf("") }
    var motivo by rememberSaveable { mutableStateOf("") }
    var confirmarAutorizacao by remember { mutableStateOf(false) }
    var confirmarCancelamento by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun trocarModo(novoModo: AdminAuthorizationMode) {
        modo = novoModo
        selecionado = null
        busca = ""
        motivo = ""
        viewModel.limparRegistroManual()
    }

    val query = busca.trim()
    val filtrados = state.colaboradores
        .asSequence()
        .filter { colaborador ->
            query.isBlank() ||
                colaborador.nome.contains(query, ignoreCase = true) ||
                colaborador.setor?.contains(query, ignoreCase = true) == true ||
                colaborador.turno?.contains(query, ignoreCase = true) == true
        }
        .sortedBy { it.nome.lowercase() }
        .toList()

    val motivoValido = motivo.trim().length >= 2
    val manual = modo == AdminAuthorizationMode.MANUAL
    val manualSucesso = state.manualPunchResult
    val podeAutorizar = selecionado != null && motivoValido && !state.carregando
    val showBottomAction = state.authorizationId == null && manualSucesso == null && selecionado != null

    if (confirmarAutorizacao && selecionado != null) {
        AlertDialog(
            onDismissRequest = { if (!state.carregando) confirmarAutorizacao = false },
            title = { Text(if (manual) "Confirmar registro manual" else "Confirmar autorização") },
            text = {
                PcDialogBody {
                    Text(
                        if (manual) "Registrar saída de ${selecionado!!.nome} agora?" else "Autorizar ${selecionado!!.nome}?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Motivo: ${motivo.trim()}")
                    Text(
                        if (manual) {
                            "Use somente quando o reconhecimento facial não identificar a pessoa dentro do horário normal. Fica registrado que esta saída foi feita manualmente."
                        } else {
                            "O período será definido pela hora oficial do servidor. A autorização expira automaticamente e só pode ser usada uma vez."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                PcPrimaryButton(
                    text = if (manual) "Registrar saída" else "Autorizar",
                    onClick = {
                        confirmarAutorizacao = false
                        selecionado?.let {
                            if (manual) viewModel.registrarPausaManual(it, motivo) else viewModel.autorizarPausa(it, motivo)
                        }
                    },
                    loading = state.carregando,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarAutorizacao = false },
                    enabled = !state.carregando,
                ) { Text("Voltar") }
            },
        )
    }

    if (confirmarCancelamento && selecionado != null) {
        AlertDialog(
            onDismissRequest = { if (!state.carregando) confirmarCancelamento = false },
            title = { Text("Cancelar autorização?") },
            text = {
                Text(
                    "${selecionado!!.nome} deixará de poder iniciar esta pausa fora do horário. Uma autorização já utilizada não pode ser cancelada.",
                )
            },
            confirmButton = {
                PcDangerButton(
                    text = "Cancelar autorização",
                    onClick = {
                        confirmarCancelamento = false
                        selecionado?.let(viewModel::cancelarAutorizacao)
                    },
                    loading = state.carregando,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarCancelamento = false },
                    enabled = !state.carregando,
                ) { Text("Manter autorização") }
            },
        )
    }

    PcHeroPage(
        heroContent = {
            PcHeroZoneScreenHeader(
                title = if (manual) "Registro manual de ponto" else "Autorizar pausa",
                onBack = viewModel::voltarHome,
                backLabel = "Painel",
                eyebrow = if (manual) "Reconhecimento indisponível" else "Fora do horário",
            )
            Text(
                when {
                    state.authorizationId != null || manualSucesso != null -> "Registrado"
                    selecionado == null -> "Passo 1 de 2 · Escolha o colaborador"
                    else -> "Passo 2 de 2 · Informe o motivo"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        },
    ) {
    PontoCafeResponsivePage(maxContentWidth = 840.dp) { responsive ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.lg,
                    bottom = if (showBottomAction) 164.dp else 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                if (state.authorizationId == null && manualSucesso == null) {
                    item(key = "mode-toggle") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AdminAuthorizationMode.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = modo == option,
                                    onClick = { trocarModo(option) },
                                    shape = SegmentedButtonDefaults.itemShape(index, AdminAuthorizationMode.entries.size),
                                ) {
                                    Text(if (option == AdminAuthorizationMode.MANUAL) "Registrar manualmente" else "Autorizar exceção")
                                }
                            }
                        }
                    }
                }

                item(key = "context") {
                    if (manual) {
                        PcHeroCard(
                            title = "Registro manual",
                            supportingText = "Use apenas quando o reconhecimento facial não identificar a pessoa dentro do horário normal. Fora do horário, use Autorizar exceção.",
                            icon = Icons.Default.AccessTime,
                            tone = PontoCafeTone.WARNING,
                        )
                    } else {
                        PcHeroCard(
                            title = "Autorização direta e temporária",
                            supportingText = "Depois de autorizar, o colaborador pode ir ao Ponto. O reconhecimento facial localizará a autorização automaticamente.",
                            icon = Icons.Default.AccessTime,
                            tone = PontoCafeTone.INFO,
                        )
                    }
                }

                item(key = "feedback") {
                    AdminFeedback(viewModel)
                }

                if (manualSucesso != null) {
                    item(key = "manual-granted") {
                        GrantedManualPunchCard(
                            result = manualSucesso,
                            onAnother = {
                                viewModel.limparRegistroManual()
                                selecionado = null
                                busca = ""
                                motivo = ""
                            },
                        )
                    }
                } else state.authorizationId?.let {
                    item(key = "authorization-granted") {
                        GrantedAuthorizationCard(
                            employeeName = state.authorizationEmployeeName ?: selecionado?.nome ?: "Colaborador",
                            period = state.authorizationPeriod,
                            expiresSeconds = state.authorizationExpirySeconds ?: 0,
                            loading = state.carregando,
                            onCancel = { confirmarCancelamento = true },
                            onAuthorizeAnother = {
                                viewModel.limparAutorizacao()
                                selecionado = null
                                busca = ""
                                motivo = ""
                            },
                        )
                    }
                } ?: run {
                    if (selecionado == null) {
                        item(key = "employee-step") {
                            StepHeader(
                                number = "1",
                                title = "Escolha o colaborador",
                                subtitle = "Busque pelo nome, setor ou turno.",
                            )
                        }

                        item(key = "search") {
                            val focusManager = LocalFocusManager.current
                            OutlinedTextField(
                                value = busca,
                                onValueChange = { busca = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Buscar colaborador") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = if (busca.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { busca = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                                        }
                                    }
                                } else {
                                    null
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                shape = MaterialTheme.shapes.large,
                            )
                        }

                        item(key = "result-count") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        if (query.isBlank()) "Colaboradores" else "Resultados",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        if (query.isBlank()) "Selecione quem receberá a exceção." else "Resultados para “$query”.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill(filtrados.size.toString(), PontoCafeTone.NEUTRAL)
                            }
                        }

                        if (filtrados.isEmpty()) {
                            item(key = "empty-search") {
                                EmptyAuthorizationSearch(query)
                            }
                        } else {
                            items(filtrados, key = { "authorization-${it.id}" }) { colaborador ->
                                Box(modifier = Modifier.animateItem()) {
                                    CollaboratorAuthorizationRow(
                                        collaborator = colaborador,
                                        onClick = {
                                            selecionado = colaborador
                                            busca = ""
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "employee-step-selected") {
                            StepHeader(
                                number = "1",
                                title = "Colaborador",
                                subtitle = if (manual) "Confirme quem terá a saída registrada." else "Confirme quem receberá a autorização.",
                                completed = true,
                            )
                        }

                        item(key = "selected-employee") {
                            SelectedCollaboratorCard(
                                collaborator = selecionado!!,
                                onChange = { selecionado = null },
                            )
                        }

                        item(key = "reason-step") {
                            StepHeader(
                                number = "2",
                                title = "Motivo",
                                subtitle = if (manual) {
                                    "Explique brevemente por que o registro precisa ser manual."
                                } else {
                                    "Explique brevemente por que a pausa precisa ocorrer fora do horário."
                                },
                            )
                        }

                        item(key = "reason") {
                            val focusManager = LocalFocusManager.current
                            OutlinedTextField(
                                value = motivo,
                                onValueChange = { motivo = it.take(300) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (manual) "Motivo do registro manual" else "Motivo da autorização") },
                                placeholder = {
                                    Text(
                                        if (manual) "Ex.: reconhecimento facial não identificou a pessoa" else "Ex.: atividade operacional terminou após o horário",
                                    )
                                },
                                minLines = 3,
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                shape = MaterialTheme.shapes.large,
                                isError = motivo.isNotBlank() && !motivoValido,
                                supportingText = {
                                    Text(
                                        "${motivo.length}/300",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (showBottomAction) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = responsive.pagePadding,
                            end = responsive.pagePadding,
                            top = PontoCafeSpacing.sm,
                            bottom = PontoCafeSpacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        if (!motivoValido) {
                            Text(
                                "Informe um motivo com pelo menos 2 caracteres.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PcPrimaryButton(
                            text = if (manual) "Registrar saída" else "Autorizar pausa",
                            icon = Icons.Default.CheckCircle,
                            onClick = { confirmarAutorizacao = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = podeAutorizar,
                            loading = state.carregando,
                        )
                    }
                }
            }

            PcScrollToTopFab(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = responsive.pagePadding,
                        bottom = if (showBottomAction) 112.dp else PontoCafeSpacing.md,
                    ),
            )
        }
    }
    }
}

@Composable
private fun StepHeader(
    number: String,
    title: String,
    subtitle: String,
    completed: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (completed) {
                LocalPontoCafeSemanticColors.current.successContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (completed) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = LocalPontoCafeSemanticColors.current.success,
                    )
                } else {
                    Text(
                        number,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollaboratorAuthorizationRow(
    collaborator: Colaborador,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPcPressScale(interactionSource)
    Card(
        modifier = Modifier.fillMaxWidth().pcPressScale(pressScale),
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            CollaboratorAvatar(
                name = collaborator.nome,
                avatarUrl = collaborator.avatarUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    collaborator.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                CollaboratorDetail(collaborator)
            }
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SelectedCollaboratorCard(
    collaborator: Colaborador,
    onChange: () -> Unit,
) {
    PcSectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            CollaboratorAvatar(
                name = collaborator.nome,
                avatarUrl = collaborator.avatarUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    collaborator.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                CollaboratorDetail(collaborator)
            }
            TextButton(onClick = onChange) { Text("Alterar") }
        }
    }
}

@Composable
private fun CollaboratorDetail(collaborator: Colaborador) {
    val detail = listOfNotNull(collaborator.setor, collaborator.turno)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    if (detail.isNotBlank()) {
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyAuthorizationSearch(query: String) {
    PcEmptyState(
        title = "Nenhum colaborador encontrado",
        supportingText = if (query.isBlank()) {
            "Não há colaboradores disponíveis para selecionar."
        } else {
            "Tente outro nome, setor ou turno."
        },
        icon = Icons.Default.Search,
    )
}

@Composable
private fun GrantedManualPunchCard(
    result: ManualPunchResult,
    onAnother: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = LocalPontoCafeSemanticColors.current.successContainer,
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = LocalPontoCafeSemanticColors.current.success,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (result.tipo == ManualPunchType.INICIO) "Saída registrada" else "Retorno registrado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Registrado manualmente às ${result.horarioLocal}",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPontoCafeSemanticColors.current.success,
                    )
                }
            }
            Text(
                result.colaboradorNome,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            PcPrimaryButton(
                text = "Registrar outra pessoa",
                onClick = onAnother,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GrantedAuthorizationCard(
    employeeName: String,
    period: String?,
    expiresSeconds: Int,
    loading: Boolean,
    onCancel: () -> Unit,
    onAuthorizeAnother: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = LocalPontoCafeSemanticColors.current.successContainer,
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = LocalPontoCafeSemanticColors.current.success,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Autorização concedida",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "O colaborador já pode bater o ponto",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPontoCafeSemanticColors.current.success,
                    )
                }
            }

            Text(employeeName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
            ) {
                Column(
                    modifier = Modifier.padding(PontoCafeSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    Text(
                        "Período: ${if (period == "MANHA") "manhã" else if (period == "TARDE") "tarde" else "definido pelo servidor"}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (expiresSeconds > 0) "Disponível por aproximadamente $expiresSeconds s · uso único"
                        else "Uso único · expira automaticamente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "O reconhecimento facial encontrará a autorização no Ponto. Ela será encerrada ao registrar a pausa, ao expirar ou ao ser cancelada.",
                style = MaterialTheme.typography.bodyMedium,
            )

            PcSecondaryButton(
                text = "Cancelar autorização",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                contentColor = MaterialTheme.colorScheme.error,
            )
            PcPrimaryButton(
                text = "Autorizar outra pessoa",
                onClick = onAuthorizeAnother,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )
        }
    }
}
