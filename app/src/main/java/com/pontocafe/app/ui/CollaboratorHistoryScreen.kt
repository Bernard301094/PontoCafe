package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.domain.PontoCafeRules

@Composable
fun CollaboratorHistoryScreen(
    viewModel: AdminViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
    onBack: () -> Unit,
) {
    val state = reliabilityViewModel.state
    val history = state.history
    var deleteConfirmation by remember { mutableStateOf(false) }

    if (deleteConfirmation && history != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmation = false },
            title = { Text("Excluir biometria?") },
            text = {
                Text("O histórico de pausas será preservado. ${history.colaborador.nome} precisará cadastrar o rosto novamente para usar reconhecimento facial.")
            },
            confirmButton = {
                Button(onClick = {
                    deleteConfirmation = false
                    reliabilityViewModel.deleteBiometric(history.colaborador.id)
                }) { Text("Excluir biometria") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Cancelar") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") {
            PontoCafeScreenHeader(title = history?.colaborador?.nome ?: "Histórico", eyebrow = "Colaborador", onBack = onBack)
        }
        item("feedback") { ReliabilityFeedback(reliabilityViewModel) }

        if (history == null) {
            item("loading") {
                if (state.loading) {
                    PontoCafeLoadingSkeleton(rows = 5)
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Text("Histórico indisponível.", Modifier.padding(PontoCafeSpacing.md))
                    }
                }
            }
            return@LazyColumn
        }

        item("identity") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(history.colaborador.nome, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(history.colaborador.setor, history.colaborador.turno).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sem setor/turno" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusPill(
                        if (history.biometria.cadastrada) "Biometria ativa" else "Biometria pendente",
                        if (history.biometria.cadastrada) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                    )
                }
            }
        }

        item("period") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = history.periodoDias == days,
                        onClick = { reliabilityViewModel.openHistory(history.colaborador.id, days) },
                        label = { Text("$days dias") },
                    )
                }
            }
        }

        item("metrics") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                MetricCard(history.resumo.totalPausas.toString(), "Pausas", Modifier.weight(1f))
                MetricCard(
                    history.resumo.mediaSegundos?.let(PontoCafeRules::formatDuration) ?: "—",
                    "Tempo médio",
                    Modifier.weight(1f),
                )
            }
        }
        item("metrics-2") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                MetricCard(history.resumo.acimaLimite.toString(), "Acima do limite", Modifier.weight(1f), emphasized = history.resumo.acimaLimite > 0)
                MetricCard(history.resumo.foraHorario.toString(), "Fora do horário", Modifier.weight(1f), emphasized = history.resumo.foraHorario > 0)
            }
        }

        item("biometric-title") { SectionTitle("Biometria", "Ciclo de vida e rastreabilidade do rosto cadastrado.") }
        item("biometric") {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Text(if (history.biometria.cadastrada) "Rosto cadastrado" else "Sem rosto cadastrado", style = MaterialTheme.typography.titleMedium)
                    history.biometria.modelo?.let { Text("Modelo · $it") }
                    history.biometria.versaoModelo?.let { Text("Versão · $it") }
                    history.biometria.atualizadaEm?.let { Text("Última atualização · ${it.take(16).replace('T', ' ')}") }
                    Text(
                        "Política de retenção · ${history.biometria.retencaoDias} dias após desativação",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val collaborator = viewModel.state.colaboradores.firstOrNull { it.id == history.colaborador.id }
                    if (collaborator != null) {
                        Button(
                            onClick = { viewModel.cadastrarOuAtualizarRosto(collaborator) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (history.biometria.cadastrada) "Recadastrar rosto" else "Cadastrar rosto") }
                    }
                    if (history.biometria.cadastrada) {
                        OutlinedButton(onClick = { deleteConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Excluir biometria", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (history.biometria.eventos.isNotEmpty()) {
            item("bio-audit-title") { SectionTitle("Auditoria biométrica", "Quem cadastrou, testou ou excluiu a biometria.") }
            items(history.biometria.eventos, key = { "bio-${it.criadoEm}-${it.acao}" }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(event.acao.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                        Text(event.atorNome ?: event.atorTipo, style = MaterialTheme.typography.bodySmall)
                        Text(event.criadoEm.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item("pauses-title") { SectionTitle("Pausas recentes", "Até 100 registros dentro do período selecionado.") }
        if (history.pausas.isEmpty()) {
            item("empty") {
                Card(Modifier.fillMaxWidth()) { Text("Nenhuma pausa encontrada neste período.", Modifier.padding(PontoCafeSpacing.md)) }
            }
        } else {
            items(history.pausas, key = { it.id }) { pause ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (pause.excedeuLimite) MaterialTheme.colorScheme.error.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(PontoCafeSpacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(pause.inicioLocal, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (pause.fimLocal == null) "Pausa em andamento" else "Retorno ${pause.fimLocal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (pause.foraHorario) StatusPill("Fora do horário", PontoCafeTone.WARNING)
                        }
                        Column {
                            Text(pause.duracaoSegundos?.let(PontoCafeRules::formatDuration) ?: "…")
                            Text(
                                "limite ${PontoCafeRules.formatDuration(pause.limiteSegundos)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
