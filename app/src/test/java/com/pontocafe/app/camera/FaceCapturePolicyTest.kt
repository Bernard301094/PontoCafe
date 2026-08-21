package com.pontocafe.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceCapturePolicyTest {

    @Test
    fun `identificacao aceita somente rosto unico frontal integro e visivel`() {
        assertNull(FaceCapturePolicy.evaluate(readyFacts(), FaceCapturePurpose.IDENTIFICATION))
        assertEquals(
            FaceCaptureRejectionReason.MULTIPLE_FACES,
            FaceCapturePolicy.evaluate(readyFacts().copy(faceCount = 2), FaceCapturePurpose.IDENTIFICATION),
        )
        assertEquals(
            FaceCaptureRejectionReason.PARTIAL_FACE,
            FaceCapturePolicy.evaluate(readyFacts().copy(fullyVisible = false), FaceCapturePurpose.IDENTIFICATION),
        )
        assertEquals(
            FaceCaptureRejectionReason.EXTREME_POSE,
            FaceCapturePolicy.evaluate(readyFacts().copy(yaw = 13f), FaceCapturePurpose.IDENTIFICATION),
        )
        assertEquals(
            FaceCaptureRejectionReason.LANDMARKS_MISSING,
            FaceCapturePolicy.evaluate(
                readyFacts().copy(reliableLandmarks = false),
                FaceCapturePurpose.IDENTIFICATION,
            ),
        )
    }

    @Test
    fun `cadastro permite poses guiadas mas mantem limites geometricos`() {
        assertNull(
            FaceCapturePolicy.evaluate(
                readyFacts().copy(yaw = 30f),
                FaceCapturePurpose.ENROLLMENT,
            ),
        )
        assertEquals(
            FaceCaptureRejectionReason.EXTREME_POSE,
            FaceCapturePolicy.evaluate(
                readyFacts().copy(yaw = 50f),
                FaceCapturePurpose.ENROLLMENT,
            ),
        )
        assertEquals(
            FaceCaptureRejectionReason.FACE_TOO_SMALL,
            FaceCapturePolicy.evaluate(
                readyFacts().copy(faceWidthRatio = 0.15f),
                FaceCapturePurpose.ENROLLMENT,
            ),
        )
        assertEquals(
            FaceCaptureRejectionReason.EXTREME_POSE,
            FaceCapturePolicy.evaluate(
                readyFacts().copy(roll = 13f),
                FaceCapturePurpose.ENROLLMENT,
            ),
        )
    }

    private fun readyFacts() = FaceCaptureFacts(
        faceCount = 1,
        centered = true,
        faceWidthRatio = 0.40f,
        faceHeightRatio = 0.55f,
        fullyVisible = true,
        yaw = 0f,
        pitch = 0f,
        roll = 0f,
        reliableLandmarks = true,
        eyesAcceptable = true,
    )
}
