package com.pontocafe.app.camera

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Resultado do liveness passivo executado antes do FaceNet.
 *
 * O modo passivo nunca altera limiar biométrico, margem de identidade ou o
 * espaço vetorial do FaceNet. Ele apenas decide se existe evidência temporal
 * suficiente para capturar o rosto sem pedir uma ação ao colaborador. Quando a
 * evidência RGB é insuficiente, o fluxo retorna ACTIVE_CHALLENGE_REQUIRED e a
 * UI usa o challenge ativo legado como fallback.
 */
enum class PassiveLivenessDecision {
    WAITING_FOR_FACE,
    OBSERVING,
    PASSED,
    ACTIVE_CHALLENGE_REQUIRED,
}

/** Dados puros para que a política possa ser testada sem CameraX/ML Kit. */
data class PassiveLivenessSample(
    val faceCount: Int,
    val trackingId: Int?,
    val captureReady: Boolean,
    val frontal: Boolean,
    val auxiliaryVisibilityReady: Boolean,
    val yaw: Float,
    val pitch: Float,
    val centerXRatio: Float,
    val centerYRatio: Float,
    val faceWidthRatio: Float,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val capturedAtMillis: Long,
)

/**
 * Liveness passivo temporal para a câmera RGB do Ponto.
 *
 * Ele procura dinâmica natural em uma sequência curta do MESMO rosto: variação
 * ocular, pequenos movimentos de pose e pequenas mudanças geométricas do rosto
 * no enquadramento. Uma imagem RGB comum não oferece a mesma garantia de um
 * sensor de profundidade; por isso uma sequência estática, sinais auxiliares
 * insuficientes ou comportamento inconclusivo NUNCA são aceitos à força: viram
 * challenge ativo excepcional.
 *
 * Touca, boné e acessórios não são classificados por este componente. O que
 * importa é o rosto continuar íntegro para FaceCapturePolicy e haver sinais
 * temporais suficientes. Isso evita bloquear EPI legítimo apenas pela aparência.
 */
