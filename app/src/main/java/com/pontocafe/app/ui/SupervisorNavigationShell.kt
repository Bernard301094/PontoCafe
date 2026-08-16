package com.pontocafe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
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
    val current = when (viewModel.state.destination) {
        SupervisorDestination.AO_VIVO -> SupervisorPrimaryDestination.LIVE
        SupervisorDestination.COLABORADORES -> SupervisorPrimaryDestination.PEOPLE
        SupervisorDestination.RELATORIOS -> SupervisorPrimaryDestination.REPORTS
        else -> null
    }

    if (current == null) {
        AnimatedContent(
            targetState = viewModel.state.destination,
            transitionSpec = {
                (fadeIn(tween(PontoCafeMotion.Standard)) +
                    slideInHorizontally(
                        animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                        initialOffsetX = { it / 12 },
                    )) togetherWith
                    (fadeOut(tween(PontoCafeMotion.Quick)) +
                        slideOutHorizontally(
                            animationSpec = tween(PontoCafeMotion.Standard),
                            targetOffsetX = { -it / 18 },
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

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            SupervisorPrimaryDestination.entries.forEach { destination ->
                item(
                    selected = current == destination,
                    onClick = {
                        when (destination) {
                            SupervisorPrimaryDestination.LIVE -> viewModel.voltarAoVivo()
                            SupervisorPrimaryDestination.PEOPLE -> viewModel.abrirColaboradores()
                            SupervisorPrimaryDestination.REPORTS -> viewModel.abrirRelatorios(7)
                        }
                    },
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
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val enterOffset: (Int) -> Int = { width -> if (forward) width / 12 else -width / 12 }
                val exitOffset: (Int) -> Int = { width -> if (forward) -width / 18 else width / 18 }
                (fadeIn(tween(PontoCafeMotion.Standard)) +
                    slideInHorizontally(
                        animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                        initialOffsetX = enterOffset,
                    )) togetherWith
                    (fadeOut(tween(PontoCafeMotion.Quick)) +
                        slideOutHorizontally(
                            animationSpec = tween(PontoCafeMotion.Standard),
                            targetOffsetX = exitOffset,
                        ))
            },
            label = "supervisor-primary-navigation",
        ) { destination ->
            when (destination) {
                SupervisorPrimaryDestination.LIVE -> SupervisorLiveScreenV2(viewModel, onClose)
                SupervisorPrimaryDestination.PEOPLE -> SupervisorPeopleScreenV2(viewModel)
                SupervisorPrimaryDestination.REPORTS -> SupervisorArea(viewModel, onClose)
            }
        }
    }
}
