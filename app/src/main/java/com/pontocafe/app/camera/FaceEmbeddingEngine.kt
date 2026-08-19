package com.pontocafe.app.camera

/**
 * Contrato do motor de embeddings faciais usado na identificação 1:N.
 *
 * O ML Kit cuida da detecção do rosto e da prova de vida por piscada.
 * Este motor transforma o rosto capturado no mesmo vetor numérico usado no
 * cadastro biométrico. A API compara esse vetor com os colaboradores ativos e
 * só devolve uma identidade quando a correspondência é suficientemente segura.
 */
interface FaceEmbeddingEngine {
    val isReady: Boolean
    val modelName: String
    val modelVersion: String

    suspend fun warmUp() = Unit

    /**
     * Embedding canônico usado no cadastro biométrico. Deve permanecer estável
     * para que todas as biometrias existentes continuem comparáveis.
     */
    suspend fun embed(frame: FaceFrame): FloatArray

    /**
     * Identificação adaptativa a partir de UMA captura.
     *
     * A primeira tentativa é sempre o mesmo embedding canônico de [embed]. A
     * implementação só deve produzir uma tentativa alternativa quando
     * [accepted] devolver false para a anterior. Assim o caso comum continua
     * com uma única inferência FaceNet e os fallbacks só custam CPU quando são
     * realmente necessários.
     *
     * [embeddings] contém apenas as tentativas realmente executadas e permite
     * reavaliá-las caso o catálogo seja atualizado após um miss.
     */
    suspend fun embedForIdentification(
        frame: FaceFrame,
        accepted: suspend (FloatArray) -> Boolean,
    ): FaceIdentificationEmbeddings {
        val canonical = embed(frame)
        val acceptedIndex = if (accepted(canonical)) 0 else null
        return FaceIdentificationEmbeddings(
            embeddings = listOf(canonical),
            acceptedIndex = acceptedIndex,
        )
    }
}

data class FaceIdentificationEmbeddings(
    val embeddings: List<FloatArray>,
    val acceptedIndex: Int?,
)

class FaceModelUnavailableException : IllegalStateException(
    "O módulo de reconhecimento facial ainda não foi instalado neste dispositivo.",
)

object MissingFaceEmbeddingEngine : FaceEmbeddingEngine {
    override val isReady: Boolean = false
    override val modelName: String = "não configurado"
    override val modelVersion: String = "0"

    override suspend fun embed(frame: FaceFrame): FloatArray {
        throw FaceModelUnavailableException()
    }
}
