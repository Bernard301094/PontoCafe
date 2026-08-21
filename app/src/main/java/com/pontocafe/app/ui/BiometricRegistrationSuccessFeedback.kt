package com.pontocafe.app.ui

import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

private const val BIOMETRIC_SUCCESS_VISIBLE_MILLIS = 6_500L

@Composable
fun BiometricRegistrationSuccessFeedback(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val successMessage = message?.takeIf { value ->
        value.startsWith("Rosto de ") && value.contains("cadastrado com 5 amostras")
    }
    val employeeName = successMessage
        ?.removePrefix("Rosto de ")
        ?.substringBefore(" cadastrado")
        ?.trim()
        ?.ifBlank { null }
    val view = LocalView.current
    val accessibilityManager = LocalAccessibilityManager.current

    LaunchedEffect(successMessage) {
        if (successMessage == null) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
        delay(
            accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = BIOMETRIC_SUCCESS_VISIBLE_MILLIS,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            ) ?: BIOMETRIC_SUCCESS_VISIBLE_MILLIS,
        )
        onDismiss()
    }

    AnimatedVisibility(
        visible = successMessage != null,
        modifier = modifier
            .fillMaxSize()
            .zIndex(50f),
        enter = fadeIn(tween(PontoCafeMotion.Standard)) + scaleIn(
            animationSpec = tween(PontoCafeMotion.Emphasized, easing = PontoCafeMotion.EmphasizedEasing),
            initialScale = 0.94f,
        ),
        exit = fadeOut(tween(PontoCafeMotion.Standard)) + scaleOut(
            animationSpec = tween(PontoCafeMotion.Standard),
            targetScale = 0.97f,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compactFeedback = maxHeight < 560.dp || LocalDensity.current.fontScale >= 1.6f
            Surface(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .semantics {
                        paneTitle = "Biometria cadastrada"
                        liveRegion = LiveRegionMode.Assertive
                        stateDescription = successMessage.orEmpty()
                    },
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = if (compactFeedback) 360.dp else 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (compactFeedback) 16.dp else 24.dp,
                            vertical = if (compactFeedback) 16.dp else 26.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PontoCafeSpacing.sm),
                ) {
                    PontoCafeSuccessAnimation(Modifier.size(if (compactFeedback) 72.dp else 112.dp))
                    Text(
                        "Rosto cadastrado com sucesso!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    employeeName?.let { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        "As 5 amostras faciais foram validadas e salvas. Este colaborador já pode usar o reconhecimento facial no Ponto Café.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            "Para aumentar a precisão, você pode repetir o cadastro com outra aparência real de trabalho — por exemplo, com touca, sem touca, com óculos ou sem óculos. As novas amostras são adicionadas às anteriores; elas não substituem o rosto já salvo.",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    StatusPill(
                        text = "Biometria pronta",
                        tone = PontoCafeTone.SUCCESS,
                    )
                    PcPrimaryButton(
                        text = "Concluir",
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PontoCafeSpacing.xs),
                    )
                }
            }
        }
    }
}
