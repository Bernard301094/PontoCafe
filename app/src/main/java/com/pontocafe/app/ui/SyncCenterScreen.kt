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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncCenterScreen(
    viewModel: AdminReliabilityViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    val snapshot = state.syncCenter
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (snapshot == null) viewModel.openSyncCenter()
    }

    PontoCafeResponsivePage(maxContentWidth = 840.dp) { responsive ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = responsive.pagePadding,
                    end = responsive.pagePadding,
                    top = PontoCafeSpacing.lg,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
            ) {
                item("header") {
                    PontoCafeScreenHeader(
                        title = "Sincronização",
                        eyebrow = "Modo offline",
                        onBack = onBack,
                    )
                }

                item("feedback") { ReliabilityFeedback(viewModel) }

                item("summary-title") {
                    SectionTitle(
                        "Resumo local",
                        "Acompanhe o que ainda precisa chegar ao servidor sem perder os registros feitos offline.",
                    )
                }

                item("summary") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
                        PcMetricTile(
                            value = (snapshot?.pending?.size ?: 0).toString(),
                            label = "Pendentes",
                            icon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                            attention = (snapshot?.pending?.size ?: 0) > 0,
                        )
                        PcMetricTile(
                            value = (snapshot?.failures?.size ?: 0).toString(),
                            label = "Com atenção",
                            icon = Icons.Default.Warning,
                            modifier = Modifier.weight(1f),
                            attention = (snapshot?.failures?.size ?: 0) > 0,
                        )
                    }
                }

                item("status") {
                    PcKeyValueCard(
                        title = "Estado local",
                        rows = listOf(
                            "Última conexão" to (snapshot?.lastServerOkMillis?.takeIf { it > 0L }?.let(::formatMillis) ?: "Sem registro"),
                            "Regras offline" to (snapshot?.rulesUpdatedAtMillis?.takeIf { it > 0L }?.let(::formatMillis) ?: "Sem cache"),
                        ),
                    )
                }

                item("privacy-note") {
                    PcStateBanner(
                        title = "Dados protegidos no aparelho",
                        supportingText = "Os registros offline permanecem cifrados até receberem confirmação do servidor.",
                        tone = PontoCafeTone.INFO,
                    )
                }

                item("actions") {
                    if (responsive.isCompact) {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            PcPrimaryButton(
                                text = if (state.loading) "Sincronizando…" else "Sincronizar agora",
                                icon = Icons.Default.Refresh,
                                onClick = viewModel::syncPending,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.loading && (snapshot?.pending?.isNotEmpty() == true),
                            )
                            PcSecondaryButton(
                                text = "Atualizar tela",
                                icon = Icons.Default.Refresh,
                                onClick = viewModel::openSyncCenter,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.loading,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcPrimaryButton(
                                text = if (state.loading) "Sincronizando…" else "Sincronizar agora",
                                icon = Icons.Default.Refresh,
                                onClick = viewModel::syncPending,
                                modifier = Modifier.weight(1f),
                                enabled = !state.loading && (snapshot?.pending?.isNotEmpty() == true),
                            )
                            PcSecondaryButton(
                                text = "Atualizar tela",
                                icon = Icons.Default.Refresh,
                                onClick = viewModel::openSyncCenter,
                                modifier = Modifier.weight(1f),
                                enabled = !state.loading,
                            )
                        }
                    }
                }

                item("pending-title") {
                    SectionTitle(
                        "Fila local",
                        if (snapshot?.pending.isNullOrEmpty()) {
                            "Nenhum registro aguardando envio."
                        } else {
                            "Cada item permanece aqui até o servidor confirmar o processamento."
                        },
                    )
                }

                if (snapshot?.pending.isNullOrEmpty()) {
                    item("empty") {
                        PcEmptyState(
                            title = "Tudo sincronizado",
                            supportingText = "Não há registros de ponto pendentes neste dispositivo.",
                            icon = Icons.Default.CheckCircle,
                        )
                    }
                } else {
                    items(snapshot!!.pending, key = { it.eventId }) { event ->
                        val failure = event.falha
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (failure == null) {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                            ),
                            border = if (failure == null) {
                                null
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
                            },
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(PontoCafeSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(event.nome, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (event.acao == "INICIAR") "Saída registrada offline" else "Retorno registrado offline",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    StatusPill(
                                        if (failure == null) "Aguardando" else "Atenção",
                                        if (failure == null) PontoCafeTone.WARNING else PontoCafeTone.DANGER,
                                    )
                                }
                                Text(
                                    formatInstant(event.ocorridoEm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                failure?.let {
                                    Text(
                                        it.mensagem,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        "Tentativas: ${it.tentativas} · última ${formatMillis(it.ultimaTentativaEmMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                                    )
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

private fun formatMillis(value: Long): String = runCatching {
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
}.getOrDefault("—")

private fun formatInstant(value: String): String = runCatching {
    Instant.parse(value)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.getOrDefault(value.take(16).replace('T', ' '))
