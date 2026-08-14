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
import com.pontocafe.app.AdminViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState

@Composable
fun AdminBiometricEnrollmentScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val colaborador = state.colaboradorSelecionado ?: return

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

    LaunchedEffect(state.biometricScanCycle) {
        liveness.reset()
        captureRequested = false
        livenessState = LivenessState.POSICIONE_ROSTO
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                onObservation = { observation ->
                    if (!state.carregando) {
                        val next = liveness.update(observation)
                        livenessState = next
                        if (next == LivenessState.CONCLUIDO && !captureRequested) {
                            captureRequested = true
                            captureController.request()
                        }
                    }
                },
                onFrame = viewModel::processarBiometria,
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("A câmera é necessária para cadastrar o rosto.", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Cadastrar rosto", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(colaborador.nome, color = Color.White.copy(alpha = 0.78f))
                }
                TextButton(onClick = viewModel::voltarColaboradores) {
                    Text("Cancelar", color = Color.White)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp).align(Alignment.BottomCenter),
            color = Color.Black.copy(alpha = 0.76f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = when {
                        !viewModel.faceModelReady -> "Modelo facial não instalado"
                        state.carregando -> "Salvando biometria..."
                        else -> when (livenessState) {
                            LivenessState.POSICIONE_ROSTO -> "Posicione o rosto no centro"
                            LivenessState.PISQUE -> "Pisque para confirmar presença"
                            LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
                            LivenessState.CONCLUIDO -> "Rosto capturado"
                        }
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "O sistema salvará somente o template facial cifrado, não a foto.",
                    color = Color.White.copy(alpha = 0.75f),
                )
                state.mensagem?.let { Text(it, color = Color(0xFFD7F3E4)) }
                state.erro?.let { Text(it, color = Color(0xFFFFC7C7)) }
            }
        }
    }
}
