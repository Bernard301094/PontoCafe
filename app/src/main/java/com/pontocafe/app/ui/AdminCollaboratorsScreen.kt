package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminCollaboratorsScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var busca by remember { mutableStateOf("") }
    val total = state.colaboradores.size
    val pendentes = state.colaboradores.count { !it.rostoCadastrado }
    val cadastrados = total - pendentes
    val primeiroPendente = state.colaboradores
        .filter { !it.rostoCadastrado }
        .minByOrNull { it.nome.lowercase() }
    val filtrados = state.colaboradores
        .filter { busca.isBlank() || it.nome.contains(busca, ignoreCase = true) }
        .sortedWith(compareBy({ it.rostoCadastrado }, { it.nome.lowercase() }))

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
                "Gerencie somente quem registra o ponto por reconhecimento facial. Supervisores são cadastrados como contas de acesso no painel.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "feedback") {
            AdminFeedback(viewModel)
        }

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
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InitialAvatar(colaborador.nome)
                    androidx.compose.foundation.layout.Column(
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
                    Button(onClick = { viewModel.cadastrarOuAtualizarRosto(colaborador) }) {
                        Text(if (colaborador.rostoCadastrado) "Atualizar" else "Cadastrar")
                    }
                }
            }
        }
    }
}
