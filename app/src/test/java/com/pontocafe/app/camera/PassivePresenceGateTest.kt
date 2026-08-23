package com.pontocafe.app.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class PassivePresenceGateTest {
    @Test
    fun `frontal natural sequence becomes ready without active challenge`() {
        val gate = PassivePresenceGate(
            minimumStableFrames = 3,
            minimumElapsedMillis = 150L,
            challengeAfterMillis = 1_000L,
        )

        assertEquals(PassivePresenceDecision.WAITING, gate.update(sample(time = 0L, yaw = 0f)))
        assertEquals(PassivePresenceDecision.WAITING, gate.update(sample(time = 80L, yaw = 0.35f)))
        assertEquals(PassivePresenceDecision.READY, gate.update(sample(time = 170L, yaw = 0.55f)))
    }

    @Test
    fun `two faces reset and never become ready`() {
        val gate = PassivePresenceGate(
            minimumStableFrames = 2,
            minimumElapsedMillis = 50L,
            challengeAfterMillis = 500L,
        )

        gate.update(sample(time = 0L, yaw = 0f))
        val result = gate.update(sample(time = 100L, yaw = 0.5f, faceCount = 2))
        assertEquals(PassivePresenceDecision.WAITING, result)
        assertEquals(PassivePresenceDecision.WAITING, gate.update(sample(time = 180L, yaw = 0.8f)))
    }

    @Test
    fun `static frontal sequence asks for fallback challenge`() {
        val gate = PassivePresenceGate(
            minimumStableFrames = 2,
            minimumElapsedMillis = 50L,
            challengeAfterMillis = 300L,
        )

        gate.update(sample(time = 0L))
        gate.update(sample(time = 100L))
        gate.update(sample(time = 200L))
        assertEquals(PassivePresenceDecision.CHALLENGE_REQUIRED, gate.update(sample(time = 310L)))
    }

    @Test
    fun `invalid capture geometry never passes passive gate`() {
        val gate = PassivePresenceGate(
            minimumStableFrames = 2,
            minimumElapsedMillis = 50L,
            challengeAfterMillis = 300L,
        )

        gate.update(sample(time = 0L, ready = false))
        assertEquals(
            PassivePresenceDecision.WAITING,
            gate.update(sample(time = 500L, yaw = 1f, ready = false)),
        )
    }

    private fun sample(
        time: Long,
        yaw: Float = 0f,
        faceCount: Int = 1,
        ready: Boolean = true,
    ) = PassivePresenceSample(
        faceCount = faceCount,
        trackingId = 7,
        identificationReady = ready,
        centerXRatio = 0.50f,
        centerYRatio = 0.48f,
        faceWidthRatio = 0.34f,
        yaw = yaw,
        pitch = 0f,
        roll = 0f,
        leftEyeOpen = 0.82f,
        rightEyeOpen = 0.80f,
        timestampMillis = time,
    )
}
