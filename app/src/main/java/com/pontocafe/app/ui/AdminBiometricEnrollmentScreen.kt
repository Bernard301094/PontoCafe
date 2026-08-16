package com.pontocafe.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    var identityConfirmed by remember(colaborador.id) { mutableStateOf(false) }
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
        if (identityConfirmed) {
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
                MotionReveal(modifier = Modifier.align(Alignment.Center)) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("A câmera é necessária para cadastrar o rosto.", color = Color.White)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Permitir câmera")
                        }
                    }
                }
            }
        } else {
            MotionReveal(modifier = Modifier.align(Alignment.Center)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = Color(0xF0141917),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, Color(0x3382E2C4)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Confirme a pessoa",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        InitialAvatar(colaborador.nome, modifier = Modifier.size(58.dp))
                        Text(
                            colaborador.nome,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF82E2C4),
                        )
                        val detail = listOfNotNull(colaborador.setor, colaborador.turno)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (detail.isNotBlank()) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Surface(
                            color = Color(0x33FFD27D),
                            contentColor = Color(0xFFFFE2A8),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                "Confira visualmente se esta é realmente a pessoa diante da câmera. O rosto ficará vinculado a este cadastro.",
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Button(
                            onClick = { identityConfirmed = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Confirmar pessoa e iniciar")
                        }
                        TextButton(onClick = viewModel::voltarColaboradores) {
                            Text("Escolher outra pessoa")
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cadastrar rosto", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        colaborador.nome,
                        color = Color(0xFF82E2C4),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = viewModel::voltarColaboradores) {
                    Text("← Voltar", color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = identityConfirmed,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(18.dp)
                .align(Alignment.BottomCenter),
            enter = fadeIn(tween(PontoCafeMotion.Standard)) + slideInVertically(
                animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                initialOffsetY = { it / 3 },
            ),
            exit = fadeOut(tween(PontoCafeMotion.Quick)) + slideOutVertically(
                animationSpec = tween(PontoCafeMotion.Standard),
                targetOffsetY = { it / 4 },
            ),
        ) {
            Surface(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                ),
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0x3382E2C4)),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedContent(
                        targetState = currentPose,
                        transitionSpec = {
                            (fadeIn(tween(PontoCafeMotion.Standard)) + scaleIn(
                                animationSpec = tween(PontoCafeMotion.Standard),
                                initialScale = 0.98f,
                            )) togetherWith
                                (fadeOut(tween(PontoCafeMotion.Quick)) + scaleOut(
                                    animationSpec = tween(PontoCafeMotion.Quick),
                                    targetScale = 0.98f,
                                ))
                        },
                        label = "biometric-pose",
                    ) { pose ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Etapa ${stepIndex + 1} de ${poses.size}",
                                color = Color(0xFF82E2C4),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                pose.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    BiometricSampleProgress(
                        captured = state.biometricSamplesCaptured,
                        total = poses.size,
                        current = stepIndex,
                        processing = state.carregando,
                    )

                    AnimatedContent(
                        targetState = when {
                            !viewModel.faceModelReady -> "Modelo facial não instalado"
                            state.carregando && state.biometricSamplesCaptured < poses.size - 1 -> "Processando amostra..."
                            state.carregando -> "Combinando, verificando identidade e salvando biometria..."
                            else -> cameraHint
                        },
                        transitionSpec = {
                            fadeIn(tween(PontoCafeMotion.Standard)) togetherWith fadeOut(tween(PontoCafeMotion.Quick))
                        },
                        label = "biometric-hint",
                    ) { hint ->
                        Text(
                            text = hint,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Text(
                        "${state.biometricSamplesCaptured} de ${poses.size} amostras capturadas",
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Text(
                        "A atualização será bloqueada se o novo rosto não corresponder à biometria anterior ou se já pertencer a outro colaborador.",
                        color = Color.White.copy(alpha = 0.66f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )

                    AnimatedVisibility(
                        visible = state.mensagem != null,
                        enter = fadeIn(tween(PontoCafeMotion.Standard)) + scaleIn(initialScale = 0.98f),
                        exit = fadeOut(tween(PontoCafeMotion.Quick)),
                    ) {
                        Text(state.mensagem.orEmpty(), color = Color(0xFFD7F3E4), textAlign = TextAlign.Center)
                    }
                    AnimatedVisibility(
                        visible = state.erro != null,
                        enter = fadeIn(tween(PontoCafeMotion.Standard)) + scaleIn(initialScale = 0.98f),
                        exit = fadeOut(tween(PontoCafeMotion.Quick)),
                    ) {
                        Text(state.erro.orEmpty(), color = Color(0xFFFFC7C7), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricSampleProgress(
    captured: Int,
    total: Int,
    current: Int,
    processing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val completed = index < captured
            val active = index == current && !completed
            val targetColor = when {
                completed -> Color(0xFF82E2C4)
                active -> if (processing) Color(0xFFFFD27D) else Color.White
                else -> Color.White.copy(alpha = 0.22f)
            }
            val color by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(PontoCafeMotion.Standard),
                label = "sample-color-$index",
            )
            val scale by animateFloatAsState(
                targetValue = when {
                    completed -> 1.08f
                    active -> 1.28f
                    else -> 1f
                },
                animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
                label = "sample-scale-$index",
            )
            Surface(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(if (completed) 13.dp else 11.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = CircleShape,
                color = color,
                border = if (active && !completed) BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)) else null,
                shadowElevation = if (completed) 4.dp else 0.dp,
            ) {}
        }
    }
}
