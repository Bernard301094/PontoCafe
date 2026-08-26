package com.pontocafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pontocafe.app.SupervisorViewModel
import com.pontocafe.app.camera.BlinkLiveness
import com.pontocafe.app.camera.FaceCameraPreview
import com.pontocafe.app.camera.FaceCapturePurpose
import com.pontocafe.app.camera.FaceObservation
import com.pontocafe.app.camera.FrameCaptureController
import com.pontocafe.app.camera.LivenessState
import kotlin.math.abs

private enum class SupervisorEnrollmentPoseV2(
    val title: String,
    val instruction: String,
) {
    FRONT("Olhe para frente", "Mantenha o rosto centralizado e olhe diretamente para a câmera."),
    LEFT("Vire para a esquerda", "Gire o rosto levemente para a sua esquerda, sem inclinar a cabeça."),
    RIGHT("Vire para a direita", "Agora gire o rosto levemente para a sua direita."),
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

private fun supervisorEnrollmentHintV2(
    observation: FaceObservation,
    pose: SupervisorEnrollmentPoseV2,
): String = when {
    observation.faceCount == 0 -> "Posicione o rosto dentro do guia"
    observation.faceCount > 1 -> "Deixe apenas uma pessoa visível"
    !observation.isCentered -> "Centralize o rosto"
    observation.faceWidthRatio < 0.22f -> "Aproxime um pouco o rosto"
    observation.faceWidthRatio > 0.68f -> "Afaste um pouco o rosto"
    abs(observation.roll) > 12f -> "Mantenha a cabeça reta"
    else -> pose.instruction
}

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
    val poses = remember(collaborator.id) { SupervisorEnrollmentPoseV2.entries.shuffled() }
    val stepIndex = state.biometricStepIndex.coerceIn(0, poses.lastIndex)
    val currentPose = poses[stepIndex]

    var identityConfirmed by rememberSaveable(collaborator.id) { mutableStateOf(false) }
    val cameraPermission = rememberCameraPermissionUiState()

    val captureController = remember { FrameCaptureController() }
    val liveness = remember { BlinkLiveness() }
    var captureRequested by remember { mutableStateOf(false) }
    var stableFrames by remember { mutableStateOf(0) }
    var cameraHint by remember { mutableStateOf(currentPose.instruction) }

    LaunchedEffect(state.biometricScanCycle, stepIndex) {
        liveness.reset()
        captureRequested = false
        stableFrames = 0
        cameraHint = currentPose.instruction
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val compactHeight = maxHeight < 480.dp
        if (identityConfirmed) {
            if (cameraPermission.granted) {
                FaceCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    captureController = captureController,
                    onObservation = { observation ->
                        if (!state.carregando && !captureRequested) {
                            cameraHint = supervisorEnrollmentHintV2(observation, currentPose)
                            if (currentPose == SupervisorEnrollmentPoseV2.BLINK) {
                                val next = liveness.update(observation)
                                cameraHint = when (next) {
                                    LivenessState.POSICIONE_ROSTO -> supervisorEnrollmentHintV2(observation, currentPose)
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
                        .padding(horizontal = 24.dp, vertical = if (compactHeight) 72.dp else 96.dp),
                )
            }
        } else {
            SupervisorIdentityConfirmationCardV2(
                name = collaborator.nome,
                sector = collaborator.setor,
                shift = collaborator.turno,
                onConfirm = { identityConfirmed = true },
                onBack = viewModel::voltarColaboradores,
                compactHeight = compactHeight,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        SupervisorEnrollmentTopBarV2(
            name = collaborator.nome,
            onBack = viewModel::voltarColaboradores,
            backEnabled = !state.carregando,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (identityConfirmed && cameraPermission.granted) {
            SupervisorEnrollmentBottomSheetV2(
                currentPose = currentPose,
                stepIndex = stepIndex,
                totalSteps = poses.size,
                captured = state.biometricSamplesCaptured,
                avatarCaptured = state.enrollmentAvatarCaptured,
                processing = state.carregando,
                faceModelReady = viewModel.faceModelReady,
                cameraHint = cameraHint,
                message = state.mensagem,
                error = state.erro,
                compactHeight = compactHeight,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SupervisorEnrollmentTopBarV2(
    name: String,
    onBack: () -> Unit,
    backEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 900.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xE817130F),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = Color(0xFFFFB781).copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = null,
                        tint = Color(0xFFFFB781),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Cadastrar rosto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(name, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.66f))
            }
            TextButton(onClick = onBack, enabled = backEnabled) {
                Text("Voltar", color = Color.White.copy(alpha = if (backEnabled) 1f else 0.38f))
            }
        }
    }
}

@Composable
private fun SupervisorIdentityConfirmationCardV2(
    name: String,
    sector: String?,
    shift: String?,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 22.dp, vertical = if (compactHeight) 76.dp else 92.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xF517130F)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = if (compactHeight) 300.dp else 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.md),
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = CircleShape,
                color = Color(0xFFFFB781).copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFFFB781), modifier = Modifier.size(28.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Confirme a pessoa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFFFB781),
                )
                val details = listOfNotNull(sector, shift).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.68f))
                }
            }
            PcStateBanner(
                title = "Validação visual obrigatória",
                supportingText = "Confirme a pessoa. Durante o cadastro, a melhor imagem frontal também será usada como foto de perfil, separada da biometria.",
                tone = PontoCafeTone.WARNING,
            )
            PcFormActions(
                primaryText = "Confirmar e iniciar",
                onPrimary = onConfirm,
                secondaryText = "Escolher outra pessoa",
                onSecondary = onBack,
            )
        }
    }
}

