package com.pontocafe.app.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AppHealthStore
import java.time.Instant
import java.time.OffsetDateTime
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
    val localHealth = remember(diagnostic, state.loading) {
        AppHealthStore(context.applicationContext).snapshot()
    }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (diagnostic == null) viewModel.openSystemDiagnostics()
    }

    PontoCafeResponsivePage(maxContentWidth = 880.dp) { responsive ->
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
                        title = "Diagnóstico",
                        eyebrow = "Saúde do sistema",
                        onBack = onBack,
                    )
                }

                item("feedback") { ReliabilityFeedback(viewModel) }

                if (diagnostic == null) {
                    item("loading") {
                        if (state.loading) {
                            PontoCafeLoadingSkeleton(rows = 4)
                        } else {
                            PcEmptyState(
                                title = "Diagnóstico indisponível",
                                supportingText = "Tente novamente para consultar a saúde do servidor e deste aparelho.",
                                icon = Icons.Default.HealthAndSafety,
                            )
                        }
                    }
                } else {
                    val healthy = diagnostic.status == "ok" && diagnostic.banco.status == "ok"

                    item("status") {
                        PcHeroCard(
                            title = if (healthy) "Sistema operacional" else "Sistema requer atenção",
                            supportingText = "Banco ${diagnostic.banco.latenciaMs} ms · Request ID ${diagnostic.requestId}",
                            icon = Icons.Default.HealthAndSafety,
                            tone = if (healthy) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
                        )
                    }

                    item("refresh-top") {
                        PcSecondaryButton(
                            text = "Verificar novamente",
                            icon = Icons.Default.Refresh,
                            onClick = viewModel::openSystemDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.loading,
                        )
                    }

                    item("operation-title") {
                        SectionTitle(
                            "Operação",
                            "Indicadores do backend neste momento. Os valores são reais e não incluem simulações locais.",
                        )
                    }

                    item("metrics") {
                        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                PcMetricTile(
                                    value = diagnostic.operacao.colaboradoresAtivos.toString(),
                                    label = "Colaboradores",
                                    icon = Icons.Default.Groups,
                                    modifier = Modifier.weight(1f),
                                )
                                PcMetricTile(
                                    value = diagnostic.operacao.dispositivosAtivos.toString(),
                                    label = "Dispositivos",
                                    icon = Icons.Default.Devices,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                            ) {
                                PcMetricTile(
                                    value = diagnostic.operacao.pausasAbertas.toString(),
                                    label = "Pausas abertas",
                                    icon = Icons.Default.Coffee,
                                    modifier = Modifier.weight(1f),
                                    attention = diagnostic.operacao.pausasAbertas > 0,
                                )
                                PcMetricTile(
                                    value = diagnostic.operacao.sessoesAtivas.toString(),
                                    label = "Sessões ativas",
                                    icon = Icons.Default.Key,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    item("database") {
                        PcKeyValueCard(
                            title = "Banco de dados",
                            rows = listOf(
                                "Status" to diagnostic.banco.status,
                                "Latência" to "${diagnostic.banco.latenciaMs} ms",
                                "Relógio do servidor" to formatServerClock(diagnostic.banco.servidor),
                            ),
                        )
                    }

                    item("configuration") {
                        PcKeyValueCard(
                            title = "Configuração segura",
                            rows = listOf(
                                "Timezone" to diagnostic.configuracao.timezone,
                                "Sessão" to "${diagnostic.configuracao.sessaoHoras} h",
                                "Reconhecimento facial" to "${diagnostic.configuracao.limiteFacial} · margem ${diagnostic.configuracao.margemFacial}",
                                "Offline máximo" to "${diagnostic.configuracao.offlineMaxHoras} h",
                                "Retenção biométrica" to "${diagnostic.configuracao.retencaoBiometricaDias} dias",
                                "Android atual" to diagnostic.configuracao.androidMaisRecente,
                                "Android mínimo" to diagnostic.configuracao.androidMinimo,
                            ),
                        )
                    }
                }

                item("local-health-title") {
                    SectionTitle(
                        "Saúde deste aparelho",
                        "Telemetria local sem PIN, senha, token, foto ou embedding.",
                    )
                }

                item("local-health") {
                    PcKeyValueCard(
                        title = "Ponto Café ${BuildConfig.VERSION_NAME}",
                        rows = listOf(
                            "Último início" to formatHealthTime(localHealth.lastStartMillis),
                            "Crashes registrados" to localHealth.crashCount.toString(),
                            "Último crash" to if (localHealth.lastCrashMillis > 0) {
                                "${formatHealthTime(localHealth.lastCrashMillis)} · ${localHealth.lastCrashType ?: "erro"}"
                            } else {
                                "Nenhum"
                            },
                            "Local do último crash" to (localHealth.lastCrashLocation ?: "—"),
                            "Travamentos >5 s" to localHealth.stallCount.toString(),
                            "Último travamento" to if (localHealth.lastStallMillis > 0) {
                                "${formatHealthTime(localHealth.lastStallMillis)} · ${localHealth.lastStallDurationMillis} ms"
                            } else {
                                "Nenhum"
                            },
                        ),
                    )
                }

                item("privacy") {
                    PcStateBanner(
                        title = "Diagnóstico sem dados biométricos",
                        supportingText = "Esta tela mostra somente estado técnico e contadores operacionais.",
                        tone = PontoCafeTone.INFO,
                    )
                }

                item("refresh") {
                    PcPrimaryButton(
                        text = if (state.loading) "Executando…" else "Executar diagnóstico novamente",
                        icon = Icons.Default.Refresh,
                        onClick = viewModel::openSystemDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                    )
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

private fun formatHealthTime(value: Long): String {
    if (value <= 0L) return "—"
    return runCatching {
        Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
    }.getOrDefault("—")
}

private fun formatServerClock(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    val normalized = value.trim()
        .replace(' ', 'T')
        .let { raw ->
            Regex("([+-]\\d{2})$").replace(raw) { match -> "${match.groupValues[1]}:00" }
        }
    return runCatching {
        OffsetDateTime.parse(normalized)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
    }.recoverCatching {
        Instant.parse(normalized)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
    }.getOrDefault(value.take(24))
}
