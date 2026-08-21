package com.pontocafe.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BiometricSecurityPolicyTest {

    @Test
    fun `integridade exige 128 dimensoes valores finitos e norma l2`() {
        assertTrue(FaceEmbeddingIntegrity.inspect(unit(0)).valid)
        assertEquals(
            EmbeddingIntegrityIssue.WRONG_DIMENSION,
            FaceEmbeddingIntegrity.inspect(floatArrayOf(1f, 0f)).issue,
        )
        assertEquals(
            EmbeddingIntegrityIssue.NON_FINITE,
            FaceEmbeddingIntegrity.inspect(unit(0).also { it[3] = Float.NaN }).issue,
        )
        assertEquals(
            EmbeddingIntegrityIssue.ZERO_NORM,
            FaceEmbeddingIntegrity.inspect(FloatArray(FACE_EMBEDDING_DIMENSION)).issue,
        )
        assertEquals(
            EmbeddingIntegrityIssue.NOT_L2_NORMALIZED,
            FaceEmbeddingIntegrity.inspect(unit(0).also { it[0] = 0.5f }).issue,
        )
    }

    @Test
    fun `agregador consolida sequencia coerente e recusa troca de pessoa`() {
        val coherent = listOf(
            unit(0),
            unit(0),
            mixed(0.98f, 0.1989975f),
            mixed(0.97f, 0.243105f),
            mixed(0.99f, 0.1410674f),
        )

        val aggregate = BiometricTemplateAggregator.aggregate(coherent)

        assertTrue(FaceEmbeddingIntegrity.inspect(aggregate.embedding).valid)
        assertTrue(aggregate.minimumSimilarityToMedoid >= 0.60)
        assertThrows(BiometricSampleConsistencyException::class.java) {
            BiometricTemplateAggregator.aggregate(
                listOf(unit(0), unit(0), unit(0), unit(0), unit(1)),
            )
        }
    }

    @Test
    fun `consenso exige mesma identidade track catalogo e janela`() {
        val consensus = TemporalFaceConsensus()
        val pending = consensus.submit(evidence("a", unit(0), trackingId = 7, capturedAt = 1_000))
        val confirmed = consensus.submit(evidence("a", unit(0), trackingId = 7, capturedAt = 1_250))

        assertEquals(TemporalConsensusDecision.Pending(1, 2), pending)
        assertTrue(confirmed is TemporalConsensusDecision.Confirmed)
        confirmed as TemporalConsensusDecision.Confirmed
        assertEquals("a", confirmed.collaboratorId)
        assertArrayEquals(unit(0), confirmed.embedding, 0.0001f)

        assertTrue(consensus.submit(evidence("a", unit(0), 7, 2_000)) is TemporalConsensusDecision.Pending)
        assertEquals(
            TemporalConsensusRejection.IDENTITY_DISAGREEMENT,
            (consensus.submit(evidence("b", unit(1), 7, 2_100)) as TemporalConsensusDecision.Rejected).reason,
        )

        assertTrue(consensus.submit(evidence("a", unit(0), 7, 3_000)) is TemporalConsensusDecision.Pending)
        assertEquals(
            TemporalConsensusRejection.TRACK_CHANGED,
            (consensus.submit(evidence("a", unit(0), 8, 3_100)) as TemporalConsensusDecision.Rejected).reason,
        )

        assertTrue(consensus.submit(evidence("a", unit(0), 7, 4_000)) is TemporalConsensusDecision.Pending)
        assertEquals(
            TemporalConsensusRejection.CATALOG_CHANGED,
            (
                consensus.submit(
                    evidence("a", unit(0), 7, 4_100).copy(catalogVersion = "catalog-2"),
                ) as TemporalConsensusDecision.Rejected
                ).reason,
        )

        assertTrue(consensus.submit(evidence("a", unit(0), 7, 5_000)) is TemporalConsensusDecision.Pending)
        assertEquals(
            TemporalConsensusRejection.SAMPLE_INCONSISTENT,
            (consensus.submit(evidence("a", unit(1), 7, 5_100)) as TemporalConsensusDecision.Rejected).reason,
        )
    }

    @Test
    fun `consenso reinicia janela vencida e recusa media degenerada`() {
        val consensus = TemporalFaceConsensus(
            windowMillis = 100,
            minimumEmbeddingSimilarity = -1.0,
        )
        assertTrue(consensus.submit(evidence("a", unit(0), 4, 1_000)) is TemporalConsensusDecision.Pending)
        val restarted = consensus.submit(evidence("a", unit(0), 4, 1_500))
        assertEquals(TemporalConsensusDecision.Pending(1, 2), restarted)

        consensus.reset()
        assertTrue(consensus.submit(evidence("a", unit(0), 4, 2_000)) is TemporalConsensusDecision.Pending)
        val opposite = unit(0).also { it[0] = -1f }
        val rejected = consensus.submit(evidence("a", opposite, 4, 2_050))
        assertEquals(
            TemporalConsensusRejection.INVALID_EMBEDDING,
            (rejected as TemporalConsensusDecision.Rejected).reason,
        )
    }

    @Test
    fun `coordenador descarta epoca antiga e bloqueia duplo registro`() {
        val coordinator = RecognitionTransactionCoordinator()
        val firstEpoch = coordinator.newRecognitionSession()
        val firstLease = coordinator.tryAcquireRegistration(firstEpoch, "a")

        assertNotNull(firstLease)
        assertTrue(coordinator.isCurrent(requireNotNull(firstLease)))
        assertTrue(coordinator.registrationLocked())
        assertNull(coordinator.tryAcquireRegistration(firstEpoch, "a"))

        coordinator.releaseRegistration(requireNotNull(firstLease))
        assertFalse(coordinator.registrationLocked())
        assertNotNull(coordinator.tryAcquireRegistration(firstEpoch, "a")?.also(coordinator::releaseRegistration))

        coordinator.newRecognitionSession()
        assertFalse(coordinator.isCurrent(requireNotNull(firstLease)))
        assertNull(coordinator.tryAcquireRegistration(firstEpoch, "a"))
    }

    private fun unit(index: Int): FloatArray =
        FloatArray(FACE_EMBEDDING_DIMENSION).also { it[index] = 1f }

    private fun mixed(first: Float, second: Float): FloatArray =
        FloatArray(FACE_EMBEDDING_DIMENSION).also {
            it[0] = first
            it[1] = second
        }

    private fun evidence(
        collaboratorId: String,
        embedding: FloatArray,
        trackingId: Int?,
        capturedAt: Long,
    ) = TemporalFaceEvidence(
        collaboratorId = collaboratorId,
        embedding = embedding,
        score = 0.90,
        secondScore = 0.70,
        catalogVersion = "catalog-1",
        model = "FaceNet 128D · LiteRT",
        modelVersion = "facenet-128d-160-v1",
        trackingId = trackingId,
        capturedAtMillis = capturedAt,
    )
}
