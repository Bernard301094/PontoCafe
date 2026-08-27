package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var search by rememberSaveable { mutableStateOf("") }

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
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
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

                item("date-presets") {
                    val today = LocalDate.now()
                    val yesterday = today.minusDays(1)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs)) {
                        item("preset-today") {
                            FilterChip(
                                selected = selectedDate == today,
                                onClick = { viewModel.abrirHistorico(today.toString()) },
                                label = { Text("Hoje") },
                            )
                        }
                        item("preset-yesterday") {
                            FilterChip(
                                selected = selectedDate == yesterday,
                                onClick = { viewModel.abrirHistorico(yesterday.toString()) },
                                label = { Text("Ontem") },
                            )
                        }
                        item("preset-custom") {
                            FilterChip(
                                selected = selectedDate != today && selectedDate != yesterday,
                                onClick = { showCalendar = true },
                                label = { Text("Escolher data…") },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }

                item("search") {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por nome") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
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
                    if (responsive.isNarrow || responsive.usesLargeText) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                        ) {
                            PcMetricTile(
                                value = total.toString(),
                                label = "Pausas",
                                icon = Icons.Default.Coffee,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PcMetricTile(
                                value = overLimit.toString(),
                                label = "Acima do limite",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.fillMaxWidth(),
                                attention = overLimit > 0,
                            )
                        }
                    } else Row(
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
                        if (state.historico.isEmpty()) "Nenhuma pausa encontrada nesta data." else "Linha do tempo · toque em um registro para ver todos os detalhes.",
                    )
                }

                val query = search.trim()
                val visiblePauses = state.historico
                    .filter { query.isBlank() || it.nome.contains(query, ignoreCase = true) }
                    .sortedByDescending { it.inicioLocal }

                if (state.carregando && state.historico.isEmpty()) {
                    item("loading") { PontoCafeLoadingSkeleton(rows = 4) }
                } else if (visiblePauses.isEmpty()) {
                    item("empty") {
                        PcEmptyState(
                            title = if (state.historico.isEmpty()) "Sem registros neste dia" else "Ninguém encontrado para \"$query\"",
                            supportingText = "Escolha outra data ou ajuste a busca para consultar o histórico.",
                            icon = Icons.Default.CalendarMonth,
                        )
                    }
                } else {
                    itemsIndexed(
                        visiblePauses,
                        key = { _, pause -> "history-v2-${pause.id}" },
                    ) { index, pause ->
                        HistoryTimelineRow(
                            isFirst = index == 0,
                            isLast = index == visiblePauses.lastIndex,
                            modifier = Modifier.animateItem(),
                        ) {
                            HistoryPauseCard(
                                pause = pause,
                                onClick = { selectedPause = pause },
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

/**
 * Nó de linha do tempo vertical -- linha contínua à esquerda com um marcador
 * circular por registro, conteúdo do cartão à direita. Não é a variante em
 * zigue-zague (nós alternando lado a lado) do pedido original: numa coluna
 * de largura de celular, alternar o lado a cada item prejudica a leitura em
 * vez de ajudar; o efeito de "linha do tempo" continua real com um único
 * trilho contínuo.
 */
@Composable
internal fun HistoryTimelineRow(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else lineColor),
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else lineColor),
            )
        }
        Box(modifier = Modifier.padding(start = 8.dp, bottom = PontoCafeSpacing.xs).weight(1f)) {
            content()
        }
    }
}

@Composable
internal fun HistoryPauseCard(
    pause: PausaSupervisor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos ?: 0
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteSegundos)
    val statusText = when {
        exceeded -> "Acima do limite"
        pause.foraHorario -> "Fora do horário"
        else -> "No limite"
    }
    val statusColor = when {
        exceeded -> LocalPontoCafeSemanticColors.current.critical
        pause.foraHorario -> LocalPontoCafeSemanticColors.current.warning
        else -> LocalPontoCafeSemanticColors.current.success
    }
    val meta = listOfNotNull(
        "${pause.inicioLocal} → ${pause.fimLocal ?: "em andamento"}",
        pause.setor?.takeIf { it.isNotBlank() },
        pause.periodo.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPcPressScale(interactionSource)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pcPressScale(pressScale)
            .semantics {
                stateDescription = "$statusText. $meta. Duração ${formatHistoryDuration(duration)}"
            },
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (exceeded) {
                LocalPontoCafeSemanticColors.current.criticalContainer.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            1.dp,
            if (exceeded) {
                LocalPontoCafeSemanticColors.current.critical.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CollaboratorAvatar(
                name = pause.nome,
                avatarUrl = pause.avatarUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = pause.nome,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = formatHistoryDuration(duration),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (exceeded) LocalPontoCafeSemanticColors.current.critical else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun HistoryPauseDetailDialog(
    pause: PausaSupervisor,
    onDismiss: () -> Unit,
) {
    val duration = pause.duracaoSegundos ?: pause.tempoSegundos ?: 0
    val exceeded = pause.excedeuLimite ?: (duration > pause.limiteSegundos)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pause.nome) },
        text = {
            PcDialogBody {
                PcKeyValueCard(
                    title = "Informações do registro",
                    rows = listOf(
                        "Data" to (pause.data?.let(::formatHistoryDateText) ?: "—"),
                        "Período" to pause.periodo,
                        "Setor" to (pause.setor ?: "—"),
                        "Saída" to pause.inicioLocal,
                        "Retorno" to (pause.fimLocal ?: "Ainda em pausa"),
                        "Duração" to formatHistoryDuration(duration),
                        "Limite" to formatHistoryDuration(pause.limiteSegundos),
                        "Situação" to if (exceeded) "Acima do limite" else "Dentro do limite",
                        "Fora do horário" to if (pause.foraHorario) "Sim" else "Não",
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

internal fun formatHistoryDuration(totalSeconds: Int): String {
    val total = totalSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

private fun formatHistoryDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("pt-BR")))
        .replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("pt-BR")) else it.toString()
        }

private fun formatHistoryDateText(value: String): String =
    runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
        .getOrDefault(value)
