package com.pontocafe.app.camera

/**
 * Contrato do motor de reconhecimento 1:1.
 *
 * ML Kit detecta o rosto e executa o liveness, mas não reconhece identidade.
 * A implementação real deste contrato deve gerar o mesmo embedding usado no cadastro.
 */
interface FaceEmbeddingEngine {
    val isReady: Boolean
    val modelName: String

    suspend fun embed(frame: FaceFrame): FloatArray
}

class FaceModelUnavailableException : IllegalStateException(
    "O modelo de reconhecimento facial ainda não foi instalado neste dispositivo.",
)

object MissingFaceEmbeddingEngine : FaceEmbeddingEngine {
    override val isReady: Boolean = false
    override val modelName: String = "não configurado"

    override suspend fun embed(frame: FaceFrame): FloatArray {
        throw FaceModelUnavailableException()
    }
}
