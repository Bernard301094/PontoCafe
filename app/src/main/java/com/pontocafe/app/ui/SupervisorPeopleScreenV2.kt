package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel

@Composable
fun SupervisorPeopleScreenV2(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    var search by remember { mutableStateOf("") }
    val collaborators = state.colaboradores
        .filter {
            search.isBlank() ||
                it.nome.contains(search, ignoreCase = true) ||
                it.setor.orEmpty().contains(search, ignoreCase = true)
        }
        .sortedWith(compareBy({ it.rostoCadastrado }, { it.nome.lowercase() }))
    val registered = state.colaboradores.count { it.rostoCadastrado }
    val pending = state.colaboradores.size - registered

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item(key = "header") {
            PontoCafeScreenHeader(title = "Pessoas", eyebrow = "Supervisor")
        }
        item(key = "intro") {
            Text(
                "Cadastre colaboradores e resolva pendências de reconhecimento facial.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.colaboradores.isNotEmpty()) {
            item(key = "progress") {
                ThinProgressSummary(
                    completed = registered,
                    total = state.colaboradores.size,
                    title = "Biometria da equipe",
                    detail = "$registered de ${state.colaboradores.size} colaboradores prontos",
                )
            }
        }

        if (pending > 0) {
            item(key = "pending") {
                OperationalAlertCard(
                    title = "$pending rostos pendentes",
                    text = "Esses colaboradores ainda não conseguem utilizar reconhecimento facial.",
                    actionLabel = "Mostrar pendentes",
                    onClick = { search = "" },
                    tone = PontoCafeTone.WARNING,
                )
            }
        }

        item(key = "new") {
            Button(onClick = viewModel::abrirNovoColaborador, modifier = Modifier.fillMaxWidth()) {
                Text("Novo colaborador")
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar colaborador") },
                placeholder = { Text("Nome ou setor") },
                singleLine = true,
            )
        }

        item(key = "title") {
            SectionTitle("Colaboradores", "${collaborators.size} resultado(s) · pendentes aparecem primeiro.")
        }

        if (collaborators.isEmpty()) {
            item(key = "empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        "Nenhum colaborador encontrado.",
                        modifier = Modifier.padding(PontoCafeSpacing.md),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(collaborators, key = { "person-${it.id}" }) { person ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(PontoCafeSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
                        InitialAvatar(person.nome)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(person.nome, style = MaterialTheme.typography.titleMedium)
                            val detail = listOfNotNull(person.setor, person.turno)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(
                                text = if (person.rostoCadastrado) "Rosto cadastrado" else "Rosto pendente",
                                tone = if (person.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                            )
                        }
                        Button(
                            onClick = { viewModel.cadastrarOuAtualizarRosto(person) },
                            enabled = !state.carregando,
                        ) {
                            Text(if (person.rostoCadastrado) "Atualizar" else "Cadastrar")
                        }
                    }
                }
            }
        }
    }
}
