package com.pontocafe.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pontocafe.app.camera.LiteRtFaceEmbeddingEngine
import com.pontocafe.app.data.AdminApiClient
import com.pontocafe.app.data.AdminReliabilityApiClient
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.AppHealthMonitor
import com.pontocafe.app.data.AppNavigationStateStore
import com.pontocafe.app.data.KioskModeStore
import com.pontocafe.app.data.SecureAdminSessionStore
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore
import com.pontocafe.app.data.SecurePontoOfflineStore
import com.pontocafe.app.data.SupervisorApiClient
import com.pontocafe.app.notifications.SupervisorAlertNotifier
import com.pontocafe.app.ui.AdminArea
import com.pontocafe.app.ui.DeviceSetupScreen
import com.pontocafe.app.ui.PontoCafeTheme
import com.pontocafe.app.ui.PontoDeviceAuthorizationScreen
import com.pontocafe.app.ui.PontoFlowHost
import com.pontocafe.app.ui.PontoNaturalVoiceProvisioningScreen
import com.pontocafe.app.ui.RestrictedAreaLockScreen
import com.pontocafe.app.ui.RestrictedLoginModeScreen
import com.pontocafe.app.ui.SupervisorAreaShell
import com.pontocafe.app.ui.invalidateNaturalVoiceProvisioning
import com.pontocafe.app.ui.isNaturalVoiceProvisioned
import com.pontocafe.app.voice.PontoNeuralVoiceRuntime
import com.pontocafe.app.voice.PontoVoiceGuidanceEffect
import com.pontocafe.app.voice.PontoVoiceRuntime
import kotlinx.coroutines.delay

internal enum class AreaRestrita { ADMIN, SUPERVISOR, LOGIN }
private const val PONTO_IDLE_AUTH_RECHECK_MILLIS = 120_000L

class MainActivity : FragmentActivity() {
    private lateinit var appHealthMonitor: AppHealthMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appHealthMonitor = AppHealthMonitor(applicationContext).also { it.installCrashHandler() }
        SupervisorAlertNotifier.ensureChannel(applicationContext)

