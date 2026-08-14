package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun EmployeeListScreen(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Selecione seu nome")
        RulesCard()
        OutlinedTextField(
            value = state.busca,
            onValueChange = viewModel::buscar,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar por nome ou matrícula") },
            singleLine = true,
        )
        MessageCard(viewModel)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.colaboradores, key = { it.id }) { colaborador ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.selecionar(colaborador) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(colaborador.nome, fontWeight = FontWeight.SemiBold)
                        val detalhe = listOfNotNull(colaborador.matricula, colaborador.setor)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (detalhe.isNotBlank()) Text(detalhe)
                    }
                }
            }
        }
    }
}
