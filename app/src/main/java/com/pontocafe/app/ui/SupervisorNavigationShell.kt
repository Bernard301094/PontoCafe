package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorDestination
import com.pontocafe.app.SupervisorViewModel

private enum class SupervisorPrimaryDestination(val label: String) {
    LIVE("Operação"),
    PEOPLE("Pessoas"),
    REPORTS("Relatórios"),
}

@Composable
fun SupervisorAreaShell(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val state = viewModel.state
    var pendingPrimaryDestination by remember { mutableStateOf<SupervisorPrimaryDestination?>(null) }

    val stateCurrent = when (state.destination) {
        SupervisorDestination.AO_VIVO -> SupervisorPrimaryDestination.LIVE
        SupervisorDestination.COLABORADORES -> SupervisorPrimaryDestination.PEOPLE
        SupervisorDestination.RELATORIOS -> SupervisorPrimaryDestination.REPORTS
        else -> null
    }

    LaunchedEffect(state.destination, state.carregando) {
        val pending = pendingPrimaryDestination ?: return@LaunchedEffect
        val resolved = when (state.destination) {
            SupervisorDestination.AO_VIVO -> SupervisorPrimaryDestination.LIVE
            SupervisorDestination.COLABORADORES -> SupervisorPrimaryDestination.PEOPLE
            SupervisorDestination.RELATORIOS -> SupervisorPrimaryDestination.REPORTS
            else -> null
        }
        if (resolved == pending || !state.carregando) {
            pendingPrimaryDestination = null
        }
    }

    val current = pendingPrimaryDestination ?: stateCurrent

    if (current == null) {
        AnimatedContent(
            targetState = state.destination,
            transitionSpec = {
                (fadeIn(tween(PontoCafeMotion.Standard)) +
                    slideInHorizontally(
                        animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                        initialOffsetX = { it / 20 },
                    )) togetherWith
                    (fadeOut(tween(PontoCafeMotion.Quick)) +
                        slideOutHorizontally(
                            animationSpec = tween(PontoCafeMotion.Standard),
                            targetOffsetX = { -it / 28 },
                        ))
            },
            label = "supervisor-detail-navigation",
        ) { destination ->
            when (destination) {
                SupervisorDestination.LOGIN -> SupervisorLoginScreenV2(viewModel, onClose)
                SupervisorDestination.NOVO_COLABORADOR -> SupervisorNewCollaboratorPersistentScreen(viewModel)
                SupervisorDestination.BIOMETRIA -> SupervisorBiometricEnrollmentScreenV2(viewModel)
                SupervisorDestination.HISTORICO -> SupervisorHistoryScreenV2(viewModel)
                SupervisorDestination.AUTORIZACAO -> SupervisorAuthorizationScreen(viewModel)
                else -> SupervisorArea(viewModel, onClose)
            }
        }
        return
    }

    fun openDestination(destination: SupervisorPrimaryDestination) {
        if (current != destination) {
            pendingPrimaryDestination = destination
        }
        when (destination) {
            SupervisorPrimaryDestination.LIVE -> viewModel.voltarAoVivo()
            SupervisorPrimaryDestination.PEOPLE -> viewModel.abrirColaboradores()
            SupervisorPrimaryDestination.REPORTS -> viewModel.abrirRelatorios(7)
        }
    }

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            SupervisorPrimaryDestination.entries.forEach { destination ->
                item(
                    selected = current == destination,
                    onClick = { openDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = when (destination) {
                                SupervisorPrimaryDestination.LIVE -> Icons.Default.Home
                                SupervisorPrimaryDestination.PEOPLE -> Icons.Default.People
                                SupervisorPrimaryDestination.REPORTS -> Icons.Default.BarChart
                            },
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 1080.dp)
                    .fillMaxWidth(),
                targetState = current,
                transitionSpec = {
                    fadeIn(tween(PontoCafeMotion.Quick)) togetherWith
                        fadeOut(tween(PontoCafeMotion.Instant))
                },
                label = "supervisor-primary-navigation",
            ) { destination ->
                when (destination) {
                    SupervisorPrimaryDestination.LIVE -> {
                        val loadingInitialLive =
                            state.pausasAtivas.isEmpty() &&
                                state.colaboradores.isEmpty() &&
                                state.ultimaAtualizacaoAoVivoEmMillis == null &&
                                state.erro == null &&
                                state.conexaoAoVivoOk
                        if (loadingInitialLive) {
                            PontoCafeListSkeletonScreen(
                                title = "Operação",
                                eyebrow = "Supervisor",
                                rows = 4,
                                showMetrics = true,
                            )
                        } else {
                            SupervisorOperationScreen(viewModel, onClose)
                        }
                    }

                    SupervisorPrimaryDestination.PEOPLE -> {
                        val loadingInitialPeople = state.carregando && state.colaboradores.isEmpty()
                        if (loadingInitialPeople) {
                            PontoCafeListSkeletonScreen(
                                title = "Pessoas",
                                eyebrow = "Supervisor",
                                rows = 5,
                                showMetrics = true,
                            )
                        } else {
                            SupervisorPeopleScreenV3(viewModel, onClose)
                        }
                    }

                    SupervisorPrimaryDestination.REPORTS -> SupervisorReportsScreenV2(viewModel, onClose)
                }
            }

            BiometricRegistrationSuccessFeedback(
                message = state.mensagem,
                onDismiss = viewModel::limparAviso,
            )
        }
    }
}
