package com.pontocafe.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveFaceLivenessTest {

    @Test
    fun `rosto frontal com dinamica natural passa sem challenge ativo`() {
        val liveness = PassiveFaceLiveness()
        val decisions = listOf(
            sample(at = 0, eye = 0.82f, yaw = 0.0f, centerX = 0.500f),
            sample(at = 70, eye = 0.79f, yaw = 0.3f, centerX = 0.502f),
            sample(at = 140, eye = 0.75f, yaw = 0.7f, centerX = 0.504f),
            sample(at = 210, eye = 0.71f, yaw = 0.9f, centerX = 0.505f),
            sample(at = 280, eye = 0.74f, yaw = 1.0f, centerX = 0.507f),
        ).map(liveness::update)

        assertEquals(PassiveLivenessDecision.PASSED, decisions.last())
    }

    @Test
    fun `piscar naturalmente tambem conclui o passivo sem comando`() {
        val liveness = PassiveFaceLiveness()
        val decisions = listOf(
            sample(at = 0, eye = 0.82f),
            sample(at = 80, eye = 0.76f),
            sample(at = 160, eye = 0.30f),
            sample(at = 250, eye = 0.74f),
        ).map(liveness::update)

        assertEquals(PassiveLivenessDecision.PASSED, decisions.last())
    }

    @Test
    fun `dois rostos bloqueiam e reiniciam a evidencia`() {
        val liveness = PassiveFaceLiveness()
        liveness.update(sample(at = 0, eye = 0.82f))
        liveness.update(sample(at = 100, eye = 0.75f, yaw = 0.8f))

        assertEquals(
            PassiveLivenessDecision.WAITING_FOR_FACE,
            liveness.update(sample(at = 200, faceCount = 2)),
        )

        val afterReset = liveness.update(sample(at = 300, eye = 0.70f, yaw = 1.2f))
        assertEquals(PassiveLivenessDecision.OBSERVING, afterReset)
    }

    @Test
    fun `mudanca de tracking nao herda evidencia do rosto anterior`() {
        val liveness = PassiveFaceLiveness()
        liveness.update(sample(at = 0, trackingId = 10, eye = 0.82f))
        liveness.update(sample(at = 100, trackingId = 10, eye = 0.72f, yaw = 0.8f))
        liveness.update(sample(at = 200, trackingId = 10, eye = 0.70f, yaw = 1.0f))

        val decision = liveness.update(
            sample(at = 300, trackingId = 11, eye = 0.62f, yaw = 1.3f, centerX = 0.510f),
        )

        assertEquals(PassiveLivenessDecision.OBSERVING, decision)
    }

    @Test
    fun `sequencia estatica exige challenge ativo em vez de ser aceita`() {
        val liveness = PassiveFaceLiveness()
        var decision = PassiveLivenessDecision.OBSERVING
        for (time in 0L..1_000L step 100L) {
            decision = liveness.update(sample(at = time, eye = 0.80f))
        }

        assertEquals(PassiveLivenessDecision.ACTIVE_CHALLENGE_REQUIRED, decision)
    }

    @Test
    fun `sinais auxiliares ausentes caem no fallback sem bloquear acessorio por nome`() {
        val liveness = PassiveFaceLiveness()
        var decision = PassiveLivenessDecision.OBSERVING
        for (time in 0L..800L step 100L) {
            decision = liveness.update(
                sample(
                    at = time,
                    eye = null,
                    yaw = time / 500f,
                    centerX = 0.5f + time / 100_000f,
                    auxiliaryVisibilityReady = false,
                ),
            )
        }

        assertEquals(PassiveLivenessDecision.ACTIVE_CHALLENGE_REQUIRED, decision)
    }

    @Test
    fun `face aprovada rejeita outro tracking`() {
        val liveness = approvedLiveness(trackingId = 7)

        assertTrue(
            liveness.matchesAcceptedFace(sample(at = 350, trackingId = 7, eye = 0.76f, centerX = 0.51f)),
        )
        assertFalse(
            liveness.matchesAcceptedFace(sample(at = 350, trackingId = 8, eye = 0.76f, centerX = 0.51f)),
        )
    }

    @Test
    fun `perda temporaria do tracking preserva mesma face pela geometria`() {
        val liveness = approvedLiveness(trackingId = 7)

        assertTrue(
            liveness.matchesAcceptedFace(
                sample(at = 350, trackingId = null, eye = 0.76f, centerX = 0.512f, faceWidth = 0.405f),
            ),
        )
    }

    @Test
    fun `salto geometrico grande nao e aceito quando tracking some`() {
        val liveness = approvedLiveness(trackingId = 7)

        assertFalse(
            liveness.matchesAcceptedFace(
                sample(at = 350, trackingId = null, eye = 0.76f, centerX = 0.72f, faceWidth = 0.58f),
            ),
        )
    }

    private fun approvedLiveness(trackingId: Int): PassiveFaceLiveness =
        PassiveFaceLiveness().also { liveness ->
            listOf(
                sample(at = 0, trackingId = trackingId, eye = 0.82f),
                sample(at = 70, trackingId = trackingId, eye = 0.78f, yaw = 0.4f, centerX = 0.502f),
                sample(at = 140, trackingId = trackingId, eye = 0.73f, yaw = 0.7f, centerX = 0.504f),
                sample(at = 210, trackingId = trackingId, eye = 0.70f, yaw = 0.9f, centerX = 0.506f),
                sample(at = 280, trackingId = trackingId, eye = 0.74f, yaw = 1.0f, centerX = 0.507f),
            ).forEach(liveness::update)
        }

    private fun sample(
        at: Long,
        faceCount: Int = 1,
        trackingId: Int? = 42,
        eye: Float? = 0.80f,
        yaw: Float = 0f,
        pitch: Float = 0f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        faceWidth: Float = 0.40f,
        auxiliaryVisibilityReady: Boolean = true,
        captureReady: Boolean = true,
        frontal: Boolean = true,
    ) = PassiveLivenessSample(
        faceCount = faceCount,
        trackingId = trackingId,
        captureReady = captureReady,
        frontal = frontal,
        auxiliaryVisibilityReady = auxiliaryVisibilityReady,
        yaw = yaw,
        pitch = pitch,
        centerXRatio = centerX,
        centerYRatio = centerY,
        faceWidthRatio = faceWidth,
        leftEyeOpen = eye,
        rightEyeOpen = eye,
        capturedAtMillis = at,
    )
}
