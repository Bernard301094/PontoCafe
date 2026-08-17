package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
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
                    if (detectedFaces != observation.faceCount) {
                        detectedFaces = observation.faceCount
                    }
                    if (
                        state.scanning && state.catalogoBiometricoPronto &&
                        !state.sincronizandoBiometrias && !state.carregando && !captureRequested
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
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF21A1F1D),
                contentColor = Color.White,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
                ) {
                    Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.10f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Face, contentDescription = null, tint = Color.White)
                        }
                    }
                    Text(
                        "Acesso à câmera",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "A câmera é necessária para registrar o Ponto Café com reconhecimento facial.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.74f),
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Permitir câmera")
                    }
                }
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

        KioskTopBar(
            offline = state.modoOffline,
            pendingEvents = state.eventosPendentes,
            totalBiometrics = state.totalBiometrias,
            hasAdminSession = hasAdminSession,
            hasSupervisorSession = hasSupervisorSession,
            onAdmin = { restrictedAreaRequest = RestrictedAreaRequest.ADMIN },
            onSupervisor = { restrictedAreaRequest = RestrictedAreaRequest.SUPERVISOR },
            onAccess = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN },
            modifier = Modifier.align(Alignment.TopCenter),
        )

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
            error = state.erro,
            onSyncCatalog = { viewModel.sincronizarBiometrias(force = true) },
            onSyncPending = viewModel::sincronizarPendenciasOffline,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun KioskTopBar(
    offline: Boolean,
    pendingEvents: Int,
    totalBiometrics: Int,
    hasAdminSession: Boolean,
    hasSupervisorSession: Boolean,
    onAdmin: () -> Unit,
    onSupervisor: () -> Unit,
    onAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = Color(0xE8161B19),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (offline) Color(0xFFFFC867).copy(alpha = 0.14f) else Color(0xFF72DCBC).copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (offline) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (offline) Color(0xFFFFC867) else Color(0xFF72DCBC),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Ponto Café",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                val faceCountLabel = if (totalBiometrics == 1) "1 rosto pronto" else "$totalBiometrics rostos prontos"
                Text(
                    if (offline) "Offline · $pendingEvents pendente(s)" else "Online · $faceCountLabel",
                    color = Color.White.copy(alpha = 0.66f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasAdminSession) {
                FilledTonalButton(onClick = onAdmin) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Admin", modifier = Modifier.padding(start = 5.dp))
                }
            }
            if (hasSupervisorSession) {
                FilledTonalButton(onClick = onSupervisor) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Supervisor", modifier = Modifier.padding(start = 5.dp))
                }
            }
            if (!hasAdminSession && !hasSupervisorSession) {
                FilledTonalButton(onClick = onAccess) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Acesso", modifier = Modifier.padding(start = 5.dp))
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
    error: String?,
    onSyncCatalog: () -> Unit,
    onSyncPending: () -> Unit,
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
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        color = Color(0xF5161B19),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = accent,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.70f),
            )

            if (updateRequired) {
                StatusPill("Atualização obrigatória · ${latestVersion ?: "nova versão"}", PontoCafeTone.DANGER)
            } else if (updateAvailable) {
                StatusPill("Nova versão · $latestVersion", PontoCafeTone.INFO)
            }
            if (offline) StatusPill("Modo offline seguro", PontoCafeTone.WARNING)

            if (!catalogReady && !syncingCatalog && modelReady) {
                Button(onClick = onSyncCatalog) { Text("Sincronizar rostos") }
            }
            if (pendingEvents > 0 && !offline) {
                OutlinedButton(onClick = onSyncPending) {
                    Text(if (syncingPending) "Sincronizando..." else "Sincronizar $pendingEvents pendente(s)")
                }
            }

            error?.let { message ->
                val faceNotRecognized = message.startsWith("ROSTO NÃO RECONHECIDO", ignoreCase = true)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = Color(0xFF3A1D1A),
                    contentColor = Color(0xFFFFDAD6),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
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
                                message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
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
        animationSpec = tween(PontoCafeMotion.Standard, easing = PontoCafeMotion.EmphasizedEasing),
        label = "kiosk-guide-color",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.70f)
            .aspectRatio(0.80f),
    ) {
        val stroke = (if (ready || warning) 4.2.dp else 3.4.dp).toPx()
        val cornerLength = size.minDimension * if (ready) 0.24f else 0.20f
        val inset = stroke
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset

        drawLine(guideColor, Offset(left, top + cornerLength), Offset(left, top), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(left, top), Offset(left + cornerLength, top), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(right - cornerLength, top), Offset(right, top), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(right, top), Offset(right, top + cornerLength), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(left, bottom - cornerLength), Offset(left, bottom), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(left, bottom), Offset(left + cornerLength, bottom), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(right - cornerLength, bottom), Offset(right, bottom), stroke, cap = StrokeCap.Round)
        drawLine(guideColor, Offset(right, bottom), Offset(right, bottom - cornerLength), stroke, cap = StrokeCap.Round)
    }
}
