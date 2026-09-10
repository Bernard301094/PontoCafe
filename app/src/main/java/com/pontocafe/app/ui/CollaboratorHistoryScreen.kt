package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    val listState = rememberLazyListState()

    
    PontoCafeResponsivePage(maxContentWidth = 900.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.lg,
                    bottom = 104.dp,
                ),
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
                }
            }
        }

        item("period") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                items(listOf(7, 30, 90), key = { it }) { days ->
                    FilterChip(
                        selected = history.periodoDias == days,
                        onClick = { reliabilityViewModel.openHistory(history.colaborador.id, days) },
                        label = { Text("$days dias") },
                    )
                }
            }
        }

        item("metrics") {
            if (responsive.isNarrow || responsive.usesLargeText) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(history.resumo.totalPausas.toString(), "Pausas", Modifier.fillMaxWidth())
                    MetricCard(
                        history.resumo.mediaSegundos?.let(PontoCafeRules::formatDuration) ?: "—",
                        "Tempo médio",
                        Modifier.fillMaxWidth(),
                    )
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                MetricCard(history.resumo.totalPausas.toString(), "Pausas", Modifier.weight(1f))
                MetricCard(
                    history.resumo.mediaSegundos?.let(PontoCafeRules::formatDuration) ?: "—",
                    "Tempo médio",
                    Modifier.weight(1f),
                )
            }
        }
        item("metrics-2") {
            if (responsive.isNarrow || responsive.usesLargeText) {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(history.resumo.acimaLimite.toString(), "Acima do limite", Modifier.fillMaxWidth(), emphasized = history.resumo.acimaLimite > 0)
                    MetricCard(history.resumo.foraHorario.toString(), "Fora do horário", Modifier.fillMaxWidth(), emphasized = history.resumo.foraHorario > 0)
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                MetricCard(history.resumo.acimaLimite.toString(), "Acima do limite", Modifier.weight(1f), emphasized = history.resumo.acimaLimite > 0)
                MetricCard(history.resumo.foraHorario.toString(), "Fora do horário", Modifier.weight(1f), emphasized = history.resumo.foraHorario > 0)
            }
        }

        item("codes-title") { SectionTitle("Códigos de café", "Quem liberou cada pausa desta pessoa, e se o passe chegou a ser usado.") }
        item("codes-summary") {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    val historicoCodigos = history.codigosAcesso
                    Text(
                        "${historicoCodigos?.eventos?.size ?: 0} código(s) emitidos recentemente",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Política de retenção · ${historicoCodigos?.retencaoDias ?: 0} dias após o código se esgotar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Um código com saída registrada e sem retorno nunca é removido: é ele que ainda permite fechar aquela pausa.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        val eventosCodigo = history.codigosAcesso?.eventos.orEmpty()
        if (eventosCodigo.isNotEmpty()) {
            items(eventosCodigo, key = { "codigo-${it.id}" }) { evento ->
                Card(Modifier.fillMaxWidth().animateItem()) {
                    Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            when {
                                evento.canceladoEm != null -> "Cancelado"
                                evento.retornoEm != null -> "Usado na saída e no retorno"
                                evento.saidaEm != null -> "Usado na saída · retorno pendente"
                                else -> "Emitido e não usado"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        evento.emitidoPor?.let {
                            Text("Emitido por $it", style = MaterialTheme.typography.bodySmall)
                        }
                        evento.motivo?.let {
                            Text("Motivo · $it", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            evento.criadoEm.take(16).replace('T', ' '),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item("pauses-title") { SectionTitle("Pausas recentes", "Até 100 registros dentro do período selecionado.") }
        if (history.pausas.isEmpty()) {
            item("empty") {
                PcEmptyState(
                    title = "Nenhuma pausa neste período",
                    supportingText = "Escolha outro intervalo para consultar o histórico desta pessoa.",
                )
            }
        } else {
            itemsIndexed(history.pausas, key = { _, pause -> pause.id }) { index, pause ->
                HistoryTimelineRow(
                    isFirst = index == 0,
                    isLast = index == history.pausas.lastIndex,
                    modifier = Modifier.animateItem(),
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                stateDescription = if (pause.excedeuLimite) {
                                    "Pausa acima do limite"
                                } else if (pause.foraHorario) {
                                    "Pausa fora do horário"
                                } else {
                                    "Pausa dentro do limite"
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (pause.excedeuLimite) LocalPontoCafeSemanticColors.current.critical.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant),
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

            PcScrollToTopFab(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = responsive.pagePadding, bottom = PontoCafeSpacing.md),
            )
        }
    }
}
