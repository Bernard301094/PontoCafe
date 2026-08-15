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
import androidx.compose.material3.CardDefaults
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
    val activeUsers = state.usuarios.count { it.ativo }
    val activeSupervisors = state.usuarios.count { it.ativo && it.perfil == "SUPERVISOR" }
    val activeAdmins = state.usuarios.count { it.ativo && it.perfil == "ADMIN" }
    val pendingFaces = state.colaboradores.count { !it.rostoCadastrado }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PontoCafeHeader("Painel do Administrador")
        Text(
            "Visão geral da operação, acessos e segurança do Ponto Café.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminFeedback(viewModel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(
                value = activeUsers.toString(),
                label = "Contas ativas",
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
            MetricCard(
                value = activeSupervisors.toString(),
                label = "Supervisores",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(
                value = activeAdmins.toString(),
                label = "Administradores",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = if (state.colaboradores.isEmpty()) "—" else pendingFaces.toString(),
                label = "Rostos pendentes",
                modifier = Modifier.weight(1f),
                emphasized = pendingFaces > 0,
            )
        }

        if (state.colaboradores.isNotEmpty() && pendingFaces > 0) {
            OperationalAlertCard(
                title = "$pendingFaces rostos pendentes",
                text = "Existem colaboradores que ainda não podem utilizar o reconhecimento facial neste dispositivo.",
                actionLabel = "Ver pendentes",
                onClick = viewModel::abrirColaboradores,
            )
        }

        SectionTitle(
            title = "Ações rápidas",
            subtitle = "As tarefas administrativas mais usadas ficam disponíveis aqui.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::abrirColaboradores, modifier = Modifier.weight(1f)) {
                Text("Colaboradores")
            }
            Button(onClick = viewModel::abrirNovaConta, modifier = Modifier.weight(1f)) {
                Text("Nova conta")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::abrirAutorizacao, modifier = Modifier.weight(1f)) {
                Text("Autorizar pausa")
            }
            OutlinedButton(onClick = viewModel::abrirConfiguracoes, modifier = Modifier.weight(1f)) {
                Text("Regras do café")
            }
        }
        OutlinedButton(onClick = onDevicesClick, modifier = Modifier.fillMaxWidth()) {
            Text("Dispositivos e PIN de desbloqueio")
        }

        SectionTitle(
            title = "Contas de acesso",
            subtitle = "Toque em uma conta para administrar perfil, senha ou status.",
        )

        if (state.usuarios.isEmpty() && !state.carregando) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nenhuma conta encontrada", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Cadastre um Supervisor ou Administrador para começar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(state.usuarios, key = { it.id }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.selecionarUsuario(user) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    AccountSummaryRow(
                        name = user.nome,
                        email = user.email,
                        profile = user.perfil,
                        active = user.ativo,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Voltar ao Ponto")
            }
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.weight(1f)) {
                Text("Sair")
            }
        }
    }
}
