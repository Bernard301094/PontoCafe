package com.pontocafe.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val FACE_GUIDE_READY_STABILITY_MILLIS = 180L

/**
 * Visual positioning guide. Neutral means no usable face yet, red means the
 * current face is outside the accepted capture geometry, and green means the
 * face has remained correctly positioned long enough to avoid frame-to-frame
 * color flicker. The short stabilization window keeps the visual feedback in
 * sync with the passive fast path instead of making the UI feel slower than the
 * recognizer.
 */
@Composable
internal fun KioskFaceGuide(
    active: Boolean,
    faceDetected: Boolean,
    warning: Boolean,
    positioned: Boolean,
    recognitionReady: Boolean,
    guideWidth: Dp,
    modifier: Modifier = Modifier,
) {
    var stablePositioned by remember { mutableStateOf(false) }

    LaunchedEffect(active, faceDetected, warning, positioned) {
        if (!active || !faceDetected || warning || !positioned) {
            stablePositioned = false
        } else {
            delay(FACE_GUIDE_READY_STABILITY_MILLIS)
            stablePositioned = true
        }
    }

    val targetColor = when {
        !active -> Color.White.copy(alpha = 0.32f)
        warning -> Color(0xFFFF5C5C)
        !faceDetected -> Color.White.copy(alpha = 0.78f)
        !stablePositioned -> Color(0xFFFF5C5C)
        else -> Color(0xFF49E39A)
    }
    val guideColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(
            PontoCafeMotion.Standard,
            easing = PontoCafeMotion.EmphasizedEasing,
        ),
        label = "kiosk-guide-color",
    )

    Canvas(
        modifier = modifier
            .width(guideWidth)
            .aspectRatio(0.80f)
            .semantics {
                contentDescription = when {
                    !active -> "Guia facial indisponível"
                    warning -> "Guia facial vermelho: mais de uma pessoa detectada"
                    !faceDetected -> "Guia facial aguardando um rosto"
                    !stablePositioned -> "Guia facial vermelho: ajuste o rosto dentro da área"
                    recognitionReady -> "Guia facial verde: rosto pronto para reconhecimento"
                    else -> "Guia facial verde: posição correta"
                }
            },
    ) {
        val emphasized = stablePositioned || recognitionReady || warning
        val stroke = (if (emphasized) 4.2.dp else 3.4.dp).toPx()
        val cornerLength = size.minDimension * if (stablePositioned) 0.24f else 0.20f
        val inset = stroke
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset

        drawLine(
            guideColor,
            Offset(left, top + cornerLength),
            Offset(left, top),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(left, top),
            Offset(left + cornerLength, top),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right - cornerLength, top),
            Offset(right, top),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right, top),
            Offset(right, top + cornerLength),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(left, bottom - cornerLength),
            Offset(left, bottom),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(left, bottom),
            Offset(left + cornerLength, bottom),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right - cornerLength, bottom),
            Offset(right, bottom),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            guideColor,
            Offset(right, bottom),
            Offset(right, bottom - cornerLength),
            stroke,
            cap = StrokeCap.Round,
        )
    }
}
