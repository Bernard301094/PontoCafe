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
import androidx.fragment.app.FragmentActivity
import com.pontocafe.app.AdminDestination
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.AdminReliabilityViewModel
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.ReliabilityDestination
import com.pontocafe.app.data.KioskModeStore

private enum class AdminPrimaryDestination(val label: String) {
    HOME("Início"),
    PEOPLE("Pessoas"),
    MANAGEMENT("Gestão"),
}

@Composable
fun AdminArea(
    activity: FragmentActivity,
    viewModel: AdminViewModel,
    deviceViewModel: AdminDeviceViewModel,
    reliabilityViewModel: AdminReliabilityViewModel,
    kioskModeStore: KioskModeStore,
    initialDevicesOpen: Boolean = false,
    initialKioskOpen: Boolean = false,
    onDevicesOpenChanged: (Boolean) -> Unit = {},
    onKioskOpenChanged: (Boolean) -> Unit = {},
    onClose: () -> Unit,
) {
    var showingDevices by remember(initialDevicesOpen) { mutableStateOf(initialDevicesOpen) }
    var showingKiosk by remember(initialKioskOpen) { mutableStateOf(initialKioskOpen) }

    LaunchedEffect(showingDevices) {
        if (showingDevices) deviceViewModel.carregar()
    }

    fun setDevicesOpen(open: Boolean) {
        showingDevices = open
        onDevicesOpenChanged(open)
    }

    fun setKioskOpen(open: Boolean) {
        showingKiosk = open
        onKioskOpenChanged(open)
    }

    val reliabilityDestination = reliabilityViewModel.state.destination

    if (showingDevices) {
        BackHandler { setDevicesOpen(false) }
        AdminDevicesScreenV2(
            viewModel = deviceViewModel,
            onBack = { setDevicesOpen(false) },
        )
        return
    }

    if (showingKiosk) {
        BackHandler { setKioskOpen(false) }
        KioskModeScreen(
            activity = activity,
            store = kioskModeStore,
            onBack = { setKioskOpen(false) },
        )
        return
    }

    if (reliabilityDestination != ReliabilityDestination.NONE) {
        BackHandler { reliabilityViewModel.closeDetail() }
        when (reliabilityDestination) {
            ReliabilityDestination.COLLABORATOR_HISTORY -> CollaboratorHistoryScreen(
                viewModel = viewModel,
                reliabilityViewModel = reliabilityViewModel,
                onBack = reliabilityViewModel::closeDetail,
            )
            ReliabilityDestination.BIOMETRIC_DIAGNOSTICS -> BiometricDiagnosticsScreen(
                adminViewModel = viewModel,
                viewModel = reliabilityViewModel,
                onBack = reliabilityViewModel::closeDetail,
            )
            ReliabilityDestination.SYNC_CENTER -> SyncCenterScreen(
                viewModel = reliabilityViewModel,
                onBack = reliabilityViewModel::closeDetail,
            )
            ReliabilityDestination.SYSTEM_DIAGNOSTICS -> SystemDiagnosticsScreen(
                viewModel = reliabilityViewModel,
                onBack = reliabilityViewModel::closeDetail,
            )
            ReliabilityDestination.NONE -> Unit
        }
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
                AdminPrimaryDestination.PEOPLE -> AdminPeopleScreenV2(
                    viewModel = viewModel,
                    reliabilityViewModel = reliabilityViewModel,
                )
                AdminPrimaryDestination.MANAGEMENT -> AdminManagementScreenV2(
                    viewModel = viewModel,
                    reliabilityViewModel = reliabilityViewModel,
                    onDevicesClick = { setDevicesOpen(true) },
                    onSyncClick = reliabilityViewModel::openSyncCenter,
                    onKioskClick = { setKioskOpen(true) },
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
