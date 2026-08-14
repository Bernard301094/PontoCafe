package com.pontocafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.ui.ActiveBreakScreen
import com.pontocafe.app.ui.AuthorizationScreen
import com.pontocafe.app.ui.DeviceSetupScreen
import com.pontocafe.app.ui.EmployeeActionsScreen
import com.pontocafe.app.ui.EmployeeListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = SecureDeviceTokenStore(applicationContext)
        val repository = ApiClient.create(applicationContext, tokenStore)
        val factory = PontoCafeViewModelFactory { createPontoCafeViewModel(repository, tokenStore) }
        setContent {
            val vm: PontoCafeViewModel = viewModel(factory = factory)
            val state = vm.state
            when {
                !state.deviceConfigured -> DeviceSetupScreen(vm)
                state.needsAuthorization -> AuthorizationScreen(vm)
                state.selecionado == null -> EmployeeListScreen(vm)
                state.pausaAtiva != null -> ActiveBreakScreen(vm)
                else -> EmployeeActionsScreen(vm)
            }
        }
    }
}
