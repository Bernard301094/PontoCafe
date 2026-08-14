package com.pontocafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontocafe.app.data.AdminApiClient
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.ui.ActiveBreakScreen
import com.pontocafe.app.ui.AdminArea
import com.pontocafe.app.ui.AuthorizationScreen
import com.pontocafe.app.ui.DeviceSetupScreen
import com.pontocafe.app.ui.EmployeeActionsScreen
import com.pontocafe.app.ui.EmployeeListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceTokenStore = SecureDeviceTokenStore(applicationContext)
        val pontoRepository = ApiClient.create(applicationContext, deviceTokenStore)
        val pontoFactory = PontoCafeViewModelFactory {
            createPontoCafeViewModel(pontoRepository, deviceTokenStore)
        }

        val adminSessionStore = SecureAdminSessionStore(applicationContext)
        val adminRepository = AdminApiClient.create(adminSessionStore)
        val adminFactory = AdminViewModelFactory { AdminViewModel(adminRepository) }

        setContent {
            var adminAreaOpen by remember { mutableStateOf(false) }

            if (adminAreaOpen) {
                val adminVm: AdminViewModel = viewModel(key = "admin", factory = adminFactory)
                AdminArea(adminVm, onClose = { adminAreaOpen = false })
            } else {
                val vm: PontoCafeViewModel = viewModel(key = "ponto", factory = pontoFactory)
                val state = vm.state
                when {
                    !state.deviceConfigured -> DeviceSetupScreen(vm, onAdminClick = { adminAreaOpen = true })
                    state.needsAuthorization -> AuthorizationScreen(vm)
                    state.selecionado == null -> EmployeeListScreen(vm, onAdminClick = { adminAreaOpen = true })
                    state.pausaAtiva != null -> ActiveBreakScreen(vm)
                    else -> EmployeeActionsScreen(vm)
                }
            }
        }
    }
}
