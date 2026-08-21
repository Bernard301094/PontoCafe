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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.BuildConfig
import com.pontocafe.app.data.AppHealthStore
import com.pontocafe.app.data.DiagnosticFleetDevice
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

    PontoCafeResponsivePage(maxContentWidth = 980.dp) { responsive ->
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
                        title = "Saúde do sistema",
                        eyebrow = "Operação · Ponto Café 1.0",
                        onBack = onBack,
                    )
                }

                item("feedback") { ReliabilityFeedback(viewModel) }

                if (diagnostic == null) {
                    item("loading") {
                        if (state.loading) {
                            PontoCafeLoadingSkeleton(rows = 5)
                        } else {
                            PcEmptyState(
                                title = "Diagnóstico indisponível",
                                supportingText = "Tente novamente para consultar servidor, integridade do Ponto e frota de dispositivos.",
                                icon = Icons.Default.HealthAndSafety,
                            )
                        }
                    }
                } else {
                    val fleet = diagnostic.frota
                    val integrity = diagnostic.integridade
                    val healthy = diagnostic.status == "ok" &&
                        diagnostic.banco.status == "ok" &&
                        (fleet?.desatualizados ?: 0) == 0 &&
                        (fleet?.alertasSaude ?: 0) == 0

                    item("status") {
                        PcHeroCard(
                            title = if (healthy) "Sistema pronto para operar" else "Sistema requer atenção",
                            supportingText = buildString {
                                append("Banco ${diagnostic.banco.latenciaMs} ms")
                                fleet?.let {
                                    append(" · ${it.totalAtivos} dispositivo(s)")
                                    if (it.desatualizados > 0) append(" · ${it.desatualizados} desatualizado(s)")
                                    if (it.alertasSaude > 0) append(" · ${it.alertasSaude} alerta(s) de saúde")
                                }
                                append(" · ID ${diagnostic.requestId}")
                            },
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
                            "Operação agora",
                            "Indicadores autoritativos do backend. Nenhum valor abaixo é uma simulação local.",
                        )
                    }

                    item("operation-metrics") {
                        MetricGrid(
                            firstValue = diagnostic.operacao.colaboradoresAtivos.toString(),
                            firstLabel = "Colaboradores",
                            firstIcon = Icons.Default.Groups,
                            secondValue = diagnostic.operacao.dispositivosAtivos.toString(),
                            secondLabel = "Dispositivos",
                            secondIcon = Icons.Default.Devices,
                            thirdValue = diagnostic.operacao.pausasAbertas.toString(),
                            thirdLabel = "Pausas abertas",
                            thirdIcon = Icons.Default.Coffee,
                            thirdAttention = diagnostic.operacao.pausasAbertas > 0,
                            fourthValue = diagnostic.operacao.sessoesAtivas.toString(),
                            fourthLabel = "Sessões ativas",
                            fourthIcon = Icons.Default.Key,
                        )
                    }

                    integrity?.let { integrityState ->
                        item("integrity-title") {
                            SectionTitle(
                                "Integridade do Ponto · últimas 24 h",
                                "Operações protegidas pela identidade idempotente. O contador não significa duplicidade: mede registros executados sob proteção exactly-once.",
                            )
                        }
                        item("integrity-metrics") {
                            MetricGrid(
                                firstValue = integrityState.pausasUltimas24h.toString(),
                                firstLabel = "Pausas registradas",
                                firstIcon = Icons.Default.Coffee,
                                secondValue = integrityState.operacoesProtegidasUltimas24h.toString(),
                                secondLabel = "Operações protegidas",
                                secondIcon = Icons.Default.Security,
                                thirdValue = integrityState.iniciosUltimas24h.toString(),
                                thirdLabel = "Inícios confirmados",
                                thirdIcon = Icons.Default.CheckCircle,
                                fourthValue = integrityState.retornosUltimas24h.toString(),
                                fourthLabel = "Retornos confirmados",
                                fourthIcon = Icons.Default.CheckCircle,
                            )
                        }
                        item("integrity-note") {
                            PcStateBanner(
                                title = "Replay seguro ativo",
                                supportingText = "Registro rápido protegido: ${integrityState.registroRapidoUltimas24h}. Em caso de resposta de rede incerta, o mesmo UUID pode ser reconciliado sem reinterpretar a pausa.",
                                tone = PontoCafeTone.INFO,
                            )
                        }
                    }

                    fleet?.let { fleetState ->
                        item("fleet-title") {
                            SectionTitle(
                                "Frota de dispositivos",
                                "Versão, telemetria e sinais de estabilidade enviados pelos aparelhos. Não inclui PIN, token, foto ou embedding.",
                            )
                        }
                        item("fleet-metrics") {
                            MetricGrid(
                                firstValue = fleetState.totalAtivos.toString(),
                                firstLabel = "Ativos",
                                firstIcon = Icons.Default.Devices,
                                secondValue = fleetState.comTelemetriaRecente.toString(),
                                secondLabel = "Telemetria <24 h",
                                secondIcon = Icons.Default.CheckCircle,
                                thirdValue = fleetState.desatualizados.toString(),
                                thirdLabel = "Desatualizados",
                                thirdIcon = Icons.Default.Warning,
                                thirdAttention = fleetState.desatualizados > 0,
                                fourthValue = fleetState.alertasSaude.toString(),
                                fourthLabel = "Alertas 24 h",
                                fourthIcon = Icons.Default.Warning,
                                fourthAttention = fleetState.alertasSaude > 0,
                            )
                        }

                        if (fleetState.semTelemetriaRecente > 0) {
                            item("fleet-stale") {
                                PcStateBanner(
                                    title = "Telemetria pendente",
                                    supportingText = "${fleetState.semTelemetriaRecente} dispositivo(s) ativo(s) não enviaram telemetria nas últimas 24 horas. Isso não bloqueia o Ponto, mas reduz a capacidade de diagnóstico remoto.",
                                    tone = PontoCafeTone.WARNING,
                                )
                            }
                        }

                        fleetState.dispositivos.orEmpty().take(12).forEach { device ->
                            item("fleet-${device.id}") { FleetDeviceCard(device) }
                        }
                    }

                    item("database") {
                        PcKeyValueCard(
                            title = "Banco de dados",
                            rows = listOf(
                                "Status" to diagnostic.banco.status,
                                "Latência" to "${diagnostic.banco.latenciaMs} ms",
                                "Relógio do servidor" to formatRemoteTime(diagnostic.banco.servidor),
                            ),
                        )
                    }

                    item("configuration") {
                        PcKeyValueCard(
                            title = "Política de produção",
                            rows = listOf(
                                "Timezone" to diagnostic.configuracao.timezone,
                                "Sessão" to "${diagnostic.configuracao.sessaoHoras} h",
                                "Reconhecimento facial" to "${diagnostic.configuracao.limiteFacial} · margem ${diagnostic.configuracao.margemFacial}",
                                "Offline máximo" to "${diagnostic.configuracao.offlineMaxHoras} h",
                                "Retenção biométrica" to "${diagnostic.configuracao.retencaoBiometricaDias} dias",
                                "Android mais recente" to diagnostic.configuracao.androidMaisRecente,
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
                        title = "Diagnóstico com minimização de dados",
                        supportingText = "Esta tela usa estado técnico, contadores e metadados de versão. Fotos e embeddings faciais não são enviados para a telemetria de saúde.",
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

@Composable
private fun MetricGrid(
    firstValue: String,
    firstLabel: String,
    firstIcon: ImageVector,
    secondValue: String,
    secondLabel: String,
    secondIcon: ImageVector,
    thirdValue: String,
    thirdLabel: String,
    thirdIcon: ImageVector,
    thirdAttention: Boolean = false,
    fourthValue: String,
    fourthLabel: String,
    fourthIcon: ImageVector,
    fourthAttention: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            PcMetricTile(firstValue, firstLabel, firstIcon, Modifier.weight(1f))
            PcMetricTile(secondValue, secondLabel, secondIcon, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            PcMetricTile(thirdValue, thirdLabel, thirdIcon, Modifier.weight(1f), attention = thirdAttention)
            PcMetricTile(fourthValue, fourthLabel, fourthIcon, Modifier.weight(1f), attention = fourthAttention)
        }
    }
}

@Composable
private fun FleetDeviceCard(device: DiagnosticFleetDevice) {
    PcSectionSurface {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(device.deviceModel, device.androidVersion?.let { "Android $it" })
                            .joinToString(" · ")
                            .ifBlank { "Aparelho sem metadados de modelo" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    if (device.alertaSaude) "Atenção" else "Operacional",
                    if (device.alertaSaude) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                StatusPill(
                    device.appVersion?.let { "App $it" } ?: "Versão não informada",
                    if (device.desatualizado) PontoCafeTone.WARNING else PontoCafeTone.NEUTRAL,
                )
                if (device.desatualizado) StatusPill("Atualização disponível", PontoCafeTone.WARNING)
            }

            Text(
                "Telemetria: ${formatRemoteTime(device.telemetriaEm)} · atividade: ${formatRemoteTime(device.ultimoAcessoEm)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (device.crashCount > 0 || device.stallCount > 0) {
                Text(
                    "Acumulado local: ${device.crashCount} crash(es) · ${device.stallCount} travamento(s) >5 s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun formatRemoteTime(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    val normalized = value.trim()
        .replace(' ', 'T')
        .let { raw -> Regex("([+-]\\d{2})$").replace(raw) { match -> "${match.groupValues[1]}:00" } }
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