class PassiveFaceLiveness(
    private val minimumSamples: Int = 5,
    private val minimumObservationMillis: Long = 240L,
    private val strongObservationMillis: Long = 420L,
    private val fallbackMillis: Long = 950L,
    private val missingAuxiliaryFallbackMillis: Long = 700L,
) {
    private val samples = ArrayDeque<PassiveLivenessSample>()
    private var activeTrackingId: Int? = null
    private var startedAtMillis: Long = 0L
    private var acceptedTrackingId: Int? = null
    private var acceptedSample: PassiveLivenessSample? = null

    init {
        require(minimumSamples >= 3)
        require(minimumObservationMillis > 0)
        require(strongObservationMillis >= minimumObservationMillis)
        require(fallbackMillis >= strongObservationMillis)
        require(missingAuxiliaryFallbackMillis >= minimumObservationMillis)
    }

    fun reset() {
        samples.clear()
        activeTrackingId = null
        startedAtMillis = 0L
        acceptedTrackingId = null
        acceptedSample = null
    }

    fun update(sample: PassiveLivenessSample): PassiveLivenessDecision {
        if (sample.faceCount != 1) {
            reset()
            return PassiveLivenessDecision.WAITING_FOR_FACE
        }

        if (!sample.captureReady || !sample.frontal) {
            resetObservationOnly()
            return PassiveLivenessDecision.OBSERVING
        }

        if (trackingChanged(sample)) {
            resetObservationOnly()
        }

        if (startedAtMillis == 0L) {
            startedAtMillis = sample.capturedAtMillis
            activeTrackingId = sample.trackingId
        }

        addSample(sample)
        val elapsed = (sample.capturedAtMillis - startedAtMillis).coerceAtLeast(0L)

        if (hasNaturalBlink() && samples.size >= 4 && elapsed >= minimumObservationMillis) {
            accept(sample)
            return PassiveLivenessDecision.PASSED
        }

        val eyeVariation = eyeVariation()
        val poseVariation = poseVariation()
        val geometryVariation = geometryVariation()
        val auxiliarySamples = samples.count { it.auxiliaryVisibilityReady }
        val auxiliaryCoverage = auxiliarySamples.toFloat() / samples.size.coerceAtLeast(1)

        // Caminho rápido normal: olhos/landmarks utilizáveis + dois canais de
        // dinâmica natural. Pequenos movimentos involuntários são suficientes;
        // ninguém precisa girar a cabeça nem piscar sob comando.
        val quickEvidence =
            samples.size >= minimumSamples &&
                elapsed >= minimumObservationMillis &&
                auxiliaryCoverage >= 0.60f &&
                eyeVariation >= MIN_EYE_VARIATION &&
                (poseVariation >= MIN_POSE_VARIATION || geometryVariation >= MIN_GEOMETRY_VARIATION)

        if (quickEvidence) {
            accept(sample)
            return PassiveLivenessDecision.PASSED
        }

        // Caminho passivo um pouco mais longo para pessoas cujos olhos variam
        // pouco. Exige dinâmica em pose E geometria para não aceitar um único
        // frame estático repetido.
        val strongEvidence =
            samples.size >= minimumSamples + 2 &&
                elapsed >= strongObservationMillis &&
                auxiliaryCoverage >= 0.60f &&
                poseVariation >= MIN_STRONG_POSE_VARIATION &&
                geometryVariation >= MIN_GEOMETRY_VARIATION

        if (strongEvidence) {
            accept(sample)
            return PassiveLivenessDecision.PASSED
        }

        val auxiliaryInsufficient = auxiliaryCoverage < 0.45f
        if (
            (auxiliaryInsufficient && elapsed >= missingAuxiliaryFallbackMillis) ||
            elapsed >= fallbackMillis
        ) {
            return PassiveLivenessDecision.ACTIVE_CHALLENGE_REQUIRED
        }

        return PassiveLivenessDecision.OBSERVING
    }

    /**
     * Depois do passivo aprovado, garante que a captura e o segundo frame do
     * consenso pertencem ao mesmo track/posição antes de enviar ao FaceNet.
     */
    fun matchesAcceptedFace(sample: PassiveLivenessSample): Boolean {
        val accepted = acceptedSample ?: return false
        if (sample.faceCount != 1 || !sample.captureReady || !sample.frontal) return false
        if (
            acceptedTrackingId != null || sample.trackingId != null
        ) {
            if (acceptedTrackingId == null || acceptedTrackingId != sample.trackingId) return false
        }

        val centerDistance = hypot(
            (sample.centerXRatio - accepted.centerXRatio).toDouble(),
            (sample.centerYRatio - accepted.centerYRatio).toDouble(),
        ).toFloat()
        val scaleDrift = abs(sample.faceWidthRatio - accepted.faceWidthRatio)
        return centerDistance <= MAX_ACCEPTED_CENTER_DRIFT && scaleDrift <= MAX_ACCEPTED_SCALE_DRIFT
    }

    private fun trackingChanged(sample: PassiveLivenessSample): Boolean {
        val current = activeTrackingId
        return current != null && sample.trackingId != null && current != sample.trackingId
    }

    private fun resetObservationOnly() {
        samples.clear()
        activeTrackingId = null
        startedAtMillis = 0L
        acceptedTrackingId = null
        acceptedSample = null
    }

    private fun addSample(sample: PassiveLivenessSample) {
        val last = samples.lastOrNull()
        // FaceCamera entrega observações a ~20 Hz. Ignora callbacks duplicados
        // com o mesmo timestamp para a evidência temporal não ser inflada.
        if (last?.capturedAtMillis == sample.capturedAtMillis) return
        samples.addLast(sample)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    private fun accept(sample: PassiveLivenessSample) {
        acceptedTrackingId = sample.trackingId
        acceptedSample = sample
    }

    private fun hasNaturalBlink(): Boolean {
        val eyeMeans = samples.mapNotNull { it.eyeMean() }
        if (eyeMeans.size < 4) return false
        return eyeMeans.minOrNull()!! <= CLOSED_EYE_SIGNAL &&
            eyeMeans.maxOrNull()!! >= OPEN_EYE_SIGNAL
    }

    private fun eyeVariation(): Float {
        val eyeMeans = samples.mapNotNull { it.eyeMean() }
        if (eyeMeans.size < 3) return 0f
        return (eyeMeans.maxOrNull() ?: 0f) - (eyeMeans.minOrNull() ?: 0f)
    }

    private fun poseVariation(): Float {
        if (samples.size < 3) return 0f
        val yawRange = samples.maxOf { it.yaw } - samples.minOf { it.yaw }
        val pitchRange = samples.maxOf { it.pitch } - samples.minOf { it.pitch }
        return maxOf(yawRange, pitchRange)
    }

    private fun geometryVariation(): Float {
        if (samples.size < 3) return 0f
        val xRange = samples.maxOf { it.centerXRatio } - samples.minOf { it.centerXRatio }
        val yRange = samples.maxOf { it.centerYRatio } - samples.minOf { it.centerYRatio }
        val scaleRange = samples.maxOf { it.faceWidthRatio } - samples.minOf { it.faceWidthRatio }
        return maxOf(hypot(xRange.toDouble(), yRange.toDouble()).toFloat(), scaleRange)
    }

    private fun PassiveLivenessSample.eyeMean(): Float? {
        val left = leftEyeOpen ?: return null
        val right = rightEyeOpen ?: return null
        return (left + right) / 2f
    }

    companion object {
        private const val MAX_SAMPLES = 18
        private const val MIN_EYE_VARIATION = 0.07f
        private const val MIN_POSE_VARIATION = 0.75f
        private const val MIN_STRONG_POSE_VARIATION = 1.25f
        private const val MIN_GEOMETRY_VARIATION = 0.006f
        private const val CLOSED_EYE_SIGNAL = 0.38f
        private const val OPEN_EYE_SIGNAL = 0.66f
        private const val MAX_ACCEPTED_CENTER_DRIFT = 0.14f
        private const val MAX_ACCEPTED_SCALE_DRIFT = 0.12f
    }
}

fun FaceObservation.toPassiveLivenessSample(
    capturedAtMillis: Long = android.os.SystemClock.elapsedRealtime(),
): PassiveLivenessSample {
    val box = bounds
    val centerXRatio = if (box != null && imageWidth > 0) box.exactCenterX() / imageWidth else 0f
    val centerYRatio = if (box != null && imageHeight > 0) box.exactCenterY() / imageHeight else 0f
    return PassiveLivenessSample(
        faceCount = faceCount,
        trackingId = trackingId,
        captureReady = isIdentificationReady,
        frontal = isFrontal,
        auxiliaryVisibilityReady = isFullyVisible && (hasReliableLandmarks || eyeClassificationAvailable),
        yaw = yaw,
        pitch = pitch,
        centerXRatio = centerXRatio,
        centerYRatio = centerYRatio,
        faceWidthRatio = faceWidthRatio,
        leftEyeOpen = leftEyeOpen,
        rightEyeOpen = rightEyeOpen,
        capturedAtMillis = capturedAtMillis,
    )
}
