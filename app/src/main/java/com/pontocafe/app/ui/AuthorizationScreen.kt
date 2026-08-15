package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

@Composable
fun AuthorizationScreen(viewModel: PontoCafeViewModel) {
    val identificacao = viewModel.state.identificacao
    val colaborador = identificacao?.colaborador
    var periodo by remember { mutableStateOf("MANHA") }
    var codigo by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = viewModel::cancelarAutorizacao) { Text("← Cancelar") }
        PontoCafeHeader("Autorização necessária")

        if (colaborador != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(colaborador.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Identidade já confirmada. Falta apenas autorizar esta pausa fora do horário normal.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text("Solicite ao Supervisor ou Administrador um código temporário de 6 dígitos.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = periodo == "MANHA",
                onClick = { periodo = "MANHA" },
                label = { Text("Manhã") },
            )
            FilterChip(
                selected = periodo == "TARDE",
                onClick = { periodo = "TARDE" },
                label = { Text("Tarde") },
            )
        }
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it.filter(Char::isDigit).take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código temporário") },
            supportingText = { Text("O código expira rapidamente e só pode ser usado uma vez.") },
            singleLine = true,
        )
        Button(
            onClick = { viewModel.confirmarAutorizacao(periodo, codigo) },
            modifier = Modifier.fillMaxWidth(),
            enabled = codigo.length == 6 && !viewModel.state.carregando,
        ) { Text("Autorizar e iniciar pausa") }
        MessageCard(viewModel)
    }
}
