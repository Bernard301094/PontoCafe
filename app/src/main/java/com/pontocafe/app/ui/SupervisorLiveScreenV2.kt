package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.data.PausaSupervisor
import kotlinx.coroutines.delay

@Composable
fun SupervisorLiveScreenV2(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val state = viewModel.state
    val lifecycleOwner = LocalLifecycleOwner.current
    val now = System.currentTimeMillis()

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.atualizarPausasAoVivoSilencioso()
            while (true) {
                delay(5_000)
                viewModel.atualizarPausasAoVivoSilencioso()
            }
        }
    }

    val alert = rememberSupervisorLiveActivityAlert(
        pausasAtivas = state.pausasAtivas,
        enabled = state.ultimaAtualizacaoAoVivoEmMillis != null,
    )
    val pendingFaces = state.colaboradores.filter { !it.rostoCadastrado }.sortedBy { it.nome.lowercase() }
    val overdue = state.pausasAtivas.count { supervisorLiveSeconds(it, now) > it.limiteSegundos }
    val secondsSinceUpdate = state.ultimaAtualizacaoAoVivoEmMillis?.let {
        ((now - it) / 1000L).coerceAtLeast(0L)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                PontoCafeScreenHeader(
                    title = "Ao vivo",
                    eyebrow = "Supervisor",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onClose) { Text("Ponto") }
            }
        }

        item(key = "connection") {
            StatusPill(
                text = when {
                    !state.conexaoAoVivoOk -> "Conexão instável"
                    secondsSinceUpdate != null && secondsSinceUpdate < 10 -> "Sincronizado"
                    secondsSinceUpdate != null -> "Atualizado há ${secondsSinceUpdate}s"
                    else -> "Conectando"
                },
                tone = if (state.conexaoAoVivoOk) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
            )
        }

        alert?.let {
            item(key = "live-alert-${it.id}") { SupervisorLiveActivityAlertBanner(it) }
        }

        item(key = "metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                MetricCard(
                    value = state.pausasAtivas.size.toString(),
                    label = "Em pausa agora",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    value = overdue.toString(),
                    label = "Acima do limite",
                    modifier = Modifier.weight(1f),
                    emphasized = overdue > 0,
                )
            }
        }

        item(key = "quick-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                Button(
                    onClick = viewModel::abrirAutorizacao,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Coffee, contentDescription = null)
                    Text(" Autorizar")
                }
                OutlinedButton(
                    onClick = viewModel::atualizarAoVivo,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(" Atualizar")
                }
            }
        }

        if (state.erro != null && !state.conexaoAoVivoOk) {
            item(key = "connection-error") {
                OperationalAlertCard(
                    title = "Exibindo os últimos dados disponíveis",
                    text = state.erro ?: "A conexão será atualizada automaticamente.",
                    actionLabel = "Tentar agora",
                    onClick = viewModel::atualizarAoVivo,
                    tone = PontoCafeTone.WARNING,
                )
            }
        }

        item(key = "active-title") {
            SectionTitle(
                "Pessoas no café",
                if (state.pausasAtivas.isEmpty()) "Nenhuma pausa em andamento." else "Cronômetros atualizados a cada segundo.",
            )
        }

        if (state.pausasAtivas.isEmpty()) {
            item(key = "active-empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        "Nenhuma pessoa está em pausa neste momento.",
                        modifier = Modifier.padding(PontoCafeSpacing.md),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.pausasAtivas, key = { "pause-${it.id}" }) { pause ->
                LivePauseCard(pause)
            }
        }

        if (pendingFaces.isNotEmpty()) {
            item(key = "pending-title") {
                SectionTitle(
                    "Biometria pendente",
                    "${pendingFaces.size} colaborador(es) ainda precisam cadastrar o rosto.",
                )
            }
            item(key = "pending-summary") {
                OperationalAlertCard(
                    title = "${pendingFaces.size} rostos pendentes",
                    text = "Resolva os cadastros pela seção Pessoas quando houver disponibilidade.",
                    actionLabel = "Abrir Pessoas",
                    onClick = viewModel::abrirColaboradores,
                    tone = PontoCafeTone.WARNING,
                )
            }
        }

        if (!state.sessaoAdministrativa) {
            item(key = "logout") {
                OutlinedButton(onClick = viewModel::sair, modifier = Modifier.fillMaxWidth()) {
                    Text("Encerrar sessão de Supervisor")
                }
            }
        }
    }
}

@Composable
private fun LivePauseCard(pause: PausaSupervisor) {
    var now by remember(pause.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(pause.id) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val seconds = supervisorLiveSeconds(pause, now)
    val overdue = seconds > pause.limiteSegundos
    val remaining = (pause.limiteSegundos - seconds).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (overdue) MaterialTheme.colorScheme.error.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(PontoCafeSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            InitialAvatar(pause.nome)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pause.nome, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Início ${pause.inicioLocal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(
                    text = if (overdue) "Limite excedido" else "Dentro do limite",
                    tone = if (overdue) PontoCafeTone.DANGER else PontoCafeTone.SUCCESS,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatTime(seconds),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (overdue) "+${formatTime(seconds - pause.limiteSegundos)}" else "restam ${formatTime(remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun supervisorLiveSeconds(pause: PausaSupervisor, now: Long): Int {
    val base = pause.tempoSegundos ?: pause.duracaoSegundos ?: 0
    if (pause.fimLocal != null || pause.clienteAtualizadoEmMillis <= 0L) return base
    val additional = ((now - pause.clienteAtualizadoEmMillis) / 1000L)
        .coerceAtLeast(0L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
    return (base.toLong() + additional).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
