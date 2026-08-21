package com.pontocafe.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceImageQualityPolicyTest {

    @Test
    fun `qualidade aceita iluminacao uniforme e nitidez suficiente`() {
        assertNull(FaceImageQualityPolicy.reject(acceptable()))
    }

    @Test
    fun `qualidade classifica exposicao desfoque sombra e contraluz`() {
        assertEquals(
            FaceImageQualityRejection.UNDEREXPOSED,
            FaceImageQualityPolicy.reject(acceptable().copy(brightness = 20f)),
        )
        assertEquals(
            FaceImageQualityRejection.OVEREXPOSED,
            FaceImageQualityPolicy.reject(acceptable().copy(brightness = 240f)),
        )
        assertEquals(
            FaceImageQualityRejection.BLURRED,
            FaceImageQualityPolicy.reject(acceptable().copy(laplacianVariance = 10f)),
        )
        assertEquals(
            FaceImageQualityRejection.EXCESSIVE_SHADOW,
            FaceImageQualityPolicy.reject(acceptable().copy(sideIlluminationDelta = 60f)),
        )
        assertEquals(
            FaceImageQualityRejection.BACKLIT,
            FaceImageQualityPolicy.reject(
                acceptable().copy(brightness = 100f, backlightDelta = 80f),
            ),
        )
    }

    private fun acceptable() = FaceImageQualityMetrics(
        brightness = 128f,
        contrast = 35f,
        sharpness = 8f,
        laplacianVariance = 80f,
        darkPixelRatio = 0.05f,
        brightPixelRatio = 0.05f,
        sideIlluminationDelta = 8f,
        backlightDelta = 5f,
    )
}
