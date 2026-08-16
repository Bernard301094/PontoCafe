package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = PontoCafeSpacing.lg,
                end = PontoCafeSpacing.lg,
                top = PontoCafeSpacing.md,
                bottom = PontoCafeSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
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
                AuthorizationInfoCard()
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
                            shape = RoundedCornerShape(20.dp),
                        )
                    }

                    item(key = "result-count") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (query.isBlank()) "Colaboradores" else "Resultados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${filtrados.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            shape = RoundedCornerShape(20.dp),
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

        if (state.authorizationCode == null && selecionado != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PontoCafePremium.glassStrong,
                border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = PontoCafeSpacing.lg,
                        end = PontoCafeSpacing.lg,
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
                    Button(
                        onClick = {
                            selecionado?.let { viewModel.gerarAutorizacao(it, periodo, motivo) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = podeGerar,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Text(
                            if (state.carregando) "Gerando autorização..." else "Gerar código de 6 dígitos",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizationInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Exceção temporária",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Gera um código de uso único. A autorização fica registrada automaticamente na auditoria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            },
            border = BorderStroke(
                1.dp,
                if (completed) {
                    LocalPontoCafeSemanticColors.current.success.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                },
            ),
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
                        color = MaterialTheme.colorScheme.primary,
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CollaboratorDetail(collaborator)
            }
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CollaboratorDetail(collaborator)
            }
            TextButton(onClick = onChange) {
                Text("Alterar")
            }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun EmptyAuthorizationSearch(query: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Nenhum colaborador encontrado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (query.isNotBlank()) {
                Text(
                    "Tente outro nome, setor ou turno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GeneratedAuthorizationCard(
    code: String,
    employeeName: String,
    expiresSeconds: Int,
    onGenerateAnother: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = PontoCafePremium.glassStrong,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
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
            Button(
                onClick = onGenerateAnother,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Text("Gerar outra autorização", fontWeight = FontWeight.Bold)
            }
        }
    }
}
