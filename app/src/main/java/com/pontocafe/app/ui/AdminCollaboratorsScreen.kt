package com.pontocafe.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.Colaborador

@Composable
fun AdminCollaboratorsScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var busca by remember { mutableStateOf("") }
    var editando by remember { mutableStateOf<Colaborador?>(null) }
    val total = state.colaboradores.size
    val pendentes = state.colaboradores.count { !it.rostoCadastrado }
    val cadastrados = total - pendentes
    val primeiroPendente = state.colaboradores
        .filter { !it.rostoCadastrado }
        .minByOrNull { it.nome.lowercase() }
    val filtrados = state.colaboradores
        .filter { busca.isBlank() || it.nome.contains(busca, ignoreCase = true) }
        .sortedWith(compareBy({ it.rostoCadastrado }, { it.nome.lowercase() }))

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
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PontoCafeScreenHeader(
                title = "Colaboradores",
                onBack = viewModel::voltarHome,
                backLabel = "Painel",
            )
        }

        item(key = "intro") {
            Text(
                "Gerencie quem registra o ponto por reconhecimento facial. O Administrador pode corrigir nome, setor e turno sem perder biometria ou histórico.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "feedback") { AdminFeedback(viewModel) }

        item(key = "metrics-row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(total.toString(), "Colaboradores", Modifier.weight(1f))
                MetricCard(cadastrados.toString(), "Rostos cadastrados", Modifier.weight(1f))
            }
        }

        item(key = "pending-metric") {
            MetricCard(
                pendentes.toString(),
                "Pendentes de registro facial",
                Modifier.fillMaxWidth(),
                emphasized = pendentes > 0,
            )
        }

        if (pendentes > 0 && primeiroPendente != null) {
            item(key = "pending-alert") {
                OperationalAlertCard(
                    title = "$pendentes rostos pendentes",
                    text = "Os colaboradores pendentes aparecem primeiro para agilizar o cadastro facial.",
                    actionLabel = "Cadastrar próximo",
                    onClick = { viewModel.cadastrarOuAtualizarRosto(primeiroPendente) },
                )
            }
        }

        item(key = "new-collaborator") {
            Button(
                onClick = viewModel::abrirNovoColaborador,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Novo colaborador")
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar colaborador") },
                placeholder = { Text("Digite o nome") },
                singleLine = true,
            )
        }

        item(key = "list-title") {
            SectionTitle(
                title = "Lista de colaboradores",
                subtitle = "${filtrados.size} resultado(s) · pendentes de rosto aparecem primeiro.",
            )
        }

        items(filtrados, key = { "collaborator-${it.id}" }) { colaborador ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InitialAvatar(colaborador.nome)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(colaborador.nome, fontWeight = FontWeight.SemiBold)
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
                                if (colaborador.rostoCadastrado) "Rosto cadastrado" else "Rosto pendente",
                                positive = colaborador.rostoCadastrado,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { editando = colaborador },
                            modifier = Modifier.weight(1f),
                            enabled = !state.carregando,
                        ) { Text("Editar dados") }
                        Button(
                            onClick = { viewModel.cadastrarOuAtualizarRosto(colaborador) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.carregando,
                        ) {
                            Text(if (colaborador.rostoCadastrado) "Atualizar rosto" else "Cadastrar rosto")
                        }
                    }
                }
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Corrija os dados digitados. A biometria e os registros de pausa deste colaborador serão mantidos.",
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
            ) { Text("Salvar alterações") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !carregando) { Text("Cancelar") }
        },
    )
}
