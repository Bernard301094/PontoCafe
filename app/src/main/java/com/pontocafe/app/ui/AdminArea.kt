package com.pontocafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
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
    val state = viewModel.state
    var showingDevices by remember(initialDevicesOpen) { mutableStateOf(initialDevicesOpen) }
    var showingKiosk by remember(initialKioskOpen) { mutableStateOf(initialKioskOpen) }
    var pendingPrimaryDestination by remember { mutableStateOf<AdminPrimaryDestination?>(null) }

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
        PontoCafeResponsivePage(maxContentWidth = 1080.dp) {
            MotionReveal {
                val deviceState = deviceViewModel.state
                if (deviceState.carregando && deviceState.dispositivos.isEmpty()) {
                    PontoCafeListSkeletonScreen(
                        title = "Dispositivos",
                        eyebrow = "Segurança do Ponto",
                        onBack = { setDevicesOpen(false) },
                        rows = 4,
                        showMetrics = true,
                    )
                } else {
                    AdminDevicesScreenV2(
                        viewModel = deviceViewModel,
                        onBack = { setDevicesOpen(false) },
                    )
                }
            }
        }
        return
    }

    if (showingKiosk) {
        BackHandler { setKioskOpen(false) }
        PontoCafeResponsivePage(maxContentWidth = 840.dp) {
            MotionReveal {
                KioskModeScreen(
                    activity = activity,
                    store = kioskModeStore,
                    onBack = { setKioskOpen(false) },
                )
            }
        }
        return
    }

    if (reliabilityDestination != ReliabilityDestination.NONE) {
        BackHandler { reliabilityViewModel.closeDetail() }
        AnimatedContent(
            targetState = reliabilityDestination,
            transitionSpec = {
                (fadeIn(tween(PontoCafeMotion.Standard)) +
                    slideInHorizontally(
                        animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                        initialOffsetX = { it / 20 },
                    )) togetherWith
                    (fadeOut(tween(PontoCafeMotion.Quick)) +
                        slideOutHorizontally(
                            animationSpec = tween(PontoCafeMotion.Standard),
                            targetOffsetX = { -it / 28 },
                        ))
            },
            label = "admin-reliability-navigation",
        ) { destination ->
            when (destination) {
                ReliabilityDestination.COLLABORATOR_HISTORY -> PontoCafeResponsivePage(maxContentWidth = 960.dp) {
                    CollaboratorHistoryScreen(
                        viewModel = viewModel,
                        reliabilityViewModel = reliabilityViewModel,
                        onBack = reliabilityViewModel::closeDetail,
                    )
                }
                ReliabilityDestination.BIOMETRIC_DIAGNOSTICS -> PontoCafeResponsivePage(maxContentWidth = 1080.dp) {
                    BiometricDiagnosticsScreen(
                        adminViewModel = viewModel,
                        viewModel = reliabilityViewModel,
                        onBack = reliabilityViewModel::closeDetail,
                    )
                }
                ReliabilityDestination.SYNC_CENTER -> PontoCafeResponsivePage(maxContentWidth = 960.dp) {
                    SyncCenterScreen(
                        viewModel = reliabilityViewModel,
                        onBack = reliabilityViewModel::closeDetail,
                    )
                }
                ReliabilityDestination.SYSTEM_DIAGNOSTICS -> PontoCafeResponsivePage(maxContentWidth = 960.dp) {
                    SystemDiagnosticsScreen(
                        viewModel = reliabilityViewModel,
                        onBack = reliabilityViewModel::closeDetail,
                    )
                }
                ReliabilityDestination.NONE -> Unit
            }
        }
        return
    }

    BackHandler(enabled = state.destination != AdminDestination.LOADING) {
        when (state.destination) {
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

    val stateRootDestination = when (state.destination) {
        AdminDestination.HOME -> AdminPrimaryDestination.HOME
        AdminDestination.COLLABORATORS -> AdminPrimaryDestination.PEOPLE
        AdminDestination.SETTINGS -> AdminPrimaryDestination.MANAGEMENT
        else -> null
    }

    LaunchedEffect(state.destination, state.carregando) {
        val pending = pendingPrimaryDestination ?: return@LaunchedEffect
        val resolved = when (state.destination) {
            AdminDestination.HOME -> AdminPrimaryDestination.HOME
            AdminDestination.COLLABORATORS -> AdminPrimaryDestination.PEOPLE
            AdminDestination.SETTINGS -> AdminPrimaryDestination.MANAGEMENT
            else -> null
        }
        if (resolved == pending || !state.carregando) {
            pendingPrimaryDestination = null
        }
    }

    val rootDestination = pendingPrimaryDestination ?: stateRootDestination

    if (rootDestination != null) {
        NavigationSuiteScaffold(
            containerColor = MaterialTheme.colorScheme.background,
            navigationSuiteItems = {
                AdminPrimaryDestination.entries.forEach { destination ->
                    item(
                        selected = rootDestination == destination,
                        onClick = {
                            if (rootDestination != destination) {
                                pendingPrimaryDestination = destination
                            }
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
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = rootDestination,
                    transitionSpec = {
                        fadeIn(tween(PontoCafeMotion.Quick)) togetherWith
                            fadeOut(tween(PontoCafeMotion.Instant))
                    },
                    label = "admin-primary-navigation",
                ) { destination ->
                    when (destination) {
                        AdminPrimaryDestination.HOME -> AdminPanelScreen(
                            viewModel = viewModel,
                            onClose = onClose,
                            onDevicesClick = { setDevicesOpen(true) },
                        )
                        AdminPrimaryDestination.PEOPLE -> {
                            val initialPeopleLoading = state.carregando && state.colaboradores.isEmpty()
                            if (initialPeopleLoading) {
                                PontoCafeResponsivePage(maxContentWidth = 1080.dp) {
                                    PontoCafeListSkeletonScreen(
                                        title = "Pessoas",
                                        eyebrow = "Equipe, biometria e acessos",
                                        rows = 5,
                                        showMetrics = true,
                                    )
                                }
                            } else {
                                PontoCafeResponsivePage(maxContentWidth = 1080.dp) {
                                    AdminPeopleScreenV4(
                                        viewModel = viewModel,
                                        reliabilityViewModel = reliabilityViewModel,
                                        onClose = onClose,
                                    )
                                }
                            }
                        }
                        AdminPrimaryDestination.MANAGEMENT -> {
                            val initialManagementLoading = state.carregando &&
                                state.regrasCafe.isEmpty() &&
                                stateRootDestination != AdminPrimaryDestination.MANAGEMENT
                            if (initialManagementLoading) {
                                PontoCafeResponsivePage(maxContentWidth = 960.dp) {
                                    PontoCafeListSkeletonScreen(
                                        title = "Gestão",
                                        eyebrow = "Configurações e operação",
                                        rows = 4,
                                        showMetrics = false,
                                    )
                                }
                            } else {
                                AdminManagementScreenV2(
                                    viewModel = viewModel,
                                    reliabilityViewModel = reliabilityViewModel,
                                    onDevicesClick = { setDevicesOpen(true) },
                                    onSyncClick = reliabilityViewModel::openSyncCenter,
                                    onKioskClick = { setKioskOpen(true) },
                                    onClose = onClose,
                                )
                            }
                        }
                    }
                }

                BiometricRegistrationSuccessFeedback(
                    message = state.mensagem,
                    onDismiss = viewModel::limparFeedback,
                )
            }
        }
        return
    }

    AnimatedContent(
        targetState = state.destination,
        transitionSpec = {
            (fadeIn(tween(PontoCafeMotion.Standard)) +
                slideInHorizontally(
                    animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                    initialOffsetX = { it / 20 },
                )) togetherWith
                (fadeOut(tween(PontoCafeMotion.Quick)) +
                    slideOutHorizontally(
                        animationSpec = tween(PontoCafeMotion.Standard),
                        targetOffsetX = { -it / 28 },
                    ))
        },
        label = "admin-detail-navigation",
    ) { destination ->
        when (destination) {
            AdminDestination.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            AdminDestination.LOGIN -> PontoCafeResponsivePage(maxContentWidth = 720.dp) {
                AdminLoginScreen(viewModel, onClose)
            }
            AdminDestination.FIRST_SETUP -> PontoCafeResponsivePage(maxContentWidth = 760.dp) {
                FirstAdminSetupScreen(viewModel, onClose)
            }
            AdminDestination.NEW_ACCOUNT -> AdminNewAccountScreen(viewModel)
            AdminDestination.USER_DETAIL -> PontoCafeResponsivePage(maxContentWidth = 840.dp) {
                AdminUserDetailScreen(viewModel)
            }
            AdminDestination.AUTHORIZATION -> PontoCafeResponsivePage(maxContentWidth = 960.dp) {
                AdminAuthorizationScreen(viewModel)
            }
            AdminDestination.NEW_COLLABORATOR -> AdminNewCollaboratorScreen(viewModel)
            AdminDestination.BIOMETRIC_ENROLLMENT -> PontoCafeResponsivePage(maxContentWidth = 840.dp) {
                AdminBiometricEnrollmentScreen(viewModel)
            }
            AdminDestination.AUDIT -> PontoCafeResponsivePage(maxContentWidth = 1080.dp) {
                AdminAuditScreen(viewModel)
            }
            AdminDestination.HOME,
            AdminDestination.COLLABORATORS,
            AdminDestination.SETTINGS -> Unit
        }
    }
}
