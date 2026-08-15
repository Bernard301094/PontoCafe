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
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import kotlin.math.abs

private enum class EnrollmentPose(
    val title: String,
    val instruction: String,
) {
    FRONT("Olhe para frente", "Mantenha o rosto centralizado e olhe diretamente para a câmera."),
    LEFT("Vire levemente para a esquerda", "Gire o rosto devagar para a sua esquerda, sem inclinar a cabeça."),
    RIGHT("Vire levemente para a direita", "Agora gire o rosto devagar para a sua direita."),
    UP("Olhe um pouco para cima", "Levante levemente o rosto, mantendo-se no centro."),
    BLINK("Pisque os olhos", "Volte a olhar para frente e faça um piscar completo."),
    ;

    fun accepts(observation: FaceObservation): Boolean {
        if (!observation.isWellPositioned) return false
        return when (this) {
            FRONT -> abs(observation.yaw) <= 10f && abs(observation.pitch) <= 10f
            LEFT -> observation.yaw in 18f..38f && abs(observation.pitch) <= 15f
            RIGHT -> observation.yaw in -38f..-18f && abs(observation.pitch) <= 15f
            UP -> observation.pitch in 12f..30f && abs(observation.yaw) <= 15f
            BLINK -> false
        }
    }
}

private fun positioningHint(observation: FaceObservation, pose: EnrollmentPose): String {
    return when {
        observation.faceCount == 0 -> "Posicione o rosto dentro da câmera"
        observation.faceCount > 1 -> "Deixe apenas uma pessoa visível"
        !observation.isCentered -> "Centralize o rosto"
        observation.faceWidthRatio < 0.22f -> "Aproxime um pouco o rosto"
        observation.faceWidthRatio > 0.68f -> "Afaste um pouco o rosto"
        abs(observation.roll) > 12f -> "Mantenha a cabeça reta"
        else -> pose.instruction
    }
}

@Composable
fun AdminBiometricEnrollmentScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val colaborador = state.colaboradorSelecionado ?: return
    val poses = remember(colaborador.id) { EnrollmentPose.entries.shuffled() }
    val stepIndex = state.biometricStepIndex.coerceIn(0, poses.lastIndex)
    val currentPose = poses[stepIndex]

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
    var stableFrames by remember { mutableStateOf(0) }
    var cameraHint by remember { mutableStateOf(currentPose.instruction) }

    LaunchedEffect(state.biometricScanCycle, stepIndex) {
        liveness.reset()
        captureRequested = false
        stableFrames = 0
        livenessState = LivenessState.POSICIONE_ROSTO
        cameraHint = currentPose.instruction
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            FaceCameraPreview(
                modifier = Modifier.fillMaxSize(),
                captureController = captureController,
                onObservation = { observation ->
                    if (!state.carregando && !captureRequested) {
                        cameraHint = positioningHint(observation, currentPose)

                        if (currentPose == EnrollmentPose.BLINK) {
                            val next = liveness.update(observation)
                            livenessState = next
                            cameraHint = when (next) {
                                LivenessState.POSICIONE_ROSTO -> positioningHint(observation, currentPose)
                                LivenessState.PISQUE -> "Pisque os olhos para confirmar presença"
                                LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
                                LivenessState.CONCLUIDO -> "Presença confirmada. Capturando..."
                            }
                            if (next == LivenessState.CONCLUIDO) {
                                captureRequested = true
                                captureController.request()
                            }
                        } else {
                            if (currentPose.accepts(observation)) {
                                stableFrames += 1
                                if (stableFrames >= 4) {
                                    captureRequested = true
                                    cameraHint = "Posição confirmada. Capturando..."
                                    captureController.request()
                                }
                            } else {
                                stableFrames = 0
                            }
                        }
                    }
                },
                onFrame = viewModel::processarAmostraBiometrica,
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
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Etapa ${stepIndex + 1} de ${poses.size} · ${currentPose.title}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        !viewModel.faceModelReady -> "Modelo facial não instalado"
                        state.carregando && state.biometricSamplesCaptured < poses.size - 1 -> "Processando amostra..."
                        state.carregando -> "Combinando e salvando biometria..."
                        else -> cameraHint
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${state.biometricSamplesCaptured} de ${poses.size} amostras capturadas",
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    "A ordem das etapas muda a cada cadastro. Somente o template facial combinado e cifrado será salvo; nenhuma foto é armazenada.",
                    color = Color.White.copy(alpha = 0.75f),
                )
                state.mensagem?.let { Text(it, color = Color(0xFFD7F3E4)) }
                state.erro?.let { Text(it, color = Color(0xFFFFC7C7)) }
            }
        }
    }
}