@Composable
private fun SupervisorEnrollmentBottomSheetV2(
    currentPose: SupervisorEnrollmentPoseV2,
    stepIndex: Int,
    totalSteps: Int,
    captured: Int,
    avatarCaptured: Boolean,
    processing: Boolean,
    faceModelReady: Boolean,
    cameraHint: String,
    message: String?,
    error: String?,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = "$cameraHint. $captured de $totalSteps amostras capturadas"
            },
        color = Color(0xF517130F),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = if (compactHeight) 196.dp else 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Text(
                "ETAPA ${stepIndex + 1} DE $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFB781),
            )
            Text(
                currentPose.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            SupervisorEnrollmentStepperV2(
                captured = captured,
                total = totalSteps,
                current = stepIndex,
                processing = processing,
            )

            val hint = when {
                !faceModelReady -> "Modelo facial não instalado"
                processing && captured < totalSteps - 1 -> "Processando amostra..."
                processing -> "Validando e salvando biometria..."
                else -> cameraHint
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.xs),
            ) {
                if (processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFFB781),
                    )
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }

            Text(
                "$captured de $totalSteps amostras capturadas",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
            Text(
                if (avatarCaptured) {
                    "Foto de perfil capturada. Ela permanece separada da biometria facial."
                } else {
                    "Olhe diretamente para a câmera: a melhor imagem frontal também será usada como foto de perfil."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (avatarCaptured) Color(0xFFB0F2DD) else Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
            )

            message?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF164D40),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(11.dp),
                        color = Color(0xFFB0F2DD),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            error?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF3A1D1A),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(11.dp),
                        color = Color(0xFFFFDAD6),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SupervisorEnrollmentStepperV2(
    captured: Int,
    total: Int,
    current: Int,
    processing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = "$captured de $total etapas concluídas; etapa ${current + 1} em andamento"
            },
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val completed = index < captured
            val active = index == current && !completed
            val color = when {
                completed -> Color(0xFFFFB781)
                active && processing -> Color(0xFFFFD54D)
                active -> Color.White
                else -> Color.White.copy(alpha = 0.22f)
            }
            Surface(
                modifier = Modifier.size(if (active) 32.dp else 28.dp),
                shape = CircleShape,
                color = color.copy(alpha = if (completed) 0.20f else 0.12f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.72f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (completed) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
