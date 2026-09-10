package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

enum class PontoCafeTone { NEUTRAL, SUCCESS, WARNING, INFO, DANGER }

@Composable
private fun toneColors(tone: PontoCafeTone): Pair<Color, Color> {
    val semantic = LocalPontoCafeSemanticColors.current
    return when (tone) {
        PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        PontoCafeTone.SUCCESS -> semantic.successContainer to semantic.onSuccessContainer
        PontoCafeTone.WARNING -> semantic.warningContainer to semantic.onWarningContainer
        PontoCafeTone.INFO -> semantic.infoContainer to semantic.onInfoContainer
        PontoCafeTone.DANGER -> semantic.criticalContainer to semantic.onCriticalContainer
    }
}

@Composable
fun PontoCafeHeader(subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(
            text = "PONTO CAFÉ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Ponto Café",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PontoCafeScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Voltar",
    eyebrow: String? = "Ponto Café",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backLabel,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Text(
                        eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val semantic = LocalPontoCafeSemanticColors.current
    val displayValue = animatedMetricValue(value)
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $displayValue"
            if (emphasized) stateDescription = "Requer atenção"
        },
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) semantic.warningContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = if (emphasized) BorderStroke(1.dp, semantic.warning.copy(alpha = 0.28f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                displayValue,
                style = MaterialTheme.typography.headlineMedium,
                color = if (emphasized) semantic.onWarningContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasized) semantic.onWarningContainer.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    StatusPill(
        text = text,
        tone = if (positive) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
        modifier = modifier,
    )
}

@Composable
fun StatusPill(text: String, tone: PontoCafeTone, modifier: Modifier = Modifier) {
    val (container, content) = toneColors(tone)
    val semantic = LocalPontoCafeSemanticColors.current
    // Ponto sólido no tom saturado, e não o ícone do Material: é a marca visual
    // que o design usa para estado em toda pílula ("Em operação", "Disponível",
    // "MDM Conectado"). O ícone de 14dp dobrava a altura da pílula e competia
    // com o próprio rótulo.
    val dot = when (tone) {
        PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.outline
        PontoCafeTone.SUCCESS -> semantic.success
        PontoCafeTone.WARNING -> semantic.warning
        PontoCafeTone.INFO -> semantic.info
        PontoCafeTone.DANGER -> semantic.critical
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            stateDescription = text
        },
        shape = CircleShape,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.xs, vertical = PontoCafeSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(6.dp).background(dot, CircleShape))
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}

@Composable
fun ProfilePill(profile: String, modifier: Modifier = Modifier) {
    val admin = profile.equals("ADMIN", ignoreCase = true)
    StatusPill(
        text = if (admin) "Administrador" else "Supervisor",
        tone = if (admin) PontoCafeTone.INFO else PontoCafeTone.NEUTRAL,
        modifier = modifier,
    )
}

@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 44.dp,
) {
    val initials = name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Surface(
        modifier = modifier
            .size(avatarSize)
            .semantics(mergeDescendants = true) {
                contentDescription = "Avatar de $name"
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                style = if (avatarSize >= 72.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Compatibilidade: o Ponto deixou de guardar fotos junto com o reconhecimento
 * facial, então o avatar passou a ser sempre as iniciais. A assinatura mantém
 * o parâmetro [avatarUrl] para não obrigar cada chamada a mudar; ele é ignorado.
 */
@Composable
fun CollaboratorAvatar(
    name: String,
    @Suppress("UNUSED_PARAMETER") avatarUrl: String? = null,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 44.dp,
) {
    InitialAvatar(name = name, modifier = modifier, avatarSize = avatarSize)
}

@Composable
fun OperationalAlertCard(
    title: String,
    text: String,
    actionLabel: String,
    onClick: () -> Unit,
    tone: PontoCafeTone = PontoCafeTone.WARNING,
) {
    val (container, content) = toneColors(tone)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = if (tone == PontoCafeTone.DANGER) LiveRegionMode.Assertive else LiveRegionMode.Polite
                stateDescription = title
            },
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (tone == PontoCafeTone.DANGER) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.86f))
            TextButton(onClick = onClick) { Text(actionLabel, color = content) }
        }
    }
}

@Composable
fun AccountSummaryRow(
    name: String,
    email: String,
    profile: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        InitialAvatar(name)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfilePill(profile)
                StatusPill(
                    if (active) "Ativo" else "Desativado",
                    tone = if (active) PontoCafeTone.SUCCESS else PontoCafeTone.NEUTRAL,
                )
            }
        }
    }
}

@Composable
fun RulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Horários de café", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Manhã · 08:00–10:00 · 15 minutos", style = MaterialTheme.typography.bodyMedium)
            Text("Tarde · 15:00–17:00 · 15 minutos", style = MaterialTheme.typography.bodyMedium)
            Text(
                "O tempo de um período não acumula para o outro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MessageCard(viewModel: PontoCafeViewModel) {
    val state = viewModel.state
    val message = state.erro ?: state.mensagem
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(PontoCafeMotion.Standard)) + scaleIn(
            animationSpec = tween(PontoCafeMotion.Standard),
            initialScale = 0.98f,
        ),
        exit = fadeOut(tween(PontoCafeMotion.Quick)) + scaleOut(
            animationSpec = tween(PontoCafeMotion.Quick),
            targetScale = 0.98f,
        ),
    ) {
        if (message == null) return@AnimatedVisibility
        val isError = state.erro != null
        val (container, content) = toneColors(if (isError) PontoCafeTone.DANGER else PontoCafeTone.INFO)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
                    stateDescription = if (isError) "Erro" else "Informação"
                },
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isError) "Atenção" else "Informação",
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
                TextButton(onClick = viewModel::limparMensagem) { Text("Fechar", color = content) }
            }
        }
    }
}
