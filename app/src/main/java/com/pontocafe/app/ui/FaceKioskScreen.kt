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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState

@Composable
fun FaceKioskScreen(
    viewModel: PontoCafeViewModel,
    onAdminClick: () -> Unit,
    onSupervisorClick: () -> Unit,
) {
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        viewModel.sincronizarBiometrias(force = false)
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
                        state.scanning &&
                        state.catalogoBiometricoPronto &&
                        !state.sincronizandoBiometrias &&
                        !state.carregando
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
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Ponto Café", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "15 min por período · ${state.totalBiometrias} rostos sincronizados",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Surface(color = Color.Black.copy(alpha = 0.62f), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(horizontal = 6.dp)) {
                    TextButton(onClick = onSupervisorClick) { Text("Supervisor", color = Color.White) }
                    TextButton(onClick = onAdminClick) { Text("Administrador", color = Color.White) }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp).align(Alignment.BottomCenter),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val noFaceVisible = state.scanning &&
                    state.catalogoBiometricoPronto &&
                    !state.sincronizandoBiometrias &&
                    !state.carregando &&
                    detectedFaces == 0
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
                    color = if (noFaceVisible || multipleFacesVisible) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.White
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        !viewModel.faceModelReady -> "O modelo facial precisa estar disponível no APK."
                        state.sincronizandoBiometrias -> "Aguarde alguns segundos."
                        !state.catalogoBiometricoPronto -> "Cadastre rostos como Administrador e toque em sincronizar."
                        state.carregando -> "O candidato foi encontrado localmente e está sendo validado pelo servidor."
                        noFaceVisible -> "Não conseguimos localizar um rosto. Encaixe todo o rosto dentro da figura na tela e olhe para a câmera."
                        multipleFacesVisible -> "Deixe apenas uma pessoa diante da câmera para continuar."
                        else -> "A identificação inicial acontece neste dispositivo. Não é necessário procurar seu nome."
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.catalogoBiometricoPronto && !state.sincronizandoBiometrias && viewModel.faceModelReady) {
                    Button(onClick = { viewModel.sincronizarBiometrias(force = true) }) {
                        Text("Sincronizar agora")
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
                            if (error.contains("reconhec", ignoreCase = true) || error.contains("identidade", ignoreCase = true)) {
                                Text(
                                    "ROSTO NÃO RECONHECIDO",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            } else {
                                Text(
                                    "ATENÇÃO",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Text(error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
