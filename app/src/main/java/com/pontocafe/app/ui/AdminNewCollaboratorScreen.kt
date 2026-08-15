package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PontoCafeScreenHeader(
            title = "Novo colaborador",
            onBack = viewModel::voltarColaboradores,
            backLabel = "Colaboradores",
        )
        Text(
            "Cadastre aqui somente pessoas que vão bater o ponto por reconhecimento facial.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Para criar um Supervisor, volte ao painel e use “Cadastrar supervisor / conta de acesso”.",
            color = MaterialTheme.colorScheme.primary,
        )
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
    }
}
