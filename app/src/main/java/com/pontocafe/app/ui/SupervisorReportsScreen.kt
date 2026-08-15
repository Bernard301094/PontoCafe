package com.pontocafe.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.SupervisorReportResponse
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@Composable
fun SupervisorReportsScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    val report = state.relatorio
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localError by remember { mutableStateOf<String?>(null) }
    val selectedDays = remember(state.relatorioInicio, state.relatorioFim) {
        val start = state.relatorioInicio
        val end = state.relatorioFim
        if (start == null || end == null) 7 else runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1L).toInt()
        }.getOrDefault(7)
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
        item(key = "header") {
            PontoCafeScreenHeader(title = "Relatórios", eyebrow = "Supervisor")
        }
        item(key = "intro") {
            Text(
                "Indicadores de utilização do café e ocorrências acima do limite.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "periods") {
            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                listOf(1 to "Hoje", 7 to "7 dias", 30 to "30 dias").forEach { (days, label) ->
                    FilterChip(
                        selected = selectedDays == days,
                        onClick = { viewModel.abrirRelatorios(days) },
                        label = { Text(label) },
                    )
                }
            }
        }

        state.erro?.let { error ->
            item(key = "server-error") {
                OperationalAlertCard(
                    title = "Não foi possível atualizar o relatório",
                    text = error,
                    actionLabel = "Tentar novamente",
                    onClick = { viewModel.abrirRelatorios(selectedDays.coerceAtLeast(1)) },
                    tone = PontoCafeTone.DANGER,
                )
            }
        }
        localError?.let { error ->
            item(key = "local-error") { Text(error, color = MaterialTheme.colorScheme.error) }
        }

        if (report != null) {
            item(key = "period") {
                Text(
                    "${report.periodo.inicio} a ${report.periodo.fim}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "metrics-1") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    MetricCard(report.resumo.totalPausas.toString(), "Pausas", Modifier.weight(1f))
                    MetricCard(report.resumo.colaboradores.toString(), "Pessoas", Modifier.weight(1f))
                }
            }
            item(key = "metrics-2") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    MetricCard(viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0), "Tempo médio", Modifier.weight(1f))
                    MetricCard(
                        report.resumo.acimaLimite.toString(),
                        "Acima do limite",
                        Modifier.weight(1f),
                        emphasized = report.resumo.acimaLimite > 0,
                    )
                }
            }
            item(key = "chart") { ExcessChart(report = report, viewModel = viewModel) }
            item(key = "export") {
                Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Button(
                        onClick = {
                            localError = null
                            scope.launch {
                                runCatching {
                                    val bytes = viewModel.baixarRelatorioCsv()
                                    val file = reportFile(context, "pontocafe-${report.periodo.inicio}-${report.periodo.fim}.csv")
                                    file.writeBytes(bytes)
                                    shareReport(context, file, "text/csv")
                                }.onFailure { localError = it.message ?: "Não foi possível exportar o CSV." }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("CSV") }
                    OutlinedButton(
                        onClick = {
                            localError = null
                            runCatching {
                                val file = createPdfReport(context, report, viewModel)
                                shareReport(context, file, "application/pdf")
                            }.onFailure { localError = it.message ?: "Não foi possível gerar o PDF." }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("PDF") }
                }
            }
            item(key = "ranking-title") {
                SectionTitle("Maiores excessos", "Ranking por tempo total acima do limite no período.")
            }
            if (report.maioresAtrasos.isEmpty()) {
                item(key = "no-excess") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LocalPontoCafeSemanticColors.current.successContainer),
                    ) {
                        Text(
                            "Nenhuma pausa acima do limite neste período.",
                            modifier = Modifier.padding(PontoCafeSpacing.md),
                            color = LocalPontoCafeSemanticColors.current.onSuccessContainer,
                        )
                    }
                }
            } else {
                items(report.maioresAtrasos, key = { it.colaboradorId }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.nome, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.ocorrencias} ocorrência(s) · excesso ${viewModel.formatarTempo(item.excessoTotalSegundos)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalPontoCafeSemanticColors.current.onWarningContainer,
                            )
                            Text(
                                "Maior pausa: ${viewModel.formatarTempo(item.maiorDuracaoSegundos)}",
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

@Composable
private fun ExcessChart(report: SupervisorReportResponse, viewModel: SupervisorViewModel) {
    val top = report.maioresAtrasos.take(6)
    if (top.isEmpty()) return
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(top) {
        modelProducer.runTransaction {
            @Suppress("DEPRECATION")
            columnSeries { series(top.map { it.excessoTotalSegundos / 60.0 }) }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            SectionTitle("Excesso acumulado", "Top ${top.size} · minutos acima do limite")
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            top.forEachIndexed { index, item ->
                Text(
                    "${index + 1}. ${item.nome} · ${viewModel.formatarTempo(item.excessoTotalSegundos)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun reportFile(context: Context, name: String): File {
    val directory = File(context.cacheDir, "reports").apply { mkdirs() }
    return File(directory, name)
}

private fun shareReport(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
}

private fun createPdfReport(context: Context, report: SupervisorReportResponse, viewModel: SupervisorViewModel): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas
    val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true }
    val headingPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }
    val textPaint = Paint().apply { textSize = 11f }
    var y = 48f
    canvas.drawText("Ponto Café — Relatório de pausas", 36f, y, titlePaint)
    y += 28f
    canvas.drawText("Período: ${report.periodo.inicio} a ${report.periodo.fim}", 36f, y, textPaint)
    y += 26f
    canvas.drawText("Resumo", 36f, y, headingPaint)
    y += 20f
    canvas.drawText("Pausas: ${report.resumo.totalPausas} · Colaboradores: ${report.resumo.colaboradores}", 36f, y, textPaint)
    y += 18f
    canvas.drawText("Tempo médio: ${viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0)}", 36f, y, textPaint)
    y += 18f
    canvas.drawText("Acima do limite: ${report.resumo.acimaLimite} · Fora do horário: ${report.resumo.foraHorario}", 36f, y, textPaint)
    y += 30f
    canvas.drawText("Maiores excessos", 36f, y, headingPaint)
    y += 20f
    report.maioresAtrasos.take(20).forEachIndexed { index, item ->
        if (y > 800f) return@forEachIndexed
        canvas.drawText(
            "${index + 1}. ${item.nome} — ${item.ocorrencias} ocorrência(s) — excesso ${viewModel.formatarTempo(item.excessoTotalSegundos)}",
            36f, y, textPaint,
        )
        y += 18f
    }
    document.finishPage(page)
    val file = reportFile(context, "pontocafe-${report.periodo.inicio}-${report.periodo.fim}.pdf")
    file.outputStream().use { output -> document.writeTo(output) }
    document.close()
    return file
}
