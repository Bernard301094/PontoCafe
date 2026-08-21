package com.pontocafe.app.data

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sqrt

const val FACE_EMBEDDING_DIMENSION = 128

enum class EmbeddingIntegrityIssue {
    EMPTY,
    WRONG_DIMENSION,
    NON_FINITE,
    ZERO_NORM,
    NOT_L2_NORMALIZED,
}

data class EmbeddingIntegrityResult(
    val valid: Boolean,
    val issue: EmbeddingIntegrityIssue? = null,
    val norm: Double? = null,
)

/**
 * Centralized validation for every vector generated or consumed by the app.
 * Matching still uses cosine similarity, but malformed vectors never reach it.
 */
object FaceEmbeddingIntegrity {
    private const val MIN_NORM = 1e-12
    private const val UNIT_NORM_TOLERANCE = 0.02

    fun inspect(
        embedding: FloatArray,
        expectedDimension: Int = FACE_EMBEDDING_DIMENSION,
        requireUnitNorm: Boolean = true,
    ): EmbeddingIntegrityResult {
        if (embedding.isEmpty()) {
            return EmbeddingIntegrityResult(false, EmbeddingIntegrityIssue.EMPTY)
        }
        if (embedding.size != expectedDimension) {
            return EmbeddingIntegrityResult(false, EmbeddingIntegrityIssue.WRONG_DIMENSION)
        }

        var normSquared = 0.0
        for (value in embedding) {
            if (!value.isFinite()) {
                return EmbeddingIntegrityResult(false, EmbeddingIntegrityIssue.NON_FINITE)
            }
            val current = value.toDouble()
            normSquared += current * current
        }
        if (!normSquared.isFinite()) {
            return EmbeddingIntegrityResult(false, EmbeddingIntegrityIssue.NON_FINITE)
        }

        val norm = sqrt(normSquared)
        if (norm <= MIN_NORM) {
            return EmbeddingIntegrityResult(false, EmbeddingIntegrityIssue.ZERO_NORM, norm)
        }
        if (requireUnitNorm && abs(norm - 1.0) > UNIT_NORM_TOLERANCE) {
            return EmbeddingIntegrityResult(false, EmbeddingIntegrityIssue.NOT_L2_NORMALIZED, norm)
        }
        return EmbeddingIntegrityResult(true, norm = norm)
    }

    fun requireValid(
        embedding: FloatArray,
        expectedDimension: Int = FACE_EMBEDDING_DIMENSION,
        requireUnitNorm: Boolean = true,
    ): FloatArray {
        val result = inspect(embedding, expectedDimension, requireUnitNorm)
        require(result.valid) { "Embedding facial invalido: ${result.issue}." }
        return embedding
    }

    fun normalizedCopy(
        embedding: FloatArray,
        expectedDimension: Int = FACE_EMBEDDING_DIMENSION,
    ): FloatArray {
        val inspection = inspect(embedding, expectedDimension, requireUnitNorm = false)
        require(inspection.valid) { "Embedding facial invalido: ${inspection.issue}." }
        val norm = requireNotNull(inspection.norm)
        return FloatArray(embedding.size) { index -> (embedding[index] / norm).toFloat() }
            .also { requireValid(it, expectedDimension) }
    }

    fun cosine(left: FloatArray, right: FloatArray): Double {
        if (left.isEmpty() || left.size != right.size) return Double.NaN
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (index in left.indices) {
            val a = left[index].toDouble()
            val b = right[index].toDouble()
            if (!a.isFinite() || !b.isFinite()) return Double.NaN
            dot += a * b
            leftNorm += a * a
            rightNorm += b * b
        }
        if (leftNorm <= MIN_NORM || rightNorm <= MIN_NORM) return Double.NaN
        return dot / sqrt(leftNorm * rightNorm)
    }
}

class BiometricSampleConsistencyException(message: String) : IllegalArgumentException(message)

data class AggregatedBiometricTemplate(
    val embedding: FloatArray,
    val minimumSimilarityToMedoid: Double,
    val meanSimilarityToMedoid: Double,
    val medoidIndex: Int,
)

/**
 * Uses a medoid to detect a sample from another person before averaging the
 * enrollment sequence. The 0.60 continuity floor is deliberately below the
 * 0.72 recognition threshold so legitimate pose variation remains possible.
 */
object BiometricTemplateAggregator {
    const val MINIMUM_INTRA_USER_SIMILARITY = 0.60

