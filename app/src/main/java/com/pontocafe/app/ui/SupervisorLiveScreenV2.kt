package com.pontocafe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
    val pendingFaces = remember(state.colaboradores) {
        state.colaboradores.filter { !it.rostoCadastrado }.sortedBy { it.nome.lowercase() }
    }
    val orderedPausas = remember(state.pausasAtivas) {
        val snapshotNow = System.currentTimeMillis()
        state.pausasAtivas.sortedWith(
            compareByDescending<PausaSupervisor> { supervisorLiveSeconds(it, snapshotNow) > it.limiteSegundos }
                .thenByDescending { supervisorLiveSeconds(it, snapshotNow) },
        )
    }
    val overdue = remember(orderedPausas) {
        val snapshotNow = System.currentTimeMillis()
        orderedPausas.count { supervisorLiveSeconds(it, snapshotNow) > it.limiteSegundos }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PontoCafeSpacing.lg),
        contentPadding = PaddingValues(top = PontoCafeSpacing.lg, bottom = PontoCafeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.lg),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                PontoCafeScreenHeader(
                    title = "Ao vivo",
                    eyebrow = "Supervisor",
                )
                PcSecondaryButton(
                    text = "Voltar ao Ponto",
                    onClick = onClose,
                )
            }
        }

        item(key = "connection") {
            LiveConnectionStatusMaterial(
                connectionOk = state.conexaoAoVivoOk,
                lastUpdateMillis = state.ultimaAtualizacaoAoVivoEmMillis,
            )
        }

        alert?.let {
            item(key = "live-alert-${it.id}") {
                SupervisorLiveActivityAlertBanner(it)
            }
        }

        item(key = "summary") {
            PcHeroCard(
                title = when {
                    overdue > 0 -> "$overdue pausa(s) acima do limite"
                    state.pausasAtivas.isEmpty() -> "Nenhuma pausa em andamento"
                    else -> "${state.pausasAtivas.size} pessoa(s) em pausa"
                },
                supportingText = when {
                    overdue > 0 -> "Os casos que exigem atenção aparecem primeiro na lista."
                    state.pausasAtivas.isEmpty() -> "A operação está livre neste momento."
                    else -> "Os cronômetros são atualizados localmente a cada segundo."
                },
                icon = if (overdue > 0) Icons.Default.Timer else Icons.Default.Coffee,
                tone = if (overdue > 0) PontoCafeTone.WARNING else PontoCafeTone.SUCCESS,
            )
        }

        item(key = "metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                PcMetricTile(
                    value = state.pausasAtivas.size.toString(),
                    label = "Em pausa agora",
                    icon = Icons.Default.Coffee,
                    modifier = Modifier.weight(1f),
                )
                PcMetricTile(
                    value = overdue.toString(),
                    label = "Acima do limite",
                    icon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f),
                    attention = overdue > 0,
                )
            }
        }

        item(key = "quick-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                PcPrimaryButton(
                    text = "Autorizar",
                    icon = Icons.Default.Coffee,
                    onClick = viewModel::abrirAutorizacao,
                    modifier = Modifier.weight(1f),
                )
                PcSecondaryButton(
                    text = "Atualizar",
                    icon = Icons.Default.Refresh,
                    onClick = viewModel::atualizarAoVivo,
                    modifier = Modifier.weight(1f),
                )
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
                if (orderedPausas.isEmpty()) {
                    "Nenhuma pausa em andamento."
                } else {
                    "Acompanhe tempo restante, progresso e situações acima do limite."
                },
            )
        }

        if (orderedPausas.isEmpty()) {
            item(key = "active-empty") {
                PcEmptyState(
                    title = "Tudo livre por aqui",
                    supportingText = "Quando alguém iniciar uma pausa, o cronômetro aparecerá automaticamente.",
                    icon = Icons.Default.Groups,
                )
            }
        } else {
            items(orderedPausas, key = { "pause-${it.id}" }) { pause ->
                LivePauseCardMaterial(pause = pause)
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
                PcActionTile(
                    title = "${pendingFaces.size} rosto(s) pendente(s)",
                    supportingText = "Abra Pessoas para concluir os cadastros biométricos.",
                    icon = Icons.Default.Face,
                    onClick = viewModel::abrirColaboradores,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!state.sessaoAdministrativa) {
            item(key = "logout") {
                PcSecondaryButton(
                    text = "Encerrar sessão de Supervisor",
                    onClick = viewModel::sair,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LiveConnectionStatusMaterial(
    connectionOk: Boolean,
    lastUpdateMillis: Long?,
) {
    var now by remember(lastUpdateMillis) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastUpdateMillis, connectionOk) {
        if (lastUpdateMillis == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val secondsSinceUpdate = lastUpdateMillis?.let {
        ((now - it) / 1000L).coerceAtLeast(0L)
    }
    PcStateBanner(
        title = when {
            !connectionOk -> "Conexão instável"
            secondsSinceUpdate != null && secondsSinceUpdate < 10 -> "Sincronizado"
            secondsSinceUpdate != null -> "Atualizado há ${secondsSinceUpdate}s"
            else -> "Conectando"
        },
        supportingText = if (connectionOk) {
            "Dados operacionais atualizados automaticamente."
        } else {
            "Os últimos dados válidos continuam visíveis enquanto reconectamos."
        },
        tone = if (connectionOk) PontoCafeTone.SUCCESS else PontoCafeTone.WARNING,
    )
}

@Composable
private fun LivePauseCardMaterial(
    pause: PausaSupervisor,
) {
    var now by remember(pause.id, pause.clienteAtualizadoEmMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(
        pause.id,
        pause.clienteAtualizadoEmMillis,
        pause.tempoSegundos,
        pause.duracaoSegundos,
        pause.fimLocal,
    ) {
        now = System.currentTimeMillis()
        if (pause.fimLocal != null) return@LaunchedEffect
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val seconds = supervisorLiveSeconds(pause, now)
    val overdue = seconds > pause.limiteSegundos
    val remaining = (pause.limiteSegundos - seconds).coerceAtLeast(0)
    val elapsedOverLimit = (seconds - pause.limiteSegundos).coerceAtLeast(0)
    val progress = if (pause.limiteSegundos <= 0) {
        0f
    } else {
        (seconds.toFloat() / pause.limiteSegundos.toFloat()).coerceIn(0f, 1f)
    }
    val nearLimit = !overdue && progress >= 0.80f
    val semantic = LocalPontoCafeSemanticColors.current
    val accent = when {
        overdue -> MaterialTheme.colorScheme.error
        nearLimit -> semantic.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val container = when {
        overdue -> MaterialTheme.colorScheme.errorContainer
        nearLimit -> semantic.warningContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                InitialAvatar(pause.nome)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        pause.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    pause.setor?.takeIf { it.isNotBlank() }?.let { setor ->
                        Text(
                            setor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatTime(seconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (overdue) "+${formatTime(elapsedOverLimit)}" else "restam ${formatTime(remaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = when {
                        overdue -> "Limite excedido"
                        nearLimit -> "Próximo do limite"
                        else -> "Dentro do limite"
                    },
                    tone = when {
                        overdue -> PontoCafeTone.DANGER
                        nearLimit -> PontoCafeTone.WARNING
                        else -> PontoCafeTone.SUCCESS
                    },
                )
                Text(
                    "Limite ${formatTime(pause.limiteSegundos)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Início", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pause.inicioLocal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (overdue) "Situação" else "Tempo restante",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (overdue) "Ação necessária" else formatTime(remaining),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
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
