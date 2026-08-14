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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminCollaboratorsScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    var busca by remember { mutableStateOf("") }
    val filtrados = state.colaboradores.filter {
        busca.isBlank() ||
            it.nome.contains(busca, ignoreCase = true) ||
            (it.matricula?.contains(busca, ignoreCase = true) == true)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Colaboradores")
        Text(
            "Cadastre as pessoas que poderão ser reconhecidas no Ponto Café e registre o rosto de cada uma.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminFeedback(viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirNovoColaborador, modifier = Modifier.weight(1f)) {
                Text("Novo colaborador")
            }
            OutlinedButton(onClick = viewModel::voltarHome, modifier = Modifier.weight(1f)) {
                Text("Voltar")
            }
        }

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar colaborador") },
            placeholder = { Text("Nome ou matrícula") },
            singleLine = true,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtrados, key = { it.id }) { colaborador ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(colaborador.nome, fontWeight = FontWeight.SemiBold)
                        val detalhe = listOfNotNull(colaborador.matricula, colaborador.setor, colaborador.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (detalhe.isNotBlank()) {
                            Text(detalhe, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { viewModel.cadastrarOuAtualizarRosto(colaborador) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cadastrar ou atualizar rosto")
                        }
                    }
                }
            }
        }
    }
}
