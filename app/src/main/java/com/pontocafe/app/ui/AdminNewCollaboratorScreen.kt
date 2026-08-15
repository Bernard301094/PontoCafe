package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminNewCollaboratorScreen(viewModel: AdminViewModel) {
    var nome by remember { mutableStateOf("") }
    var setor by remember { mutableStateOf("Produção") }
    var turno by remember { mutableStateOf("A") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Novo colaborador")
        Text("Depois de salvar os dados, a câmera abrirá para cadastrar o rosto.")
        AdminFeedback(viewModel)

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome completo") },
            singleLine = true,
        )
        OutlinedTextField(
            value = setor,
            onValueChange = { setor = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Setor") },
            singleLine = true,
        )
        OutlinedTextField(
            value = turno,
            onValueChange = { turno = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Turno") },
            singleLine = true,
        )

        Button(
            onClick = { viewModel.criarColaborador("", nome, setor, turno) },
            modifier = Modifier.fillMaxWidth(),
            enabled = nome.trim().length >= 2 && !viewModel.state.carregando,
        ) {
            Text("Salvar e cadastrar rosto")
        }
        OutlinedButton(
            onClick = viewModel::voltarColaboradores,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancelar")
        }
    }
}
