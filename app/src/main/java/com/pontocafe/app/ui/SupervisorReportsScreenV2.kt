package com.pontocafe.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.ReportDay
import com.pontocafe.app.data.ReportDelay
import com.pontocafe.app.data.SupervisorReportResponse
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorReportsScreenV2(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val state = viewModel.state
    val report = state.relatorio
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var localError by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var selectedDelay by remember { mutableStateOf<ReportDelay?>(null) }

    val selectedDays = remember(state.relatorioInicio, state.relatorioFim) {
        val start = state.relatorioInicio
        val end = state.relatorioFim
        if (start == null || end == null) 7 else runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1L).toInt()
        }.getOrDefault(7)
    }

    if (showCalendar) {
        val initial = state.relatorioFim?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.carregarRelatorio(date.toString(), date.toString())
                        }
                        showCalendar = false
                    },
                ) { Text("Ver relatório") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(
                state = pickerState,
                title = { Text("Escolha uma data", modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) },
                headline = null,
                showModeToggle = false,
            )
        }
    }

    selectedDelay?.let { delay ->
        AlertDialog(
            onDismissRequest = { selectedDelay = null },
            title = { Text(delay.nome) },
            text = {
                PcKeyValueCard(
                    title = "Resumo no período",
                    rows = listOf(
                        "Ocorrências" to delay.ocorrencias.toString(),
                        "Maior pausa" to viewModel.formatarTempo(delay.maiorDuracaoSegundos),
                        "Excesso acumulado" to viewModel.formatarTempo(delay.excessoTotalSegundos),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedDelay = null }) { Text("Fechar") }
            },
        )
    }

    PontoCafeResponsivePage(maxContentWidth = 960.dp) { responsive ->
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
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        PontoCafeScreenHeader(title = "Relatórios", eyebrow = "Supervisor")
                        PcSecondaryButton(
                            text = "Voltar ao Ponto",
                            onClick = onClose,
                            modifier = if (responsive.isCompact) Modifier.fillMaxWidth() else Modifier,
                        )
                    }
                }

                item("period-title") {
                    SectionTitle(
                        "Período",
                        "Use um atalho ou escolha uma data no calendário. Cada dia do relatório pode ser aberto.",
                    )
                }

                item("periods") {
                    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                        ) {
                            listOf(1 to "Hoje", 7 to "7 dias", 30 to "30 dias").forEach { (days, label) ->
                                FilterChip(
                                    selected = selectedDays == days,
                                    onClick = { viewModel.abrirRelatorios(days) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        PcSecondaryButton(
                            text = "Escolher data no calendário",
                            icon = Icons.Default.CalendarMonth,
                            onClick = { showCalendar = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.erro?.let { error ->
                    item("server-error") {
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
                    item("local-error") {
                        PcStateBanner(
                            title = "Falha ao exportar",
                            supportingText = error,
                            tone = PontoCafeTone.DANGER,
                        )
                    }
                }

                if (state.carregando && report == null) {
                    item("loading") { PontoCafeLoadingSkeleton(rows = 5) }
                }

                if (report != null) {
                    item("hero") {
                        PcHeroCard(
                            title = formatReportPeriod(report),
                            supportingText = "${report.resumo.totalPausas} pausa(s) de ${report.resumo.colaboradores} pessoa(s) no período selecionado.",
                            icon = Icons.Default.CalendarMonth,
                            tone = if (report.resumo.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                        )
                    }

                    item("metrics-1") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = report.resumo.totalPausas.toString(),
                                label = "Pausas",
                                icon = Icons.Default.Coffee,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = report.resumo.colaboradores.toString(),
                                label = "Pessoas",
                                icon = Icons.Default.Groups,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    item("metrics-2") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0),
                                label = "Tempo médio",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f),
                            )
                            PcMetricTile(
                                value = report.resumo.acimaLimite.toString(),
                                label = "Acima do limite",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f),
                                attention = report.resumo.acimaLimite > 0,
                            )
                        }
                    }

                    if (report.resumo.foraHorario > 0) {
                        item("outside") {
                            PcStateBanner(
                                title = "${report.resumo.foraHorario} pausa(s) fora do horário",
                                supportingText = "Abra uma data abaixo para consultar os registros individuais.",
                                tone = PontoCafeTone.WARNING,
                            )
                        }
                    }

                    item("days-title") {
                        SectionTitle(
                            "Histórico por data",
                            "Toque em um dia para abrir todas as pausas registradas naquela data.",
                        )
                    }

                    if (report.porDia.isEmpty()) {
                        item("days-empty") {
                            PcEmptyState(
                                title = "Sem dados diários",
                                supportingText = "Não há registros no período selecionado.",
                                icon = Icons.Default.CalendarMonth,
                            )
                        }
                    } else {
                        items(report.porDia.sortedByDescending { it.data }, key = { "report-day-${it.data}" }) { day ->
                            ReportDayCardV2(
                                day = day,
                                onClick = { viewModel.abrirHistorico(day.data) },
                            )
                        }
                    }

                    item("ranking-title") {
                        SectionTitle(
                            "Maiores excessos",
                            "Toque em uma pessoa para abrir o resumo completo de ocorrências no período.",
                        )
                    }

                    if (report.maioresAtrasos.isEmpty()) {
                        item("ranking-empty") {
                            PcEmptyState(
                                title = "Nenhuma pausa acima do limite",
                                supportingText = "Todos os registros do período permaneceram dentro do tempo configurado.",
                                icon = Icons.Default.Timer,
                            )
                        }
                    } else {
                        items(report.maioresAtrasos, key = { "delay-${it.colaboradorId}" }) { delay ->
                            androidx.compose.material3.Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedDelay = delay },
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                shape = MaterialTheme.shapes.large,
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(PontoCafeSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                                ) {
                                    InitialAvatar(delay.nome)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(delay.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${delay.ocorrencias} ocorrência(s) · maior pausa ${viewModel.formatarTempo(delay.maiorDuracaoSegundos)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    StatusPill(
                                        text = viewModel.formatarTempo(delay.excessoTotalSegundos),
                                        tone = PontoCafeTone.WARNING,
                                    )
                                }
                            }
                        }
                    }

                    item("export-title") {
                        SectionTitle("Exportar", "Compartilhe o período atual em CSV ou PDF.")
                    }

                    item("export") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcPrimaryButton(
                                text = "CSV",
                                icon = Icons.Default.Download,
                                onClick = {
                                    localError = null
                                    scope.launch {
                                        runCatching {
                                            val bytes = viewModel.baixarRelatorioCsv()
                                            val file = supervisorReportFileV2(
                                                context,
                                                "pontocafe-${report.periodo.inicio}-${report.periodo.fim}.csv",
                                            )
                                            file.writeBytes(bytes)
                                            shareSupervisorReportV2(context, file, "text/csv")
                                        }.onFailure {
                                            localError = it.message ?: "Não foi possível exportar o CSV."
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PcSecondaryButton(
                                text = "PDF",
                                icon = Icons.Default.Download,
                                onClick = {
                                    localError = null
                                    runCatching {
                                        val file = createSupervisorPdfReportV2(context, report, viewModel)
                                        shareSupervisorReportV2(context, file, "application/pdf")
                                    }.onFailure {
                                        localError = it.message ?: "Não foi possível gerar o PDF."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
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

@Composable
private fun ReportDayCardV2(day: ReportDay, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (day.acimaLimite > 0) {
                LocalPontoCafeSemanticColors.current.warningContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = MaterialTheme.shapes.large,
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(formatReportDate(day.data), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${day.pausas} pausa(s) · ${day.foraHorario} fora do horário",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (day.acimaLimite > 0) "${day.acimaLimite} acima" else "Dentro do limite",
                tone = if (day.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
            )
        }
    }
}

private fun formatReportDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(value)

private fun formatReportPeriod(report: SupervisorReportResponse): String =
    if (report.periodo.inicio == report.periodo.fim) {
        formatReportDate(report.periodo.inicio)
    } else {
        "${formatReportDate(report.periodo.inicio)} a ${formatReportDate(report.periodo.fim)}"
    }

private fun supervisorReportFileV2(context: Context, name: String): File {
    val directory = File(context.cacheDir, "reports").apply { mkdirs() }
    return File(directory, name)
}

private fun shareSupervisorReportV2(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
}

private fun createSupervisorPdfReportV2(
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
    canvas.drawText("Período: ${formatReportPeriod(report)}", 36f, y, textPaint)
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
    val file = supervisorReportFileV2(context, "pontocafe-${report.periodo.inicio}-${report.periodo.fim}.pdf")
    file.outputStream().use { output -> document.writeTo(output) }
    document.close()
    return file
}