    fun aggregate(
        samples: List<FloatArray>,
        expectedDimension: Int = FACE_EMBEDDING_DIMENSION,
        minimumSimilarity: Double = MINIMUM_INTRA_USER_SIMILARITY,
    ): AggregatedBiometricTemplate {
        require(samples.isNotEmpty()) { "Nenhuma amostra biometrica foi capturada." }
        samples.forEach { FaceEmbeddingIntegrity.requireValid(it, expectedDimension) }

        val similarities = Array(samples.size) { DoubleArray(samples.size) { 1.0 } }
        for (left in samples.indices) {
            for (right in left + 1 until samples.size) {
                val score = FaceEmbeddingIntegrity.cosine(samples[left], samples[right])
                if (!score.isFinite()) {
                    throw BiometricSampleConsistencyException("A sequencia contem uma amostra facial corrompida.")
                }
                similarities[left][right] = score
                similarities[right][left] = score
            }
        }

        val medoidIndex = samples.indices.maxByOrNull { index -> similarities[index].sum() } ?: 0
        val scoresToMedoid = samples.indices
            .filter { it != medoidIndex }
            .map { similarities[medoidIndex][it] }
        val minimum = scoresToMedoid.minOrNull() ?: 1.0
        val mean = scoresToMedoid.average().takeIf { it.isFinite() } ?: 1.0
        if (minimum < minimumSimilarity) {
            throw BiometricSampleConsistencyException(
                "As amostras nao parecem pertencer com seguranca a mesma pessoa. Reinicie o cadastro.",
            )
        }

        val average = FloatArray(expectedDimension)
        for (sample in samples) {
            for (index in average.indices) average[index] += sample[index]
        }
        for (index in average.indices) average[index] /= samples.size.toFloat()
        val normalized = FaceEmbeddingIntegrity.normalizedCopy(average, expectedDimension)

        // A media tambem precisa permanecer coerente com cada amostra original.
        if (samples.any { FaceEmbeddingIntegrity.cosine(normalized, it) < minimumSimilarity }) {
            throw BiometricSampleConsistencyException(
                "Nao foi possivel consolidar as amostras faciais com seguranca.",
            )
        }

        return AggregatedBiometricTemplate(normalized, minimum, mean, medoidIndex)
    }
}

data class TemporalFaceEvidence(
    val collaboratorId: String,
    val embedding: FloatArray,
    val score: Double,
    val secondScore: Double?,
    val catalogVersion: String,
    val model: String,
    val modelVersion: String,
    val trackingId: Int?,
    val capturedAtMillis: Long,
)

enum class TemporalConsensusRejection {
    IDENTITY_DISAGREEMENT,
    TRACK_CHANGED,
    CATALOG_CHANGED,
    SAMPLE_INCONSISTENT,
    INVALID_EMBEDDING,
}

sealed interface TemporalConsensusDecision {
    data class Pending(val count: Int, val required: Int) : TemporalConsensusDecision
    data class Confirmed(
        val collaboratorId: String,
        val embedding: FloatArray,
        val count: Int,
    ) : TemporalConsensusDecision
    data class Rejected(val reason: TemporalConsensusRejection) : TemporalConsensusDecision
}

/** Short, deterministic two-frame window. Each frame has already passed top-1/top-2 checks. */
class TemporalFaceConsensus(
    private val requiredMatches: Int = 2,
    private val windowMillis: Long = 3_000L,
    private val expectedDimension: Int = FACE_EMBEDDING_DIMENSION,
    private val minimumEmbeddingSimilarity: Double = BiometricTemplateAggregator.MINIMUM_INTRA_USER_SIMILARITY,
) {
    private val evidence = ArrayList<TemporalFaceEvidence>(requiredMatches)

    init {
        require(requiredMatches >= 2)
        require(windowMillis > 0L)
        require(minimumEmbeddingSimilarity in -1.0..1.0)
    }

    @Synchronized
    fun reset() {
        evidence.clear()
    }

    @Synchronized
    fun submit(sample: TemporalFaceEvidence): TemporalConsensusDecision {
        if (!FaceEmbeddingIntegrity.inspect(sample.embedding, expectedDimension).valid) {
            evidence.clear()
            return TemporalConsensusDecision.Rejected(TemporalConsensusRejection.INVALID_EMBEDDING)
        }

        val first = evidence.firstOrNull()
        if (first != null && sample.capturedAtMillis - first.capturedAtMillis !in 0..windowMillis) {
            evidence.clear()
        }

        val current = evidence.firstOrNull()
        if (current != null) {
            if (
                current.catalogVersion != sample.catalogVersion ||
                current.model != sample.model ||
                current.modelVersion != sample.modelVersion
            ) {
                evidence.clear()
                return TemporalConsensusDecision.Rejected(TemporalConsensusRejection.CATALOG_CHANGED)
            }
            if (
                current.trackingId != null && sample.trackingId != null &&
                current.trackingId != sample.trackingId
            ) {
                evidence.clear()
                return TemporalConsensusDecision.Rejected(TemporalConsensusRejection.TRACK_CHANGED)
            }
            if (current.collaboratorId != sample.collaboratorId) {
                evidence.clear()
                return TemporalConsensusDecision.Rejected(TemporalConsensusRejection.IDENTITY_DISAGREEMENT)
            }
            val directSimilarity = FaceEmbeddingIntegrity.cosine(current.embedding, sample.embedding)
            if (!directSimilarity.isFinite() || directSimilarity < minimumEmbeddingSimilarity) {
                evidence.clear()
                return TemporalConsensusDecision.Rejected(TemporalConsensusRejection.SAMPLE_INCONSISTENT)
            }
        }

        evidence += sample.copy(embedding = sample.embedding.copyOf())
        if (evidence.size < requiredMatches) {
            return TemporalConsensusDecision.Pending(evidence.size, requiredMatches)
        }

        val average = FloatArray(expectedDimension)
        evidence.forEach { item ->
            for (index in average.indices) average[index] += item.embedding[index]
        }
        for (index in average.indices) average[index] /= evidence.size.toFloat()
        val aggregateIntegrity = FaceEmbeddingIntegrity.inspect(
            average,
            expectedDimension,
            requireUnitNorm = false,
        )
        if (!aggregateIntegrity.valid) {
            evidence.clear()
            return TemporalConsensusDecision.Rejected(TemporalConsensusRejection.INVALID_EMBEDDING)
        }
        val normalized = FaceEmbeddingIntegrity.normalizedCopy(average, expectedDimension)
        val collaboratorId = evidence.first().collaboratorId
        val count = evidence.size
        evidence.clear()
        return TemporalConsensusDecision.Confirmed(collaboratorId, normalized, count)
    }
}

