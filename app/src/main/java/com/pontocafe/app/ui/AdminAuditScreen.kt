package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.data.AuditEvent

@Composable
fun AdminAuditScreen(viewModel: AdminViewModel) {
    val state = viewModel.state

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PontoCafeHeader("Auditoria e segurança")
        Text(
            "Histórico das principais ações administrativas, biométricas e de dispositivos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminFeedback(viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::abrirAuditoria, modifier = Modifier.weight(1f)) {
                Text("Atualizar")
            }
            OutlinedButton(onClick = viewModel::voltarHome, modifier = Modifier.weight(1f)) {
                Text("Voltar ao painel")
            }
        }

        if (state.auditoria.isEmpty() && !state.carregando) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Nenhum evento encontrado", fontWeight = FontWeight.SemiBold)
                    Text(
                        "As ações auditáveis aparecerão aqui conforme o sistema for utilizado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.auditoria, key = { it.id }) { event ->
                AuditEventCard(event)
            }
        }
    }
}

@Composable
private fun AuditEventCard(event: AuditEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    auditActionLabel(event.acao),
                    modifier = Modifier.weight(1f),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val targetName = event.detalhes?.get("nome")?.toString()
                ?: event.detalhes?.get("nomeNovo")?.toString()
                ?: event.entidadeId?.take(12)
            if (!targetName.isNullOrBlank()) {
                Text(
                    "Referência: $targetName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun auditActionLabel(action: String): String = when (action) {
    "CRIAR_CONTA" -> "Conta criada"
    "DESATIVAR_CONTA" -> "Conta desativada"
    "REATIVAR_CONTA" -> "Conta reativada"
    "EXCLUIR_CONTA" -> "Conta excluída"
    "REDEFINIR_SENHA" -> "Senha redefinida"
    "ALTERAR_PERFIL" -> "Perfil de acesso alterado"
    "ALTERAR_REGRA_CAFE" -> "Regra de café alterada"
    "ALTERAR_PIN_DISPOSITIVO" -> "PIN de dispositivo alterado"
    "RENOMEAR_DISPOSITIVO" -> "Dispositivo renomeado"
    "DESATIVAR_DISPOSITIVO" -> "Dispositivo desativado"
    "ROTACIONAR_TOKEN_DISPOSITIVO" -> "Token de dispositivo revogado"
    "ATIVAR_DISPOSITIVO" -> "Dispositivo ativado"
    "SINCRONIZAR_PONTO_OFFLINE" -> "Registro offline sincronizado"
    "DESBLOQUEAR_MODO_PONTO" -> "Modo Ponto desbloqueado"
    else -> action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
