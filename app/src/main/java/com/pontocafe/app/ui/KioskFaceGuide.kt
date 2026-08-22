package com.pontocafe.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual face guide kept separate so voice guidance does not alter camera geometry. */
@Composable
internal fun KioskFaceGuide(
    active: Boolean,
    warning: Boolean,
    ready: Boolean,
    guideWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val targetColor = when {
        warning -> Color(0xFFFFB4AB)
        ready -> Color(0xFF72DCBC)
        active -> Color.White.copy(alpha = 0.90f)
        else -> Color.White.copy(alpha = 0.32f)
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
                    warning -> "Guia facial: mais de uma pessoa detectada"
                    ready -> "Guia facial: rosto pronto para reconhecimento"
                    active -> "Guia facial ativo"
                    else -> "Guia facial indisponível"
                }
            },
    ) {
        val stroke = (if (ready || warning) 4.2.dp else 3.4.dp).toPx()
        val cornerLength = size.minDimension * if (ready) 0.24f else 0.20f
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
