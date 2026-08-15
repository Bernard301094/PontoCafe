package com.pontocafe.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.SupervisorReportResponse
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SupervisorReportsScreen(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    val report = state.relatorio
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Relatórios")
        Text(
            "Indicadores das pausas registradas e ocorrências acima do limite.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.abrirRelatorios(1) }, modifier = Modifier.weight(1f)) { Text("Hoje") }
            OutlinedButton(onClick = { viewModel.abrirRelatorios(7) }, modifier = Modifier.weight(1f)) { Text("7 dias") }
            OutlinedButton(onClick = { viewModel.abrirRelatorios(30) }, modifier = Modifier.weight(1f)) { Text("30 dias") }
        }

        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (report != null) {
            Text(
                "Período: ${report.periodo.inicio} a ${report.periodo.fim}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(report.resumo.totalPausas.toString(), "Pausas", Modifier.weight(1f))
                MetricCard(report.resumo.colaboradores.toString(), "Pessoas", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0),
                    "Tempo médio",
                    Modifier.weight(1f),
                )
                MetricCard(
                    report.resumo.acimaLimite.toString(),
                    "Acima do limite",
                    Modifier.weight(1f),
                    emphasized = report.resumo.acimaLimite > 0,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ) { Text("Exportar CSV") }
                OutlinedButton(
                    onClick = {
                        localError = null
                        runCatching {
                            val file = createPdfReport(context, report, viewModel)
                            shareReport(context, file, "application/pdf")
                        }.onFailure { localError = it.message ?: "Não foi possível gerar o PDF." }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Exportar PDF") }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (report != null) {
                item {
                    SectionTitle(
                        "Maiores excessos",
                        "Ranking do período por tempo total acima do limite.",
                    )
                }
                if (report.maioresAtrasos.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text("Nenhuma pausa acima do limite neste período.", modifier = Modifier.padding(16.dp))
                        }
                    }
                } else {
                    items(report.maioresAtrasos, key = { it.colaboradorId }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.nome, fontWeight = FontWeight.SemiBold)
                                Text("${item.ocorrencias} ocorrência(s)")
                                Text(
                                    "Excesso acumulado: ${viewModel.formatarTempo(item.excessoTotalSegundos)}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Maior pausa: ${viewModel.formatarTempo(item.maiorDuracaoSegundos)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = viewModel::voltarAoVivo, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar ao acompanhamento")
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

private fun createPdfReport(
    context: Context,
    report: SupervisorReportResponse,
    viewModel: SupervisorViewModel,
): File {
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
            36f,
            y,
            textPaint,
        )
        y += 18f
    }

    document.finishPage(page)
    val file = reportFile(context, "pontocafe-${report.periodo.inicio}-${report.periodo.fim}.pdf")
    file.outputStream().use(document::writeTo)
    document.close()
    return file
}
