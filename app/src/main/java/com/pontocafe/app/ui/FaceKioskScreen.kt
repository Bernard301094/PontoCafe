package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceObservation
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
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionGranted = it },
    )

    val captureController = remember { FrameCaptureController() }
    val liveness = remember { BlinkLiveness() }
    var livenessState by remember { mutableStateOf(LivenessState.POSICIONE_ROSTO) }
    var challenge by remember { mutableStateOf(KioskLivenessChallenge.entries.random()) }
    val stableChallengeFrames = remember { intArrayOf(0) }
    val stableRecognitionFrames = remember { intArrayOf(0) }
    val blinkPendingFrames = remember { intArrayOf(0) }
    var challengeAdjustedForEyes by remember { mutableStateOf(false) }
    var challengeCompleted by remember { mutableStateOf(false) }
    var captureRequested by remember { mutableStateOf(false) }
    var detectedFaces by remember { mutableStateOf(0) }
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
        val instruction = when (target) {
            RestrictedAreaRequest.ADMIN -> "Informe o PIN deste dispositivo para abrir o perfil Administrador já salvo."
            RestrictedAreaRequest.SUPERVISOR -> "Informe o PIN deste dispositivo para abrir o perfil Supervisor já salvo."
            RestrictedAreaRequest.LOGIN -> "Informe o PIN deste dispositivo para sair do modo Ponto e abrir o início de sessão."
        }
        AlertDialog(
            onDismissRequest = { if (!unlockLoading) fecharSolicitacaoAcesso() },
            title = { Text("Desbloquear área restrita") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm)) {
                    Text(instruction)
                    OutlinedTextField(
                        value = exitPin,
                        onValueChange = { value ->
                            exitPin = value.filter(Char::isDigit).take(12)
                            unlockError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN do dispositivo") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = unlockError != null,
                        supportingText = { Text(unlockError ?: "4 a 12 números") },
                        enabled = !unlockLoading,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !unlockLoading && exitPin.length in 4..12,
                    onClick = {
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
                ) { Text(if (unlockLoading) "Verificando..." else "Desbloquear") }
            },
            dismissButton = {
                TextButton(onClick = { fecharSolicitacaoAcesso() }, enabled = !unlockLoading) { Text("Cancelar") }
            },
        )
    }

    LaunchedEffect(state.scanCycle) {
        liveness.reset()
        challenge = KioskLivenessChallenge.entries.random()
        stableChallengeFrames[0] = 0
        stableRecognitionFrames[0] = 0
        blinkPendingFrames[0] = 0
        challengeAdjustedForEyes = false
        challengeCompleted = false
        captureRequested = false
        detectedFaces = 0
        livenessState = LivenessState.POSICIONE_ROSTO
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                showPositionGuide = false,
                onObservation = { observation ->
                    detectedFaces = observation.faceCount
                    if (
                        state.scanning && state.catalogoBiometricoPronto &&
                        !state.sincronizandoBiometrias && !state.carregando && !captureRequested
                    ) {
                        if (!challengeCompleted) {
                            if (challenge == KioskLivenessChallenge.BLINK) {
                                val next = liveness.update(observation)
                                livenessState = next

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
                                    // Reflexos, lentes grossas ou algumas armações podem impedir
                                    // o ML Kit de estimar a abertura dos olhos de forma estável.
                                    // Mantemos a prova de vida, mas trocamos automaticamente o
                                    // desafio por movimento de cabeça em vez de bloquear a pessoa.
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
                                livenessState = if (observation.isWellPositioned) {
                                    LivenessState.PISQUE
                                } else {
                                    LivenessState.POSICIONE_ROSTO
                                }
                                if (challenge.accepts(observation)) {
                                    stableChallengeFrames[0] += 1
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
                            // A prova de vida pode exigir o rosto de lado. O embedding usado
                            // para reconhecer a pessoa, porém, só é capturado depois que ela
                            // volta a olhar de frente e permanece estável por alguns frames.
                            if (observation.isFrontal) {
                                stableRecognitionFrames[0] += 1
                                if (stableRecognitionFrames[0] >= RECOGNITION_STABLE_FRAMES) {
                                    captureRequested = true
                                    captureController.request()
                                }
                            } else {
                                stableRecognitionFrames[0] = 0
                            }
                        }
                    }
                },
                onFrame = viewModel::processarFrame,
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
            ) {
                Text("A câmera é necessária para registrar o Ponto Café.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            }
        }

        val noFaceVisible = state.scanning && state.catalogoBiometricoPronto &&
            !state.sincronizandoBiometrias && !state.carregando && detectedFaces == 0
        val multipleFacesVisible = state.scanning && detectedFaces > 1
        val recognitionReady = challengeCompleted && !state.carregando && !multipleFacesVisible

        KioskFaceGuide(
            active = permissionGranted && state.catalogoBiometricoPronto,
            warning = multipleFacesVisible,
            ready = recognitionReady,
            modifier = Modifier.align(Alignment.Center),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            color = Color(0xC9141917),
            contentColor = Color.White,
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ponto Café",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val faceCountLabel = if (state.totalBiometrias == 1) {
                        "1 rosto pronto"
                    } else {
                        "${state.totalBiometrias} rostos prontos"
                    }
                    Text(
                        if (state.modoOffline) {
                            "Offline · ${state.eventosPendentes} pendente(s)"
                        } else {
                            "Online · $faceCountLabel"
                        },
                        color = if (state.modoOffline) Color(0xFFFFD27D) else Color(0xFF82E2C4),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasAdminSession) {
                    TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.ADMIN }) {
                        Text("Admin", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (hasSupervisorSession) {
                    TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.SUPERVISOR }) {
                        Text("Supervisor", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!hasAdminSession && !hasSupervisorSession) {
                    TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN }) {
                        Text("Acesso", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val challengeInstruction = when {
            challengeCompleted -> "Volte ao centro"
            challenge == KioskLivenessChallenge.BLINK && livenessState == LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
            livenessState == LivenessState.POSICIONE_ROSTO -> "Olhe para a câmera"
            else -> challenge.instruction
        }
        val instructionTitle = when {
            !viewModel.faceModelReady -> "Reconhecimento indisponível"
            state.sincronizandoBiometrias -> "Preparando reconhecimento"
            !state.catalogoBiometricoPronto -> "Rostos ainda não sincronizados"
            state.carregando -> "Confirmando identidade"
            multipleFacesVisible -> "Apenas uma pessoa por vez"
            noFaceVisible -> "Posicione seu rosto"
            challengeCompleted -> "Olhe de frente"
            else -> challengeInstruction
        }
        val instructionDetail = when {
            !viewModel.faceModelReady -> "O modelo facial precisa estar disponível neste APK."
            state.sincronizandoBiometrias -> "Sincronizando o catálogo facial deste dispositivo."
            !state.catalogoBiometricoPronto -> "Abra Admin ou Supervisor para cadastrar e sincronizar os rostos."
            state.carregando -> "Aguarde um instante enquanto validamos seu rosto."
            multipleFacesVisible -> "Deixe somente uma pessoa visível na câmera."
            noFaceVisible -> "Centralize o rosto dentro do guia."
            challengeCompleted -> "Mantenha o rosto reto por um instante. A captura é automática."
            challengeAdjustedForEyes -> "O piscar não ficou nítido. Siga o movimento de cabeça indicado; ele funciona melhor com reflexos ou óculos."
            state.modoOffline -> "O registro será protegido no aparelho e sincronizado quando a conexão voltar."
            else -> "Siga a instrução e mantenha cerca de 40 cm de distância."
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .align(Alignment.BottomCenter),
            color = Color(0xDD121714),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    instructionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = when {
                        multipleFacesVisible -> Color(0xFFFF9B9B)
                        challengeCompleted -> Color(0xFF82E2C4)
                        noFaceVisible -> Color(0xFFFFD27D)
                        else -> Color.White
                    },
                )
                Text(
                    instructionDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.76f),
                )

                if (state.atualizacaoObrigatoria) {
                    StatusPill("Atualização obrigatória · ${state.versaoMaisRecente ?: "nova versão"}", PontoCafeTone.DANGER)
                } else if (state.atualizacaoDisponivel) {
                    StatusPill("Nova versão · ${state.versaoMaisRecente}", PontoCafeTone.INFO)
                }

                if (state.modoOffline) {
                    StatusPill("Modo offline seguro", PontoCafeTone.WARNING)
                }

                if (!state.catalogoBiometricoPronto && !state.sincronizandoBiometrias && viewModel.faceModelReady) {
                    Button(onClick = { viewModel.sincronizarBiometrias(force = true) }) { Text("Sincronizar rostos") }
                }
                if (state.eventosPendentes > 0 && !state.modoOffline) {
                    OutlinedButton(onClick = viewModel::sincronizarPendenciasOffline) {
                        Text(if (state.sincronizandoPendencias) "Sincronizando..." else "Sincronizar ${state.eventosPendentes} pendente(s)")
                    }
                }

                state.erro?.let { error ->
                    val faceNotRecognized = error.startsWith("ROSTO NÃO RECONHECIDO", ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = if (faceNotRecognized) Color(0xCC6D211E) else Color(0xCC7C2323),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                if (faceNotRecognized) "Rosto não reconhecido" else "Não foi possível continuar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                if (faceNotRecognized) {
                                    "Olhe de frente, mantenha o rosto no guia e tente novamente."
                                } else {
                                    error
                                },
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.86f),
                            )
                        }
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
    modifier: Modifier = Modifier,
) {
    val targetColor = when {
        warning -> Color(0xFFFF8A80)
        ready -> Color(0xFF79E5C2)
        active -> Color.White.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.38f)
    }
    val guideColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.EmphasizedEasing),
        label = "kiosk-guide-color",
    )
    val guideScale = remember { Animatable(1f) }

    LaunchedEffect(active, warning, ready) {
        val searching = active && !warning && !ready
        if (!searching) {
            guideScale.animateTo(
                targetValue = when {
                    ready -> 1.022f
                    warning -> 0.986f
                    else -> 1f
                },
                animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.EmphasizedEasing),
            )
        } else {
            while (true) {
                guideScale.animateTo(
                    targetValue = 1.018f,
                    animationSpec = tween(900, easing = PontoCafeMotion.StandardEasing),
                )
                guideScale.animateTo(
                    targetValue = 0.988f,
                    animationSpec = tween(900, easing = PontoCafeMotion.StandardEasing),
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(0.80f)
            .graphicsLayer {
                scaleX = guideScale.value
                scaleY = guideScale.value
            },
    ) {
        val stroke = (if (ready || warning) 4.6.dp else 4.dp).toPx()
        val cornerLength = size.minDimension * if (ready) 0.23f else 0.20f
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
