package com.pontocafe.app.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import com.pontocafe.app.AdminDestination
import com.pontocafe.app.AdminViewModel

@Composable
fun AdminArea(viewModel: AdminViewModel, onClose: () -> Unit) {
    when (viewModel.state.destination) {
        AdminDestination.LOADING -> CircularProgressIndicator()
        AdminDestination.LOGIN -> AdminLoginScreen(viewModel, onClose)
        AdminDestination.FIRST_SETUP -> FirstAdminSetupScreen(viewModel, onClose)
        AdminDestination.HOME -> AdminPanelScreen(viewModel, onClose)
        AdminDestination.NEW_ACCOUNT -> AdminNewAccountScreen(viewModel)
        AdminDestination.USER_DETAIL -> AdminUserDetailScreen(viewModel)
        AdminDestination.AUTHORIZATION -> AdminAuthorizationScreen(viewModel)
        AdminDestination.SETTINGS -> AdminRulesScreen(viewModel)
        AdminDestination.COLLABORATORS -> AdminCollaboratorsScreen(viewModel)
        AdminDestination.NEW_COLLABORATOR -> AdminNewCollaboratorScreen(viewModel)
        AdminDestination.BIOMETRIC_ENROLLMENT -> AdminBiometricEnrollmentScreen(viewModel)
    }
}
