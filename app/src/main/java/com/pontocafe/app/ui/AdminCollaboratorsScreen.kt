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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador

private enum class PeopleFilter(val label: String) {
    ALL("Todos"),
    COLLABORATORS("Colaboradores"),
    ACCESS("Acessos"),
}

@Composable
fun AdminCollaboratorsScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(PeopleFilter.ALL) }
    var editando by remember { mutableStateOf<Colaborador?>(null) }

    val total = state.colaboradores.size
    val pendentes = state.colaboradores.count { !it.rostoCadastrado }
    val cadastrados = total - pendentes
    val primeiroPendente = state.colaboradores
        .filter { !it.rostoCadastrado }
        .minByOrNull { it.nome.lowercase() }

    val colaboradoresFiltrados = state.colaboradores
        .filter {
            busca.isBlank() ||
                it.nome.contains(busca, ignoreCase = true) ||
                it.setor.orEmpty().contains(busca, ignoreCase = true)
        }
        .sortedWith(compareBy({ it.rostoCadastrado }, { it.nome.lowercase() }))

    val contasFiltradas = state.usuarios
        .filter {
            busca.isBlank() ||
                it.nome.contains(busca, ignoreCase = true) ||
                it.email.contains(busca, ignoreCase = true)
        }
        .sortedWith(compareBy({ it.perfil != "SUPERVISOR" }, { it.nome.lowercase() }))

    editando?.let { colaborador ->
        EditCollaboratorDialog(
            colaborador = colaborador,
            carregando = state.carregando,
            onDismiss = { editando = null },
            onSave = { nome, setor, turno ->
                viewModel.editarColaborador(colaborador, nome, setor, turno)
                editando = null
            },
        )
    }

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
            PontoCafeScreenHeader(
                title = "Pessoas",
                eyebrow = "Equipe e acessos",
            )
        }

        item(key = "intro") {
            Text(
                "Colaboradores usam reconhecimento facial para registrar o café. Supervisores e administradores possuem contas de acesso separadas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "feedback") { AdminFeedback(viewModel) }

        if (total > 0) {
            item(key = "face-progress") {
                ThinProgressSummary(
                    completed = cadastrados,
                    total = total,
                    title = "Cadastro facial",
                    detail = "$cadastrados de $total colaboradores prontos para reconhecimento",
                )
            }
        }

        if (pendentes > 0 && primeiroPendente != null) {
            item(key = "pending-alert") {
                OperationalAlertCard(
                    title = "$pendentes rostos aguardando cadastro",
                    text = "Pendência operacional: essas pessoas ainda não conseguem utilizar reconhecimento facial.",
                    actionLabel = "Cadastrar próximo",
                    onClick = { viewModel.cadastrarOuAtualizarRosto(primeiroPendente) },
                    tone = PontoCafeTone.WARNING,
                )
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Button(
                    onClick = viewModel::abrirNovoColaborador,
                    modifier = Modifier.weight(1f),
                ) { Text("Novo colaborador") }
                OutlinedButton(
                    onClick = viewModel::abrirNovaConta,
                    modifier = Modifier.weight(1f),
                ) { Text("Novo acesso") }
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar pessoa") },
                placeholder = { Text("Nome, setor ou e-mail") },
                singleLine = true,
            )
        }

        item(key = "filters") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                PeopleFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filtro == item,
                        onClick = { filtro = item },
                        label = { Text(item.label) },
                    )
                }
            }
        }

        if (filtro != PeopleFilter.ACCESS) {
            item(key = "collaborators-title") {
                SectionTitle(
                    title = "Colaboradores",
                    subtitle = "${colaboradoresFiltrados.size} resultado(s) · quem tem rosto pendente aparece primeiro.",
                )
            }

            if (colaboradoresFiltrados.isEmpty()) {
                item(key = "collaborators-empty") {
                    EmptyPeopleCard("Nenhum colaborador encontrado", "Tente outro termo de busca ou cadastre uma nova pessoa.")
                }
            } else {
                items(colaboradoresFiltrados, key = { "collaborator-${it.id}" }) { colaborador ->
                    CollaboratorPersonCard(
                        colaborador = colaborador,
                        carregando = state.carregando,
                        onEdit = { editando = colaborador },
                        onBiometric = { viewModel.cadastrarOuAtualizarRosto(colaborador) },
                    )
                }
            }
        }

        if (filtro != PeopleFilter.COLLABORATORS) {
            item(key = "accounts-title") {
                SectionTitle(
                    title = "Contas de acesso",
                    subtitle = "${contasFiltradas.size} conta(s) de Supervisor ou Administrador.",
                )
            }

            if (contasFiltradas.isEmpty()) {
                item(key = "accounts-empty") {
                    EmptyPeopleCard(
                        "Nenhuma conta encontrada",
                        "Cadastre um Supervisor para delegar o acompanhamento operacional.",
                    )
                }
            } else {
                items(contasFiltradas, key = { "account-${it.id}" }) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.selecionarUsuario(user) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        AccountSummaryRow(
                            name = user.nome,
                            email = user.email,
                            profile = user.perfil,
                            active = user.ativo,
                            modifier = Modifier.padding(PontoCafeSpacing.md),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollaboratorPersonCard(
    colaborador: Colaborador,
    carregando: Boolean,
    onEdit: () -> Unit,
    onBiometric: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                InitialAvatar(colaborador.nome)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(colaborador.nome, style = MaterialTheme.typography.titleMedium)
                    val detalhe = listOfNotNull(colaborador.setor, colaborador.turno)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (detalhe.isNotBlank()) {
                        Text(
                            detalhe,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    StatusPill(
                        text = if (colaborador.rostoCadastrado) "Rosto cadastrado" else "Rosto pendente",
                        tone = if (colaborador.rostoCadastrado) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    enabled = !carregando,
                ) { Text("Editar") }
                Button(
                    onClick = onBiometric,
                    modifier = Modifier.weight(1f),
                    enabled = !carregando,
                ) {
                    Text(if (colaborador.rostoCadastrado) "Atualizar rosto" else "Cadastrar rosto")
                }
            }
        }
    }
}

@Composable
private fun EmptyPeopleCard(title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditCollaboratorDialog(
    colaborador: Colaborador,
    carregando: Boolean,
    onDismiss: () -> Unit,
    onSave: (nome: String, setor: String, turno: String) -> Unit,
) {
    var nome by remember(colaborador.id) { mutableStateOf(colaborador.nome) }
    var setor by remember(colaborador.id) { mutableStateOf(colaborador.setor.orEmpty()) }
    var turno by remember(colaborador.id) { mutableStateOf(colaborador.turno.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar colaborador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Text(
                    "Corrija os dados digitados. A biometria e os registros de pausa serão mantidos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome completo") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = setor,
                    onValueChange = { setor = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Setor") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = turno,
                    onValueChange = { turno = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Turno") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nome, setor, turno) },
                enabled = !carregando && nome.trim().length >= 2,
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !carregando) { Text("Cancelar") }
        },
    )
}
