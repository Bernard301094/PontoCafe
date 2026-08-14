package com.pontocafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontocafe.app.data.AdminApiClient
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SupervisorApiClient
import com.pontocafe.app.ui.AdminArea
import com.pontocafe.app.ui.AuthorizationScreen
import com.pontocafe.app.ui.DeviceSetupScreen
import com.pontocafe.app.ui.FaceKioskScreen
import com.pontocafe.app.ui.IdentityConfirmationScreen
import com.pontocafe.app.ui.PointReceiptScreen
import com.pontocafe.app.ui.SupervisorArea

private enum class AreaRestrita { ADMIN, SUPERVISOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceTokenStore = SecureDeviceTokenStore(applicationContext)
        val pontoRepository = ApiClient.create(applicationContext, deviceTokenStore)
        val pontoFactory = PontoCafeViewModelFactory {
            createPontoCafeViewModel(
                context = applicationContext,
                repository = pontoRepository,
                tokenStore = deviceTokenStore,
            )
        }

        val adminSessionStore = SecureAdminSessionStore(applicationContext, "admin")
        val adminRepository = AdminApiClient.create(adminSessionStore)
        val adminFactory = AdminViewModelFactory { AdminViewModel(adminRepository) }

        val supervisorSessionStore = SecureAdminSessionStore(applicationContext, "supervisor")
        val supervisorRepository = SupervisorApiClient.create(supervisorSessionStore)
        val supervisorFactory = SupervisorViewModelFactory { SupervisorViewModel(supervisorRepository) }

        setContent {
            MaterialTheme {
                var areaRestrita by remember { mutableStateOf<AreaRestrita?>(null) }

                when (areaRestrita) {
                    AreaRestrita.ADMIN -> {
                        val adminVm: AdminViewModel = viewModel(key = "admin", factory = adminFactory)
                        AdminArea(adminVm, onClose = { areaRestrita = null })
                    }

                    AreaRestrita.SUPERVISOR -> {
                        val supervisorVm: SupervisorViewModel = viewModel(key = "supervisor", factory = supervisorFactory)
                        SupervisorArea(supervisorVm, onClose = { areaRestrita = null })
                    }

                    null -> {
                        val vm: PontoCafeViewModel = viewModel(key = "ponto", factory = pontoFactory)
                        val state = vm.state

                        when {
                            !state.deviceConfigured -> DeviceSetupScreen(
                                vm,
                                onAdminClick = { areaRestrita = AreaRestrita.ADMIN },
                            )

                            state.comprovante != null -> PointReceiptScreen(vm)
                            state.needsAuthorization -> AuthorizationScreen(vm)
                            state.identificacao != null -> IdentityConfirmationScreen(vm)
                            else -> FaceKioskScreen(
                                viewModel = vm,
                                onAdminClick = { areaRestrita = AreaRestrita.ADMIN },
                                onSupervisorClick = { areaRestrita = AreaRestrita.SUPERVISOR },
                            )
                        }
                    }
                }
            }
        }
    }
}
