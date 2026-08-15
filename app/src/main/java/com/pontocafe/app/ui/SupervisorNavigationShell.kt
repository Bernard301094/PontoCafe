package com.pontocafe.app.ui

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
        SupervisorArea(viewModel, onClose)
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
        when (current) {
            SupervisorPrimaryDestination.LIVE -> SupervisorLiveScreenV2(viewModel, onClose)
            SupervisorPrimaryDestination.PEOPLE,
            SupervisorPrimaryDestination.REPORTS -> SupervisorArea(viewModel, onClose)
        }
    }
}
