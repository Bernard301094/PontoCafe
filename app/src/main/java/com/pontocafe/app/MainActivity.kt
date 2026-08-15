package com.pontocafe.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontocafe.app.camera.LiteRtFaceEmbeddingEngine
import com.pontocafe.app.data.AdminApiClient
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.AppNavigationStateStore
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore
import com.pontocafe.app.data.SecurePontoOfflineStore
import com.pontocafe.app.data.SupervisorApiClient
import com.pontocafe.app.ui.AdminArea
import com.pontocafe.app.ui.AuthorizationScreen
import com.pontocafe.app.ui.DeviceSetupScreen
import com.pontocafe.app.ui.FaceKioskScreen
import com.pontocafe.app.ui.IdentityConfirmationScreen
import com.pontocafe.app.ui.PointReceiptScreen
import com.pontocafe.app.ui.PontoCafeTheme
import com.pontocafe.app.ui.RestrictedAreaLockScreen
import com.pontocafe.app.ui.RestrictedLoginModeScreen
import com.pontocafe.app.ui.SupervisorAreaShell

private enum class AreaRestrita { ADMIN, SUPERVISOR, LOGIN }

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val faceEmbeddingEngine = LiteRtFaceEmbeddingEngine(applicationContext)
        val navigationStore = AppNavigationStateStore(applicationContext)

        val deviceTokenStore = SecureDeviceTokenStore(applicationContext)
        val faceCatalogStore = SecureFaceCatalogStore(applicationContext)
        val offlineStore = SecurePontoOfflineStore(applicationContext)
        val pontoRepository = ApiClient.create(applicationContext, deviceTokenStore)
        val pontoFactory = PontoCafeViewModelFactory {
            createPontoCafeViewModel(
                repository = pontoRepository,
                tokenStore = deviceTokenStore,
                faceCatalogStore = faceCatalogStore,
                offlineStore = offlineStore,
                embeddingEngine = faceEmbeddingEngine,
            )
        }

        val adminSessionStore = SecureAdminSessionStore(applicationContext, "admin")
        val adminRepository = AdminApiClient.create(adminSessionStore)
        val adminFactory = AdminViewModelFactory { AdminViewModel(adminRepository, faceEmbeddingEngine) }
        val adminDeviceFactory = AdminDeviceViewModelFactory { AdminDeviceViewModel(adminRepository) }

        val supervisorSessionStore = SecureAdminSessionStore(applicationContext, "supervisor")
        val supervisorRepository = SupervisorApiClient.create(supervisorSessionStore = supervisorSessionStore)
        val supervisorFactory = SupervisorViewModelFactory {
            SupervisorViewModel(supervisorRepository, faceEmbeddingEngine)
        }

        setContent {
            PontoCafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val initialAdminSession = remember { adminSessionStore.hasToken() }
                    val initialSupervisorSession = remember { supervisorSessionStore.hasToken() }
                    val savedArea = remember {
                        navigationStore.readArea()?.let { value -> runCatching { AreaRestrita.valueOf(value) }.getOrNull() }
                    }
                    val savedAdminDestination = remember {
                        if (initialAdminSession) navigationStore.readAdminDestination() else null
                    }
                    val savedAdminUserId = remember {
                        if (initialAdminSession) navigationStore.readAdminUserId() else null
                    }
                    val savedAdminCollaboratorId = remember {
                        if (initialAdminSession) navigationStore.readAdminCollaboratorId() else null
                    }
                    val savedAdminDevicesOpen = remember {
                        initialAdminSession && navigationStore.isAdminDevicesOpen()
                    }
                    val protectedSessionAtLaunch = remember(savedArea) {
                        (savedArea == AreaRestrita.ADMIN && initialAdminSession) ||
                            (savedArea == AreaRestrita.SUPERVISOR && initialSupervisorSession)
                    }

                    var areaRestrita by remember { mutableStateOf(savedArea) }
                    var restrictedLocked by remember {
                        mutableStateOf(navigationStore.isRestrictedLocked() || protectedSessionAtLaunch)
                    }
                    var sincronizarCatalogoAoVoltar by remember { mutableStateOf(false) }
                    var adminSessionDisponivel by remember { mutableStateOf(adminSessionStore.hasToken()) }
                    var supervisorSessionDisponivel by remember { mutableStateOf(supervisorSessionStore.hasToken()) }
                    var adminNavigationRestored by remember { mutableStateOf(!initialAdminSession) }

                    LaunchedEffect(protectedSessionAtLaunch) {
                        if (protectedSessionAtLaunch) navigationStore.setRestrictedLocked(true)
                    }

                    fun hasSessionFor(area: AreaRestrita?): Boolean = when (area) {
                        AreaRestrita.ADMIN -> adminSessionStore.hasToken()
                        AreaRestrita.SUPERVISOR -> supervisorSessionStore.hasToken()
                        else -> false
                    }

                    fun enterRestricted(area: AreaRestrita) {
                        areaRestrita = area
                        navigationStore.saveArea(area.name)
                        val mustLock = hasSessionFor(area)
                        navigationStore.setRestrictedLocked(mustLock)
                        restrictedLocked = mustLock
                    }

                    fun backToPonto() {
                        sincronizarCatalogoAoVoltar = true
                        areaRestrita = null
                        restrictedLocked = false
                        navigationStore.saveArea(null)
                        navigationStore.setRestrictedLocked(false)
                    }

                    DisposableEffect(lifecycleOwner, areaRestrita) {
                        val observer = LifecycleEventObserver { _, event ->
                            val protectedArea = areaRestrita == AreaRestrita.ADMIN || areaRestrita == AreaRestrita.SUPERVISOR
                            if (!protectedArea || !hasSessionFor(areaRestrita)) return@LifecycleEventObserver
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    navigationStore.setRestrictedLocked(true)
                                    restrictedLocked = true
                                }
                                Lifecycle.Event.ON_START -> {
                                    if (navigationStore.isRestrictedLocked()) restrictedLocked = true
                                }
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val protectedAreaHasSession =
                        (areaRestrita == AreaRestrita.ADMIN || areaRestrita == AreaRestrita.SUPERVISOR) && hasSessionFor(areaRestrita)

                    if (protectedAreaHasSession && restrictedLocked) {
                        RestrictedAreaLockScreen(
                            activity = this@MainActivity,
                            profileLabel = if (areaRestrita == AreaRestrita.ADMIN) "Administrador" else "Supervisor",
                            onUnlocked = {
                                navigationStore.setRestrictedLocked(false)
                                restrictedLocked = false
                            },
                            onBackToPonto = ::backToPonto,
                        )
                    } else {
                        when (areaRestrita) {
                            AreaRestrita.ADMIN -> {
                                val adminVm: AdminViewModel = viewModel(key = "admin", factory = adminFactory)
                                val adminDeviceVm: AdminDeviceViewModel = viewModel(key = "admin-devices", factory = adminDeviceFactory)

                                LaunchedEffect(adminVm.state.destination) {
                                    if (!adminNavigationRestored && adminVm.state.destination == AdminDestination.HOME) {
                                        adminNavigationRestored = true
                                        adminVm.restaurarNavegacao(
                                            destinationName = savedAdminDestination,
                                            userId = savedAdminUserId,
                                            collaboratorId = savedAdminCollaboratorId,
                                        )
                                        return@LaunchedEffect
                                    }
                                    if (adminNavigationRestored) {
                                        navigationStore.saveAdminState(
                                            destination = adminVm.state.destination.name,
                                            userId = adminVm.state.selecionado?.id,
                                            collaboratorId = adminVm.state.colaboradorSelecionado?.id,
                                        )
                                    }
                                }

                                AdminArea(
                                    viewModel = adminVm,
                                    deviceViewModel = adminDeviceVm,
                                    initialDevicesOpen = savedAdminDevicesOpen,
                                    onDevicesOpenChanged = navigationStore::setAdminDevicesOpen,
                                    onClose = ::backToPonto,
                                )
                            }

                            AreaRestrita.SUPERVISOR -> {
                                val supervisorVm: SupervisorViewModel = viewModel(key = "supervisor", factory = supervisorFactory)
                                SupervisorAreaShell(supervisorVm, onClose = ::backToPonto)
                            }

                            AreaRestrita.LOGIN -> RestrictedLoginModeScreen(
                                onAdminClick = { enterRestricted(AreaRestrita.ADMIN) },
                                onSupervisorClick = { enterRestricted(AreaRestrita.SUPERVISOR) },
                                onBackToPonto = ::backToPonto,
                            )

                            null -> {
                                val vm: PontoCafeViewModel = viewModel(key = "ponto", factory = pontoFactory)
                                val state = vm.state

                                LaunchedEffect(state.deviceConfigured) {
                                    if (state.deviceConfigured) {
                                        vm.atualizarConectividadeESincronizar()
                                        adminSessionDisponivel = adminRepository.validarSessaoSalva()
                                        supervisorSessionDisponivel = supervisorRepository.validarSessaoSalva()
                                    }
                                }

                                LaunchedEffect(sincronizarCatalogoAoVoltar) {
                                    if (sincronizarCatalogoAoVoltar) {
                                        vm.sincronizarBiometrias(force = true)
                                        vm.atualizarConectividadeESincronizar()
                                        adminSessionDisponivel = adminRepository.validarSessaoSalva()
                                        supervisorSessionDisponivel = supervisorRepository.validarSessaoSalva()
                                        sincronizarCatalogoAoVoltar = false
                                    }
                                }

                                when {
                                    !state.deviceConfigured -> DeviceSetupScreen(vm, onAdminClick = { enterRestricted(AreaRestrita.ADMIN) })
                                    state.comprovante != null -> PointReceiptScreen(vm)
                                    state.needsAuthorization -> AuthorizationScreen(vm)
                                    state.identificacao != null -> IdentityConfirmationScreen(vm)
                                    else -> FaceKioskScreen(
                                        viewModel = vm,
                                        hasAdminSession = adminSessionDisponivel,
                                        hasSupervisorSession = supervisorSessionDisponivel,
                                        onAdminClick = { enterRestricted(AreaRestrita.ADMIN) },
                                        onSupervisorClick = { enterRestricted(AreaRestrita.SUPERVISOR) },
                                        onLoginModeClick = {
                                            areaRestrita = AreaRestrita.LOGIN
                                            navigationStore.saveArea(AreaRestrita.LOGIN.name)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
