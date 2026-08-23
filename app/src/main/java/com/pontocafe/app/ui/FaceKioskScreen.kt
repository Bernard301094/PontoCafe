package com.pontocafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.PontoRecognitionStage
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceCapturePurpose
import com.pontocafe.app.camera.FaceCaptureRejectionReason
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FaceTrackContinuity
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import com.pontocafe.app.camera.PassiveFaceLiveness
import com.pontocafe.app.camera.PassiveLivenessDecision
import com.pontocafe.app.camera.toPassiveLivenessSample
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.voice.PontoVoiceKioskCue
import com.pontocafe.app.voice.PontoVoicePromptPolicy
import com.pontocafe.app.voice.PontoVoiceRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class RestrictedAreaRequest { SUPERVISOR, ADMIN, LOGIN }

/**
 * Challenge ativo legado. Ele não é mais executado no fluxo normal: só entra
 * quando o liveness passivo RGB considera a sequência inconclusiva.
 */
private enum class KioskFallbackChallenge(val instruction: String) {
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

private const val FALLBACK_CHALLENGE_STABLE_FRAMES = 3
private const val RECOGNITION_STABLE_FRAMES_AFTER_LIVENESS = 1

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
    val passiveLiveness = remember { PassiveFaceLiveness() }
    val blinkLiveness = remember { BlinkLiveness() }
    val fallbackContinuity = remember { FaceTrackContinuity() }

    var verificationCompleted by remember { mutableStateOf(false) }
    var completedByFallback by remember { mutableStateOf(false) }
    var activeFallback by remember { mutableStateOf(false) }
    var fallbackChallenge by remember { mutableStateOf(KioskFallbackChallenge.BLINK) }
    var fallbackLivenessState by remember { mutableStateOf(LivenessState.POSICIONE_ROSTO) }
    val fallbackStableFrames = remember { intArrayOf(0) }
    val recognitionStableFrames = remember { intArrayOf(0) }

    var captureRequested by remember { mutableStateOf(false) }
    var detectedFaces by remember { mutableStateOf(0) }
    var facePositioned by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var restrictedAreaRequest by remember { mutableStateOf<RestrictedAreaRequest?>(null) }
    var exitPin by remember { mutableStateOf("") }
    var unlockLoading by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    fun resetLiveness() {
        passiveLiveness.reset()
        blinkLiveness.reset()
        fallbackContinuity.reset()
        verificationCompleted = false
        completedByFallback = false
        activeFallback = false
        fallbackChallenge = KioskFallbackChallenge.BLINK
        fallbackLivenessState = LivenessState.POSICIONE_ROSTO
        fallbackStableFrames[0] = 0
        recognitionStableFrames[0] = 0
        captureRequested = false
    }

    fun startFallback(observation: FaceObservation) {
        activeFallback = true
        verificationCompleted = false
        completedByFallback = false
        fallbackStableFrames[0] = 0
        recognitionStableFrames[0] = 0
        blinkLiveness.reset()
        fallbackContinuity.reset()
        fallbackChallenge = if (observation.eyeClassificationAvailable) {
            KioskFallbackChallenge.BLINK
        } else {
            listOf(KioskFallbackChallenge.TURN_LEFT, KioskFallbackChallenge.TURN_RIGHT).random()
        }
        fallbackLivenessState = LivenessState.POSICIONE_ROSTO
    }

    fun completeVerification(byFallback: Boolean) {
        verificationCompleted = true
        completedByFallback = byFallback
        recognitionStableFrames[0] = 0
        fallbackLivenessState = LivenessState.CONCLUIDO
    }

