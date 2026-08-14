package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminPanelScreen(viewModel: AdminViewModel, onClose: () -> Unit) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Painel do Administrador")
        Text("Contas de acesso", style = MaterialTheme.typography.titleMedium)
        AdminFeedback(viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirNovaConta, modifier = Modifier.weight(1f)) {
                Text("Nova conta")
            }
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.weight(1f)) {
                Text("Sair")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.usuarios, key = { it.id }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.selecionarUsuario(user) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.nome, fontWeight = FontWeight.SemiBold)
                        Text(user.email)
                        Text(
                            "${if (user.perfil == "ADMIN") "Administrador" else "Supervisor"} · ${if (user.ativo) "Ativo" else "Desativado"}",
                            color = if (user.ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao Ponto Café")
        }
    }
}
