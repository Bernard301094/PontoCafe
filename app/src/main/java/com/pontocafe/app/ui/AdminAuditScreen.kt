package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AuditEvent

@Composable
fun AdminAuditScreen(viewModel: AdminViewModel) {
    val state = viewModel.state

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(vertical = PontoCafeSpacing.lg),
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
                supportingText = "Ações administrativas, alterações biométricas, dispositivos e eventos críticos ficam registradas aqui.",
                icon = Icons.Default.Security,
                tone = PontoCafeTone.INFO,
            )
        }

        item(key = "feedback") { AdminFeedback(viewModel) }

        item(key = "refresh") {
            PcSecondaryButton(
                text = "Atualizar auditoria",
                icon = Icons.Default.Refresh,
                onClick = viewModel::abrirAuditoria,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.carregando,
            )
        }

        if (state.auditoria.isEmpty() && !state.carregando) {
            item(key = "empty") {
                PcEmptyState(
                    title = "Nenhum evento encontrado",
                    supportingText = "As ações auditáveis aparecerão aqui conforme o sistema for utilizado.",
                    icon = Icons.Default.History,
                )
            }
        }

        if (state.auditoria.isNotEmpty()) {
            item(key = "events-title") {
                SectionTitle(
                    title = "Eventos recentes",
                    subtitle = "${state.auditoria.size} evento(s) carregado(s)",
                )
            }
        }

        items(state.auditoria, key = { "audit-${it.id}" }) { event ->
            AuditEventCard(event)
        }
    }
}

@Composable
private fun AuditEventCard(event: AuditEvent) {
    val biometricDeletion = event.acao == "EXCLUIR_ROSTO"
    val icon = auditActionIcon(event.acao)
    val tone = when {
        biometricDeletion -> PontoCafeTone.DANGER
        event.acao.contains("EXCLUIR") || event.acao.contains("DESATIVAR") -> PontoCafeTone.WARNING
        event.acao.contains("BIOMETR") || event.acao.contains("ROSTO") -> PontoCafeTone.INFO
        else -> PontoCafeTone.NEUTRAL
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                color = when (tone) {
                    PontoCafeTone.DANGER -> MaterialTheme.colorScheme.errorContainer
                    PontoCafeTone.WARNING -> LocalPontoCafeSemanticColors.current.warningContainer
                    PontoCafeTone.INFO -> LocalPontoCafeSemanticColors.current.infoContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when (tone) {
                            PontoCafeTone.DANGER -> MaterialTheme.colorScheme.error
                            PontoCafeTone.WARNING -> LocalPontoCafeSemanticColors.current.warning
                            PontoCafeTone.INFO -> LocalPontoCafeSemanticColors.current.info
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                    )
                    Text(
                        event.criadoLocal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    "${event.atorNome} · ${event.atorTipo.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val targetName = event.detalhes?.get("nome")?.toString()
                    ?: event.detalhes?.get("nomeNovo")?.toString()
                    ?: event.detalhes?.get("colaboradorNome")?.toString()
                    ?: event.entidadeId?.take(12)
                if (!targetName.isNullOrBlank()) {
                    Text(
                        "Referência: $targetName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (biometricDeletion) {
                    StatusPill(
                        text = "Biometria removida definitivamente",
                        tone = PontoCafeTone.DANGER,
                    )
                }
            }
        }
    }
}

private fun auditActionIcon(action: String): ImageVector = when {
    action == "EXCLUIR_ROSTO" -> Icons.Default.DeleteForever
    action.contains("BIOMETR") || action.contains("ROSTO") -> Icons.Default.Fingerprint
    action.contains("PIN") || action.contains("TOKEN") || action.contains("CONTA") -> Icons.Default.Security
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