        val faceEmbeddingEngine = LiteRtFaceEmbeddingEngine(applicationContext)
        val navigationStore = AppNavigationStateStore(applicationContext)
        val kioskModeStore = KioskModeStore(applicationContext)
        val kioskSettings = kioskModeStore.read()
        if (kioskSettings.enabled && kioskSettings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

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
        val adminReliabilityRepository = AdminReliabilityApiClient.create(adminSessionStore)
        val adminFactory = AdminViewModelFactory { AdminViewModel(adminRepository, faceEmbeddingEngine) }
        val adminDeviceFactory = AdminDeviceViewModelFactory { AdminDeviceViewModel(adminRepository) }

        val supervisorSessionStore = SecureAdminSessionStore(applicationContext, "supervisor")
        val supervisorRepository = SupervisorApiClient.create(supervisorSessionStore = supervisorSessionStore)
        val supervisorFactory = SupervisorViewModelFactory {
            SupervisorViewModel(supervisorRepository, faceEmbeddingEngine, faceCatalogStore, applicationContext)
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
                    val savedReliabilityDestination = remember {
                        if (initialAdminSession) navigationStore.readAdminReliabilityDestination() else null
                    }
                    val savedReliabilityCollaboratorId = remember {
                        if (initialAdminSession) navigationStore.readAdminReliabilityCollaboratorId() else null
                    }
                    val protectedSessionAtLaunch = remember(savedArea) {
                        (savedArea == AreaRestrita.ADMIN && initialAdminSession) ||
                            (savedArea == AreaRestrita.SUPERVISOR && initialSupervisorSession)
                    }

                    val initialArea = remember(savedArea, protectedSessionAtLaunch) {
                        resolveInitialArea(savedArea, protectedSessionAtLaunch)
                    }
                    var areaRestrita by remember { mutableStateOf(initialArea) }
                    var restrictedLocked by remember {
                        mutableStateOf(navigationStore.isRestrictedLocked() || protectedSessionAtLaunch)
                    }
                    var sincronizarCatalogoAoVoltar by remember { mutableStateOf(false) }
                    var adminSessionDisponivel by remember { mutableStateOf(adminSessionStore.hasToken()) }
                    var supervisorSessionDisponivel by remember { mutableStateOf(supervisorSessionStore.hasToken()) }
                    var adminNavigationRestored by remember { mutableStateOf(!initialAdminSession) }
                    var reliabilityNavigationRestored by remember { mutableStateOf(!initialAdminSession) }
                    var adminDevicesOpenPersisted by remember {
                        mutableStateOf(initialAdminSession && navigationStore.isAdminDevicesOpen())
                    }
                    var adminKioskOpenPersisted by remember {
                        mutableStateOf(initialAdminSession && navigationStore.isAdminKioskOpen())
                    }
                    var naturalVoiceReadyForSession by remember {
                        mutableStateOf(isNaturalVoiceProvisioned(applicationContext))
                    }

                    // A past session verified the neural voice and persisted
                    // that fact, but the engine itself always rebuilds fresh
                    // per process. Confirm it actually comes back up in THIS
                    // process before trusting the old flag for the rest of
                    // this session; on a confirmed failure, drop back into
                    // setup so the kiosk operator sees it immediately instead
                    // of silently and permanently running on the Android
                    // fallback voice.
                    LaunchedEffect(Unit) {
                        if (naturalVoiceReadyForSession) {
                            val confirmedHealthy = PontoNeuralVoiceRuntime.awaitStartupHealthCheck(applicationContext)
                            if (!confirmedHealthy) {
                                invalidateNaturalVoiceProvisioning(applicationContext)
                                naturalVoiceReadyForSession = false
                            }
                        }
                    }

                    LaunchedEffect(protectedSessionAtLaunch) {
                        if (protectedSessionAtLaunch) navigationStore.setRestrictedLocked(true)
                    }

                    // A stale persisted area (process died in Admin/Supervisor/
                    // the login chooser without a real session, or without the
                    // user tapping "voltar ao Ponto") must not linger in
                    // storage: it would keep resolving away from Ponto on every
                    // future cold start. Correctness doesn't depend on this —
                    // resolveInitialArea() already re-derives the right value
                    // every time — this is cleanup only.
                    LaunchedEffect(savedArea, initialArea) {
                        if (savedArea != null && initialArea == null) {
                            navigationStore.saveArea(null)
                            if (savedArea == AreaRestrita.ADMIN) navigationStore.clearAdminNavigation()
                        }
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

                    fun openAccountSelector() {
                        areaRestrita = AreaRestrita.LOGIN
                        restrictedLocked = false
                        navigationStore.saveArea(AreaRestrita.LOGIN.name)
                        navigationStore.setRestrictedLocked(false)
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
                                val adminAccountScope = adminSessionStore.activeAccountId()
                                    ?: if (adminSessionStore.hasToken()) "legacy" else "login"
                                val adminVm: AdminViewModel = viewModel(
                                    key = "admin:$adminAccountScope",
                                    factory = adminFactory,
                                )
                                val adminDeviceVm: AdminDeviceViewModel = viewModel(
                                    key = "admin-devices:$adminAccountScope",
                                    factory = adminDeviceFactory,
                                )
                                val reliabilityFactory = remember(adminVm) {
                                    AdminReliabilityViewModelFactory {
                                        AdminReliabilityViewModel(
                                            repository = adminReliabilityRepository,
                                            pontoRepository = pontoRepository,
                                            offlineStore = offlineStore,
                                            embeddingEngine = faceEmbeddingEngine,
                                            onWorkforceChanged = {
                                                faceCatalogStore.clear()
                                                adminVm.abrirColaboradores()
                                            },
                                        )
                                    }
                                }
                                val reliabilityVm: AdminReliabilityViewModel = viewModel(
                                    key = "admin-reliability:$adminAccountScope",
                                    factory = reliabilityFactory,
                                )

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

                                LaunchedEffect(adminNavigationRestored) {
                                    if (!adminNavigationRestored || reliabilityNavigationRestored) return@LaunchedEffect
                                    reliabilityNavigationRestored = true
                                    when (savedReliabilityDestination?.let { runCatching { ReliabilityDestination.valueOf(it) }.getOrNull() }) {
                                        ReliabilityDestination.COLLABORATOR_HISTORY -> {
                                            savedReliabilityCollaboratorId?.let { reliabilityVm.openHistory(it) }
                                        }
                                        ReliabilityDestination.BIOMETRIC_DIAGNOSTICS -> reliabilityVm.openBiometricDiagnostics()
                                        ReliabilityDestination.SYNC_CENTER -> reliabilityVm.openSyncCenter()
                                        ReliabilityDestination.SYSTEM_DIAGNOSTICS -> reliabilityVm.openSystemDiagnostics()
                                        ReliabilityDestination.NONE,
                                        null -> Unit
                                    }
                                }

                                LaunchedEffect(
                                    reliabilityVm.state.destination,
                                    reliabilityVm.state.targetCollaboratorId,
                                    reliabilityNavigationRestored,
                                ) {
                                    if (reliabilityNavigationRestored) {
                                        navigationStore.saveAdminReliabilityState(
                                            destination = reliabilityVm.state.destination.name,
                                            collaboratorId = reliabilityVm.state.targetCollaboratorId,
                                        )
                                    }
                                }

                                AdminArea(
                                    activity = this@MainActivity,
                                    viewModel = adminVm,
                                    deviceViewModel = adminDeviceVm,
                                    reliabilityViewModel = reliabilityVm,
                                    kioskModeStore = kioskModeStore,
                                    initialDevicesOpen = adminDevicesOpenPersisted,
                                    initialKioskOpen = adminKioskOpenPersisted,
                                    onDevicesOpenChanged = { open ->
                                        adminDevicesOpenPersisted = open
                                        navigationStore.setAdminDevicesOpen(open)
                                    },
                                    onKioskOpenChanged = { open ->
                                        adminKioskOpenPersisted = open
                                        navigationStore.setAdminKioskOpen(open)
                                    },
                                    onClose = ::backToPonto,
                                )
                            }

                            AreaRestrita.SUPERVISOR -> {
                                val supervisorAccountScope = supervisorSessionStore.activeAccountId()
                                    ?: if (supervisorSessionStore.hasToken()) "legacy" else "login"
                                val supervisorVm: SupervisorViewModel = viewModel(
                                    key = "supervisor:$supervisorAccountScope",
                                    factory = supervisorFactory,
                                )
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

                                // Re-entering the app foreground is an authoritative
                                // security boundary. Existing local credentials do
                                // not regain the operational camera until validated.
                                DisposableEffect(lifecycleOwner, vm) {
                                    val observer = LifecycleEventObserver { _, event ->
                                        if (event == Lifecycle.Event.ON_RESUME) {
                                            vm.validarAutorizacaoDoDispositivo(bloquearDuranteValidacao = true)
                                        }
                                    }
                                    lifecycleOwner.lifecycle.addObserver(observer)
                                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                                }

                                // Idle foreground revalidation replaces the old
                                // voice-layer 10-second network loop. Active Ponto
                                // requests still revoke immediately through the
                                // DEVICE_AUTH_INVALID response interceptor.
                                LaunchedEffect(
                                    lifecycleOwner,
                                    vm,
                                    state.deviceAuthorizationState,
                                    state.deviceConfigured,
                                ) {
                                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                        val authorized = state.deviceAuthorizationState == DeviceAuthorizationState.AUTHORIZED_ONLINE ||
                                            state.deviceAuthorizationState == DeviceAuthorizationState.AUTHORIZED_OFFLINE
                                        if (!state.deviceConfigured || !authorized) return@repeatOnLifecycle
                                        while (true) {
                                            delay(PONTO_IDLE_AUTH_RECHECK_MILLIS)
                                            vm.validarAutorizacaoDoDispositivo(bloquearDuranteValidacao = false)
                                        }
                                    }
                                }

                                LaunchedEffect(state.deviceConfigured) {
                                    if (state.deviceConfigured) {
                                        adminSessionDisponivel = adminSessionStore.hasToken()
                                        supervisorSessionDisponivel = supervisorSessionStore.hasToken()
                                    }
                                }

                                LaunchedEffect(sincronizarCatalogoAoVoltar) {
                                    if (sincronizarCatalogoAoVoltar) {
                                        vm.sincronizarBiometrias(force = true)
                                        vm.atualizarConectividadeESincronizar()
                                        adminSessionDisponivel = adminSessionStore.hasToken()
                                        supervisorSessionDisponivel = supervisorSessionStore.hasToken()
                                        sincronizarCatalogoAoVoltar = false
                                    }
                                }

                                when (
                                    determinePontoScreenRoute(
                                        deviceAuthorizationState = state.deviceAuthorizationState,
                                        deviceConfigured = state.deviceConfigured,
                                        naturalVoiceReadyForSession = naturalVoiceReadyForSession,
                                    )
                                ) {
                                    PontoScreenRoute.CHECKING_DEVICE -> {
                                        PontoDeviceAuthorizationScreen(
                                            checking = true,
                                            error = null,
                                            onRetry = {
                                                vm.validarAutorizacaoDoDispositivo(bloquearDuranteValidacao = true)
                                            },
                                            onAdminClick = { enterRestricted(AreaRestrita.ADMIN) },
                                            onSupervisorClick = { enterRestricted(AreaRestrita.SUPERVISOR) },
                                        )
                                    }

                                    PontoScreenRoute.DEVICE_CHECK_FAILED -> {
                                        PontoDeviceAuthorizationScreen(
                                            checking = false,
                                            error = state.erro,
                                            onRetry = {
                                                vm.validarAutorizacaoDoDispositivo(bloquearDuranteValidacao = true)
                                            },
                                            onAdminClick = { enterRestricted(AreaRestrita.ADMIN) },
                                            onSupervisorClick = { enterRestricted(AreaRestrita.SUPERVISOR) },
                                        )
                                    }

                                    PontoScreenRoute.NEEDS_TOKEN_SETUP -> {
                                        DeviceSetupScreen(
                                            viewModel = vm,
                                            onAdminClick = { enterRestricted(AreaRestrita.ADMIN) },
                                            onSupervisorClick = { enterRestricted(AreaRestrita.SUPERVISOR) },
                                        )
                                    }

                                    PontoScreenRoute.NEEDS_VOICE_SETUP -> {
                                        PontoNaturalVoiceProvisioningScreen(
                                            onReady = { naturalVoiceReadyForSession = true },
                                            onContinueWithAndroidVoice = { naturalVoiceReadyForSession = true },
                                            onAdminClick = { enterRestricted(AreaRestrita.ADMIN) },
                                            onSupervisorClick = { enterRestricted(AreaRestrita.SUPERVISOR) },
                                        )
                                    }

                                    PontoScreenRoute.READY -> {
                                        PontoVoiceGuidanceEffect(viewModel = vm)
                                        PontoFlowHost(
                                            viewModel = vm,
                                            hasAdminSession = adminSessionDisponivel,
                                            hasSupervisorSession = supervisorSessionDisponivel,
                                            onAdminClick = ::openAccountSelector,
                                            onSupervisorClick = ::openAccountSelector,
                                            onLoginModeClick = ::openAccountSelector,
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

    override fun onStart() {
        super.onStart()
        if (::appHealthMonitor.isInitialized) appHealthMonitor.startStallWatchdog()
    }

    override fun onStop() {
        if (::appHealthMonitor.isInitialized) appHealthMonitor.stopStallWatchdog()
        super.onStop()
    }

    override fun onDestroy() {
        PontoVoiceRuntime.shutdown()
        if (::appHealthMonitor.isInitialized) {
            appHealthMonitor.stopStallWatchdog()
            appHealthMonitor.uninstallCrashHandler()
        }
        super.onDestroy()
    }
}
