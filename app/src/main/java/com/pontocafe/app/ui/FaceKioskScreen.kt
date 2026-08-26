package com.pontocafe.app.ui

import android.os.SystemClock
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
import com.pontocafe.app.camera.FaceCaptureRejectionReason
import com.pontocafe.app.camera.FaceCapturePurpose
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FaceTrackContinuity
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import com.pontocafe.app.camera.PassivePresenceDecision
import com.pontocafe.app.camera.PassivePresenceGate
import com.pontocafe.app.camera.toPassivePresenceSample
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.voice.PontoVoiceKioskCue
import com.pontocafe.app.voice.PontoVoicePromptPolicy
import com.pontocafe.app.voice.PontoVoiceRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KIOSK_ZONE: ZoneId = ZoneId.of("America/Fortaleza")

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

private const val CHALLENGE_STABLE_FRAMES = 3
private const val RECOGNITION_STABLE_FRAMES = 2
private const val BLINK_FALLBACK_FRAMES = 24

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
    val state = viewModel.state
    val cameraPermission = rememberCameraPermissionUiState()
    val permissionGranted = cameraPermission.granted

    val captureController = remember { FrameCaptureController() }
    val passivePresence = remember { PassivePresenceGate() }
    val liveness = remember { BlinkLiveness() }
    val turnChallengeContinuity = remember { FaceTrackContinuity() }
    var livenessState by remember { mutableStateOf(LivenessState.POSICIONE_ROSTO) }
    var challenge by remember { mutableStateOf(KioskLivenessChallenge.BLINK) }
    val stableChallengeFrames = remember { intArrayOf(0) }
    val stableRecognitionFrames = remember { intArrayOf(0) }
    val blinkPendingFrames = remember { intArrayOf(0) }
    var activeFallback by remember { mutableStateOf(false) }
    // Espelha stableChallengeFrames (um IntArray simples, não observável) para que
    // o anel de progresso do KioskFaceGuide possa recompor sem alterar a máquina
    // de estados de liveness em si.
    var turnChallengeProgress by remember { mutableStateOf(0f) }
    var passiveAccepted by remember { mutableStateOf(false) }
    var challengeAdjustedForEyes by remember { mutableStateOf(false) }
    var challengeCompleted by remember { mutableStateOf(false) }
    var captureRequested by remember { mutableStateOf(false) }
    var detectedFaces by remember { mutableStateOf(0) }
    var facePositioned by remember { mutableStateOf(false) }
    var lastCaptureRejection by remember { mutableStateOf<FaceCaptureRejectionReason?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var restrictedAreaRequest by remember { mutableStateOf<RestrictedAreaRequest?>(null) }
    var exitPin by remember { mutableStateOf("") }
    var unlockLoading by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    fun resetLivenessFlow() {
        passivePresence.reset()
        liveness.reset()
        turnChallengeContinuity.reset()
        challenge = KioskLivenessChallenge.BLINK
        stableChallengeFrames[0] = 0
        stableRecognitionFrames[0] = 0
        blinkPendingFrames[0] = 0
        turnChallengeProgress = 0f
        activeFallback = false
        passiveAccepted = false
        challengeAdjustedForEyes = false
        challengeCompleted = false
        livenessState = LivenessState.POSICIONE_ROSTO
    }

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
            onLogin = {
                if (!unlockLoading) {
                    fecharSolicitacaoAcesso()
                    onLoginModeClick()
                }
            },
            onConfirm = {
                val destination = target
                unlockLoading = true
                unlockError = null
                scope.launch {
                    runCatching { viewModel.validarPinSaida(exitPin, destination.name) }
                        .onSuccess {
                            fecharSolicitacaoAcesso()
                            when (destination) {
                                RestrictedAreaRequest.SUPERVISOR -> onSupervisorClick()
                                RestrictedAreaRequest.ADMIN -> onAdminClick()
                                RestrictedAreaRequest.LOGIN -> onLoginModeClick()
                            }
                        }
                        .onFailure { error ->
                            if (PontoCafeRepository.isDevicePinNotConfigured(error)) {
                                fecharSolicitacaoAcesso()
                                onLoginModeClick()
                            } else {
                                unlockLoading = false
                                exitPin = ""
                                unlockError = PontoCafeRepository.mensagemErro(error)
                            }
                        }
                }
            },
        )
    }

    LaunchedEffect(state.scanCycle) {
        resetLivenessFlow()
        captureRequested = false
        detectedFaces = 0
        facePositioned = false
        cameraError = null
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
                    detectedFaces = observation.faceCount
                    facePositioned = observation.faceCount == 1 && observation.isIdentificationReady
                    if (facePositioned) lastCaptureRejection = null

                    if (
                        state.scanning && state.catalogoBiometricoPronto &&
                        !state.carregando && !captureRequested
                    ) {
                        val now = SystemClock.uptimeMillis()
                        val passiveSample = observation.toPassivePresenceSample(now)

                        if (!challengeCompleted && !activeFallback) {
                            when (passivePresence.update(passiveSample)) {
                                PassivePresenceDecision.READY -> {
                                    passiveAccepted = true
                                    challengeCompleted = true
                                    stableRecognitionFrames[0] = 0
                                    livenessState = LivenessState.CONCLUIDO
                                }
                                PassivePresenceDecision.CHALLENGE_REQUIRED -> {
                                    activeFallback = true
                                    passiveAccepted = false
                                    challenge = if (observation.eyeClassificationAvailable) {
                                        KioskLivenessChallenge.BLINK
                                    } else {
                                        listOf(
                                            KioskLivenessChallenge.TURN_LEFT,
                                            KioskLivenessChallenge.TURN_RIGHT,
                                        ).random()
                                    }
                                    liveness.reset()
                                    turnChallengeContinuity.reset()
                                    stableChallengeFrames[0] = 0
                                    blinkPendingFrames[0] = 0
                                    turnChallengeProgress = 0f
                                    livenessState = LivenessState.POSICIONE_ROSTO
                                }
                                PassivePresenceDecision.WAITING -> Unit
                            }
                        } else if (!challengeCompleted && activeFallback) {
                            if (challenge == KioskLivenessChallenge.BLINK) {
                                val next = liveness.update(observation)
                                livenessState = next
                                if (observation.isFrontal) blinkPendingFrames[0] += 1 else blinkPendingFrames[0] = 0

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
                                    turnChallengeProgress = 0f
                                    challengeAdjustedForEyes = true
                                    liveness.reset()
                                    livenessState = LivenessState.POSICIONE_ROSTO
                                }
                            } else {
                                livenessState = if (observation.isWellPositioned) {
                                    LivenessState.PISQUE
                                } else {
                                    LivenessState.POSICIONE_ROSTO
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
                                    turnChallengeProgress = stableChallengeFrames[0] / CHALLENGE_STABLE_FRAMES.toFloat()
                                    if (stableChallengeFrames[0] >= CHALLENGE_STABLE_FRAMES) {
                                        challengeCompleted = true
                                        stableRecognitionFrames[0] = 0
                                        livenessState = LivenessState.CONCLUIDO
                                    }
                                } else {
                                    stableChallengeFrames[0] = 0
                                    turnChallengeProgress = 0f
                                }
                            }
                        } else if (challengeCompleted) {
                            val sameLivenessFace = if (passiveAccepted) {
                                passivePresence.matches(passiveSample)
                            } else if (challenge == KioskLivenessChallenge.BLINK) {
                                liveness.matchesChallengeFace(observation)
                            } else {
                                turnChallengeContinuity.matches(observation)
                            }

                            if (!sameLivenessFace) {
                                resetLivenessFlow()
                            } else if (observation.isIdentificationReady) {
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
                onCaptureRejected = { reason ->
                    captureRequested = false
                    facePositioned = false
                    // O motivo do rejeite era descartado aqui e a tela mostrava
                    // sempre a mesma frase generica. A pessoa via vermelho sem
                    // saber se devia aproximar, centralizar ou endireitar a cabeca.
                    lastCaptureRejection = reason
                    resetLivenessFlow()
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
                faceDetected = detectedFaces == 1,
                warning = multipleFacesVisible,
                positioned = facePositioned,
                recognitionReady = recognitionReady,
                guideWidth = guideWidth,
                modifier = Modifier.align(Alignment.Center),
                turnProgress = if (activeFallback && challenge != KioskLivenessChallenge.BLINK && !challengeCompleted) {
                    turnChallengeProgress
                } else {
                    0f
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KioskTopBar(
                offline = state.modoOffline,
                pendingEvents = state.eventosPendentes,
                hasAdminSession = hasAdminSession,
                hasSupervisorSession = hasSupervisorSession,
                onAdmin = { restrictedAreaRequest = RestrictedAreaRequest.ADMIN },
                onSupervisor = { restrictedAreaRequest = RestrictedAreaRequest.SUPERVISOR },
                onAccess = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN },
            )
            KioskClock()
        }

        val challengeInstruction = when {
            !activeFallback -> "Olhe para a câmera"
            challengeCompleted -> "Olhe para a câmera"
            challenge == KioskLivenessChallenge.BLINK &&
                livenessState == LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
            livenessState == LivenessState.POSICIONE_ROSTO -> "Olhe para a câmera"
            else -> challenge.instruction
        }
        val recognitionBusy = state.recognitionStage != null || captureRequested || state.carregando
        val instructionTitle = when {
            cameraError != null -> "Câmera indisponível"
            !viewModel.faceModelReady -> "Reconhecimento indisponível"
            state.sincronizandoBiometrias && !state.catalogoBiometricoPronto -> "Preparando reconhecimento"
            !state.catalogoBiometricoPronto && state.erroSincronizacaoBiometrica != null -> "Rostos indisponíveis"
            state.catalogoBiometricoCarregado && !state.catalogoBiometricoPronto -> "Nenhum rosto disponível"
            !state.catalogoBiometricoPronto -> "Rostos ainda não sincronizados"
            recognitionBusy -> "Reconhecendo…"
            multipleFacesVisible -> "Apenas uma pessoa por vez"
            noFaceVisible -> "Aproxime-se da câmera"
            activeFallback && !challengeCompleted -> challengeInstruction
            lastCaptureRejection != null -> when (lastCaptureRejection) {
                FaceCaptureRejectionReason.FACE_TOO_SMALL -> "Aproxime-se um pouco"
                FaceCaptureRejectionReason.FACE_TOO_LARGE -> "Afaste-se um pouco"
                FaceCaptureRejectionReason.NOT_CENTERED -> "Centralize o rosto no guia"
                FaceCaptureRejectionReason.PARTIAL_FACE -> "Mostre o rosto inteiro"
                FaceCaptureRejectionReason.EXTREME_POSE -> "Olhe de frente para a tela"
                else -> "Olhe para a câmera"
            }
            else -> "Olhe para a câmera"
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
            recognitionBusy -> "Só mais um instante."
            multipleFacesVisible -> "Deixe somente uma pessoa dentro do enquadramento."
            noFaceVisible -> "Centralize o rosto dentro do guia para começar."
            activeFallback && !challengeCompleted ->
                "Precisamos de uma confirmação rápida. Siga apenas esta instrução."
            state.modoOffline -> "O registro ficará protegido neste aparelho até a conexão voltar."
            else -> "Olhe normalmente para a câmera. O ponto é registrado automaticamente."
        }

        val faceRecognitionError = state.erro?.startsWith(
            "ROSTO NÃO RECONHECIDO",
            ignoreCase = true,
        ) == true
        val voiceCue = when {
            !permissionGranted -> PontoVoiceKioskCue.CAMERA_PERMISSION_REQUIRED
            restrictedAreaRequest != null -> null
            cameraError != null -> PontoVoiceKioskCue.CAMERA_UNAVAILABLE
            !viewModel.faceModelReady -> PontoVoiceKioskCue.MODEL_UNAVAILABLE
            faceRecognitionError -> PontoVoiceKioskCue.FACE_NOT_RECOGNIZED
            state.comprovante != null || state.identificacao != null -> null
            !state.catalogoBiometricoPronto || state.carregando || captureRequested ||
                state.recognitionStage != null -> null
            multipleFacesVisible -> PontoVoiceKioskCue.MULTIPLE_FACES
            noFaceVisible -> PontoVoiceKioskCue.NO_FACE
            !activeFallback -> PontoVoiceKioskCue.LOOK_AT_CAMERA
            challengeCompleted -> null
            challenge == KioskLivenessChallenge.BLINK &&
                livenessState == LivenessState.ABRA_OS_OLHOS -> PontoVoiceKioskCue.OPEN_EYES
            livenessState == LivenessState.POSICIONE_ROSTO -> PontoVoiceKioskCue.LOOK_AT_CAMERA
            challenge == KioskLivenessChallenge.BLINK -> PontoVoiceKioskCue.BLINK
            challenge == KioskLivenessChallenge.TURN_LEFT -> PontoVoiceKioskCue.TURN_LEFT
            else -> PontoVoiceKioskCue.TURN_RIGHT
        }

        LaunchedEffect(state.scanCycle, voiceCue) {
            voiceCue?.let { cue ->
                val prompt = PontoVoicePromptPolicy.kiosk(cue)
                if (prompt.stabilityDelayMillis > 0L) {
                    delay(prompt.stabilityDelayMillis)
                }
                PontoVoiceRuntime.speak(
                    context = context,
                    prompt = prompt,
                    sessionKey = "scan:${state.scanCycle}",
                )
            }
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
    onLogin: () -> Unit,
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
                Text(
                    "Se este aparelho não tiver PIN configurado, use Entrar com conta para autenticar um Administrador ou Supervisor.",
                    style = MaterialTheme.typography.bodySmall,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !loading,
                ) {
                    Text("Cancelar")
                }
                TextButton(
                    onClick = onLogin,
                    enabled = !loading,
                ) {
                    Text("Entrar com conta")
                }
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
                        tint = DarkSemanticColors.success,
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
                    DarkSemanticColors.warning.copy(alpha = 0.14f)
                } else {
                    DarkSemanticColors.success.copy(alpha = 0.14f)
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
                        tint = if (offline) DarkSemanticColors.warning else DarkSemanticColors.success,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        if (offline) {
                            if (pendingEvents > 0) "Offline · $pendingEvents" else "Offline"
                        } else {
                            "Online"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (offline) DarkSemanticColors.warning else DarkSemanticColors.success,
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
private fun KioskClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(ZonedDateTime.now(KIOSK_ZONE)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(KIOSK_ZONE)
            delay(15_000L)
        }
    }
    val time = remember(now.hour, now.minute) {
        now.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val date = remember(now.dayOfYear) {
        now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("pt-BR")))
            .replaceFirstChar { it.uppercase() }
    }

    Surface(
        modifier = modifier.padding(top = 8.dp),
        color = Color(0xB3161B19),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                time,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                date,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
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
        multipleFaces -> DarkSemanticColors.critical
        ready -> DarkSemanticColors.success
        noFace -> DarkSemanticColors.warning
        loading -> DarkSemanticColors.info
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
                    color = DarkSemanticColors.criticalContainer,
                    contentColor = DarkSemanticColors.onCriticalContainer,
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
                            color = DarkSemanticColors.onCriticalContainer.copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }
    }
}
