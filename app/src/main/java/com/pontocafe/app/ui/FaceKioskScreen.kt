package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    BLINK("Pisque para confirmar presença"),
    TURN_LEFT("Vire levemente o rosto para a esquerda"),
    TURN_RIGHT("Vire levemente o rosto para a direita"),
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
        captureRequested = false
        detectedFaces = 0
        livenessState = LivenessState.POSICIONE_ROSTO
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                onObservation = { observation ->
                    detectedFaces = observation.faceCount
                    if (
                        state.scanning && state.catalogoBiometricoPronto &&
                        !state.sincronizandoBiometrias && !state.carregando && !captureRequested
                    ) {
                        if (challenge == KioskLivenessChallenge.BLINK) {
                            val next = liveness.update(observation)
                            livenessState = next
                            if (next == LivenessState.CONCLUIDO) {
                                captureRequested = true
                                captureController.request()
                            }
                        } else {
                            livenessState = if (observation.isWellPositioned) LivenessState.PISQUE else LivenessState.POSICIONE_ROSTO
                            if (challenge.accepts(observation)) {
                                stableChallengeFrames[0] += 1
                                if (stableChallengeFrames[0] >= 4) {
                                    livenessState = LivenessState.CONCLUIDO
                                    captureRequested = true
                                    captureController.request()
                                }
                            } else {
                                stableChallengeFrames[0] = 0
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

        KioskFaceGuide(
            active = permissionGranted && state.catalogoBiometricoPronto,
            warning = detectedFaces > 1,
            modifier = Modifier.align(Alignment.Center),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = PontoCafeSpacing.sm, vertical = PontoCafeSpacing.xs)
                .align(Alignment.TopCenter),
            color = Color(0xCC101513),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PontoCafeSpacing.md, vertical = PontoCafeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ponto Café",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                    Text(
                        if (state.modoOffline) {
                            "Offline · ${state.eventosPendentes} pendente(s)"
                        } else {
                            "Online · ${state.totalBiometrias} rostos prontos"
                        },
                        color = if (state.modoOffline) Color(0xFFF7C66C) else Color(0xFF8DD4C2),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasAdminSession) {
                    TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.ADMIN }) {
                        Text("Admin", color = Color.White)
                    }
                }
                if (hasSupervisorSession) {
                    TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.SUPERVISOR }) {
                        Text("Supervisor", color = Color.White)
                    }
                }
                if (!hasAdminSession && !hasSupervisorSession) {
                    TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN }) {
                        Text("Acesso", color = Color.White)
                    }
                }
            }
        }

        val noFaceVisible = state.scanning && state.catalogoBiometricoPronto &&
            !state.sincronizandoBiometrias && !state.carregando && detectedFaces == 0
        val multipleFacesVisible = state.scanning && detectedFaces > 1
        val challengeInstruction = when {
            livenessState == LivenessState.CONCLUIDO -> "Presença confirmada"
            challenge == KioskLivenessChallenge.BLINK && livenessState == LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
            livenessState == LivenessState.POSICIONE_ROSTO -> "Olhe para a câmera"
            else -> challenge.instruction
        }
        val instructionTitle = when {
            !viewModel.faceModelReady -> "Reconhecimento indisponível"
            state.sincronizandoBiometrias -> "Preparando reconhecimento"
            !state.catalogoBiometricoPronto -> "Cadastre os rostos para começar"
            state.carregando -> "Confirmando identidade"
            noFaceVisible -> "Aproxime o rosto"
            multipleFacesVisible -> "Apenas uma pessoa por vez"
            else -> challengeInstruction
        }
        val instructionDetail = when {
            !viewModel.faceModelReady -> "O modelo facial precisa estar disponível neste APK."
            state.sincronizandoBiometrias -> "Sincronizando o catálogo facial deste dispositivo."
            !state.catalogoBiometricoPronto -> "Abra Admin ou Supervisor e registre a biometria dos colaboradores."
            state.carregando -> if (state.modoOffline) "Validando localmente com segurança." else "Validando o reconhecimento."
            noFaceVisible -> "Centralize todo o rosto dentro do quadro."
            multipleFacesVisible -> "Peça para as demais pessoas se afastarem da câmera."
            state.modoOffline -> "O registro ficará cifrado no aparelho e será sincronizado quando a conexão voltar."
            else -> "Mantenha aproximadamente 40 cm de distância."
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(PontoCafeSpacing.md)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(PontoCafeSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                Text(
                    instructionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = when {
                        multipleFacesVisible -> MaterialTheme.colorScheme.error
                        noFaceVisible -> LocalPontoCafeSemanticColors.current.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    instructionDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            error,
                            modifier = Modifier.padding(PontoCafeSpacing.sm),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
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
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        warning -> MaterialTheme.colorScheme.error
        active -> Color.White.copy(alpha = 0.9f)
        else -> Color.White.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .aspectRatio(0.78f)
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(40.dp)),
    )
}
