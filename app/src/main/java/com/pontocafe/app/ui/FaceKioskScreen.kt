package com.pontocafe.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.PontoRecognitionStage
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceCapturePurpose
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FaceTrackContinuity
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class RestrictedAreaRequest { SUPERVISOR, ADMIN, LOGIN }

private enum class KioskLivenessChallenge(val instruction: String) {
    BLINK("Pisque uma vez"),
    TURN_LEFT("Vire levemente para a esquerda"),
    TURN_RIGHT("Vire levemente para a direita"),
    ;

    fun accepts(observation: FaceObservation): Boolean {
        if (!observation.isWellPositioned || abs(observation.pitch) > 15f) return false
        return when (this) {
            BLINK -> false
            TURN_LEFT -> observation.yaw in 18f..38f
            TURN_RIGHT -> observation.yaw in -38f..-18f
        }
    }
}

private const val CHALLENGE_STABLE_FRAMES = 4
private const val RECOGNITION_STABLE_FRAMES = 4
private const val BLINK_FALLBACK_FRAMES = 36

@Composable
fun FaceKioskScreen(
    viewModel: PontoCafeViewModel,
    hasAdminSession: Boolean,
    hasSupervisorSession: Boolean,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
    onLoginModeClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlockRepository = remember(context) {
        ApiClient.create(context.applicationContext, SecureDeviceTokenStore(context.applicationContext))
    }
    val state = viewModel.state
    val cameraPermission = rememberCameraPermissionUiState()
    val permissionGranted = cameraPermission.granted

    val captureController = remember { FrameCaptureController() }
    val liveness = remember { BlinkLiveness() }
    val turnChallengeContinuity = remember { FaceTrackContinuity() }
    var livenessState by remember { mutableStateOf(LivenessState.POSICIONE_ROSTO) }
    var challenge by remember { mutableStateOf(KioskLivenessChallenge.entries.random()) }
    val stableChallengeFrames = remember { intArrayOf(0) }
    val stableRecognitionFrames = remember { intArrayOf(0) }
    val blinkPendingFrames = remember { intArrayOf(0) }
    var challengeAdjustedForEyes by remember { mutableStateOf(false) }
    var challengeCompleted by remember { mutableStateOf(false) }
    var captureRequested by remember { mutableStateOf(false) }
    var detectedFaces by remember { mutableStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var restrictedAreaRequest by remember { mutableStateOf<RestrictedAreaRequest?>(null) }
    var exitPin by remember { mutableStateOf("") }
    var unlockLoading by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    fun fecharSolicitacaoAcesso() {
        restrictedAreaRequest = null
        exitPin = ""
        unlockLoading = false
        unlockError = null
    }

    restrictedAreaRequest?.let { target ->
        RestrictedAccessDialog(
            target = target,
            pin = exitPin,
            loading = unlockLoading,
            error = unlockError,
            onPinChange = { value ->
                exitPin = value.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onDismiss = { if (!unlockLoading) fecharSolicitacaoAcesso() },
            onConfirm = {
                val destination = target
                unlockLoading = true
                unlockError = null
                scope.launch {
                    runCatching { unlockRepository.validarPinSaida(exitPin, destination.name) }
                        .onSuccess {
                            fecharSolicitacaoAcesso()
                            when (destination) {
                                RestrictedAreaRequest.SUPERVISOR -> onSupervisorClick()
                                RestrictedAreaRequest.ADMIN -> onAdminClick()
                                RestrictedAreaRequest.LOGIN -> onLoginModeClick()
                            }
                        }
                        .onFailure { error ->
                            unlockLoading = false
                            exitPin = ""
                            unlockError = PontoCafeRepository.mensagemErro(error)
                        }
                }
            },
        )
    }

    LaunchedEffect(state.scanCycle) {
        liveness.reset()
        turnChallengeContinuity.reset()
        challenge = KioskLivenessChallenge.entries.random()
        stableChallengeFrames[0] = 0
        stableRecognitionFrames[0] = 0
        blinkPendingFrames[0] = 0
        challengeAdjustedForEyes = false
        challengeCompleted = false
        captureRequested = false
        detectedFaces = 0
        cameraError = null
        livenessState = LivenessState.POSICIONE_ROSTO
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val compactHeight = maxHeight < 480.dp
        val expanded = maxWidth >= 600.dp && !compactHeight
        val guideWidth = minOf(
            maxWidth * if (expanded) 0.46f else 0.72f,
            maxHeight * if (compactHeight) 0.42f else 0.58f,
        )

        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                analysisEnabled = viewModel.faceModelReady && state.scanning &&
                    state.catalogoBiometricoPronto && !state.carregando,
                showPositionGuide = false,
                onObservation = { observation ->
                    cameraError = null
                    if (detectedFaces != observation.faceCount) {
                        detectedFaces = observation.faceCount
                    }
                    if (
                        state.scanning && state.catalogoBiometricoPronto &&
                        !state.carregando && !captureRequested
                    ) {
                        if (!challengeCompleted) {
                            if (challenge == KioskLivenessChallenge.BLINK) {
                                val next = liveness.update(observation)
                                if (livenessState != next) {
                                    livenessState = next
                                }

                                if (observation.isFrontal) {
                                    blinkPendingFrames[0] += 1
                                } else {
                                    blinkPendingFrames[0] = 0
                                }

                                if (next == LivenessState.CONCLUIDO) {
                                    challengeCompleted = true
                                    stableRecognitionFrames[0] = 0
                                    blinkPendingFrames[0] = 0
                                } else if (blinkPendingFrames[0] >= BLINK_FALLBACK_FRAMES) {
                                    challenge = listOf(
                                        KioskLivenessChallenge.TURN_LEFT,
                                        KioskLivenessChallenge.TURN_RIGHT,
                                    ).random()
                                    stableChallengeFrames[0] = 0
                                    blinkPendingFrames[0] = 0
                                    challengeAdjustedForEyes = true
                                    liveness.reset()
                                    livenessState = LivenessState.POSICIONE_ROSTO
                                }
                            } else {
                                val nextState = if (observation.isWellPositioned) {
                                    LivenessState.PISQUE
                                } else {
                                    LivenessState.POSICIONE_ROSTO
                                }
                                if (livenessState != nextState) {
                                    livenessState = nextState
                                }
                                if (challenge.accepts(observation)) {
                                    if (stableChallengeFrames[0] == 0) {
                                        turnChallengeContinuity.bind(observation)
                                        stableChallengeFrames[0] = 1
                                    } else if (turnChallengeContinuity.matches(observation)) {
                                        stableChallengeFrames[0] += 1
                                    } else {
                                        turnChallengeContinuity.bind(observation)
                                        stableChallengeFrames[0] = 1
                                    }
                                    if (stableChallengeFrames[0] >= CHALLENGE_STABLE_FRAMES) {
                                        challengeCompleted = true
                                        stableRecognitionFrames[0] = 0
                                        livenessState = LivenessState.CONCLUIDO
                                    }
                                } else {
                                    stableChallengeFrames[0] = 0
                                }
                            }
                        } else {
                            val sameLivenessFace = if (challenge == KioskLivenessChallenge.BLINK) {
                                liveness.matchesChallengeFace(observation)
                            } else {
                                turnChallengeContinuity.matches(observation)
                            }
                            if (!sameLivenessFace) {
                                challengeCompleted = false
                                stableChallengeFrames[0] = 0
                                stableRecognitionFrames[0] = 0
                                blinkPendingFrames[0] = 0
                                liveness.reset()
                                turnChallengeContinuity.reset()
                                livenessState = LivenessState.POSICIONE_ROSTO
                            } else if (observation.isFrontal) {
                                stableRecognitionFrames[0] += 1
                                if (stableRecognitionFrames[0] >= RECOGNITION_STABLE_FRAMES) {
                                    captureRequested = true
                                    captureController.request(observation, FaceCapturePurpose.IDENTIFICATION)
                                }
                            } else {
                                stableRecognitionFrames[0] = 0
                            }
                        }
                    }
                },
                onFrame = { frame ->
                    viewModel.processarFrame(frame)
                    captureRequested = false
                },
                onCaptureRejected = {
                    captureRequested = false
                    challengeCompleted = false
                    stableChallengeFrames[0] = 0
                    stableRecognitionFrames[0] = 0
                    blinkPendingFrames[0] = 0
                    liveness.reset()
                    turnChallengeContinuity.reset()
                    livenessState = LivenessState.POSICIONE_ROSTO
                },
                onError = { cameraError = it },
            )
        } else {
            CameraPermissionCard(
                state = cameraPermission,
                title = "Ative a câmera para bater o ponto",
                rationale = "O Ponto Café usa reconhecimento facial para registrar a saída e o retorno com segurança.",
                dark = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = if (compactHeight) 68.dp else 92.dp),
            )
        }

        if (permissionGranted) {
            KioskCameraScrims(compactHeight = compactHeight)
        }

        val noFaceVisible = state.scanning && state.catalogoBiometricoPronto &&
            !state.carregando && detectedFaces == 0
        val multipleFacesVisible = state.scanning && detectedFaces > 1
        val recognitionReady = challengeCompleted && !captureRequested &&
            !state.carregando && !multipleFacesVisible

        if (permissionGranted) {
            KioskFaceGuide(
                active = state.catalogoBiometricoPronto,
                warning = multipleFacesVisible,
                ready = recognitionReady,
                guideWidth = guideWidth,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        KioskTopBar(
            offline = state.modoOffline,
            pendingEvents = state.eventosPendentes,
            hasAdminSession = hasAdminSession,
            hasSupervisorSession = hasSupervisorSession,
            onAdmin = { restrictedAreaRequest = RestrictedAreaRequest.ADMIN },
            onSupervisor = { restrictedAreaRequest = RestrictedAreaRequest.SUPERVISOR },
            onAccess = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        val challengeInstruction = when {
            challengeCompleted -> "Volte ao centro"
            challenge == KioskLivenessChallenge.BLINK &&
                livenessState == LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
            livenessState == LivenessState.POSICIONE_ROSTO -> "Olhe para a câmera"
            else -> challenge.instruction
        }
        val instructionTitle = when {
            cameraError != null -> "Câmera indisponível"
            !viewModel.faceModelReady -> "Reconhecimento indisponível"
            state.sincronizandoBiometrias && !state.catalogoBiometricoPronto -> "Preparando reconhecimento"
            !state.catalogoBiometricoPronto && state.erroSincronizacaoBiometrica != null -> "Rostos indisponíveis"
            state.catalogoBiometricoCarregado && !state.catalogoBiometricoPronto -> "Nenhum rosto disponível"
            !state.catalogoBiometricoPronto -> "Rostos ainda não sincronizados"
            state.recognitionStage == PontoRecognitionStage.IDENTIFICANDO -> "Identificando rosto"
            state.recognitionStage == PontoRecognitionStage.VALIDANDO_CONSISTENCIA -> "Confirmando o mesmo rosto"
            state.recognitionStage == PontoRecognitionStage.CONFIRMANDO_IDENTIDADE -> "Confirmando identidade"
            state.recognitionStage == PontoRecognitionStage.REGISTRANDO_PONTO -> "Confirmando seu ponto"
            captureRequested -> "Capturando rosto"
            state.carregando -> "Processando reconhecimento"
            multipleFacesVisible -> "Apenas uma pessoa por vez"
            noFaceVisible -> "Aproxime-se da câmera"
            challengeCompleted -> "Identidade pronta"
            else -> challengeInstruction
        }
        val instructionDetail = when {
            cameraError != null -> cameraError.orEmpty()
            !viewModel.faceModelReady -> "O modelo facial precisa estar disponível neste APK."
            state.sincronizandoBiometrias && !state.catalogoBiometricoPronto ->
                "Carregando o catálogo facial seguro deste dispositivo."
            !state.catalogoBiometricoPronto && state.erroSincronizacaoBiometrica != null ->
                state.erroSincronizacaoBiometrica.orEmpty()
            state.catalogoBiometricoCarregado && !state.catalogoBiometricoPronto ->
                "O catálogo está sincronizado, mas não contém rostos ativos compatíveis com este modelo."
            !state.catalogoBiometricoPronto -> "Abra Admin ou Supervisor para cadastrar e sincronizar os rostos."
            state.recognitionStage == PontoRecognitionStage.IDENTIFICANDO ->
                "Comparando a captura com o catálogo seguro deste dispositivo."
            state.recognitionStage == PontoRecognitionStage.VALIDANDO_CONSISTENCIA ->
                "Mantenha o rosto centralizado por mais um instante."
            state.recognitionStage == PontoRecognitionStage.CONFIRMANDO_IDENTIDADE ->
                "Validando a correspondência facial de forma autoritativa."
            state.recognitionStage == PontoRecognitionStage.REGISTRANDO_PONTO ->
                "Identidade confirmada. Aguarde o resultado do registro."
            captureRequested -> "Mantenha o rosto centralizado por mais um instante."
            state.carregando -> "Aguarde a conclusão do processamento."
            multipleFacesVisible -> "Deixe somente uma pessoa dentro do enquadramento."
            noFaceVisible -> "Centralize o rosto dentro do guia para começar."
            challengeCompleted -> "Olhe de frente e mantenha o rosto estável por um instante."
            challengeAdjustedForEyes -> "Siga o movimento de cabeça indicado."
            state.modoOffline -> "O registro ficará protegido neste aparelho até a conexão voltar."
            else -> "Siga a instrução acima. O registro acontece automaticamente."
        }

        if (permissionGranted) {
            KioskInstructionSheet(
                title = instructionTitle,
                detail = instructionDetail,
                loading = state.carregando,
                noFace = noFaceVisible,
                multipleFaces = multipleFacesVisible,
                ready = challengeCompleted,
                offline = state.modoOffline,
                updateRequired = state.atualizacaoObrigatoria,
                updateAvailable = state.atualizacaoDisponivel,
                latestVersion = state.versaoMaisRecente,
                catalogReady = state.catalogoBiometricoPronto,
                syncingCatalog = state.sincronizandoBiometrias,
                modelReady = viewModel.faceModelReady,
                pendingEvents = state.eventosPendentes,
                syncingPending = state.sincronizandoPendencias,
                catalogSyncError = state.erroSincronizacaoBiometrica,
                error = cameraError ?: state.erro ?:
                    state.erroSincronizacaoBiometrica.takeIf { !state.catalogoBiometricoPronto },
                onSyncCatalog = { viewModel.sincronizarBiometrias(force = true) },
                onSyncPending = viewModel::sincronizarPendenciasOffline,
                compactHeight = compactHeight,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun RestrictedAccessDialog(
    target: RestrictedAreaRequest,
    pin: String,
    loading: Boolean,
    error: String?,
    onPinChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val destinationName = when (target) {
        RestrictedAreaRequest.ADMIN -> "Administrador"
        RestrictedAreaRequest.SUPERVISOR -> "Supervisor"
        RestrictedAreaRequest.LOGIN -> "Início de sessão"
    }
    val instruction = when (target) {
        RestrictedAreaRequest.ADMIN -> "Use o PIN deste dispositivo para abrir a sessão de Administrador já salva."
        RestrictedAreaRequest.SUPERVISOR -> "Use o PIN deste dispositivo para abrir a sessão de Supervisor já salva."
        RestrictedAreaRequest.LOGIN -> "Use o PIN deste dispositivo para sair do modo Ponto e abrir o início de sessão."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        title = { Text("Acesso restrito · $destinationName") },
        text = {
            PcDialogBody {
                Text(
                    instruction,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN do dispositivo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (!loading && pin.length in 4..12) onConfirm()
                    }),
                    isError = error != null,
                    supportingText = { Text(error ?: "4 a 12 números") },
                    enabled = !loading,
                )
            }
        },
        confirmButton = {
            PcPrimaryButton(
                text = "Desbloquear",
                enabled = !loading && pin.length in 4..12,
                onClick = onConfirm,
                loading = loading,
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !loading,
            ) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun KioskCameraScrims(compactHeight: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactHeight) 104.dp else 180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.68f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactHeight) 176.dp else 260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun KioskTopBar(
    offline: Boolean,
    pendingEvents: Int,
    hasAdminSession: Boolean,
    hasSupervisorSession: Boolean,
    onAdmin: () -> Unit,
    onSupervisor: () -> Unit,
    onAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        color = Color(0xE7161B19),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = Color(0xFF72DCBC),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    "Ponto Café",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    "Bater ponto por reconhecimento facial",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }

            Surface(
                shape = CircleShape,
                color = if (offline) {
                    Color(0xFFFFC867).copy(alpha = 0.14f)
                } else {
                    Color(0xFF72DCBC).copy(alpha = 0.14f)
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = if (offline) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (offline) Color(0xFFFFC867) else Color(0xFF72DCBC),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        if (offline) {
                            if (pendingEvents > 0) "Offline · $pendingEvents" else "Offline"
                        } else {
                            "Online"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (offline) Color(0xFFFFC867) else Color(0xFF72DCBC),
                    )
                }
            }

            if (hasAdminSession) {
                IconButton(onClick = onAdmin, modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget)) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = "Abrir Administrador",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (hasSupervisorSession) {
                IconButton(onClick = onSupervisor, modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget)) {
                    Icon(
                        Icons.Default.SupervisorAccount,
                        contentDescription = "Abrir Supervisor",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (!hasAdminSession && !hasSupervisorSession) {
                IconButton(onClick = onAccess, modifier = Modifier.size(PontoCafeDimensions.minimumTouchTarget)) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Acesso restrito",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KioskInstructionSheet(
    title: String,
    detail: String,
    loading: Boolean,
    noFace: Boolean,
    multipleFaces: Boolean,
    ready: Boolean,
    offline: Boolean,
    updateRequired: Boolean,
    updateAvailable: Boolean,
    latestVersion: String?,
    catalogReady: Boolean,
    syncingCatalog: Boolean,
    modelReady: Boolean,
    pendingEvents: Int,
    syncingPending: Boolean,
    catalogSyncError: String?,
    error: String?,
    onSyncCatalog: () -> Unit,
    onSyncPending: () -> Unit,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        multipleFaces -> Color(0xFFFFB4AB)
        ready -> Color(0xFF72DCBC)
        noFace -> Color(0xFFFFC867)
        loading -> Color(0xFFA5CDFF)
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = listOf(title, detail).filter(String::isNotBlank).joinToString(". ")
            },
        color = Color(0xF3161B19),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = if (compactHeight) 196.dp else 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.70f),
                        )
                    }
                }
            }

            if (updateRequired) {
                StatusPill(
                    "Atualização obrigatória · ${latestVersion ?: "nova versão"}",
                    PontoCafeTone.DANGER,
                )
            } else if (updateAvailable) {
                StatusPill("Nova versão · $latestVersion", PontoCafeTone.INFO)
            }
            if (offline) {
                StatusPill("Modo offline seguro", PontoCafeTone.WARNING)
            }
            if (catalogReady && catalogSyncError != null) {
                StatusPill("Catálogo local ativo · atualização pendente", PontoCafeTone.WARNING)
            }

            if (!catalogReady && !syncingCatalog && modelReady) {
                Button(
                    onClick = onSyncCatalog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sincronizar rostos")
                }
            }

            if (pendingEvents > 0 && !offline) {
                OutlinedButton(
                    onClick = onSyncPending,
                    enabled = !syncingPending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (syncingPending) {
                            "Sincronizando..."
                        } else {
                            "Sincronizar $pendingEvents pendente(s)"
                        },
                    )
                }
            }

            error?.let { message ->
                val faceNotRecognized = message.startsWith(
                    "ROSTO NÃO RECONHECIDO",
                    ignoreCase = true,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF3A1D1A),
                    contentColor = Color(0xFFFFDAD6),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            if (faceNotRecognized) {
                                "Rosto não reconhecido"
                            } else {
                                "Não foi possível registrar"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (faceNotRecognized) {
                                "Olhe de frente, centralize o rosto e tente novamente."
                            } else {
                                message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFDAD6).copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KioskFaceGuide(
    active: Boolean,
    warning: Boolean,
    ready: Boolean,
    guideWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val targetColor = when {
        warning -> Color(0xFFFFB4AB)
        ready -> Color(0xFF72DCBC)
        active -> Color.White.copy(alpha = 0.90f)
        else -> Color.White.copy(alpha = 0.32f)
    }
    val guideColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            PontoCafeMotion.Standard,
            easing = PontoCafeMotion.EmphasizedEasing,
        ),
        label = "kiosk-guide-color",
    )

    Canvas(
        modifier = modifier
            .width(guideWidth)
            .aspectRatio(0.80f)
            .semantics {
                contentDescription = when {
                    warning -> "Guia facial: mais de uma pessoa detectada"
                    ready -> "Guia facial: rosto pronto para reconhecimento"
                    active -> "Guia facial ativo"
                    else -> "Guia facial indisponível"
                }
            },
    ) {
        val stroke = (if (ready || warning) 4.2.dp else 3.4.dp).toPx()
        val cornerLength = size.minDimension * if (ready) 0.24f else 0.20f
        val inset = stroke
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset

        drawLine(
            guideColor,
            Offset(left, top + cornerLength),
            Offset(left, top),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(left, top),
            Offset(left + cornerLength, top),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right - cornerLength, top),
            Offset(right, top),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right, top),
            Offset(right, top + cornerLength),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(left, bottom - cornerLength),
            Offset(left, bottom),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(left, bottom),
            Offset(left + cornerLength, bottom),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right - cornerLength, bottom),
            Offset(right, bottom),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right, bottom),
            Offset(right, bottom - cornerLength),
            stroke,
            cap = StrokeCap.Round,
        )
    }
}
