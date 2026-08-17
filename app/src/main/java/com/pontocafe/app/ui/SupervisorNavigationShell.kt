package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorDestination
import com.pontocafe.app.SupervisorViewModel

private enum class SupervisorPrimaryDestination(val label: String) {
    LIVE("Ao vivo"),
    PEOPLE("Pessoas"),
    REPORTS("Relatórios"),
}

@Composable
fun SupervisorAreaShell(
    viewModel: SupervisorViewModel,
    onClose: () -> Unit,
) {
    val state = viewModel.state
    val current = when (state.destination) {
        SupervisorDestination.AO_VIVO -> SupervisorPrimaryDestination.LIVE
        SupervisorDestination.COLABORADORES -> SupervisorPrimaryDestination.PEOPLE
        SupervisorDestination.RELATORIOS -> SupervisorPrimaryDestination.REPORTS
        else -> null
    }

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
                else -> SupervisorArea(viewModel, onClose)
            }
        }
        return
    }

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600

    fun openDestination(destination: SupervisorPrimaryDestination) {
        when (destination) {
            SupervisorPrimaryDestination.LIVE -> viewModel.voltarAoVivo()
            SupervisorPrimaryDestination.PEOPLE -> viewModel.abrirColaboradores()
            SupervisorPrimaryDestination.REPORTS -> viewModel.abrirRelatorios(7)
        }
    }

    val primaryContent: @Composable (Modifier) -> Unit = { outerModifier ->
        Box(
            modifier = outerModifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 960.dp)
                    .fillMaxWidth(),
                targetState = current,
                transitionSpec = {
                    // Troca de aba deve parecer imediata. Evita slide + animações internas empilhadas.
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
                                title = "Ao vivo",
                                eyebrow = "Supervisor",
                                rows = 3,
                                showMetrics = true,
                            )
                        } else {
                            SupervisorLiveScreenV2(viewModel, onClose)
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
                            SupervisorPeopleScreenV2(viewModel)
                        }
                    }

                    SupervisorPrimaryDestination.REPORTS -> SupervisorArea(viewModel, onClose)
                }
            }

            BiometricRegistrationSuccessFeedback(
                message = state.mensagem,
                onDismiss = viewModel::limparAviso,
            )
        }
    }

    if (useNavigationRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                SupervisorPrimaryDestination.entries.forEach { destination ->
                    NavigationRailItem(
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
            }
            primaryContent(Modifier.weight(1f))
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!imeVisible) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    SupervisorPrimaryDestination.entries.forEach { destination ->
                        NavigationBarItem(
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
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = if (imeVisible) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        }
        primaryContent(contentModifier)
    }
}
