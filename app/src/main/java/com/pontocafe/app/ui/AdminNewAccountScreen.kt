package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminNewAccountScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeScreenHeader(
            title = "Nova conta de acesso",
            onBack = viewModel::voltarHome,
            backLabel = "Painel",
        )
        Text(
            "Supervisor já vem selecionado. Informe o nome, o e-mail e a senha que a pessoa usará para entrar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminAccountForm(
            carregando = state.carregando,
            initialProfile = AccountProfile.SUPERVISOR,
            showHeader = false,
            onSubmit = viewModel::criarConta,
        )
        AdminFeedback(viewModel)
    }
}
