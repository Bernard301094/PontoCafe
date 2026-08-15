package com.pontocafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import com.pontocafe.app.AdminDestination
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.AdminViewModel

private enum class AdminPrimaryDestination(val label: String) {
    HOME("Início"),
    PEOPLE("Pessoas"),
    MANAGEMENT("Gestão"),
}

@Composable
fun AdminArea(
    viewModel: AdminViewModel,
    deviceViewModel: AdminDeviceViewModel,
    initialDevicesOpen: Boolean = false,
    onDevicesOpenChanged: (Boolean) -> Unit = {},
    onClose: () -> Unit,
) {
    var mostrandoDispositivos by remember(initialDevicesOpen) { mutableStateOf(initialDevicesOpen) }

    LaunchedEffect(mostrandoDispositivos) {
        if (mostrandoDispositivos) deviceViewModel.carregar()
    }

    fun setDevicesOpen(open: Boolean) {
        mostrandoDispositivos = open
        onDevicesOpenChanged(open)
    }

    if (mostrandoDispositivos) {
        BackHandler { setDevicesOpen(false) }
        AdminDevicesScreenV2(
            viewModel = deviceViewModel,
            onBack = { setDevicesOpen(false) },
        )
        return
    }

    BackHandler(enabled = viewModel.state.destination != AdminDestination.LOADING) {
        when (viewModel.state.destination) {
            AdminDestination.NEW_COLLABORATOR,
            AdminDestination.BIOMETRIC_ENROLLMENT -> viewModel.voltarColaboradores()

            AdminDestination.NEW_ACCOUNT,
            AdminDestination.USER_DETAIL,
            AdminDestination.AUTHORIZATION,
            AdminDestination.AUDIT -> viewModel.voltarHome()

            AdminDestination.COLLABORATORS,
            AdminDestination.SETTINGS -> viewModel.voltarHome()

            AdminDestination.HOME,
            AdminDestination.LOGIN,
            AdminDestination.FIRST_SETUP -> onClose()

            AdminDestination.LOADING -> Unit
        }
    }

    val rootDestination = when (viewModel.state.destination) {
        AdminDestination.HOME -> AdminPrimaryDestination.HOME
        AdminDestination.COLLABORATORS -> AdminPrimaryDestination.PEOPLE
        AdminDestination.SETTINGS -> AdminPrimaryDestination.MANAGEMENT
        else -> null
    }

    if (rootDestination != null) {
        NavigationSuiteScaffold(
            containerColor = MaterialTheme.colorScheme.background,
            navigationSuiteItems = {
                AdminPrimaryDestination.entries.forEach { destination ->
                    item(
                        selected = rootDestination == destination,
                        onClick = {
                            when (destination) {
                                AdminPrimaryDestination.HOME -> viewModel.voltarHome()
                                AdminPrimaryDestination.PEOPLE -> viewModel.abrirColaboradores()
                                AdminPrimaryDestination.MANAGEMENT -> viewModel.abrirConfiguracoes()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    AdminPrimaryDestination.HOME -> Icons.Default.Home
                                    AdminPrimaryDestination.PEOPLE -> Icons.Default.People
                                    AdminPrimaryDestination.MANAGEMENT -> Icons.Default.Settings
                                },
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            },
        ) {
            when (rootDestination) {
                AdminPrimaryDestination.HOME -> AdminPanelScreen(
                    viewModel = viewModel,
                    onClose = onClose,
                    onDevicesClick = { setDevicesOpen(true) },
                )
                AdminPrimaryDestination.PEOPLE -> AdminCollaboratorsScreen(viewModel)
                AdminPrimaryDestination.MANAGEMENT -> AdminRulesScreen(
                    viewModel = viewModel,
                    onDevicesClick = { setDevicesOpen(true) },
                )
            }
        }
        return
    }

    when (viewModel.state.destination) {
        AdminDestination.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AdminDestination.LOGIN -> AdminLoginScreen(viewModel, onClose)
        AdminDestination.FIRST_SETUP -> FirstAdminSetupScreen(viewModel, onClose)
        AdminDestination.NEW_ACCOUNT -> AdminNewAccountScreen(viewModel)
        AdminDestination.USER_DETAIL -> AdminUserDetailScreen(viewModel)
        AdminDestination.AUTHORIZATION -> AdminAuthorizationScreen(viewModel)
        AdminDestination.NEW_COLLABORATOR -> AdminNewCollaboratorScreen(viewModel)
        AdminDestination.BIOMETRIC_ENROLLMENT -> AdminBiometricEnrollmentScreen(viewModel)
        AdminDestination.AUDIT -> AdminAuditScreen(viewModel)
        AdminDestination.HOME,
        AdminDestination.COLLABORATORS,
        AdminDestination.SETTINGS -> Unit
    }
}
