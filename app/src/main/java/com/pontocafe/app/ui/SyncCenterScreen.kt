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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    LaunchedEffect(Unit) {
        if (snapshot == null) viewModel.openSyncCenter()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") {
            PontoCafeScreenHeader(title = "Sincronização", eyebrow = "Modo offline", onBack = onBack)
        }
        item("feedback") { ReliabilityFeedback(viewModel) }

        item("summary") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                MetricCard(
                    value = (snapshot?.pending?.size ?: 0).toString(),
                    label = "Pendentes",
                    modifier = Modifier.weight(1f),
                    emphasized = (snapshot?.pending?.size ?: 0) > 0,
                )
                MetricCard(
                    value = (snapshot?.failures?.size ?: 0).toString(),
                    label = "Com atenção",
                    modifier = Modifier.weight(1f),
                    emphasized = (snapshot?.failures?.size ?: 0) > 0,
                )
            }
        }

        item("status") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Estado local", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Última conexão com o servidor · ${snapshot?.lastServerOkMillis?.takeIf { it > 0L }?.let(::formatMillis) ?: "sem registro"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Regras offline atualizadas · ${snapshot?.rulesUpdatedAtMillis?.takeIf { it > 0L }?.let(::formatMillis) ?: "sem cache"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Os registros offline permanecem cifrados no aparelho até serem processados pelo servidor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item("actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                Button(
                    onClick = viewModel::syncPending,
                    modifier = Modifier.weight(1f),
                    enabled = !state.loading && (snapshot?.pending?.isNotEmpty() == true),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sincronizar agora")
                    Text(if (state.loading) " Sincronizando…" else " Sincronizar agora")
                }
                OutlinedButton(
                    onClick = viewModel::openSyncCenter,
                    modifier = Modifier.weight(1f),
                    enabled = !state.loading,
                ) { Text("Atualizar tela") }
            }
        }

        item("pending-title") {
            SectionTitle(
                "Fila local",
                if (snapshot?.pending.isNullOrEmpty()) "Nenhum registro aguardando envio." else "Cada item permanece até receber confirmação do servidor.",
            )
        }

        if (snapshot?.pending.isNullOrEmpty()) {
            item("empty") {
                OperationalAlertCard(
                    title = "Tudo sincronizado",
                    text = "Não há registros de ponto pendentes neste dispositivo.",
                    actionLabel = "Atualizar",
                    onClick = viewModel::openSyncCenter,
                    tone = PontoCafeTone.SUCCESS,
                )
            }
        } else {
            items(snapshot!!.pending, key = { it.eventId }) { event ->
                val failure = event.falha
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (failure != null) MaterialTheme.colorScheme.error.copy(alpha = .35f) else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.nome, style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                if (failure == null) "Aguardando" else "Requer atenção",
                                if (failure == null) PontoCafeTone.WARNING else PontoCafeTone.DANGER,
                            )
                        }
                        Text(
                            if (event.acao == "INICIAR") "Saída registrada offline" else "Retorno registrado offline",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            formatInstant(event.ocorridoEm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        failure?.let {
                            Text(it.mensagem, color = MaterialTheme.colorScheme.error)
                            Text(
                                "Tentativas: ${it.tentativas} · última ${formatMillis(it.ultimaTentativaEmMillis)}",
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

private fun formatMillis(value: Long): String = runCatching {
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
}.getOrDefault("—")

private fun formatInstant(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.getOrDefault(value.take(16).replace('T', ' '))
