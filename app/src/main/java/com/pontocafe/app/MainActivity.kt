package com.pontocafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontocafe.app.camera.LiteRtFaceEmbeddingEngine
import com.pontocafe.app.data.AdminApiClient
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore
import com.pontocafe.app.data.SupervisorApiClient
import com.pontocafe.app.ui.AdminArea
import com.pontocafe.app.ui.AuthorizationScreen
import com.pontocafe.app.ui.DeviceSetupScreen
import com.pontocafe.app.ui.FaceKioskScreen
import com.pontocafe.app.ui.IdentityConfirmationScreen
import com.pontocafe.app.ui.PointReceiptScreen
import com.pontocafe.app.ui.RestrictedLoginModeScreen
import com.pontocafe.app.ui.SupervisorArea

private enum class AreaRestrita { ADMIN, SUPERVISOR, LOGIN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val faceEmbeddingEngine = LiteRtFaceEmbeddingEngine(applicationContext)

        val deviceTokenStore = SecureDeviceTokenStore(applicationContext)
        val faceCatalogStore = SecureFaceCatalogStore(applicationContext)
        val pontoRepository = ApiClient.create(applicationContext, deviceTokenStore)
        val pontoFactory = PontoCafeViewModelFactory {
            createPontoCafeViewModel(
                repository = pontoRepository,
                tokenStore = deviceTokenStore,
                faceCatalogStore = faceCatalogStore,
                embeddingEngine = faceEmbeddingEngine,
            )
        }

        val adminSessionStore = SecureAdminSessionStore(applicationContext, "admin")
        val adminRepository = AdminApiClient.create(adminSessionStore)
        val adminFactory = AdminViewModelFactory {
            AdminViewModel(adminRepository, faceEmbeddingEngine)
        }
        val adminDeviceFactory = AdminDeviceViewModelFactory {
            AdminDeviceViewModel(adminRepository)
        }

        val supervisorSessionStore = SecureAdminSessionStore(applicationContext, "supervisor")
        val supervisorRepository = SupervisorApiClient.create(
            supervisorSessionStore = supervisorSessionStore,
        )
        val supervisorFactory = SupervisorViewModelFactory {
            SupervisorViewModel(supervisorRepository, faceEmbeddingEngine)
        }

        setContent {
            MaterialTheme {
                var areaRestrita by remember { mutableStateOf<AreaRestrita?>(null) }
                var sincronizarCatalogoAoVoltar by remember { mutableStateOf(false) }
                var adminSessionDisponivel by remember { mutableStateOf(adminSessionStore.hasToken()) }
                var supervisorSessionDisponivel by remember { mutableStateOf(supervisorSessionStore.hasToken()) }

                when (areaRestrita) {
                    AreaRestrita.ADMIN -> {
                        val adminVm: AdminViewModel = viewModel(key = "admin", factory = adminFactory)
                        val adminDeviceVm: AdminDeviceViewModel = viewModel(
                            key = "admin-devices",
                            factory = adminDeviceFactory,
                        )
                        AdminArea(
                            viewModel = adminVm,
                            deviceViewModel = adminDeviceVm,
                            onClose = {
                                sincronizarCatalogoAoVoltar = true
                                areaRestrita = null
                            },
                        )
                    }

                    AreaRestrita.SUPERVISOR -> {
                        val supervisorVm: SupervisorViewModel = viewModel(key = "supervisor", factory = supervisorFactory)
                        SupervisorArea(
                            supervisorVm,
                            onClose = {
                                sincronizarCatalogoAoVoltar = true
                                areaRestrita = null
                            },
                        )
                    }

                    AreaRestrita.LOGIN -> RestrictedLoginModeScreen(
                        onAdminClick = { areaRestrita = AreaRestrita.ADMIN },
                        onSupervisorClick = { areaRestrita = AreaRestrita.SUPERVISOR },
                        onBackToPonto = { areaRestrita = null },
                    )

                    null -> {
                        val vm: PontoCafeViewModel = viewModel(key = "ponto", factory = pontoFactory)
                        val state = vm.state

                        LaunchedEffect(state.deviceConfigured) {
                            if (state.deviceConfigured) {
                                adminSessionDisponivel = adminRepository.validarSessaoSalva()
                                supervisorSessionDisponivel = supervisorRepository.validarSessaoSalva()
                            }
                        }

                        LaunchedEffect(sincronizarCatalogoAoVoltar) {
                            if (sincronizarCatalogoAoVoltar) {
                                vm.sincronizarBiometrias(force = true)
                                adminSessionDisponivel = adminRepository.validarSessaoSalva()
                                supervisorSessionDisponivel = supervisorRepository.validarSessaoSalva()
                                sincronizarCatalogoAoVoltar = false
                            }
                        }

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
                                hasAdminSession = adminSessionDisponivel,
                                hasSupervisorSession = supervisorSessionDisponivel,
                                onAdminClick = { areaRestrita = AreaRestrita.ADMIN },
                                onSupervisorClick = { areaRestrita = AreaRestrita.SUPERVISOR },
                                onLoginModeClick = { areaRestrita = AreaRestrita.LOGIN },
                            )
                        }
                    }
                }
            }
        }
    }
}
