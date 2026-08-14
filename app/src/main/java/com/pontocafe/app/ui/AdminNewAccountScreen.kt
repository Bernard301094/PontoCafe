package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminNewAccountScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdminAccountForm(
            carregando = state.carregando,
            onSubmit = viewModel::criarConta,
        )
        AdminFeedback(viewModel)
        OutlinedButton(onClick = viewModel::voltarHome, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar e voltar")
        }
    }
}
