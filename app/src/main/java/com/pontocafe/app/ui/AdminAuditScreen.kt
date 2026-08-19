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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AuditEvent
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class AuditCategory(val label: String) {
    ALL("Todos"),
    PEOPLE("Pessoas"),
    BIOMETRIC("Biometria"),
    DEVICES("Dispositivos"),
    SECURITY("Segurança"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuditScreen(viewModel: AdminViewModel) {
    val state = viewModel.state
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AuditCategory.ALL) }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<AuditEvent?>(null) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = pickerState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        }
                        showDatePicker = false
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    selectedEvent?.let { event ->
        AuditEventDetailSheet(event = event, onDismiss = { selectedEvent = null })
    }

    val filtered = remember(state.auditoria, query, category, selectedDate) {
        val cleanQuery = query.trim().lowercase()
        state.auditoria.filter { event ->
            val matchesQuery = cleanQuery.isBlank() || listOfNotNull(
                event.atorNome,
                event.atorTipo,
                event.acao,
                event.entidade,
                event.entidadeId,
                event.detalhes?.get("nome")?.toString(),
                event.detalhes?.get("nomeNovo")?.toString(),
                event.detalhes?.get("colaboradorNome")?.toString(),
            ).any { it.lowercase().contains(cleanQuery) }
            val matchesCategory = category == AuditCategory.ALL || auditCategory(event) == category
            val matchesDate = selectedDate == null || event.criadoLocal.startsWith(selectedDate!!)
            matchesQuery && matchesCategory && matchesDate
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.criadoLocal.substringBefore(' ').ifBlank { "Sem data" } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = PontoCafeSpacing.lg,
                end = PontoCafeSpacing.lg,
                top = PontoCafeSpacing.lg,
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
        ) {
            item(key = "header") {
                PontoCafeScreenHeader(
                    title = "Auditoria e segurança",
                    onBack = viewModel::voltarHome,
                    backLabel = "Painel",
                    eyebrow = "Administrador",
                )
            }

            item(key = "hero") {
                PcHeroCard(
                    title = "Rastreabilidade operacional",
                    supportingText = "Pesquise ações administrativas, filtre por área ou escolha uma data para investigar o que aconteceu.",
                    icon = Icons.Default.Security,
                    tone = PontoCafeTone.INFO,
                )
            }

            item(key = "feedback") { AdminFeedback(viewModel) }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar na auditoria") },
                    placeholder = { Text("Pessoa, ação, dispositivo ou ator") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }

            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        AuditCategory.entries.take(3).forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { category = option },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    ) {
                        AuditCategory.entries.drop(3).forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { category = option },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item(key = "date-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    PcSecondaryButton(
                        text = selectedDate?.let { "Data: $it" } ?: "Escolher data",
                        icon = Icons.Default.CalendarMonth,
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedDate != null) {
                        PcSecondaryButton(
                            text = "Todas",
                            onClick = { selectedDate = null },
                        )
                    }
                }
            }

            item(key = "summary") {
                PcStateBanner(
                    title = "${filtered.size} evento(s) encontrado(s)",
                    supportingText = when {
                        selectedDate != null -> "Exibindo somente $selectedDate. Toque em qualquer evento para abrir todos os detalhes."
                        query.isNotBlank() || category != AuditCategory.ALL -> "Filtros ativos. Toque em qualquer evento para abrir todos os detalhes."
                        else -> "Eventos recentes agrupados por data."
                    },
                    tone = PontoCafeTone.NEUTRAL,
                )
            }

            item(key = "refresh") {
                PcSecondaryButton(
                    text = "Atualizar auditoria",
                    icon = Icons.Default.Refresh,
                    onClick = viewModel::abrirAuditoria,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.carregando,
                )
            }

            if (filtered.isEmpty() && !state.carregando) {
                item(key = "empty") {
                    PcEmptyState(
                        title = "Nenhum evento encontrado",
                        supportingText = "Altere a busca, a categoria ou a data para ampliar os resultados.",
                        icon = Icons.Default.History,
                    )
                }
            }

            grouped.forEach { (date, events) ->
                item(key = "date-$date") {
                    SectionTitle(
                        title = auditDateLabel(date),
                        subtitle = "${events.size} evento(s)",
                    )
                }
                items(events, key = { "audit-${it.id}" }) { event ->
                    AuditEventCard(event = event, onClick = { selectedEvent = event })
                }
            }
        }

        PcScrollToTopFab(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.md),
        )
    }
}

