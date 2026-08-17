package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador

@Composable
fun AdminAuthorizationScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var selecionado by remember { mutableStateOf<Colaborador?>(null) }
    var busca by remember { mutableStateOf("") }
    var periodo by remember { mutableStateOf("MANHA") }
    var motivo by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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
    val podeGerar = selecionado != null && motivoValido && !state.carregando
    val showBottomAction = state.authorizationCode == null && selecionado != null

    PontoCafeResponsivePage(maxContentWidth = 840.dp) { responsive ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                item(key = "header") {
                    PontoCafeScreenHeader(
                        title = "Autorizar pausa",
                        onBack = viewModel::voltarHome,
                        backLabel = "Painel",
                        eyebrow = "Fora do horário",
                    )
                }

                item(key = "context") {
                    PcHeroCard(
                        title = "Exceção temporária",
                        supportingText = "Gera um código de uso único. A autorização real permanece registrada na auditoria.",
                        icon = Icons.Default.AccessTime,
                        tone = PontoCafeTone.INFO,
                    )
                }

                item(key = "feedback") {
                    AdminFeedback(viewModel)
                }

                state.authorizationCode?.let { codigo ->
                    item(key = "generated-code") {
                        GeneratedAuthorizationCard(
                            code = codigo,
                            employeeName = state.authorizationEmployeeName ?: "-",
                            expiresSeconds = state.authorizationExpiresSeconds ?: 0,
                            onGenerateAnother = viewModel::limparAutorizacaoGerada,
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
                                CollaboratorAuthorizationRow(
                                    collaborator = colaborador,
                                    onClick = {
                                        selecionado = colaborador
                                        busca = ""
                                    },
                                )
                            }
                        }
                    } else {
                        item(key = "employee-step-selected") {
                            StepHeader(
                                number = "1",
                                title = "Colaborador",
                                subtitle = "Confirme quem receberá a autorização.",
                                completed = true,
                            )
                        }

                        item(key = "selected-employee") {
                            SelectedCollaboratorCard(
                                collaborator = selecionado!!,
                                onChange = { selecionado = null },
                            )
                        }

                        item(key = "period-step") {
                            StepHeader(
                                number = "2",
                                title = "Período da pausa",
                                subtitle = "Escolha em qual janela a exceção será válida.",
                            )
                        }

                        item(key = "period") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                PeriodChip(
                                    label = "Manhã",
                                    selected = periodo == "MANHA",
                                    onClick = { periodo = "MANHA" },
                                    modifier = Modifier.weight(1f),
                                )
                                PeriodChip(
                                    label = "Tarde",
                                    selected = periodo == "TARDE",
                                    onClick = { periodo = "TARDE" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        item(key = "reason-step") {
                            StepHeader(
                                number = "3",
                                title = "Motivo",
                                subtitle = "Explique brevemente por que a pausa precisa ocorrer fora do horário.",
                            )
                        }

                        item(key = "reason") {
                            OutlinedTextField(
                                value = motivo,
                                onValueChange = { motivo = it.take(300) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Motivo da autorização") },
                                placeholder = { Text("Ex.: atividade operacional terminou após o horário") },
                                minLines = 3,
                                maxLines = 5,
                                shape = MaterialTheme.shapes.large,
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
                            text = if (state.carregando) "Gerando autorização…" else "Gerar código de 6 dígitos",
                            icon = Icons.Default.Key,
                            onClick = {
                                selecionado?.let { viewModel.gerarAutorizacao(it, periodo, motivo) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = podeGerar,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(collaborator.nome)
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
            InitialAvatar(collaborator.nome)
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
private fun PeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
    )
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
private fun GeneratedAuthorizationCard(
    code: String,
    employeeName: String,
    expiresSeconds: Int,
    onGenerateAnother: () -> Unit,
) {
    PcSectionSurface {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            StatusPill("Autorização criada", tone = PontoCafeTone.SUCCESS)
            Text(
                employeeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Código de uso único",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Expira em aproximadamente $expiresSeconds s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "O código pode ser usado uma única vez. Gerar outro código para o mesmo período cancela o anterior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            PcPrimaryButton(
                text = "Gerar outra autorização",
                onClick = onGenerateAnother,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
