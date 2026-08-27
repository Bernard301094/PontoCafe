package com.pontocafe.app.camera

import com.pontocafe.app.ui.KIOSK_GUIDE_CANVAS_ASPECT
import com.pontocafe.app.ui.KIOSK_GUIDE_OVAL_FILL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trava o contrato entre o guia e a política. Se alguém mexer nas constantes de
 * FaceCapturePolicy, no desenho do KioskFaceGuide ou na resolução do ImageAnalysis
 * sem refazer a conta, é aqui que quebra — em vez de quebrar no rosto do operador.
 */
class FaceGuideGeometryTest {

    private val galaxyA55Aspect = 1080f / 2340f

    @Test
    fun `rosto que preenche o guia e aceito pela politica`() {
        val ovalHeightFraction = FaceGuideGeometry.ovalHeightFractionOfPreview(galaxyA55Aspect)
        val height = FaceGuideGeometry.faceHeightRatioForOval(ovalHeightFraction, galaxyA55Aspect)
        val width = FaceGuideGeometry.faceWidthRatioFor(height)

        assertNull(FaceCapturePolicy.evaluate(filling(width, height), FaceCapturePurpose.IDENTIFICATION))
        assertNull(FaceCapturePolicy.evaluate(filling(width, height), FaceCapturePurpose.ENROLLMENT))
    }

    @Test
    fun `o alvo fica no meio geometrico com folga igual para os dois lados`() {
        val target = FaceGuideGeometry.targetFaceHeightRatio
        val roomToStepBack = target / FaceGuideGeometry.minAcceptedHeightRatio
        val roomToStepIn = FaceGuideGeometry.maxAcceptedHeightRatio / target

        assertEquals(roomToStepBack, roomToStepIn, 0.001f)
        assertTrue("folga insuficiente: $roomToStepBack", roomToStepBack > 1.4f)
    }

    @Test
    fun `largura do guia em 1080x2340 sai da politica`() {
        val ovalHeightFraction = FaceGuideGeometry.ovalHeightFractionOfPreview(galaxyA55Aspect)
        val guideWidthPx =
            2340f * ovalHeightFraction * KIOSK_GUIDE_CANVAS_ASPECT / KIOSK_GUIDE_OVAL_FILL

        // Número reportado na revisão desta mudança; se ele mudar, a conta mudou.
        assertEquals(0.880f, guideWidthPx / 1080f, 0.005f)
        assertEquals(0.375f, FaceGuideGeometry.targetFaceHeightRatio, 0.005f)
    }

    @Test
    fun `o anel circular anterior reprovava por rosto pequeno`() {
        // Geometria antiga: guideWidth = 0.72 * largura, círculo de 0.84 * guideWidth.
        val oldOvalHeightFraction = 0.72f * 1080f * 0.84f / 2340f
        val height = FaceGuideGeometry.faceHeightRatioForOval(oldOvalHeightFraction, galaxyA55Aspect)

        assertTrue(height < FaceCapturePolicy.MIN_FACE_HEIGHT_RATIO)
        assertEquals(
            FaceCaptureRejectionReason.FACE_TOO_SMALL,
            FaceCapturePolicy.evaluate(
                filling(FaceGuideGeometry.faceWidthRatioFor(height), height),
                FaceCapturePurpose.IDENTIFICATION,
            ),
        )
    }

    @Test
    fun `preview deitado pede um oval maior que a tela e depende do clamp de quem chama`() {
        // Tablet 2560x1600: o FILL_CENTER corta a ALTURA do buffer, então um rosto do
        // tamanho da política não cabe verticalmente. Documenta o limite conhecido.
        assertTrue(FaceGuideGeometry.ovalHeightFractionOfPreview(1.6f) > 1f)
        assertEquals(1f, FaceGuideGeometry.visibleBufferWidthFraction(1.6f), 0.0001f)
        assertEquals(0.615f, FaceGuideGeometry.visibleBufferWidthFraction(galaxyA55Aspect), 0.005f)
        assertEquals(1f, FaceGuideGeometry.visibleBufferHeightFraction(galaxyA55Aspect), 0.0001f)
    }

    private fun filling(widthRatio: Float, heightRatio: Float) = FaceCaptureFacts(
        faceCount = 1,
        centered = true,
        faceWidthRatio = widthRatio,
        faceHeightRatio = heightRatio,
        fullyVisible = true,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        reliableLandmarks = true,
        eyesAcceptable = true,
    )
}
