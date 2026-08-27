package com.pontocafe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pontocafe.app.camera.FaceObservation
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Poses e limiar de aceite compartilhados entre SupervisorBiometricEnrollmentScreenV2
 * e AdminBiometricEnrollmentScreen -- as duas telas tinham exatamente a mesma lógica
 * duplicada (mesmos ângulos, mesmo texto).
 */
internal enum class BiometricEnrollmentPose(
    val title: String,
    val instruction: String,
    internal val targetYaw: Float,
    internal val targetPitch: Float,
    internal val angleTolerance: Float,
) {
    FRONT("Olhe para frente", "Mantenha o rosto centralizado e olhe diretamente para a câmera.", 0f, 0f, 10f),
    LEFT("Vire para a esquerda", "Gire o rosto levemente para a sua esquerda, sem inclinar a cabeça.", 28f, 0f, 15f),
    RIGHT("Vire para a direita", "Agora gire o rosto levemente para a sua direita.", -28f, 0f, 15f),
    UP("Olhe um pouco para cima", "Levante levemente o rosto, mantendo-se no centro.", 0f, 21f, 15f),
    BLINK("Pisque os olhos", "Volte a olhar para frente e faça um piscar completo.", 0f, 0f, 10f),
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

internal fun biometricEnrollmentHint(observation: FaceObservation, pose: BiometricEnrollmentPose): String = when {
    observation.faceCount == 0 -> "Posicione o rosto dentro do guia"
    observation.faceCount > 1 -> "Deixe apenas uma pessoa visível"
    !observation.isCentered -> "Centralize o rosto"
    observation.faceWidthRatio < 0.22f -> "Aproxime um pouco o rosto"
    observation.faceWidthRatio > 0.68f -> "Afaste um pouco o rosto"
    abs(observation.roll) > 12f -> "Mantenha a cabeça reta"
    else -> pose.instruction
}

private const val IDEAL_FACE_WIDTH_RATIO = 0.45f
private const val FACE_WIDTH_RATIO_SPAN = 0.23f

/**
 * Proximidade contínua (0..1) do ângulo atual em relação ao alvo da pose --
 * dados reais (yaw/pitch por frame), não uma simulação. Vira 1 exatamente
 * quando o quadro já atende [BiometricEnrollmentPose.accepts].
 */
internal fun FaceObservation.poseAngleScore(pose: BiometricEnrollmentPose): Float {
    val dYaw = yaw - pose.targetYaw
    val dPitch = pitch - pose.targetPitch
    val distance = sqrt(dYaw * dYaw + dPitch * dPitch)
    return (1f - (distance / (pose.angleTolerance * 2f))).coerceIn(0f, 1f)
}

/** Proximidade contínua (0..1) do enquadramento (distância) ideal da câmera. */
internal fun FaceObservation.framingScore(): Float =
    (1f - (abs(faceWidthRatio - IDEAL_FACE_WIDTH_RATIO) / FACE_WIDTH_RATIO_SPAN)).coerceIn(0f, 1f)

@Composable
internal fun BiometricEnrollmentTopBar(
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
        shape = MaterialTheme.shapes.medium,
        color = PontoCafeBrand.deepEspresso.copy(alpha = 0.9f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.small,
                color = PontoCafeBrand.tonalAmber.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, PontoCafeBrand.tonalAmber.copy(alpha = 0.35f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Face, contentDescription = null, tint = PontoCafeBrand.tonalAmber, modifier = Modifier.size(20.dp))
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
internal fun BiometricIdentityConfirmationCard(
    name: String,
    sector: String?,
    shift: String?,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    compactHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 22.dp, vertical = if (compactHeight) 76.dp else 92.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(PontoCafeBrand.tonalAmber, MaterialTheme.shapes.extraSmall),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PontoCafeBrand.deepEspresso.copy(alpha = 0.96f)),
            shape = MaterialTheme.shapes.extraLarge,
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
                    shape = MaterialTheme.shapes.medium,
                    color = PontoCafeBrand.tonalAmber.copy(alpha = 0.16f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = PontoCafeBrand.tonalAmber, modifier = Modifier.size(28.dp))
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
                        color = PontoCafeBrand.tonalAmber,
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
}

@Composable
internal fun BiometricEnrollmentBottomSheet(
    currentPose: BiometricEnrollmentPose,
    stepIndex: Int,
    totalSteps: Int,
    captured: Int,
    avatarCaptured: Boolean,
    processing: Boolean,
    faceModelReady: Boolean,
    cameraHint: String,
    angleScore: Float,
    framingScore: Float,
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
        color = PontoCafeBrand.deepEspresso.copy(alpha = 0.96f),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = if (compactHeight) 260.dp else 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
        ) {
            Text(
                "ETAPA ${stepIndex + 1} DE $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = PontoCafeBrand.tonalAmber,
            )
            Text(
                currentPose.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            BiometricEnrollmentStepper(
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
                        color = PontoCafeBrand.tonalAmber,
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

            if (currentPose != BiometricEnrollmentPose.BLINK && !processing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QualityMeterRow("Ângulo", angleScore)
                    QualityMeterRow("Enquadramento", framingScore)
                }
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
                color = if (avatarCaptured) DarkSemanticColors.success else Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
            )

            message?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = DarkSemanticColors.successContainer,
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(11.dp),
                        color = DarkSemanticColors.onSuccessContainer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            error?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = DarkSemanticColors.criticalContainer,
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(11.dp),
                        color = DarkSemanticColors.onCriticalContainer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Medidor de qualidade em tempo real (0..1), fica esmeralda ao cruzar o
 * limiar de aceite. Só cobre Ângulo (yaw/pitch) e Enquadramento (distância)
 * porque só esses dois têm dado real por quadro em FaceObservation --
 * iluminação/nitidez (FaceImageQuality) só são calculadas sobre a imagem já
 * capturada, não quadro a quadro, então não entraram aqui para não simular
 * uma leitura "ao vivo" que não existe.
 */
@Composable
private fun QualityMeterRow(label: String, score: Float) {
    val ready = score >= 0.8f
    val color = if (ready) DarkSemanticColors.success else PontoCafeBrand.tonalAmber
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.widthIn(min = 92.dp),
        )
        LinearProgressIndicator(
            progress = { score },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = color,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
        if (ready) {
            Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Prévia das poses que virão na próxima etapa (cadastro facial), mostrada no
 * formulário de novo colaborador antes de a câmera abrir. Usa o mesmo enum
 * BiometricEnrollmentPose da tela de cadastro real -- não é uma lista
 * inventada à parte que poderia divergir das poses que a pessoa vai ver.
 */
@Composable
internal fun UpcomingEnrollmentPreview(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
    ) {
        BiometricEnrollmentPose.entries.forEach { pose ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    pose.title.substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun BiometricEnrollmentStepper(
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
                completed -> PontoCafeBrand.tonalAmber
                active && processing -> DarkSemanticColors.warning
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
