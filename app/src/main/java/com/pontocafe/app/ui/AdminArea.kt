package com.pontocafe.app.ui

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
    onClose: () -> Unit,
) {
    var mostrandoDispositivos by remember { mutableStateOf(false) }

    if (mostrandoDispositivos) {
        AdminDevicesScreen(
            viewModel = deviceViewModel,
            onBack = { mostrandoDispositivos = false },
        )
        return
    }

    when (viewModel.state.destination) {
        AdminDestination.LOADING -> CircularProgressIndicator()
        AdminDestination.LOGIN -> AdminLoginScreen(viewModel, onClose)
        AdminDestination.FIRST_SETUP -> FirstAdminSetupScreen(viewModel, onClose)
        AdminDestination.HOME -> AdminPanelScreen(
            viewModel = viewModel,
            onClose = onClose,
            onDevicesClick = {
                deviceViewModel.carregar()
                mostrandoDispositivos = true
            },
        )
        AdminDestination.NEW_ACCOUNT -> AdminNewAccountScreen(viewModel)
        AdminDestination.USER_DETAIL -> AdminUserDetailScreen(viewModel)
        AdminDestination.AUTHORIZATION -> AdminAuthorizationScreen(viewModel)
        AdminDestination.SETTINGS -> AdminRulesScreen(viewModel)
        AdminDestination.COLLABORATORS -> AdminCollaboratorsScreen(viewModel)
        AdminDestination.NEW_COLLABORATOR -> AdminNewCollaboratorScreen(viewModel)
        AdminDestination.BIOMETRIC_ENROLLMENT -> AdminBiometricEnrollmentScreen(viewModel)
    }
}
