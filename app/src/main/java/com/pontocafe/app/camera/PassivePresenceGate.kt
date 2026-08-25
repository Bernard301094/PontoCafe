package com.pontocafe.app.camera

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Evidência temporal leve e passiva usada antes da identificação facial.
 *
 * Isto não altera a similaridade FaceNet nem substitui a confirmação de
 * identidade. O objetivo é retirar desafios ativos do caminho normal e exigir
 * uma pequena sequência frontal coerente do mesmo rosto. Quando a sequência
 * permanece excessivamente estática ou inconsistente, o chamador deve recorrer
 * ao desafio ativo existente como fallback.
 */
enum class PassivePresenceDecision {
    WAITING,
    READY,
    CHALLENGE_REQUIRED,
}

data class PassivePresenceSample(
    val faceCount: Int,
    val trackingId: Int?,
    val identificationReady: Boolean,
    val centerXRatio: Float,
    val centerYRatio: Float,
    val faceWidthRatio: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val timestampMillis: Long,
)

class PassivePresenceGate(
    private val minimumStableFrames: Int = 4,
    private val minimumElapsedMillis: Long = 220L,
    // Uma pessoa genuinamente parada nunca produz o jitter mínimo exigido por
    // hasNaturalTemporalSignal (é assim que o gate distingue de uma foto estática
    // parada) e só sai da espera quando cai no desafio ativo. 1200ms fazia essa
    // pessoa esperar quase mais um segundo só para então ainda ter que piscar.
    // Encurtar não afrouxa a detecção de foto estática (o piso do sinal natural
    // continua exigido) — só chega mais rápido no desafio que de qualquer forma
    // seria pedido.
    private val challengeAfterMillis: Long = 700L,
) {
    private var firstSampleAtMillis = 0L
    private var boundTrackingId: Int? = null
    private var previous: PassivePresenceSample? = null
    private var stableFrames = 0
    private var naturalSignalFrames = 0
    private var accepted = false

    init {
        require(minimumStableFrames >= 2)
        require(minimumElapsedMillis >= 0L)
        require(challengeAfterMillis > minimumElapsedMillis)
    }

    fun reset() {
        firstSampleAtMillis = 0L
        boundTrackingId = null
        previous = null
        stableFrames = 0
        naturalSignalFrames = 0
        accepted = false
    }

    fun update(sample: PassivePresenceSample): PassivePresenceDecision {
        if (sample.faceCount != 1 || !sample.identificationReady) {
            reset()
            return PassivePresenceDecision.WAITING
        }

        val previousSample = previous
        if (previousSample == null || !sameFace(previousSample, sample)) {
            bind(sample)
            return PassivePresenceDecision.WAITING
        }

        stableFrames += 1
        if (hasNaturalTemporalSignal(previousSample, sample)) {
            naturalSignalFrames += 1
        }
        previous = sample

        val elapsed = (sample.timestampMillis - firstSampleAtMillis).coerceAtLeast(0L)
        if (
            stableFrames >= minimumStableFrames &&
            elapsed >= minimumElapsedMillis &&
            naturalSignalFrames >= 1
        ) {
            accepted = true
            return PassivePresenceDecision.READY
        }

        return if (elapsed >= challengeAfterMillis) {
            PassivePresenceDecision.CHALLENGE_REQUIRED
        } else {
            PassivePresenceDecision.WAITING
        }
    }

    fun matches(sample: PassivePresenceSample): Boolean {
        if (!accepted || sample.faceCount != 1 || !sample.identificationReady) return false
        val reference = previous ?: return false
        return sameFace(reference, sample)
    }

    private fun bind(sample: PassivePresenceSample) {
        firstSampleAtMillis = sample.timestampMillis
        boundTrackingId = sample.trackingId
        previous = sample
        stableFrames = 1
        naturalSignalFrames = 0
        accepted = false
    }

    private fun sameFace(a: PassivePresenceSample, b: PassivePresenceSample): Boolean {
        if (boundTrackingId != null || b.trackingId != null) {
            return boundTrackingId != null && boundTrackingId == b.trackingId
        }

        val centerDistance = hypot(
            (a.centerXRatio - b.centerXRatio).toDouble(),
            (a.centerYRatio - b.centerYRatio).toDouble(),
        ).toFloat()
        return centerDistance <= 0.12f && abs(a.faceWidthRatio - b.faceWidthRatio) <= 0.12f
    }

    private fun hasNaturalTemporalSignal(
        a: PassivePresenceSample,
        b: PassivePresenceSample,
    ): Boolean {
        val poseDelta = abs(a.yaw - b.yaw) + abs(a.pitch - b.pitch) + abs(a.roll - b.roll)
        val centerDelta = hypot(
            (a.centerXRatio - b.centerXRatio).toDouble(),
            (a.centerYRatio - b.centerYRatio).toDouble(),
        ).toFloat()
        val eyeDelta = listOfNotNull(
            eyeDelta(a.leftEyeOpen, b.leftEyeOpen),
            eyeDelta(a.rightEyeOpen, b.rightEyeOpen),
        ).maxOrNull() ?: 0f

        // Pequenas mudanças naturais entre frames são suficientes. Saltos grandes
        // não contam como presença: normalmente significam troca de pose, track ou
        // captura instável e serão tratados pelo fluxo de qualidade da câmera.
        val subtlePose = poseDelta in 0.25f..7.0f
        val subtleTranslation = centerDelta in 0.0025f..0.045f
        val eyeVariation = eyeDelta in 0.05f..0.65f
        return subtlePose || subtleTranslation || eyeVariation
    }

    private fun eyeDelta(a: Float?, b: Float?): Float? =
        if (a == null || b == null) null else abs(a - b)
}

fun FaceObservation.toPassivePresenceSample(timestampMillis: Long): PassivePresenceSample {
    val box = bounds
    val centerX = if (box != null && imageWidth > 0) box.exactCenterX() / imageWidth.toFloat() else 0f
    val centerY = if (box != null && imageHeight > 0) box.exactCenterY() / imageHeight.toFloat() else 0f
    return PassivePresenceSample(
        faceCount = faceCount,
        trackingId = trackingId,
        identificationReady = isIdentificationReady,
        centerXRatio = centerX,
        centerYRatio = centerY,
        faceWidthRatio = faceWidthRatio,
        yaw = yaw,
        pitch = pitch,
        roll = roll,
        leftEyeOpen = leftEyeOpen,
        rightEyeOpen = rightEyeOpen,
        timestampMillis = timestampMillis,
    )
}
