package com.pontocafe.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.ReportDay
import com.pontocafe.app.data.ReportDelay
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SupervisorReportResponse
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorReportsScreenV2(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val state = viewModel.state
    val report = state.relatorio
    val previousReport = state.relatorioAnterior
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var localError by remember { mutableStateOf<String?>(null) }
    var exportInProgress by remember { mutableStateOf<String?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var selectedDelay by remember { mutableStateOf<ReportDelay?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val collaboratorById = remember(state.colaboradores) { state.colaboradores.associateBy { it.id } }

    val sessionStore = remember(context, state.sessaoAdministrativa) {
        SecureAdminSessionStore(
            context.applicationContext,
            if (state.sessaoAdministrativa) "admin" else "supervisor",
        )
    }
    val activeAccount = remember(sessionStore, state.sessaoAdministrativa) { sessionStore.activeAccount() }
    val accountProfileLabel = if (state.sessaoAdministrativa) "Administrador" else "Supervisor"
    val accountFallbackName = activeAccount?.name?.takeIf { it.isNotBlank() } ?: accountProfileLabel

    val selectedDays = remember(state.relatorioInicio, state.relatorioFim) {
        val start = state.relatorioInicio
        val end = state.relatorioFim
        if (start == null || end == null) 7 else runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1L).toInt()
        }.getOrDefault(7)
    }

    if (showAccountSheet) {
        PcAccountProfileSheet(
            account = activeAccount,
            fallbackName = accountFallbackName,
            profileLabel = accountProfileLabel,
            onDismiss = { showAccountSheet = false },
            onLogout = if (state.sessaoAdministrativa) null else {
                {
                    showAccountSheet = false
                    viewModel.sair()
                }
            },
        )
    }

    if (showCalendar) {
        val initial = state.relatorioFim
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
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
                title = {
                    Text(
                        "Escolha uma data",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                },
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
                PcDialogBody {
                    PcKeyValueCard(
                        title = "Resumo no período",
                        rows = listOf(
                            "Ocorrências" to delay.ocorrencias.toString(),
                            "Maior pausa" to viewModel.formatarTempo(delay.maiorDuracaoSegundos),
                            "Excesso acumulado" to viewModel.formatarTempo(delay.excessoTotalSegundos),
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDelay = null }) { Text("Fechar") }
            },
        )
    }

    val exportReport = report
    if (showExportSheet && exportReport != null) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            PcBottomSheetContent {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Emitir relatório",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Escolha o formato. O arquivo usa exatamente o período exibido na tela.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PcStateBanner(
                    title = formatReportPeriod(exportReport),
                    supportingText = "${exportReport.resumo.totalPausas} pausa(s) · ${exportReport.resumo.colaboradores} pessoa(s)",
                    tone = if (exportReport.resumo.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                )
                PcFeedbackBanner(
                    message = localError,
                    tone = PontoCafeTone.DANGER,
                    onDismiss = { localError = null },
                )
                PcPrimaryButton(
                    text = "Gerar e compartilhar PDF",
                    icon = Icons.Default.Download,
                    onClick = {
                        localError = null
                        exportInProgress = "PDF"
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    createSupervisorPdfReportV2(context, exportReport, viewModel)
                                }
                            }.onSuccess { file ->
                                exportInProgress = null
                                showExportSheet = false
                                shareSupervisorReportV2(context, file, "application/pdf")
                            }.onFailure {
                                exportInProgress = null
                                localError = it.message ?: "Não foi possível gerar o PDF."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = exportInProgress == "PDF",
                    enabled = exportInProgress == null,
                )
                PcSecondaryButton(
                    text = "Exportar e compartilhar CSV",
                    icon = Icons.Default.Download,
                    onClick = {
                        localError = null
                        exportInProgress = "CSV"
                        scope.launch {
                            runCatching {
                                val bytes = viewModel.baixarRelatorioCsv()
                                withContext(Dispatchers.IO) {
                                    supervisorReportFileV2(
                                        context,
                                        "pontocafe-${exportReport.periodo.inicio}-${exportReport.periodo.fim}.csv",
                                    ).also { it.writeBytes(bytes) }
                                }
                            }.onSuccess { file ->
                                exportInProgress = null
                                showExportSheet = false
                                shareSupervisorReportV2(context, file, "text/csv")
                            }.onFailure {
                                exportInProgress = null
                                localError = it.message ?: "Não foi possível exportar o CSV."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = exportInProgress == "CSV",
                    enabled = exportInProgress == null,
                )
                Text(
                    "PDF é ideal para leitura e envio. CSV é indicado para análise em planilhas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    PontoCafeResponsivePage(maxContentWidth = 1080.dp) { responsive ->
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
                    top = PontoCafeSpacing.md,
                    bottom = 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                item("header") {
                    PcAreaTopBar(
                        title = "Relatórios",
                        eyebrow = accountProfileLabel,
                        account = activeAccount,
                        fallbackName = accountFallbackName,
                        onProfileClick = { showAccountSheet = true },
                        onBackToPonto = onClose,
                    )
                }

                item("period-title") {
                    SectionTitle(
                        title = "Período do relatório",
                        subtitle = "Altere o período para atualizar indicadores e exportação.",
                    )
                }
                item("periods") {
                    ReportPeriodSelector(
                        selectedDays = selectedDays,
                        wide = responsive.supportsTwoColumns,
                        onDays = viewModel::abrirRelatorios,
                        onCalendar = { showCalendar = true },
                    )
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
                            title = "Falha ao emitir relatório",
                            supportingText = error,
                            tone = PontoCafeTone.DANGER,
                        )
                    }
                }

                if (state.carregando && report == null) {
                    item("loading") { PontoCafeLoadingSkeleton(rows = 5) }
                }
                if (!state.carregando && report == null && state.erro.isNullOrBlank()) {
                    item("empty") {
                        PcEmptyState(
                            title = "Nenhum relatório carregado",
                            supportingText = "Escolha um período acima para consultar os dados.",
                            icon = Icons.Default.CalendarMonth,
                        )
                    }
                }

                if (report != null) {
                    item("summary") {
                        if (responsive.isExpanded && responsive.supportsTwoColumns) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                                verticalAlignment = Alignment.Top,
                            ) {
                                PcHeroCard(
                                    title = formatReportPeriod(report),
                                    supportingText = "${report.resumo.totalPausas} pausa(s) de ${report.resumo.colaboradores} pessoa(s).",
                                    icon = Icons.Default.CalendarMonth,
                                    tone = if (report.resumo.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                                    modifier = Modifier.weight(1.25f),
                                )
                                ReportExportActionCard(
                                    period = formatReportPeriod(report),
                                    onEmit = { showExportSheet = true },
                                    modifier = Modifier.weight(0.75f),
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                                PcHeroCard(
                                    title = formatReportPeriod(report),
                                    supportingText = "${report.resumo.totalPausas} pausa(s) de ${report.resumo.colaboradores} pessoa(s).",
                                    icon = Icons.Default.CalendarMonth,
                                    tone = if (report.resumo.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                                )
                                PcPrimaryButton(
                                    text = "Emitir relatório",
                                    icon = Icons.Default.Download,
                                    onClick = { showExportSheet = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    item("metrics") {
                        ReportMetricsGrid(
                            report = report,
                            viewModel = viewModel,
                            expanded = responsive.isExpanded && !responsive.usesLargeText,
                        )
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

                    item("comparison") {
                        PcReportComparisonCard(
                            current = report.resumo,
                            previous = previousReport?.resumo,
                        )
                    }
                    if (report.porDia.isNotEmpty()) {
                        item("trend") { PcReportTrendChart(report.porDia) }
                    }

                    item("days-title") {
                        SectionTitle(
                            "Registros por data",
                            "Abra um dia para consultar as pausas individuais.",
                        )
                    }
                    if (report.porDia.isEmpty()) {
                        item("days-empty") {
                            PcEmptyState(
                                title = "Sem dados no período",
                                supportingText = "Não há registros diários para a seleção atual.",
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
                            "Excessos que pedem atenção",
                            "Pessoas com maior excesso acumulado no período.",
                        )
                    }
                    if (report.maioresAtrasos.isEmpty()) {
                        item("ranking-empty") {
                            PcEmptyState(
                                title = "Tudo dentro do limite",
                                supportingText = "Nenhuma pessoa excedeu o tempo configurado neste período.",
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
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                                ) {
                                    CollaboratorAvatar(
                                        name = delay.nome,
                                        avatarUrl = collaboratorById[delay.colaboradorId]?.avatarUrl,
                                        avatarSize = 40.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            delay.nome,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "${delay.ocorrencias} ocorrência(s) · maior ${viewModel.formatarTempo(delay.maiorDuracaoSegundos)}",
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
private fun ReportPeriodSelector(
    selectedDays: Int,
    wide: Boolean,
    onDays: (Int) -> Unit,
    onCalendar: () -> Unit,
) {
    val chips: @Composable () -> Unit = {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            items(listOf(1 to "Hoje", 7 to "7 dias", 30 to "30 dias"), key = { "period-${it.first}" }) { (days, label) ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { onDays(days) },
                    label = { Text(label) },
                )
            }
        }
    }

    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f)) { chips() }
            PcSecondaryButton(
                text = "Escolher data",
                icon = Icons.Default.CalendarMonth,
                onClick = onCalendar,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            chips()
            PcSecondaryButton(
                text = "Escolher data no calendário",
                icon = Icons.Default.CalendarMonth,
                onClick = onCalendar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReportExportActionCard(
    period: String,
    onEmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PcSectionSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Text(
                "Emitir relatório",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                period,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "PDF para leitura ou CSV para análise em planilhas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PcPrimaryButton(
                text = "Escolher formato",
                icon = Icons.Default.Download,
                onClick = onEmit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReportMetricsGrid(
    report: SupervisorReportResponse,
    viewModel: SupervisorViewModel,
    expanded: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.6f
        when {
            singleColumn -> Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                PcMetricTile(report.resumo.totalPausas.toString(), "Pausas", Icons.Default.Coffee, Modifier.fillMaxWidth())
                PcMetricTile(report.resumo.colaboradores.toString(), "Pessoas", Icons.Default.Groups, Modifier.fillMaxWidth())
                PcMetricTile(
                    viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0),
                    "Tempo médio",
                    Icons.Default.Timer,
                    Modifier.fillMaxWidth(),
                )
                PcMetricTile(
                    report.resumo.acimaLimite.toString(),
                    "Acima do limite",
                    Icons.Default.Timer,
                    Modifier.fillMaxWidth(),
                    attention = report.resumo.acimaLimite > 0,
                )
            }
            expanded -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                ReportMetricTiles(report, viewModel, singleRow = true)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    PcMetricTile(report.resumo.totalPausas.toString(), "Pausas", Icons.Default.Coffee, Modifier.weight(1f))
                    PcMetricTile(report.resumo.colaboradores.toString(), "Pessoas", Icons.Default.Groups, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    PcMetricTile(
                        viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0),
                        "Tempo médio",
                        Icons.Default.Timer,
                        Modifier.weight(1f),
                    )
                    PcMetricTile(
                        report.resumo.acimaLimite.toString(),
                        "Acima do limite",
                        Icons.Default.Timer,
                        Modifier.weight(1f),
                        attention = report.resumo.acimaLimite > 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ReportMetricTiles(
    report: SupervisorReportResponse,
    viewModel: SupervisorViewModel,
    singleRow: Boolean,
) {
    if (!singleRow) return
    PcMetricTile(report.resumo.totalPausas.toString(), "Pausas", Icons.Default.Coffee, Modifier.weight(1f))
    PcMetricTile(report.resumo.colaboradores.toString(), "Pessoas", Icons.Default.Groups, Modifier.weight(1f))
    PcMetricTile(
        viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0),
        "Tempo médio",
        Icons.Default.Timer,
        Modifier.weight(1f),
    )
    PcMetricTile(
        report.resumo.acimaLimite.toString(),
        "Acima do limite",
        Icons.Default.Timer,
        Modifier.weight(1f),
        attention = report.resumo.acimaLimite > 0,
    )
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
        BoxWithConstraints {
            val stack = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.6f
            val summary: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        formatReportDate(day.data),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${day.pausas} pausa(s) · ${day.foraHorario} fora do horário",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (stack) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                ) {
                    summary()
                    StatusPill(
                        text = if (day.acimaLimite > 0) "${day.acimaLimite} acima" else "Dentro do limite",
                        tone = if (day.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    Box(modifier = Modifier.weight(1f)) { summary() }
                    StatusPill(
                        text = if (day.acimaLimite > 0) "${day.acimaLimite} acima" else "Dentro do limite",
                        tone = if (day.acimaLimite > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
                    )
                }
            }
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
    canvas.drawText(
        "Pausas: ${report.resumo.totalPausas} · Colaboradores: ${report.resumo.colaboradores}",
        36f,
        y,
        textPaint,
    )
    y += 18f
    canvas.drawText("Tempo médio: ${viewModel.formatarTempo(report.resumo.mediaSegundos ?: 0)}", 36f, y, textPaint)
    y += 18f
    canvas.drawText(
        "Acima do limite: ${report.resumo.acimaLimite} · Fora do horário: ${report.resumo.foraHorario}",
        36f,
        y,
        textPaint,
    )
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
