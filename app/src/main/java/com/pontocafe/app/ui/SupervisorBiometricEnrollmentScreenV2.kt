package com.pontocafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceCapturePurpose
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState

@Composable
fun SupervisorBiometricEnrollmentScreenV2(viewModel: SupervisorViewModel) {
    val state = viewModel.state
    val collaborator = state.colaboradorSelecionado ?: return
    BackHandler(enabled = state.carregando) { /* Evita abandonar uma gravação biométrica em andamento. */ }
    if (state.biometricEnrollmentCompleted) {
        BiometricEnrollmentAvatarResult(
            collaborator = collaborator,
            avatarPreviewWebp = state.enrollmentAvatarPreview,
            avatarUrl = state.enrollmentAvatarUrl ?: collaborator.avatarUrl,
            avatarStatus = state.enrollmentAvatarStatus,
            avatarError = state.enrollmentAvatarError,
            message = state.mensagem,
            busy = state.carregando,
            onRetryAvatar = viewModel::tentarNovamenteAvatarDoCadastro,
            onReplaceAvatar = viewModel::substituirAvatarDoCadastro,
            onDone = viewModel::voltarColaboradores,
        )
        return
    }
    state.enrollmentDuplicateWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = viewModel::cancelarCadastroPorDuplicidade,
            title = { Text("Possível cadastro duplicado") },
            text = {
                Text(
                    "Este rosto ficou muito parecido com o de ${warning.matchedCollaboradorName} " +
                        "já cadastrado neste aparelho. Confirme apenas se tiver certeza de que são " +
                        "pessoas diferentes (por exemplo, gêmeos).",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmarCadastroApesarDeDuplicidade) {
                    Text("Cadastrar mesmo assim")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelarCadastroPorDuplicidade) {
                    Text("Cancelar")
                }
            },
        )
    }
    val poses = remember(collaborator.id) { BiometricEnrollmentPose.entries.shuffled() }
    val stepIndex = state.biometricStepIndex.coerceIn(0, poses.lastIndex)
    val currentPose = poses[stepIndex]

    var identityConfirmed by rememberSaveable(collaborator.id) { mutableStateOf(false) }
    val cameraPermission = rememberCameraPermissionUiState()

    val captureController = remember { FrameCaptureController() }
    val liveness = remember { BlinkLiveness() }
    var captureRequested by remember { mutableStateOf(false) }
    var stableFrames by remember { mutableStateOf(0) }
    var cameraHint by remember { mutableStateOf(currentPose.instruction) }
    var angleScore by remember { mutableFloatStateOf(0f) }
    var framingScore by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.biometricScanCycle, stepIndex) {
        liveness.reset()
        captureRequested = false
        stableFrames = 0
        cameraHint = currentPose.instruction
        angleScore = 0f
        framingScore = 0f
    }

    // Preto puro trocado pelo mesmo quase-preto quente do resto do app.
    PontoCafeResponsiveOverlayScreen(
        modifier = Modifier.background(PontoCafeBrand.deepEspresso),
    ) { responsive ->
        // Mesma regra do resto do app. Cobre também telefone deitado, que o antigo
        // `maxHeight < 480.dp` tratava como tela alta por olhar só a altura.
        val compactHeight = responsive.useCompactVerticalLayout
        if (identityConfirmed) {
            if (cameraPermission.granted) {
                FaceCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    captureController = captureController,
                    onObservation = { observation ->
                        if (!state.carregando && !captureRequested) {
                            cameraHint = biometricEnrollmentHint(observation, currentPose)
                            angleScore = observation.poseAngleScore(currentPose)
                            framingScore = observation.framingScore()
                            if (currentPose == BiometricEnrollmentPose.BLINK) {
                                val next = liveness.update(observation)
                                cameraHint = when (next) {
                                    LivenessState.POSICIONE_ROSTO -> biometricEnrollmentHint(observation, currentPose)
                                    LivenessState.PISQUE -> "Pisque os olhos para confirmar presença"
                                    LivenessState.ABRA_OS_OLHOS -> "Agora abra os olhos"
                                    LivenessState.CONCLUIDO -> "Presença confirmada. Capturando..."
                                }
                                if (next == LivenessState.CONCLUIDO) {
                                    captureRequested = true
                                    captureController.request(observation, FaceCapturePurpose.ENROLLMENT)
                                }
                            } else if (currentPose.accepts(observation)) {
                                stableFrames += 1
                                if (stableFrames >= 4) {
                                    captureRequested = true
                                    cameraHint = "Posição confirmada. Capturando..."
                                    captureController.request(observation, FaceCapturePurpose.ENROLLMENT)
                                }
                            } else {
                                stableFrames = 0
                            }
                        }
                    },
                    onFrame = viewModel::processarAmostraBiometrica,
                    onCaptureRejected = {
                        captureRequested = false
                        stableFrames = 0
                        liveness.reset()
                    },
                )
            } else {
                CameraPermissionCard(
                    state = cameraPermission,
                    title = "Permissão de câmera",
                    rationale = "A câmera é necessária para cadastrar as amostras faciais desta pessoa.",
                    dark = true,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(
                            horizontal = responsive.pagePadding,
                            vertical = if (compactHeight) 72.dp else 96.dp,
                        ),
                )
            }
        } else {
            BiometricIdentityConfirmationCard(
                name = collaborator.nome,
                sector = collaborator.setor,
                shift = collaborator.turno,
                onConfirm = { identityConfirmed = true },
                onBack = viewModel::voltarColaboradores,
                compactHeight = compactHeight,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        BiometricEnrollmentTopBar(
            name = collaborator.nome,
            onBack = viewModel::voltarColaboradores,
            backEnabled = !state.carregando,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (identityConfirmed && cameraPermission.granted) {
            BiometricEnrollmentBottomSheet(
                currentPose = currentPose,
                stepIndex = stepIndex,
                totalSteps = poses.size,
                captured = state.biometricSamplesCaptured,
                avatarCaptured = state.enrollmentAvatarCaptured,
                processing = state.carregando,
                faceModelReady = viewModel.faceModelReady,
                cameraHint = cameraHint,
                angleScore = angleScore,
                framingScore = framingScore,
                message = state.mensagem,
                error = state.erro,
                compactHeight = compactHeight,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
