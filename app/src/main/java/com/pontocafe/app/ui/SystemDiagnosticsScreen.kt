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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AppHealthStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SystemDiagnosticsScreen(
    viewModel: AdminReliabilityViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.state
    val diagnostic = state.diagnostic
    val localHealth = remember(diagnostic, state.loading) { AppHealthStore(context.applicationContext).snapshot() }

    LaunchedEffect(Unit) {
        if (diagnostic == null) viewModel.openSystemDiagnostics()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item("header") { PontoCafeScreenHeader(title = "Diagnóstico", eyebrow = "Saúde do sistema", onBack = onBack) }
        item("feedback") { ReliabilityFeedback(viewModel) }

        if (diagnostic == null) {
            item("loading") {
                if (state.loading) {
                    PontoCafeLoadingSkeleton(rows = 4)
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Text("Diagnóstico indisponível.", Modifier.padding(PontoCafeSpacing.md))
                    }
                }
            }
        } else {
            item("status") {
                OperationalAlertCard(
                    title = if (diagnostic.status == "ok" && diagnostic.banco.status == "ok") "Sistema operacional" else "Verifique o sistema",
                    text = "Request ID ${diagnostic.requestId} · banco ${diagnostic.banco.latenciaMs} ms",
                    actionLabel = "Verificar novamente",
                    onClick = viewModel::openSystemDiagnostics,
                    tone = if (diagnostic.status == "ok" && diagnostic.banco.status == "ok") PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                )
            }
            item("metrics") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(diagnostic.operacao.colaboradoresAtivos.toString(), "Colaboradores", Modifier.weight(1f))
                    MetricCard(diagnostic.operacao.dispositivosAtivos.toString(), "Dispositivos", Modifier.weight(1f))
                }
            }
            item("metrics2") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                    MetricCard(diagnostic.operacao.pausasAbertas.toString(), "Pausas abertas", Modifier.weight(1f), emphasized = diagnostic.operacao.pausasAbertas > 0)
                    MetricCard(diagnostic.operacao.sessoesAtivas.toString(), "Sessões ativas", Modifier.weight(1f))
                }
            }
            item("database") {
                DiagnosticCard(
                    "Banco de dados",
                    listOf(
                        "Status" to diagnostic.banco.status,
                        "Latência" to "${diagnostic.banco.latenciaMs} ms",
                        "Relógio do servidor" to (diagnostic.banco.servidor ?: "—"),
                    ),
                )
            }
            item("configuration") {
                DiagnosticCard(
                    "Configuração segura",
                    listOf(
                        "Timezone" to diagnostic.configuracao.timezone,
                        "Sessão" to "${diagnostic.configuracao.sessaoHoras} h",
                        "Reconhecimento facial" to "limiar ${diagnostic.configuracao.limiteFacial} · margem ${diagnostic.configuracao.margemFacial}",
                        "Offline máximo" to "${diagnostic.configuracao.offlineMaxHoras} h",
                        "Retenção biométrica" to "${diagnostic.configuracao.retencaoBiometricaDias} dias após desativação",
                        "Android atual" to diagnostic.configuracao.androidMaisRecente,
                        "Android mínimo" to diagnostic.configuracao.androidMinimo,
                    ),
                )
            }
        }

        item("local-health-title") {
            SectionTitle("Saúde deste aparelho", "Telemetria local sem PIN, senha, token, foto ou embedding.")
        }
        item("local-health") {
            DiagnosticCard(
                "Ponto Café ${BuildConfig.VERSION_NAME}",
                listOf(
                    "Último início" to formatHealthTime(localHealth.lastStartMillis),
                    "Crashes registrados" to localHealth.crashCount.toString(),
                    "Último crash" to if (localHealth.lastCrashMillis > 0) {
                        "${formatHealthTime(localHealth.lastCrashMillis)} · ${localHealth.lastCrashType ?: "erro"}"
                    } else "nenhum",
                    "Local do último crash" to (localHealth.lastCrashLocation ?: "—"),
                    "Travamentos >5 s" to localHealth.stallCount.toString(),
                    "Último travamento" to if (localHealth.lastStallMillis > 0) {
                        "${formatHealthTime(localHealth.lastStallMillis)} · ${localHealth.lastStallDurationMillis} ms"
                    } else "nenhum",
                ),
            )
        }

        item("refresh") {
            Button(onClick = viewModel::openSystemDiagnostics, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) {
                Text("Executar diagnóstico novamente")
            }
        }
    }
}

@Composable
private fun DiagnosticCard(title: String, rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatHealthTime(value: Long): String {
    if (value <= 0L) return "—"
    return runCatching {
        Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
    }.getOrDefault("—")
}
