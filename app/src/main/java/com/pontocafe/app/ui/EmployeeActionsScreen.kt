package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun EmployeeActionsScreen(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    val colaborador = state.selecionado ?: return

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = viewModel::voltarParaLista) { Text("← Voltar") }
        PontoCafeHeader("Olá, ${colaborador.nome}")
        RulesCard()
        MessageCard(viewModel)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = viewModel::prepararInicio,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            enabled = !state.carregando,
        ) { Text("INICIAR CAFÉ") }
        OutlinedButton(
            onClick = viewModel::iniciarFinalizacao,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            enabled = !state.carregando,
        ) { Text("FINALIZAR CAFÉ") }
    }
}
