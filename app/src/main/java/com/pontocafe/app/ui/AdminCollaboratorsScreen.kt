package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    val filtrados = state.colaboradores
        .filter { busca.isBlank() || it.nome.contains(busca, ignoreCase = true) }
        .sortedWith(compareBy({ it.rostoCadastrado }, { it.nome.lowercase() }))

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PontoCafeHeader("Colaboradores")
        Text(
            "Gerencie quem utiliza o reconhecimento facial e resolva pendências de biometria.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminFeedback(viewModel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(total.toString(), "Colaboradores", Modifier.weight(1f))
            MetricCard(cadastrados.toString(), "Rostos cadastrados", Modifier.weight(1f))
            MetricCard(pendentes.toString(), "Pendentes", Modifier.weight(1f), emphasized = pendentes > 0)
        }

        if (pendentes > 0) {
            OperationalAlertCard(
                title = "$pendentes rostos pendentes",
                text = "Os colaboradores pendentes aparecem primeiro na lista para agilizar o cadastro facial.",
                actionLabel = "Ver lista abaixo",
                onClick = {},
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirNovoColaborador, modifier = Modifier.weight(1f)) {
                Text("Novo colaborador")
            }
            OutlinedButton(onClick = viewModel::voltarHome, modifier = Modifier.weight(1f)) {
                Text("Voltar ao painel")
            }
        }

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar colaborador") },
            placeholder = { Text("Digite o nome") },
            singleLine = true,
        )

        SectionTitle(
            title = "Lista de colaboradores",
            subtitle = "Pendentes de rosto aparecem primeiro.",
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(filtrados, key = { it.id }) { colaborador ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                        Button(onClick = { viewModel.cadastrarOuAtualizarRosto(colaborador) }) {
                            Text(if (colaborador.rostoCadastrado) "Atualizar" else "Cadastrar")
                        }
                    }
                }
            }
        }
    }
}