class RegistrationLease internal constructor(
    val epoch: Long,
    val collaboratorId: String,
)

/** Guards stale coroutine results and duplicate point submissions independently. */
class RecognitionTransactionCoordinator {
    private val epoch = AtomicLong(0L)
    private val registration = AtomicReference<RegistrationLease?>(null)

    fun currentEpoch(): Long = epoch.get()

    fun newRecognitionSession(): Long = epoch.incrementAndGet()

    fun isCurrent(candidateEpoch: Long): Boolean = epoch.get() == candidateEpoch

    fun isCurrent(lease: RegistrationLease): Boolean =
        registration.get() === lease && isCurrent(lease.epoch)

    fun tryAcquireRegistration(candidateEpoch: Long, collaboratorId: String): RegistrationLease? {
        if (!isCurrent(candidateEpoch)) return null
        val lease = RegistrationLease(candidateEpoch, collaboratorId)
        if (!registration.compareAndSet(null, lease)) return null
        if (isCurrent(candidateEpoch)) return lease

        registration.compareAndSet(lease, null)
        return null
    }

    fun releaseRegistration(lease: RegistrationLease) {
        registration.compareAndSet(lease, null)
    }

    fun registrationLocked(): Boolean = registration.get() != null
}

data class BiometricRuntimeSnapshot(
    val bestScore: Double? = null,
    val secondScore: Double? = null,
    val margin: Double? = null,
    val candidateCount: Int = 0,
    val validTemplateCount: Int = 0,
    val rejectedFrameCount: Long = 0,
    val lastQualityRejection: String? = null,
    val temporalConsensusCount: Int = 0,
    val recognitionLatencyMillis: Long? = null,
    val inferenceCount: Int = 0,
    val modelVersion: String? = null,
    val catalogVersion: String? = null,
    val quarantinedTemplateCount: Int = 0,
)

/** Process-local, non-sensitive diagnostics. No images, vectors, IDs or names are retained. */
object BiometricRuntimeDiagnostics {
    private val rejectedFrames = AtomicLong(0L)

    @Volatile
    private var current = BiometricRuntimeSnapshot()

    @Synchronized
    fun recordQualityRejection(reason: String) {
        val total = rejectedFrames.incrementAndGet()
        current = current.copy(rejectedFrameCount = total, lastQualityRejection = reason)
    }

    @Synchronized
    fun recordCatalog(version: String, modelVersion: String, quarantinedTemplates: Int) {
        current = current.copy(
            catalogVersion = version,
            modelVersion = modelVersion,
            quarantinedTemplateCount = quarantinedTemplates,
        )
    }

    @Synchronized
    fun recordRecognition(
        bestScore: Double?,
        secondScore: Double?,
        candidateCount: Int,
        validTemplateCount: Int,
        consensusCount: Int,
        latencyMillis: Long,
        inferenceCount: Int,
        modelVersion: String,
        catalogVersion: String?,
    ) {
        current = current.copy(
            bestScore = bestScore,
            secondScore = secondScore,
            margin = if (bestScore != null && secondScore != null) bestScore - secondScore else null,
            candidateCount = candidateCount,
            validTemplateCount = validTemplateCount,
            temporalConsensusCount = consensusCount,
            recognitionLatencyMillis = latencyMillis,
            inferenceCount = inferenceCount,
            modelVersion = modelVersion,
            catalogVersion = catalogVersion,
        )
    }

    fun snapshot(): BiometricRuntimeSnapshot = current.copy(rejectedFrameCount = rejectedFrames.get())
}
