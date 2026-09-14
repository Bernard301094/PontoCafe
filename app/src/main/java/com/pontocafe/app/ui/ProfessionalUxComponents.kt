package com.pontocafe.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
            }
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Surface(
            onClick = onBackToPonto,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    "Ponto",
                    modifier = Modifier.padding(start = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        ) {
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget),
            ) {
                InitialAvatar(name = displayName, avatarSize = 34.dp)
            }
        }
    }
}

/**
 * Página com zona colorida fixa no topo (saudação, navegação, números-chave)
 * e uma folha arredondada abaixo, rolável, com o resto do conteúdo -- o
 * padrão de apps bancários (Nubank/Revolut) em vez de uma barra fina seguida
 * de uma coluna plana de cartões. Reservada para as telas de maior tráfego
 * (Início Admin/Supervisor); o restante do app continua em
 * PontoCafeResponsivePage.
 */
@Composable
fun PcHeroPage(
    modifier: Modifier = Modifier,
    heroContent: @Composable ColumnScope.() -> Unit,
    sheetContent: @Composable () -> Unit,
) {
    // A zona de cabeçalho deixou de ser uma faixa saturada e passou a ser o
    // próprio canvas do design: título em on-surface, sobrancelha em primary e
    // conteúdo elevado logo abaixo.
    //
    // Como o app é sempre claro (ver PontoCafeTheme), os ícones da barra de
    // status ficam escuros de forma fixa — antes eram forçados a claros para
    // sobreviver ao fundo âmbar que existia aqui.
    val darkTheme = false
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, darkTheme) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previous = controller?.isAppearanceLightStatusBars
            controller?.isAppearanceLightStatusBars = !darkTheme
            onDispose {
                if (previous != null) controller?.isAppearanceLightStatusBars = previous
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = PontoCafeSpacing.lg, vertical = PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            content = heroContent,
        )
        // A folha não é mais um recorte arredondado sobre a faixa: cabeçalho e
        // corpo dividem o mesmo canvas chapado, e a hierarquia vem dos cartões.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MotionReveal { sheetContent() }
        }
    }
}

/**
 * Variante de PcAreaTopBar para uso dentro da zona colorida de PcHeroPage --
 * mesmo layout, mas com tons `onPrimary`/translúcidos em vez de cores que
 * assumem um fundo neutro.
 */
@Composable
fun PcHeroZoneTopBar(
    title: String,
    eyebrow: String,
    account: SavedRestrictedAccount?,
    fallbackName: String,
    onProfileClick: () -> Unit,
    onBackToPonto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = account?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val topoBarraStatus = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // A barra branca do painel vai de ponta a ponta do ecrã, mas este
            // cabeçalho é chamado dentro da coluna com margem do PcHeroPage,
            // ao lado do conteúdo da tela. Em vez de mexer nas seis telas que o
            // usam, a barra sangra para fora da margem e para cima até ao topo,
            // e devolve ao layout só a altura que ocupava antes.
            .sangrarAteABorda(lateral = PontoCafeSpacing.lg, topo = PontoCafeSpacing.md + topoBarraStatus)
            .background(Color.White)
            .drawBehind {
                drawLine(
                    color = Painel.stone200,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(
                start = PontoCafeSpacing.lg,
                end = PontoCafeSpacing.lg,
                top = PontoCafeSpacing.md + topoBarraStatus,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        // O logótipo do painel: quadrado coffee-800 com a xícara em âmbar.
        Surface(
            modifier = Modifier.size(40.dp),
            shape = Painel.cantoXl,
            color = Painel.coffee800,
            contentColor = Painel.amber400,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Text(
                    text = "Ponto Café",
                    style = MaterialTheme.typography.titleLarge,
                    color = Painel.coffee950,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                // O perfil na pílula âmbar do painel -- a mesma do selo da versão.
                Text(
                    text = eyebrow,
                    modifier = Modifier
                        .background(Painel.amber100, CircleShape)
                        .border(1.dp, Painel.amber200, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Painel.amber800,
                    maxLines = 1,
                )
            }
            // O título da tela desce para a linha de apoio: no painel ela diz
            // "onde estou", em stone-500, e não é o texto de maior peso.
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.bodySmall,
                color = Painel.stone500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Volta ao totem: é a saída da área restrita e some do app se sair
        // daqui. Fica compacta, só com o ícone, na caixa stone-50 do painel.
        Surface(
            onClick = onBackToPonto,
            modifier = Modifier.size(36.dp),
            shape = Painel.cantoXl,
            color = Painel.stone50,
            contentColor = Painel.stone600,
            border = PainelBordaSuave,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Coffee,
                    contentDescription = "Voltar ao Ponto",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // Avatar com o ponto de presença verde do design.
        Box(contentAlignment = Alignment.BottomEnd) {
            IconButton(onClick = onProfileClick, modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget)) {
                InitialAvatar(name = displayName, avatarSize = 34.dp)
            }
            Box(
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(1.5.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            )
        }
    }
}

/**
 * Variante de PontoCafeScreenHeader para a zona colorida de PcHeroPage --
 * telas secundárias (com botão "voltar" em vez do seletor Ponto/conta) usam
 * esta em vez de PcHeroZoneTopBar.
 */
@Composable
fun PcHeroZoneScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Voltar",
    eyebrow: String? = null,
    modifier: Modifier = Modifier,
) {
    val onColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = onColor,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backLabel,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = onColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Um número da tira de estatísticas do cabeçalho.
 *
 * Era texto solto sobre a faixa âmbar; com o cabeçalho chapado ele virou o
 * "bento" branco do design -- número centrado e legenda curta embaixo, que é
 * como as tiras de Ativo/Com alerta/Sem PIN aparecem nas telas.
 */
@Composable
fun PcHeroStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PontoCafeSpacing.xs, vertical = PontoCafeSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                animatedMetricValue(value),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    val trendDescription = buildString {
        append("Tendência diária de ${ordered.size} dias. ")
        append("${ordered.sumOf { it.pausas }} pausas e ")
        append("${ordered.sumOf { it.acimaLimite }} registros acima do limite no total.")
    }

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

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .semantics { contentDescription = trendDescription },
            ) {
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

/**
 * Deixa um elemento ocupar a margem lateral e o espaço acima dele, sem empurrar
 * o resto do layout.
 *
 * Mede com a largura acrescida das duas margens, desenha deslocado para a
 * esquerda e para cima, e reporta ao pai só a altura que sobra -- para quem vem
 * a seguir na coluna, nada mudou de lugar.
 */
internal fun Modifier.sangrarAteABorda(lateral: Dp, topo: Dp): Modifier = layout { measurable, constraints ->
    val lateralPx = lateral.roundToPx()
    val topoPx = topo.roundToPx()
    val largura = constraints.maxWidth + lateralPx * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = largura, maxWidth = largura),
    )
    val alturaReportada = (placeable.height - topoPx).coerceAtLeast(0)
    layout(constraints.maxWidth, alturaReportada) {
        placeable.place(-lateralPx, -topoPx)
    }
}
