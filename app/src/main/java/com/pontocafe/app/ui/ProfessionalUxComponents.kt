package com.pontocafe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.data.AdminTestPause
import com.pontocafe.app.data.ReportDay
import com.pontocafe.app.data.ReportSummary
import com.pontocafe.app.data.SavedRestrictedAccount
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PcAreaTopBar(
    title: String,
    eyebrow: String,
    account: SavedRestrictedAccount?,
    fallbackName: String,
    onProfileClick: () -> Unit,
    onBackToPonto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = account?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextButton(onClick = onBackToPonto) {
            Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Ponto", modifier = Modifier.padding(start = 5.dp))
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            IconButton(onClick = onProfileClick, modifier = Modifier.size(44.dp)) {
                InitialAvatar(name = displayName, avatarSize = 34.dp)
            }
        }
    }
}

@Composable
fun PcAdminVisualTestTool(
    testPause: AdminTestPause?,
    adminName: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PcSectionSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            PcStateBanner(
                title = if (testPause == null) "Nenhum teste ativo" else "TESTE ativo no painel operacional",
                supportingText = if (testPause == null) {
                    "A simulação existe somente neste aparelho e desaparece ao reiniciar a app."
                } else {
                    "${testPause.adminName.ifBlank { adminName }} aparece com o mesmo cartão de uma pausa real."
                },
                tone = if (testPause == null) PontoCafeTone.NEUTRAL else PontoCafeTone.INFO,
            )
            Text(
                "Não altera pausas reais, métricas, histórico, fila offline, banco de dados ou auditoria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (testPause == null) {
                PcPrimaryButton(
                    text = "Iniciar teste de ponto",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Science,
                )
            } else {
                PcSecondaryButton(
                    text = "Encerrar teste",
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.StopCircle,
                )
            }
        }
    }
}

@Composable
fun PcReportComparisonCard(
    current: ReportSummary,
    previous: ReportSummary?,
    modifier: Modifier = Modifier,
) {
    if (previous == null) return

    val excessDelta = percentDelta(current.acimaLimite, previous.acimaLimite)
    val pauseDelta = percentDelta(current.totalPausas, previous.totalPausas)
    val better = excessDelta != null && excessDelta < 0
    val worse = excessDelta != null && excessDelta > 0

    PcStateBanner(
        title = when {
            better -> "${abs(excessDelta ?: 0)}% menos excessos"
            worse -> "${abs(excessDelta ?: 0)}% mais excessos"
            excessDelta == 0 -> "Excessos estáveis"
            current.acimaLimite == 0 && previous.acimaLimite == 0 -> "Sem excessos nos dois períodos"
            else -> "Comparação com o período anterior"
        },
        supportingText = buildString {
            append("Anterior: ${previous.acimaLimite} acima do limite")
            if (pauseDelta != null) {
                append(" · volume de pausas ")
                append(if (pauseDelta >= 0) "+" else "")
                append(pauseDelta)
                append("%")
            }
        },
        tone = when {
            better -> PontoCafeTone.SUCCESS
            worse -> PontoCafeTone.WARNING
            else -> PontoCafeTone.NEUTRAL
        },
        modifier = modifier,
    )
}

@Composable
fun PcReportTrendChart(
    days: List<ReportDay>,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val ordered = days.sortedBy { it.data }
    val primary = MaterialTheme.colorScheme.primary
    val warning = LocalPontoCafeSemanticColors.current.warning
    val track = MaterialTheme.colorScheme.outlineVariant
    val maxValue = ordered.maxOf { maxOf(it.pausas, it.acimaLimite, 1) }.toFloat()

    PcSectionSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tendência diária", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pausas totais e registros acima do limite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${ordered.size} dia(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
                if (ordered.size == 1) {
                    val x = size.width / 2f
                    val baseY = size.height - 8.dp.toPx()
                    val pauseY = baseY - (ordered.first().pausas / maxValue) * (size.height - 24.dp.toPx())
                    val excessY = baseY - (ordered.first().acimaLimite / maxValue) * (size.height - 24.dp.toPx())
                    drawLine(track, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())
                    drawCircle(primary, radius = 5.dp.toPx(), center = Offset(x, pauseY))
                    drawCircle(warning, radius = 4.dp.toPx(), center = Offset(x, excessY))
                    return@Canvas
                }

                val usableHeight = size.height - 24.dp.toPx()
                val baseY = size.height - 8.dp.toPx()
                val stepX = size.width / (ordered.lastIndex.toFloat())
                drawLine(track, Offset(0f, baseY), Offset(size.width, baseY), 1.dp.toPx())

                var previousPause: Offset? = null
                var previousExcess: Offset? = null
                ordered.forEachIndexed { index, day ->
                    val x = index * stepX
                    val pausePoint = Offset(x, baseY - (day.pausas / maxValue) * usableHeight)
                    val excessPoint = Offset(x, baseY - (day.acimaLimite / maxValue) * usableHeight)
                    previousPause?.let { drawLine(primary, it, pausePoint, 3.dp.toPx(), cap = StrokeCap.Round) }
                    previousExcess?.let { drawLine(warning, it, excessPoint, 2.dp.toPx(), cap = StrokeCap.Round) }
                    drawCircle(primary, radius = 3.5.dp.toPx(), center = pausePoint)
                    drawCircle(warning, radius = 3.dp.toPx(), center = excessPoint)
                    previousPause = pausePoint
                    previousExcess = excessPoint
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md)) {
                ChartLegend("Pausas", primary)
                ChartLegend("Acima do limite", warning)
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = color) {}
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun percentDelta(current: Int, previous: Int): Int? {
    if (previous <= 0) return null
    return (((current - previous).toDouble() / previous.toDouble()) * 100.0).roundToInt()
}
