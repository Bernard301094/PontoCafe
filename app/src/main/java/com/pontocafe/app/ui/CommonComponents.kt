package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel

enum class PontoCafeTone { NEUTRAL, SUCCESS, WARNING, INFO, DANGER }

@Composable
private fun toneColors(tone: PontoCafeTone): Pair<Color, Color> {
    val semantic = LocalPontoCafeSemanticColors.current
    return when (tone) {
        PontoCafeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        PontoCafeTone.SUCCESS -> semantic.successContainer to semantic.onSuccessContainer
        PontoCafeTone.WARNING -> semantic.warningContainer to semantic.onWarningContainer
        PontoCafeTone.INFO -> semantic.infoContainer to semantic.onInfoContainer
        PontoCafeTone.DANGER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
}

@Composable
fun PontoCafeHeader(subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(
            text = "Ponto Café",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
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
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) semantic.warningContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            1.dp,
            if (emphasized) semantic.warning.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xxs),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (emphasized) semantic.onWarningContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) semantic.onWarningContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(modifier = modifier, shape = CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (tone) {
                PontoCafeTone.SUCCESS -> Icons.Default.CheckCircle
                PontoCafeTone.WARNING, PontoCafeTone.DANGER -> Icons.Default.Warning
                PontoCafeTone.INFO -> Icons.Default.Info
                PontoCafeTone.NEUTRAL -> null
            }
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
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
fun InitialAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Surface(
        modifier = modifier.size(46.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, content.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = content)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.84f))
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
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Horários de café", style = MaterialTheme.typography.titleMedium)
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
        enter = fadeIn() + scaleIn(initialScale = 0.98f),
        exit = fadeOut() + scaleOut(targetScale = 0.98f),
    ) {
        if (message == null) return@AnimatedVisibility
        val isError = state.erro != null
        val (container, content) = toneColors(if (isError) PontoCafeTone.DANGER else PontoCafeTone.INFO)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = container),
            border = BorderStroke(1.dp, content.copy(alpha = 0.1f)),
        ) {
            Column(Modifier.padding(PontoCafeSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isError) "Atenção" else "Informação", style = MaterialTheme.typography.titleMedium, color = content)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
                TextButton(onClick = viewModel::limparMensagem) { Text("Fechar", color = content) }
            }
        }
    }
}
