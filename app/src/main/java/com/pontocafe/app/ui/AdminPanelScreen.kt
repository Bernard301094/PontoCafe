package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
    val summary = state.resumoOperacional
    val activeUsers = state.usuarios.count { it.ativo }
    val activeSupervisors = summary?.supervisoresAtivos ?: state.usuarios.count { it.ativo && it.perfil == "SUPERVISOR" }
    val activeAdmins = summary?.administradoresAtivos ?: state.usuarios.count { it.ativo && it.perfil == "ADMIN" }
    val pendingFaces = summary?.rostosPendentes ?: state.colaboradores.count { !it.rostoCadastrado }
    val openPauses = summary?.pausasAbertas ?: 0
    val devicesWithoutPin = summary?.dispositivosSemPin ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PontoCafeScreenHeader(
                title = "Painel do Administrador",
                onBack = onClose,
                backLabel = "Ponto Café",
            )
        }

        item(key = "intro") {
            Text(
                "Visão geral da operação, acessos e segurança do Ponto Café.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "feedback") {
            AdminFeedback(viewModel)
        }

        item(key = "metrics-1") {
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
                    emphasized = activeSupervisors == 0,
                )
            }
        }

        item(key = "metrics-2") {
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
                    value = if (state.colaboradores.isEmpty() && summary == null) "—" else pendingFaces.toString(),
                    label = "Rostos pendentes",
                    modifier = Modifier.weight(1f),
                    emphasized = pendingFaces > 0,
                )
            }
        }

        if (activeSupervisors == 0) {
            item(key = "no-supervisor") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Nenhum supervisor cadastrado",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "Supervisor é uma conta de acesso separada dos colaboradores do ponto.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(onClick = viewModel::abrirNovaConta) {
                            Text("Cadastrar supervisor")
                        }
                    }
                }
            }
        }

        if (pendingFaces > 0) {
            item(key = "pending-faces") {
                OperationalAlertCard(
                    title = "$pendingFaces rostos pendentes",
                    text = "Existem colaboradores que ainda não podem utilizar o reconhecimento facial.",
                    actionLabel = "Ver pendentes",
                    onClick = viewModel::abrirColaboradores,
                )
            }
        }

        if (devicesWithoutPin > 0) {
            item(key = "devices-without-pin") {
                OperationalAlertCard(
                    title = "$devicesWithoutPin dispositivo(s) sem PIN próprio",
                    text = "Defina um PIN individual para eliminar dependência do código legado de compatibilidade.",
                    actionLabel = "Gerenciar dispositivos",
                    onClick = onDevicesClick,
                )
            }
        }

        item(key = "operation-title") {
            SectionTitle(
                title = "Operação agora",
                subtitle = if (summary != null) {
                    "$openPauses pessoa(s) em pausa · ${summary.dispositivosAtivos} dispositivo(s) ativo(s)"
                } else {
                    "Resumo operacional será atualizado quando houver conexão."
                },
            )
        }

        item(key = "quick-title") {
            SectionTitle(
                title = "Ações rápidas",
                subtitle = "Cadastre acessos e administre a operação sem confundir contas com colaboradores.",
            )
        }

        item(key = "quick-supervisor") {
            Button(
                onClick = viewModel::abrirNovaConta,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cadastrar supervisor / conta de acesso")
            }
        }

        item(key = "quick-1") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::abrirColaboradores, modifier = Modifier.weight(1f)) {
                    Text("Colaboradores")
                }
                OutlinedButton(onClick = viewModel::abrirAutorizacao, modifier = Modifier.weight(1f)) {
                    Text("Autorizar pausa")
                }
            }
        }

        item(key = "quick-2") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::abrirConfiguracoes, modifier = Modifier.weight(1f)) {
                    Text("Regras do café")
                }
                OutlinedButton(onClick = onDevicesClick, modifier = Modifier.weight(1f)) {
                    Text("Dispositivos")
                }
            }
        }

        item(key = "quick-3") {
            OutlinedButton(onClick = viewModel::abrirAuditoria, modifier = Modifier.fillMaxWidth()) {
                Text("Auditoria")
            }
        }

        item(key = "accounts-title") {
            SectionTitle(
                title = "Contas de acesso",
                subtitle = "Toque em uma conta para administrar perfil, senha ou status.",
            )
        }

        if (state.usuarios.isEmpty() && !state.carregando) {
            item(key = "no-accounts") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    androidx.compose.foundation.layout.Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Nenhuma conta carregada", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Se estiver sem conexão, a sessão continua preservada. Atualize o painel quando o servidor voltar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(state.usuarios, key = { "account-${it.id}" }) { user ->
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

        item(key = "logout") {
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Sair da conta administrativa")
            }
        }
    }
}
