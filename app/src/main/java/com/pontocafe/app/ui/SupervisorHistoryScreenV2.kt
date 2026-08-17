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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.PausaSupervisor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorHistoryScreenV2(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    val listState = rememberLazyListState()
    val selectedDate = state.historicoData?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    var showCalendar by remember { mutableStateOf(false) }
    var selectedPause by remember { mutableStateOf<PausaSupervisor?>(null) }

    if (showCalendar) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.abrirHistorico(date.toString())
                        }
                        showCalendar = false
                    },
                ) { Text("Abrir dia") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(
                state = pickerState,
                title = { Text("Escolha a data do histórico", modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) },
                headline = null,
                showModeToggle = false,
            )
        }
    }

    selectedPause?.let { pause ->
        HistoryPauseDetailDialog(
            pause = pause,
            viewModel = viewModel,
            onDismiss = { selectedPause = null },
        )
    }

    val total = state.historico.size
    val overLimit = state.historico.count {
        it.excedeuLimite ?: ((it.duracaoSegundos ?: it.tempoSegundos ?: 0) > it.limiteSegundos)
    }
    val outside = state.historico.count { it.foraHorario }

    PontoCafeResponsivePage(maxContentWidth = 860.dp) { responsive ->
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
                        title = "Histórico",
                        eyebrow = "Supervisor",
                        onBack = viewModel::voltarAoVivo,
                        backLabel = "Operação",
                    )
                }

                item("date") {
                    PcHeroCard(
                        title = formatHistoryDate(selectedDate),
                        supportingText = "Os registros abaixo pertencem somente a esta data. Toque em qualquer pausa para ver todos os detalhes.",
                        icon = Icons.Default.CalendarMonth,
                        tone = PontoCafeTone.INFO,
                    )
                }

                item("calendar") {
                    PcPrimaryButton(
                        text = "Escolher outra data",
                        icon = Icons.Default.CalendarMonth,
                        onClick = { showCalendar = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.erro != null) {
                    item("error") {
                        OperationalAlertCard(
                            title = "Não foi possível carregar o histórico",
                            text = state.erro ?: "Erro desconhecido",
                            actionLabel = "Tentar novamente",
                            onClick = { viewModel.abrirHistorico(selectedDate.toString()) },
                            tone = PontoCafeTone.DANGER,
                        )
                    }
                }

                item("metrics") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    ) {
                        PcMetricTile(
                            value = total.toString(),
                            label = "Pausas",
                            icon = Icons.Default.Coffee,
                            modifier = Modifier.weight(1f),
                        )
                        PcMetricTile(
                            value = overLimit.toString(),
                            label = "Acima do limite",
                            icon = Icons.Default.Timer,
                            modifier = Modifier.weight(1f),
                            attention = overLimit > 0,
                        )
                    }
                }

                if (outside > 0) {
                    item("outside") {
                        PcStateBanner(
                            title = "$outside registro(s) fora do horário",
                            supportingText = "Abra os registros para conferir período, horários e duração.",
                            tone = PontoCafeTone.WARNING,
                        )
                    }
                }

                item("title") {
                    SectionTitle(
                        "Registros de ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                        if (state.historico.isEmpty()) "Nenhuma pausa encontrada nesta data." else "Todos os cartões são clicáveis.",
                    )
                }

                if (state.carregando && state.historico.isEmpty()) {
                    item("loading") { PontoCafeLoadingSkeleton(rows = 4) }
                } else if (state.historico.isEmpty()) {
                    item("empty") {
                        PcEmptyState(
                            title = "Sem registros neste dia",
                            supportingText = "Escolha outra data no calendário para consultar o histórico.",
                            icon = Icons.Default.CalendarMonth,
                        )
                    }
                } else {
                    items(
                        state.historico.sortedByDescending { it.inicioLocal },
                        key = { "history-v2-${it.id}" },
                    ) { pause ->
                        HistoryPauseCard(
                            pause = pause,
                            viewModel = viewModel,
                            onClick = { selectedPause = pause },
                        )
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
private fun HistoryPauseCard(
    pause: PausaSupervisor,
    viewModel: SupervisorViewModel,
    onClick: () -> Unit,
) {
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos ?: 0
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteSegundos)
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (exceeded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(pause.nome)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(pause.nome, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${pause.inicioLocal} → ${pause.fimLocal ?: "em andamento"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    listOfNotNull(pause.setor, pause.periodo.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    viewModel.formatarTempo(duration),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                StatusPill(
                    text = if (exceeded) "Excedido" else "Dentro do limite",
                    tone = if (exceeded) PontoCafeTone.DANGER else PontoCafeTone.SUCCESS,
                )
            }
        }
    }
}

@Composable
private fun HistoryPauseDetailDialog(
    pause: PausaSupervisor,
    viewModel: SupervisorViewModel,
    onDismiss: () -> Unit,
) {
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos ?: 0
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteSegundos)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pause.nome) },
        text = {
            PcKeyValueCard(
                title = "Informações do registro",
                rows = listOf(
                    "Data" to (pause.data?.let(::formatHistoryDateText) ?: "—"),
                    "Período" to pause.periodo,
                    "Setor" to (pause.setor ?: "—"),
                    "Saída" to pause.inicioLocal,
                    "Retorno" to (pause.fimLocal ?: "Ainda em pausa"),
                    "Duração" to viewModel.formatarTempo(duration),
                    "Limite" to viewModel.formatarTempo(pause.limiteSegundos),
                    "Situação" to if (exceeded) "Acima do limite" else "Dentro do limite",
                    "Fora do horário" to if (pause.foraHorario) "Sim" else "Não",
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

private fun formatHistoryDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale("pt", "BR")))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }

private fun formatHistoryDateText(value: String): String =
    runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
        .getOrDefault(value)
