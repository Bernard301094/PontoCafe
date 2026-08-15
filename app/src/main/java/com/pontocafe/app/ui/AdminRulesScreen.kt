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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AdminCoffeeRule

@Composable
fun AdminRulesScreen(
    viewModel: AdminViewModel,
    onDevicesClick: () -> Unit,
) {
    val state = viewModel.state
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
            PontoCafeScreenHeader(
                title = "Gestão",
                eyebrow = "Administração",
            )
        }
        item(key = "intro") {
            Text(
                "Configurações operacionais, segurança e rastreabilidade em um único lugar.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "feedback") { AdminFeedback(viewModel) }

        item(key = "management-title") {
            SectionTitle("Ferramentas", "Acesse as áreas administrativas menos frequentes.")
        }
        item(key = "management-grid-1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                ManagementTile(
                    title = "Dispositivos",
                    subtitle = "PIN, tokens e saúde",
                    icon = Icons.Default.Devices,
                    onClick = onDevicesClick,
                    modifier = Modifier.weight(1f),
                )
                ManagementTile(
                    title = "Autorizações",
                    subtitle = "Exceções fora do horário",
                    icon = Icons.Default.LockClock,
                    onClick = viewModel::abrirAutorizacao,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "management-grid-2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                ManagementTile(
                    title = "Auditoria",
                    subtitle = "Ações e segurança",
                    icon = Icons.Default.History,
                    onClick = viewModel::abrirAuditoria,
                    modifier = Modifier.weight(1f),
                )
                ManagementTile(
                    title = "Regras",
                    subtitle = "Horários e duração",
                    icon = Icons.Default.Schedule,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )
            }
        }

        item(key = "rules-title") {
            SectionTitle(
                "Horários e tempo de café",
                "As alterações entram em vigor no servidor e valem para todos os dispositivos.",
            )
        }
        state.regrasCafe.forEach { regra ->
            item(key = "rule-${regra.periodo}") {
                CoffeeRuleEditor(viewModel, regra)
            }
        }
    }
}

@Composable
private fun ManagementTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoffeeRuleEditor(viewModel: AdminViewModel, regra: AdminCoffeeRule) {
    var inicio by remember(regra) { mutableStateOf(regra.inicio) }
    var fim by remember(regra) { mutableStateOf(regra.fim) }
    var limite by remember(regra) { mutableStateOf(regra.limiteMinutos.toString()) }
    var ativo by remember(regra) { mutableStateOf(regra.ativo) }
    var erroLocal by remember(regra) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        if (regra.periodo == "MANHA") "Período da manhã" else "Período da tarde",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (ativo) "Regra ativa" else "Regra pausada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ativo, onCheckedChange = { ativo = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                OutlinedTextField(
                    value = inicio,
                    onValueChange = { inicio = it.take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Início") },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = fim,
                    onValueChange = { fim = it.take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Fim") },
                    placeholder = { Text("10:00") },
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = limite,
                onValueChange = { limite = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tempo permitido em minutos") },
                singleLine = true,
            )

            erroLocal?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val minutos = limite.toIntOrNull()
                    erroLocal = when {
                        !inicio.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário inicial válido."
                        !fim.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário final válido."
                        inicio >= fim -> "O horário final deve ser posterior ao horário inicial."
                        minutos == null || minutos !in 1..120 -> "O tempo deve ficar entre 1 e 120 minutos."
                        else -> null
                    }
                    if (erroLocal == null && minutos != null) {
                        viewModel.salvarRegraCafe(regra.periodo, inicio, fim, minutos, ativo)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.state.carregando,
            ) {
                Text("Salvar regra")
            }
        }
    }
}
