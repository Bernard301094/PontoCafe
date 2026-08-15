package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import com.pontocafe.app.data.ApiClient
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import kotlinx.coroutines.launch

private enum class RestrictedAreaRequest { SUPERVISOR, ADMIN, LOGIN }

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
        ApiClient.create(
            context.applicationContext,
            SecureDeviceTokenStore(context.applicationContext),
        )
    }
    val state = viewModel.state
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionGranted = it },
    )

    val captureController = remember { FrameCaptureController() }
    val liveness = remember { BlinkLiveness() }
    var livenessState by remember { mutableStateOf(LivenessState.POSICIONE_ROSTO) }
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
            title = { Text("Sair do modo Ponto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(instruction)
                    OutlinedTextField(
                        value = exitPin,
                        onValueChange = { value ->
                            exitPin = value.filter(Char::isDigit).take(12)
                            unlockError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN de desbloqueio") },
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

    LaunchedEffect(Unit) {
        viewModel.sincronizarBiometrias(force = false)
        viewModel.atualizarConectividadeESincronizar()
    }

    LaunchedEffect(state.scanCycle) {
        liveness.reset()
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
                        !state.sincronizandoBiometrias && !state.carregando
                    ) {
                        val next = liveness.update(observation)
                        livenessState = next
                        if (next == LivenessState.CONCLUIDO && !captureRequested) {
                            captureRequested = true
                            captureController.request()
                        }
                    }
                },
                onFrame = viewModel::processarFrame,
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("A câmera é necessária para registrar o Ponto Café.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.68f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Ponto Café",
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hasAdminSession) {
                        TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.ADMIN }) {
                            Text("Admin", color = Color.White, maxLines = 1)
                        }
                    }
                    if (hasSupervisorSession) {
                        TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.SUPERVISOR }) {
                            Text("Supervisor", color = Color.White, maxLines = 1)
                        }
                    }
                    if (!hasAdminSession && !hasSupervisorSession) {
                        TextButton(onClick = { restrictedAreaRequest = RestrictedAreaRequest.LOGIN }) {
                            Text("Sair do Ponto", color = Color.White, maxLines = 1)
                        }
                    }
                }
                Text(
                    if (state.modoOffline) {
                        "● OFFLINE · ${state.totalBiometrias} rostos · ${state.eventosPendentes} registro(s) pendente(s)"
                    } else {
                        "● Online · ${state.totalBiometrias} rostos · ${state.eventosPendentes} pendente(s)"
                    },
                    color = if (state.modoOffline) Color(0xFFFFD18A) else Color(0xFFB7F5D9),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp).align(Alignment.BottomCenter),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val noFaceVisible = state.scanning && state.catalogoBiometricoPronto &&
                    !state.sincronizandoBiometrias && !state.carregando && detectedFaces == 0
                val multipleFacesVisible = state.scanning && detectedFaces > 1

                Text(
                    text = when {
                        !viewModel.faceModelReady -> "Reconhecimento facial indisponível"
                        state.sincronizandoBiometrias -> "Sincronizando rostos cadastrados..."
                        !state.catalogoBiometricoPronto -> "Nenhum rosto disponível para reconhecimento"
                        state.carregando -> "Confirmando sua identidade..."
                        noFaceVisible -> "ROSTO NÃO DETECTADO"
                        multipleFacesVisible -> "MAIS DE UM ROSTO DETECTADO"
                        else -> when (livenessState) {
                            LivenessState.POSICIONE_ROSTO -> "Posicione o rosto dentro do contorno"
                            LivenessState.PISQUE -> "Pisque para confirmar presença"
                            LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
                            LivenessState.CONCLUIDO -> "Rosto capturado"
                        }
                    },
                    color = if (noFaceVisible || multipleFacesVisible) MaterialTheme.colorScheme.error else Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        !viewModel.faceModelReady -> "O modelo facial precisa estar disponível no APK."
                        state.sincronizandoBiometrias -> "Aguarde alguns segundos."
                        !state.catalogoBiometricoPronto -> "Cadastre rostos como Administrador ou Supervisor e toque em sincronizar."
                        state.carregando -> if (state.modoOffline) "Validando localmente neste aparelho." else "O candidato foi encontrado localmente e está sendo validado."
                        noFaceVisible -> "Não conseguimos localizar um rosto. Encaixe todo o rosto dentro da figura na tela e olhe para a câmera."
                        multipleFacesVisible -> "Deixe apenas uma pessoa diante da câmera para continuar."
                        state.modoOffline -> "Modo offline seguro: pontos dentro do horário ficam cifrados neste aparelho e serão sincronizados automaticamente."
                        else -> "A identificação inicial acontece neste dispositivo. Não é necessário procurar seu nome."
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )

                if (state.modoOffline) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF5A4300).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "Sem conexão. Pausas fora do horário continuam exigindo o servidor e não são liberadas offline.",
                            modifier = Modifier.padding(12.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (!state.catalogoBiometricoPronto && !state.sincronizandoBiometrias && viewModel.faceModelReady) {
                    Button(onClick = { viewModel.sincronizarBiometrias(force = true) }) { Text("Sincronizar rostos") }
                }
                if (state.eventosPendentes > 0 && !state.modoOffline) {
                    OutlinedButton(onClick = viewModel::sincronizarPendenciasOffline) {
                        Text(if (state.sincronizandoPendencias) "Sincronizando..." else "Sincronizar ${state.eventosPendentes} registro(s)")
                    }
                }
                state.erro?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (error.contains("reconhec", ignoreCase = true) || error.contains("identidade", ignoreCase = true)) "ROSTO NÃO RECONHECIDO" else "ATENÇÃO",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
