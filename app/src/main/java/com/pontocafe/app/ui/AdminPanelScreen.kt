package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val collaborators = summary?.colaboradoresAtivos ?: state.colaboradores.size
    val activeSupervisors = summary?.supervisoresAtivos ?: state.usuarios.count { it.ativo && it.perfil == "SUPERVISOR" }
    val pendingFaces = summary?.rostosPendentes ?: state.colaboradores.count { !it.rostoCadastrado }
    val registeredFaces = (collaborators - pendingFaces).coerceAtLeast(0)
    val openPauses = summary?.pausasAbertas ?: 0
    val activeDevices = summary?.dispositivosAtivos ?: 0
    val devicesWithoutPin = summary?.dispositivosSemPin ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                PontoCafeScreenHeader(
                    title = "Visão geral",
                    eyebrow = "Administrador",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onClose) { Text("Ponto") }
            }
        }

        item(key = "feedback") { AdminFeedback(viewModel) }

        item(key = "operation-status") {
            OperationStatusCard(
                online = state.erro == null,
                openPauses = openPauses,
                activeDevices = activeDevices,
            )
        }

        item(key = "metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                MetricCard(
                    value = collaborators.toString(),
                    label = "Colaboradores",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    value = openPauses.toString(),
                    label = "Em pausa agora",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (collaborators > 0) {
            item(key = "face-progress") {
                ThinProgressSummary(
                    completed = registeredFaces,
                    total = collaborators,
                    title = "Reconhecimento facial",
                    detail = "$registeredFaces de $collaborators colaboradores com rosto cadastrado",
                )
            }
        }

        if (pendingFaces > 0 || devicesWithoutPin > 0 || activeSupervisors == 0) {
            item(key = "pending-title") {
                SectionTitle(
                    "Pendências",
                    "Itens que merecem atenção, sem tratar tarefas operacionais comuns como falhas críticas.",
                )
            }
        }

        if (pendingFaces > 0) {
            item(key = "pending-faces") {
                OperationalAlertCard(
                    title = "$pendingFaces rostos aguardando cadastro",
                    text = "Esses colaboradores ainda não conseguem utilizar o reconhecimento facial.",
                    actionLabel = "Abrir Pessoas",
                    onClick = viewModel::abrirColaboradores,
                    tone = PontoCafeTone.WARNING,
                )
            }
        }

        if (devicesWithoutPin > 0) {
            item(key = "devices-without-pin") {
                OperationalAlertCard(
                    title = "$devicesWithoutPin dispositivo(s) sem PIN próprio",
                    text = "Defina um PIN individual para eliminar dependência do código legado.",
                    actionLabel = "Gerenciar dispositivos",
                    onClick = onDevicesClick,
                    tone = PontoCafeTone.WARNING,
                )
            }
        }

        if (activeSupervisors == 0) {
            item(key = "no-supervisor") {
                OperationalAlertCard(
                    title = "Nenhum Supervisor ativo",
                    text = "Cadastre uma conta de Supervisor para delegar acompanhamento, biometria e autorizações.",
                    actionLabel = "Cadastrar Supervisor",
                    onClick = viewModel::abrirNovaConta,
                    tone = PontoCafeTone.INFO,
                )
            }
        }

        item(key = "quick-title") {
            SectionTitle("Ações rápidas", "As tarefas que normalmente precisam de resposta imediata.")
        }

        item(key = "quick-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                DashboardActionCard(
                    title = "Nova pessoa",
                    subtitle = "Colaborador ou acesso",
                    icon = Icons.Default.PersonAdd,
                    onClick = viewModel::abrirColaboradores,
                    modifier = Modifier.weight(1f),
                )
                DashboardActionCard(
                    title = "Autorizar pausa",
                    subtitle = "Exceção temporária",
                    icon = Icons.Default.Coffee,
                    onClick = viewModel::abrirAutorizacao,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "operation-context") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.padding(PontoCafeSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Equipe de acesso", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$activeSupervisors Supervisor(es) ativo(s) · ${state.usuarios.count { it.ativo }} conta(s) ativa(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "logout") {
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Encerrar sessão administrativa")
            }
        }
    }
}

@Composable
private fun OperationStatusCard(
    online: Boolean,
    openPauses: Int,
    activeDevices: Int,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    val container = if (online) semantic.successContainer else semantic.warningContainer
    val content = if (online) semantic.onSuccessContainer else semantic.onWarningContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            StatusPill(
                text = if (online) "Operação normal" else "Dados locais",
                tone = if (online) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (online) "Ponto Café disponível" else "Conexão sem confirmação",
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                )
                Text(
                    "$openPauses em pausa · $activeDevices dispositivo(s) ativo(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
