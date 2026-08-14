package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
    var periodo by remember { mutableStateOf("MANHA") }
    var codigo by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = viewModel::voltarParaLista) { Text("← Cancelar") }
        PontoCafeHeader("Autorização necessária")
        Text("Fora do horário normal. Solicite ao supervisor um código temporário.")
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
            singleLine = true,
        )
        Button(
            onClick = { viewModel.confirmarAutorizacao(periodo, codigo) },
            modifier = Modifier.fillMaxWidth(),
            enabled = codigo.length == 6,
        ) { Text("Continuar para verificação facial") }
        MessageCard(viewModel)
    }
}