    fun sameVerifiedFace(observation: FaceObservation): Boolean {
        val sample = observation.toPassiveLivenessSample()
        return if (!completedByFallback) {
            passiveLiveness.matchesAcceptedFace(sample)
        } else if (fallbackChallenge == KioskFallbackChallenge.BLINK) {
            blinkLiveness.matchesChallengeFace(observation)
        } else {
            fallbackContinuity.matches(observation)
        }
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
        resetLiveness()
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

                    if (observation.faceCount != 1) {
                        if (verificationCompleted || activeFallback) resetLiveness()
                        return@FaceCameraPreview
                    }

                    if (
                        !state.scanning || !state.catalogoBiometricoPronto ||
                        state.carregando || captureRequested
                    ) {
                        return@FaceCameraPreview
                    }

                    if (!verificationCompleted && !activeFallback) {
                        when (passiveLiveness.update(observation.toPassiveLivenessSample())) {
                            PassiveLivenessDecision.PASSED -> completeVerification(byFallback = false)
                            PassiveLivenessDecision.ACTIVE_CHALLENGE_REQUIRED -> startFallback(observation)
                            PassiveLivenessDecision.WAITING_FOR_FACE,
                            PassiveLivenessDecision.OBSERVING -> Unit
                        }
                    }

                    if (!verificationCompleted && activeFallback) {
                        when (fallbackChallenge) {
                            KioskFallbackChallenge.BLINK -> {
                                val next = blinkLiveness.update(observation)
                                fallbackLivenessState = next
                                if (next == LivenessState.CONCLUIDO) {
                                    completeVerification(byFallback = true)
                                }
                            }

                            KioskFallbackChallenge.TURN_LEFT,
                            KioskFallbackChallenge.TURN_RIGHT -> {
                                fallbackLivenessState = if (observation.isWellPositioned) {
                                    LivenessState.PISQUE
                                } else {
                                    LivenessState.POSICIONE_ROSTO
                                }
                                if (fallbackChallenge.accepts(observation)) {
                                    if (fallbackStableFrames[0] == 0) {
                                        fallbackContinuity.bind(observation)
                                        fallbackStableFrames[0] = 1
                                    } else if (fallbackContinuity.matches(observation)) {
                                        fallbackStableFrames[0] += 1
                                    } else {
                                        fallbackContinuity.bind(observation)
                                        fallbackStableFrames[0] = 1
                                    }
                                    if (fallbackStableFrames[0] >= FALLBACK_CHALLENGE_STABLE_FRAMES) {
                                        completeVerification(byFallback = true)
                                    }
                                } else {
                                    fallbackStableFrames[0] = 0
                                }
                            }
                        }
                    }

                    if (verificationCompleted) {
                        val sameFace = sameVerifiedFace(observation)
                        if (!sameFace) {
                            resetLiveness()
                            return@FaceCameraPreview
                        }
                        if (observation.isIdentificationReady) {
                            recognitionStableFrames[0] += 1
                            if (recognitionStableFrames[0] >= RECOGNITION_STABLE_FRAMES_AFTER_LIVENESS) {
                                captureRequested = true
                                captureController.request(observation, FaceCapturePurpose.IDENTIFICATION)
                            }
                        } else {
                            recognitionStableFrames[0] = 0
                        }
                    }
                },
                onFrame = { frame ->
                    viewModel.processarFrame(frame)
                    captureRequested = false
                    recognitionStableFrames[0] = 0
                },
                onCaptureRejected = { reason ->
                    captureRequested = false
                    recognitionStableFrames[0] = 0
                    when (reason) {
                        FaceCaptureRejectionReason.REQUEST_EXPIRED,
                        FaceCaptureRejectionReason.POSE_CHANGED,
                        FaceCaptureRejectionReason.NOT_CENTERED,
                        FaceCaptureRejectionReason.FACE_TOO_SMALL,
                        FaceCaptureRejectionReason.FACE_TOO_LARGE,
                        FaceCaptureRejectionReason.EXTREME_POSE -> {
                            // Variação transitória: preserva o liveness já concluído
                            // e tenta outro frame útil da mesma pessoa imediatamente.
                        }

                        FaceCaptureRejectionReason.NO_FACE,
                        FaceCaptureRejectionReason.MULTIPLE_FACES,
                        FaceCaptureRejectionReason.PARTIAL_FACE,
                        FaceCaptureRejectionReason.TRACK_CHANGED,
                        FaceCaptureRejectionReason.LANDMARKS_MISSING,
                        FaceCaptureRejectionReason.EYES_NOT_VISIBLE -> resetLiveness()
                    }
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

        val noFaceVisible = state.scanning && state.catalogoBiometricoPronto &&
            !state.carregando && detectedFaces == 0
        val multipleFacesVisible = state.scanning && detectedFaces > 1
        val recognitionReady = verificationCompleted && !captureRequested &&
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

        val fallbackInstruction = when {
            fallbackChallenge == KioskFallbackChallenge.BLINK &&
                fallbackLivenessState == LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
            fallbackLivenessState == LivenessState.POSICIONE_ROSTO -> "Olhe para a câmera"
            else -> fallbackChallenge.instruction
        }

        val instructionTitle = when {
            cameraError != null -> "Câmera indisponível"
            !viewModel.faceModelReady -> "Reconhecimento indisponível"
            state.sincronizandoBiometrias && !state.catalogoBiometricoPronto -> "Preparando reconhecimento"
            !state.catalogoBiometricoPronto && state.erroSincronizacaoBiometrica != null -> "Rostos indisponíveis"
            state.catalogoBiometricoCarregado && !state.catalogoBiometricoPronto -> "Nenhum rosto disponível"
            !state.catalogoBiometricoPronto -> "Rostos ainda não sincronizados"
            state.recognitionStage == PontoRecognitionStage.REGISTRANDO_PONTO -> "Registrando ponto…"
            state.recognitionStage != null || state.carregando || captureRequested -> "Reconhecendo…"
            multipleFacesVisible -> "Apenas uma pessoa por vez"
            activeFallback && !verificationCompleted -> "Só mais uma verificação"
            noFaceVisible -> "Olhe para a câmera"
            !facePositioned && detectedFaces == 1 -> "Centralize o rosto"
            else -> "Olhe para a câmera"
        }

        val instructionDetail = when {
            cameraError != null -> cameraError.orEmpty()
            !viewModel.faceModelReady -> "O modelo facial precisa estar disponível neste APK."
            state.sincronizandoBiometrias && !state.catalogoBiometricoPronto ->
                "Carregando os rostos deste dispositivo."
            !state.catalogoBiometricoPronto && state.erroSincronizacaoBiometrica != null ->
                state.erroSincronizacaoBiometrica.orEmpty()
            state.catalogoBiometricoCarregado && !state.catalogoBiometricoPronto ->
                "O catálogo está sincronizado, mas não contém rostos ativos compatíveis."
            !state.catalogoBiometricoPronto -> "Abra Admin ou Supervisor para cadastrar e sincronizar os rostos."
            state.recognitionStage == PontoRecognitionStage.REGISTRANDO_PONTO ->
                "Identidade confirmada. Aguarde o resultado."
            state.recognitionStage != null || state.carregando || captureRequested ->
                "Mantenha-se olhando para a câmera por um instante."
            multipleFacesVisible -> "Deixe somente uma pessoa diante da câmera."
            activeFallback && !verificationCompleted ->
                "$fallbackInstruction. Esta etapa só aparece quando a verificação automática precisa de confirmação extra."
            noFaceVisible -> "Aproxime-se. O reconhecimento começa automaticamente."
            detectedFaces == 1 && !facePositioned ->
                "Deixe olhos, nariz e o centro do rosto visíveis. Touca ou boné podem permanecer se não cobrirem o rosto."
            state.modoOffline ->
                "Não é preciso piscar nem virar a cabeça. O registro será sincronizado quando a conexão voltar."
            else -> "Não é preciso piscar nem virar a cabeça. Apenas olhe de frente; o registro é automático."
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
            activeFallback && !verificationCompleted -> when (fallbackChallenge) {
                KioskFallbackChallenge.BLINK -> if (fallbackLivenessState == LivenessState.ABRA_OS_OLHOS) {
                    PontoVoiceKioskCue.OPEN_EYES
                } else {
                    PontoVoiceKioskCue.BLINK
                }
                KioskFallbackChallenge.TURN_LEFT -> PontoVoiceKioskCue.TURN_LEFT
                KioskFallbackChallenge.TURN_RIGHT -> PontoVoiceKioskCue.TURN_RIGHT
            }
            noFaceVisible -> PontoVoiceKioskCue.NO_FACE
            else -> PontoVoiceKioskCue.LOOK_AT_CAMERA
        }

        LaunchedEffect(state.scanCycle, voiceCue) {
            voiceCue?.let { cue ->
                val prompt = PontoVoicePromptPolicy.kiosk(cue)
                if (prompt.stabilityDelayMillis > 0L) delay(prompt.stabilityDelayMillis)
                PontoVoiceRuntime.speak(
                    context = context,
                    prompt = prompt,
                    sessionKey = "scan:${state.scanCycle}",
                )
            }
        }

        if (permissionGranted) {
            KioskInstructionPanel(
                title = instructionTitle,
                detail = instructionDetail,
                multipleFaces = multipleFacesVisible,
                ready = recognitionReady,
                error = cameraError ?: state.erro ?:
                    state.erroSincronizacaoBiometrica.takeIf { !state.catalogoBiometricoPronto },
                compact = compactHeight,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
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
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFF72DCBC))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Ponto Café", fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Reconhecimento facial automático",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.64f),
                )
            }

            Surface(
                color = if (offline) Color(0x33FFC867) else Color(0x3372DCBC),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (offline) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (offline) Color(0xFFFFC867) else Color(0xFF72DCBC),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        if (offline && pendingEvents > 0) "Offline · $pendingEvents" else if (offline) "Offline" else "Online",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (offline) Color(0xFFFFC867) else Color(0xFF72DCBC),
                    )
                }
            }

            if (hasAdminSession) {
                IconButton(onClick = onAdmin) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = "Abrir Administrador")
                }
            }
            if (hasSupervisorSession) {
                IconButton(onClick = onSupervisor) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = "Abrir Supervisor")
                }
            }
            if (!hasAdminSession && !hasSupervisorSession) {
                IconButton(onClick = onAccess) {
                    Icon(Icons.Default.Lock, contentDescription = "Acesso restrito")
                }
            }
        }
    }
}

@Composable
private fun KioskInstructionPanel(
    title: String,
    detail: String,
    multipleFaces: Boolean,
    ready: Boolean,
    error: String?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        error != null || multipleFaces -> Color(0xFFFFB4AB)
        ready -> Color(0xFF72DCBC)
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
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = if (compact) 170.dp else 260.dp)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                if (error != null && !error.startsWith("ROSTO NÃO RECONHECIDO", ignoreCase = true)) error else detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
            if (error?.startsWith("ROSTO NÃO RECONHECIDO", ignoreCase = true) == true) {
                Text(
                    "Rosto não reconhecido. Olhe de frente e tente novamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFDAD6),
                )
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("Acesso restrito · $destinationName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                Text(
                    "Digite o PIN deste dispositivo ou use Entrar com conta.",
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
            Button(
                onClick = onConfirm,
                enabled = !loading && pin.length in 4..12,
            ) {
                Text(if (loading) "Validando…" else "Desbloquear")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
                TextButton(onClick = onLogin, enabled = !loading) { Text("Entrar com conta") }
            }
        },
    )
}
