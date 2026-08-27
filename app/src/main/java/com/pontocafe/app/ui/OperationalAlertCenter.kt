package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.OperationalAlertHistoryItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SupervisorOperationalAlertCenter(
    history: List<OperationalAlertHistoryItem>,
    onMarkAllRead: () -> Unit,
    onClear: () -> Unit,
    onDismissItem: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val unread = history.count { !it.read }
    val recent = history.take(5)

    PcSectionSurface(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Centro de alertas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (unread > 0) "$unread aviso(s) não lido(s) neste aparelho" else "Nenhum aviso pendente de leitura",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (unread > 0) StatusPill("$unread", PontoCafeTone.WARNING)
            }

            if (recent.isEmpty()) {
                PcStateBanner(
                    title = "Tudo certo por aqui",
                    supportingText = "Saídas, retornos e mudanças de limite aparecerão neste centro.",
                    tone = PontoCafeTone.SUCCESS,
                )
            } else {
                recent.forEach { item ->
                    AlertHistoryRow(item, onDismiss = { onDismissItem(item.id) })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (unread > 0) {
                        TextButton(onClick = onMarkAllRead) { Text("Marcar como lidos") }
                    }
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Limpar avisos") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertHistoryRow(item: OperationalAlertHistoryItem, onDismiss: () -> Unit) {
    val isCritical = item.type == "EXCESSO" || item.type == "CRITICO"
    val tone = when {
        isCritical -> PontoCafeTone.DANGER
        item.type == "PROXIMO_LIMITE" -> PontoCafeTone.WARNING
        item.type == "RETORNO" -> PontoCafeTone.SUCCESS
        else -> PontoCafeTone.INFO
    }
    val semantic = LocalPontoCafeSemanticColors.current
    val accent = when (tone) {
        PontoCafeTone.DANGER -> semantic.critical
        PontoCafeTone.WARNING -> semantic.warning
        PontoCafeTone.SUCCESS -> semantic.success
        PontoCafeTone.INFO -> semantic.info
        PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var expanded by remember(item.id) { mutableStateOf(false) }

    // Só eventos não-críticos podem ser arrastados para dispensar -- excesso e
    // estado crítico continuam exigindo "Marcar como lidos"/"Limpar avisos"
    // explícitos, para não sumirem sem querer com um gesto acidental.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (!isCritical && value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )

    val rowContent = @Composable {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics {
                    liveRegion = if (!item.read && isCritical) LiveRegionMode.Assertive else LiveRegionMode.Polite
                },
            shape = MaterialTheme.shapes.medium,
            color = if (item.read) MaterialTheme.colorScheme.surfaceContainer else accent.copy(alpha = 0.09f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (isCritical || tone == PontoCafeTone.WARNING) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatAlertTime(item.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            item.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.padding(start = 27.dp, top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Registrado em ${formatAlertFullDate(item.createdAtMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!isCritical) {
                            Text(
                                "Arraste para dispensar este aviso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (isCritical) {
        rowContent()
    } else {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(semantic.critical.copy(alpha = 0.16f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = semantic.critical)
                }
            },
        ) {
            rowContent()
        }
    }
}

private fun formatAlertTime(millis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.of("America/Fortaleza"))
        .format(Instant.ofEpochMilli(millis))
}.getOrDefault("--:--")

private fun formatAlertFullDate(millis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        .withZone(ZoneId.of("America/Fortaleza"))
        .format(Instant.ofEpochMilli(millis))
}.getOrDefault("--")