@Composable
private fun AuditEventCard(event: AuditEvent, onClick: () -> Unit) {
    val icon = auditActionIcon(event.acao)
    val tone = auditActionTone(event.acao)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = auditToneContainer(tone),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = auditToneContent(tone),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        auditActionLabel(event.acao),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        event.criadoLocal.substringAfter(' ', event.criadoLocal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "${event.atorNome} · ${event.atorTipo.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                auditTargetName(event)?.let { target ->
                    Text(
                        target,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                StatusPill(text = auditCategory(event).label, tone = tone)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditEventDetailSheet(event: AuditEvent, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = PontoCafeSpacing.lg,
                    end = PontoCafeSpacing.lg,
                    bottom = PontoCafeSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
                Text(
                    auditActionLabel(event.acao),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    event.criadoLocal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PcKeyValueCard(
                title = "Evento",
                rows = listOfNotNull(
                    "Ator" to event.atorNome,
                    "Perfil" to event.atorTipo,
                    "Categoria" to auditCategory(event).label,
                    event.entidade?.let { "Entidade" to it },
                    event.entidadeId?.let { "ID da entidade" to it },
                    "Código da ação" to event.acao,
                ),
            )

            if (!event.detalhes.isNullOrEmpty()) {
                PcKeyValueCard(
                    title = "Detalhes registrados",
                    rows = event.detalhes.entries
                        .sortedBy { it.key }
                        .map { (key, value) -> auditDetailLabel(key) to (value?.toString() ?: "—") },
                )
            }

            PcSecondaryButton(
                text = "Fechar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun auditDateLabel(date: String): String {
    val today = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    val yesterday = java.time.LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    return when (date) {
        today -> "Hoje · $date"
        yesterday -> "Ontem · $date"
        else -> date
    }
}

private fun auditTargetName(event: AuditEvent): String? = event.detalhes?.get("nome")?.toString()
    ?: event.detalhes?.get("nomeNovo")?.toString()
    ?: event.detalhes?.get("colaboradorNome")?.toString()
    ?: event.entidadeId?.takeIf { it.isNotBlank() }?.let { "Referência: ${it.take(12)}" }

private fun auditCategory(event: AuditEvent): AuditCategory = when {
    event.acao.contains("BIOMETR", ignoreCase = true) || event.acao.contains("ROSTO", ignoreCase = true) -> AuditCategory.BIOMETRIC
    event.acao.contains("COLABORADOR", ignoreCase = true) || event.entidade?.contains("colaborador", ignoreCase = true) == true -> AuditCategory.PEOPLE
    event.acao.contains("DISPOSITIVO", ignoreCase = true) || event.acao.contains("TOKEN", ignoreCase = true) || event.acao.contains("PIN", ignoreCase = true) -> AuditCategory.DEVICES
    else -> AuditCategory.SECURITY
}

private fun auditActionTone(action: String): PontoCafeTone = when {
    action.contains("EXCLUIR", ignoreCase = true) -> PontoCafeTone.DANGER
    action.contains("DESATIVAR", ignoreCase = true) || action.contains("TENTATIVA", ignoreCase = true) -> PontoCafeTone.WARNING
    action.contains("BIOMETR", ignoreCase = true) || action.contains("ROSTO", ignoreCase = true) -> PontoCafeTone.INFO
    else -> PontoCafeTone.NEUTRAL
}

@Composable
private fun auditToneContainer(tone: PontoCafeTone) = when (tone) {
    PontoCafeTone.DANGER -> MaterialTheme.colorScheme.errorContainer
    PontoCafeTone.WARNING -> LocalPontoCafeSemanticColors.current.warningContainer
    PontoCafeTone.INFO -> LocalPontoCafeSemanticColors.current.infoContainer
    PontoCafeTone.SUCCESS -> LocalPontoCafeSemanticColors.current.successContainer
    PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun auditToneContent(tone: PontoCafeTone) = when (tone) {
    PontoCafeTone.DANGER -> MaterialTheme.colorScheme.error
    PontoCafeTone.WARNING -> LocalPontoCafeSemanticColors.current.warning
    PontoCafeTone.INFO -> LocalPontoCafeSemanticColors.current.info
    PontoCafeTone.SUCCESS -> LocalPontoCafeSemanticColors.current.success
    PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun auditActionIcon(action: String): ImageVector = when {
    action == "EXCLUIR_ROSTO" -> Icons.Default.DeleteForever
    action.contains("BIOMETR") || action.contains("ROSTO") -> Icons.Default.Fingerprint
    action.contains("COLABORADOR") -> Icons.Default.People
    action.contains("DISPOSITIVO") || action.contains("PIN") || action.contains("TOKEN") -> Icons.Default.Devices
    action.contains("CONTA") || action.contains("SENHA") || action.contains("PERFIL") -> Icons.Default.Security
    else -> Icons.Default.History
}

private fun auditActionLabel(action: String): String = when (action) {
    "CRIAR_CONTA" -> "Conta criada"
    "DESATIVAR_CONTA" -> "Conta desativada"
    "REATIVAR_CONTA" -> "Conta reativada"
    "EXCLUIR_CONTA" -> "Conta excluída"
    "REDEFINIR_SENHA" -> "Senha redefinida"
    "ALTERAR_PERFIL" -> "Perfil de acesso alterado"
    "ALTERAR_REGRA_CAFE" -> "Regra de café alterada"
    "EDITAR_COLABORADOR" -> "Dados do colaborador corrigidos"
    "EXCLUIR_COLABORADOR" -> "Colaborador removido da operação"
    "CADASTRAR_ROSTO" -> "Biometria facial cadastrada"
    "ATUALIZAR_ROSTO" -> "Biometria facial atualizada"
    "EXCLUIR_ROSTO" -> "Biometria facial excluída"
    "ALTERAR_PIN_DISPOSITIVO" -> "PIN de dispositivo alterado"
    "RENOMEAR_DISPOSITIVO" -> "Dispositivo renomeado"
    "DESATIVAR_DISPOSITIVO" -> "Dispositivo desativado"
    "EXCLUIR_DISPOSITIVO" -> "Dispositivo excluído definitivamente"
    "ROTACIONAR_TOKEN_DISPOSITIVO" -> "Token de dispositivo revogado"
    "ATIVAR_DISPOSITIVO" -> "Dispositivo ativado"
    "SINCRONIZAR_PONTO_OFFLINE" -> "Registro offline sincronizado"
    "TENTATIVA_PONTO_REPETIDA" -> "Tentativa repetida de pausa bloqueada"
    "DESBLOQUEAR_MODO_PONTO" -> "Modo Ponto desbloqueado"
    else -> action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun auditDetailLabel(key: String): String = key
    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.uppercase() }
