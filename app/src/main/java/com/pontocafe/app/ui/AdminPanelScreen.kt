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
fun AdminPanelScreen(
    viewModel: AdminViewModel,
    onClose: () -> Unit,
    onDevicesClick: () -> Unit,
) {
    val state = viewModel.state

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Painel do Administrador")
        AdminFeedback(viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirColaboradores, modifier = Modifier.weight(1f)) {
                Text("Colaboradores")
            }
            Button(onClick = viewModel::abrirNovaConta, modifier = Modifier.weight(1f)) {
                Text("Nova conta")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirAutorizacao, modifier = Modifier.weight(1f)) {
                Text("Autorizar pausa")
            }
            Button(onClick = viewModel::abrirConfiguracoes, modifier = Modifier.weight(1f)) {
                Text("Configurar café")
            }
        }
        Button(onClick = onDevicesClick, modifier = Modifier.fillMaxWidth()) {
            Text("Dispositivos e PIN de desbloqueio")
        }

        Text(
            "Em Dispositivos e PIN você pode gerar o token de ativação de um novo aparelho e definir ou alterar o PIN usado para sair do modo Ponto em cada dispositivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Contas de acesso", style = MaterialTheme.typography.titleMedium)
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

        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
            Text("Encerrar sessão do Administrador")
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Voltar ao Ponto Café")
        }
    }
}
