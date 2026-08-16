package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    var liveNow by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            liveNow = System.currentTimeMillis()
            delay(1_000)
        }
    }

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
    val orderedPausas = state.pausasAtivas.sortedWith(
        compareByDescending<PausaSupervisor> { supervisorLiveSeconds(it, liveNow) > it.limiteSegundos }
            .thenByDescending { supervisorLiveSeconds(it, liveNow) },
    )
    val overdue = orderedPausas.count { supervisorLiveSeconds(it, liveNow) > it.limiteSegundos }
    val secondsSinceUpdate = state.ultimaAtualizacaoAoVivoEmMillis?.let {
        ((liveNow - it) / 1000L).coerceAtLeast(0L)
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
            MotionReveal {
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
            item(key = "live-alert-${it.id}") {
                MotionReveal { SupervisorLiveActivityAlertBanner(it) }
            }
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
                MotionReveal {
                    OperationalAlertCard(
                        title = "Exibindo os últimos dados disponíveis",
                        text = state.erro ?: "A conexão será atualizada automaticamente.",
                        actionLabel = "Tentar agora",
                        onClick = viewModel::atualizarAoVivo,
                        tone = PontoCafeTone.WARNING,
                    )
                }
            }
        }

        item(key = "active-title") {
            SectionTitle(
                "Pessoas no café",
                when {
                    state.pausasAtivas.isEmpty() -> "Nenhuma pausa em andamento."
                    overdue > 0 -> "$overdue pessoa(s) exigem atenção. Casos acima do limite aparecem primeiro."
                    else -> "Cronômetros atualizados a cada segundo."
                },
            )
        }

        if (orderedPausas.isEmpty()) {
            item(key = "active-empty") {
                MotionReveal {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PontoCafePremium.glassStrong),
                        border = BorderStroke(1.dp, PontoCafePremium.borderSoft),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(
                            "Nenhuma pessoa está em pausa neste momento.",
                            modifier = Modifier.padding(PontoCafeSpacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(orderedPausas, key = { "pause-${it.id}" }) { pause ->
                LivePauseCard(pause = pause, now = liveNow)
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
                MotionReveal {
                    OperationalAlertCard(
                        title = "${pendingFaces.size} rostos pendentes",
                        text = "Resolva os cadastros pela seção Pessoas quando houver disponibilidade.",
                        actionLabel = "Abrir Pessoas",
                        onClick = viewModel::abrirColaboradores,
                        tone = PontoCafeTone.WARNING,
                    )
                }
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
private fun LivePauseCard(
    pause: PausaSupervisor,
    now: Long,
) {
    val seconds = supervisorLiveSeconds(pause, now)
    val overdue = seconds > pause.limiteSegundos
    val remaining = (pause.limiteSegundos - seconds).coerceAtLeast(0)
    val elapsedOverLimit = (seconds - pause.limiteSegundos).coerceAtLeast(0)
    val rawProgress = if (pause.limiteSegundos <= 0) {
        0f
    } else {
        (seconds.toFloat() / pause.limiteSegundos.toFloat()).coerceIn(0f, 1f)
    }
    val progress = animatedProgress(rawProgress)
    val nearLimit = !overdue && rawProgress >= 0.80f
    val semantic = LocalPontoCafeSemanticColors.current

    val targetContainerColor = when {
        overdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
        nearLimit -> semantic.warningContainer.copy(alpha = 0.55f)
        else -> PontoCafePremium.glassStrong
    }
    val targetBorderColor = when {
        overdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        nearLimit -> semantic.warning.copy(alpha = 0.35f)
        else -> PontoCafePremium.borderSoft
    }
    val targetProgressColor = when {
        overdue -> MaterialTheme.colorScheme.error
        nearLimit -> semantic.warning
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(PontoCafeMotion.Emphasized),
        label = "pause-container",
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(PontoCafeMotion.Emphasized),
        label = "pause-border",
    )
    val progressColor by animateColorAsState(
        targetValue = targetProgressColor,
        animationSpec = tween(PontoCafeMotion.Standard),
        label = "pause-progress-color",
    )
    val elevation by animateDpAsState(
        targetValue = if (overdue) 8.dp else if (nearLimit) 6.dp else 4.dp,
        animationSpec = tween(PontoCafeMotion.Standard),
        label = "pause-elevation",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .motionScale(overdue, activeScale = 1.012f)
            .animateContentSize(
                animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PontoCafeSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
            ) {
                InitialAvatar(pause.nome)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = pause.nome,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    pause.setor?.takeIf { it.isNotBlank() }?.let { setor ->
                        Text(
                            text = setor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = formatTime(seconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (overdue) "+${formatTime(elapsedOverLimit)}" else "restam ${formatTime(remaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            overdue -> MaterialTheme.colorScheme.error
                            nearLimit -> semantic.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (overdue || nearLimit) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tempo da pausa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Limite ${formatTime(pause.limiteSegundos)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Início",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = pause.inicioLocal,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (overdue) "Situação" else "Tempo restante",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (overdue) "Ação necessária" else formatTime(remaining),
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                overdue -> MaterialTheme.colorScheme.error
                                nearLimit -> semantic.warning
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = overdue || nearLimit,
                enter = fadeIn(tween(PontoCafeMotion.Standard)) + expandVertically(tween(PontoCafeMotion.Standard)),
                exit = fadeOut(tween(PontoCafeMotion.Quick)) + shrinkVertically(tween(PontoCafeMotion.Standard)),
            ) {
                Text(
                    text = if (overdue) {
                        "Esta pausa ultrapassou o limite em ${formatTime(elapsedOverLimit)}."
                    } else {
                        "Atenção: esta pausa já consumiu ${(rawProgress * 100).toInt()}% do tempo permitido."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else semantic.warning,
                    fontWeight = FontWeight.SemiBold,
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
