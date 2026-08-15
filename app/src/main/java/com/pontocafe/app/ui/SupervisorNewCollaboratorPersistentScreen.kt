package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.FormDraftRegistry
import com.pontocafe.app.SupervisorViewModel

@Composable
fun SupervisorNewCollaboratorPersistentScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    val draftState = remember(viewModel) { FormDraftRegistry.supervisorCollaborator(viewModel) }
    val draft = draftState.draft

    LaunchedEffect(Unit) {
        draftState.prepareForDisplay(serverError = state.erro, loading = state.carregando)
    }
    LaunchedEffect(state.erro) {
        if (state.erro != null) draftState.markServerFailure()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Novo colaborador")
        Text(
            "Depois de salvar os dados, a câmera abrirá automaticamente para cadastrar o rosto.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.mensagem?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
        state.erro?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

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
                viewModel.criarColaborador(draft.nome, draft.setor, draft.turno)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.nome.trim().length >= 2 && !state.carregando,
        ) {
            Text(if (state.carregando) "Salvando..." else "Salvar e cadastrar rosto")
        }
        OutlinedButton(
            onClick = {
                draftState.reset()
                viewModel.voltarColaboradores()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.carregando,
        ) {
            Text("Cancelar")
        }
    }
}
