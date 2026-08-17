package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.CoffeeRuleV2
import com.pontocafe.app.domain.PontoCafeRules

@Composable
fun AdminManagementScreenV2(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
    onDevicesClick: () -> Unit,
    onSyncClick: () -> Unit,
    onKioskClick: () -> Unit,
) {
    val reliability = reliabilityViewModel.state

    LaunchedEffect(Unit) {
        reliabilityViewModel.loadManagement()
    }

    PontoCafeResponsivePage(maxContentWidth = 1080.dp) { responsive ->
        val stackTiles = responsive.availableWidth < 420.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = responsive.pagePadding),
            contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            item("header") {
                PontoCafeScreenHeader(title = "Gestão", eyebrow = "Operação e segurança")
            }
            item("intro") {
                Text(
                    "Saúde do sistema, rastreabilidade, sincronização, biometria e regras operacionais.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item("feedback-admin") { AdminFeedback(viewModel) }
            item("feedback-reliability") { ReliabilityFeedback(reliabilityViewModel) }

            item("tools-title") {
                SectionTitle("Ferramentas", "Áreas administrativas organizadas por finalidade.")
            }
            item("tools-row-1") {
                if (stackTiles) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2("Dispositivos", "PIN, tokens e aparelhos", Icons.Default.Devices, onDevicesClick, Modifier.fillMaxWidth())
                        ManagementTileV2("Sincronização", "Offline e pendências", Icons.Default.Sync, onSyncClick, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2("Dispositivos", "PIN, tokens e aparelhos", Icons.Default.Devices, onDevicesClick, Modifier.weight(1f))
                        ManagementTileV2("Sincronização", "Offline e pendências", Icons.Default.Sync, onSyncClick, Modifier.weight(1f))
                    }
                }
            }
            item("tools-row-2") {
                if (stackTiles) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2(
                            "Biometria",
                            "Precisão, modelos e retenção",
                            Icons.Default.Fingerprint,
                            reliabilityViewModel::openBiometricDiagnostics,
                            Modifier.fillMaxWidth(),
                        )
                        ManagementTileV2(
                            "Diagnóstico",
                            "Servidor, DB e configuração",
                            Icons.Default.HealthAndSafety,
                            reliabilityViewModel::openSystemDiagnostics,
                            Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2(
                            "Biometria",
                            "Precisão, modelos e retenção",
                            Icons.Default.Fingerprint,
                            reliabilityViewModel::openBiometricDiagnostics,
                            Modifier.weight(1f),
                        )
                        ManagementTileV2(
                            "Diagnóstico",
                            "Servidor, DB e configuração",
                            Icons.Default.HealthAndSafety,
                            reliabilityViewModel::openSystemDiagnostics,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            item("tools-row-3") {
                if (stackTiles) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2("Modo terminal", "Quiosque e tela protegida", Icons.Default.Security, onKioskClick, Modifier.fillMaxWidth())
                        ManagementTileV2("Auditoria", "Ações administrativas", Icons.Default.History, viewModel::abrirAuditoria, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2("Modo terminal", "Quiosque e tela protegida", Icons.Default.Security, onKioskClick, Modifier.weight(1f))
                        ManagementTileV2("Auditoria", "Ações administrativas", Icons.Default.History, viewModel::abrirAuditoria, Modifier.weight(1f))
                    }
                }
            }
            item("tools-row-4") {
                if (stackTiles) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2("Autorizações", "Exceções fora do horário", Icons.Default.LockClock, viewModel::abrirAutorizacao, Modifier.fillMaxWidth())
                        DefaultRuleCard(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        ManagementTileV2("Autorizações", "Exceções fora do horário", Icons.Default.LockClock, viewModel::abrirAutorizacao, Modifier.weight(1f))
                        DefaultRuleCard(Modifier.weight(1f))
                    }
                }
            }

            item("rules-title") {
                SectionTitle(
                    "Horários e tempo de café",
                    "O padrão atual é 15 minutos (900 s). O editor aceita segundos para manter precisão sem alterar esse padrão.",
                )
            }

            if (reliability.rules.isEmpty() && reliability.loading) {
                item("rules-loading") {
                    Card(Modifier.fillMaxWidth()) {
                        Text("Carregando regras…", Modifier.padding(PontoCafeSpacing.md))
                    }
                }
            } else {
                reliability.rules.forEach { rule ->
                    item("rule-${rule.periodo}") {
                        CoffeeRuleEditorV2(reliabilityViewModel, rule)
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultRuleCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Regra padrão", style = MaterialTheme.typography.labelLarge)
            Text("15:00", style = MaterialTheme.typography.headlineSmall)
            Text("15 minutos por pausa", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ManagementTileV2(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoffeeRuleEditorV2(viewModel: AdminReliabilityViewModel, rule: CoffeeRuleV2) {
    val initial = PontoCafeRules.splitDuration(rule.limiteSegundos)
    var start by remember(rule) { mutableStateOf(rule.inicio) }
    var end by remember(rule) { mutableStateOf(rule.fim) }
    var minutes by remember(rule) { mutableStateOf(initial.first.toString()) }
    var seconds by remember(rule) { mutableStateOf(initial.second.toString()) }
    var active by remember(rule) { mutableStateOf(rule.ativo) }
    var localError by remember(rule) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackFields = maxWidth < 430.dp
            Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (rule.periodo == "MANHA") "Período da manhã" else "Período da tarde", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Duração atual ${PontoCafeRules.formatDuration(rule.limiteSegundos)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                if (stackFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { start = it.take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Início") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = end,
                            onValueChange = { end = it.take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Fim") },
                            singleLine = true,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        OutlinedTextField(
                            value = start,
                            onValueChange = { start = it.take(5) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Início") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = end,
                            onValueChange = { end = it.take(5) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Fim") },
                            singleLine = true,
                        )
                    }
                }

                if (stackFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Minutos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = seconds,
                            onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Segundos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Minutos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = seconds,
                            onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Segundos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }

                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val mins = minutes.toIntOrNull()
                        val secs = seconds.toIntOrNull()
                        localError = when {
                            !start.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário inicial válido."
                            !end.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) -> "Informe um horário final válido."
                            start >= end -> "O horário final deve ser posterior ao inicial."
                            mins == null || secs == null -> "Informe minutos e segundos."
                            secs !in 0..59 -> "Os segundos devem ficar entre 0 e 59."
                            else -> runCatching { PontoCafeRules.durationSeconds(mins, secs) }.exceptionOrNull()?.message
                        }
                        if (localError == null && mins != null && secs != null) {
                            viewModel.saveRule(rule.periodo, start, end, PontoCafeRules.durationSeconds(mins, secs), active)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.state.loading,
                ) {
                    Text("Salvar regra")
                }
            }
        }
    }
}

@Composable
fun ReliabilityFeedback(viewModel: AdminReliabilityViewModel) {
    val state = viewModel.state
    state.message?.let { message ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(message, Modifier.padding(PontoCafeSpacing.md), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    state.error?.let { error ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(error, Modifier.padding(PontoCafeSpacing.md), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
