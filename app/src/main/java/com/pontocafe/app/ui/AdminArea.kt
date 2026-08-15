package com.pontocafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pontocafe.app.AdminDestination
import com.pontocafe.app.AdminDeviceViewModel
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminArea(
    viewModel: AdminViewModel,
    deviceViewModel: AdminDeviceViewModel,
    initialDevicesOpen: Boolean = false,
    onDevicesOpenChanged: (Boolean) -> Unit = {},
    onClose: () -> Unit,
) {
    var mostrandoDispositivos by remember(initialDevicesOpen) { mutableStateOf(initialDevicesOpen) }

    fun setDevicesOpen(open: Boolean) {
        mostrandoDispositivos = open
        onDevicesOpenChanged(open)
        if (open) deviceViewModel.carregar()
    }

    if (mostrandoDispositivos) {
        BackHandler { setDevicesOpen(false) }
        AdminDevicesScreen(
            viewModel = deviceViewModel,
            onBack = { setDevicesOpen(false) },
        )
        return
    }

    BackHandler(enabled = viewModel.state.destination != AdminDestination.LOADING) {
        when (viewModel.state.destination) {
            AdminDestination.NEW_COLLABORATOR,
            AdminDestination.BIOMETRIC_ENROLLMENT -> viewModel.voltarColaboradores()

            AdminDestination.COLLABORATORS,
            AdminDestination.NEW_ACCOUNT,
            AdminDestination.USER_DETAIL,
            AdminDestination.AUTHORIZATION,
            AdminDestination.SETTINGS,
            AdminDestination.AUDIT -> viewModel.voltarHome()

            AdminDestination.HOME,
            AdminDestination.LOGIN,
            AdminDestination.FIRST_SETUP -> onClose()

            AdminDestination.LOADING -> Unit
        }
    }

    when (viewModel.state.destination) {
        AdminDestination.LOADING -> CircularProgressIndicator()
        AdminDestination.LOGIN -> AdminLoginScreen(viewModel, onClose)
        AdminDestination.FIRST_SETUP -> FirstAdminSetupScreen(viewModel, onClose)
        AdminDestination.HOME -> AdminPanelScreen(
            viewModel = viewModel,
            onClose = onClose,
            onDevicesClick = { setDevicesOpen(true) },
        )
        AdminDestination.NEW_ACCOUNT -> AdminNewAccountScreen(viewModel)
        AdminDestination.USER_DETAIL -> AdminUserDetailScreen(viewModel)
        AdminDestination.AUTHORIZATION -> AdminAuthorizationScreen(viewModel)
        AdminDestination.SETTINGS -> AdminRulesScreen(viewModel)
        AdminDestination.COLLABORATORS -> AdminCollaboratorsScreen(viewModel)
        AdminDestination.NEW_COLLABORATOR -> AdminNewCollaboratorScreen(viewModel)
        AdminDestination.BIOMETRIC_ENROLLMENT -> AdminBiometricEnrollmentScreen(viewModel)
        AdminDestination.AUDIT -> AdminAuditScreen(viewModel)
    }
}
