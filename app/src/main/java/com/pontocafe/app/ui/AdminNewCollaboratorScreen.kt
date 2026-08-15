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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.FormDraftRegistry
import com.pontocafe.app.trackCollaboratorDraftSubmission

@Composable
fun AdminNewCollaboratorScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    val draftState = remember(viewModel) { FormDraftRegistry.adminCollaborator(viewModel) }
    val draft = draftState.draft

    LaunchedEffect(Unit) {
        draftState.prepareForDisplay(serverError = state.erro, loading = state.carregando)
    }
    LaunchedEffect(state.erro) {
        if (state.erro != null) draftState.markServerFailure()
    }

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
            onBack = {
                draftState.reset()
                viewModel.voltarColaboradores()
            },
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
            value = draft.nome,
            onValueChange = { draftState.update(draft.copy(nome = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome completo") },
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.setor,
            onValueChange = { draftState.update(draft.copy(setor = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Setor") },
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.turno,
            onValueChange = { draftState.update(draft.copy(turno = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Turno") },
            singleLine = true,
        )

        Button(
            onClick = {
                draftState.markSubmitted()
                viewModel.trackCollaboratorDraftSubmission(draftState)
                viewModel.criarColaborador("", draft.nome, draft.setor, draft.turno)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.nome.trim().length >= 2 && !state.carregando,
        ) {
            Text(if (state.carregando) "Salvando..." else "Salvar e cadastrar rosto")
        }
    }
}
