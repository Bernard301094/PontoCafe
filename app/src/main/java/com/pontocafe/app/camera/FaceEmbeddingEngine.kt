package com.pontocafe.app.camera

/**
 * Contrato do motor de embeddings faciais usado na identificação 1:N.
 *
 * O ML Kit cuida da detecção do rosto e da prova de vida por piscada.
 * Este motor deve transformar o rosto capturado no mesmo vetor numérico usado
 * no cadastro biométrico. A API compara esse vetor com os colaboradores ativos
 * e só devolve uma identidade quando a correspondência é suficientemente segura.
 */
interface FaceEmbeddingEngine {
    val isReady: Boolean
    val modelName: String

    suspend fun embed(frame: FaceFrame): FloatArray
}

class FaceModelUnavailableException : IllegalStateException(
    "O módulo de reconhecimento facial ainda não foi instalado neste dispositivo.",
)

object MissingFaceEmbeddingEngine : FaceEmbeddingEngine {
    override val isReady: Boolean = false
    override val modelName: String = "não configurado"

    override suspend fun embed(frame: FaceFrame): FloatArray {
        throw FaceModelUnavailableException()
    }
}
