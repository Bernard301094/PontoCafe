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

    /**
     * Permite preparar runtimes/modelos pesados antes da primeira captura.
     * Implementações que não precisam de aquecimento mantêm o comportamento
     * padrão sem custo e sem alterar o contrato existente.
     */
    suspend fun warmUp() = Unit

    /**
     * Embedding canônico usado no cadastro biométrico e mantido compatível com
     * todas as biometrias já existentes.
     */
    suspend fun embed(frame: FaceFrame): FloatArray

    /**
     * Gera candidatos para identificação a partir de UMA única captura.
     *
     * A primeira posição é sempre o embedding canônico. Depois de cada candidato
     * [shouldContinue] decide se ainda vale executar outra inferência. Assim o
     * caminho normal encerra após uma única passagem pelo FaceNet e os recortes
     * alternativos só custam CPU/GPU quando o canônico realmente não reconhece.
     */
    suspend fun embedForIdentification(
        frame: FaceFrame,
        shouldContinue: (embedding: FloatArray, candidateIndex: Int) -> Boolean = { _, _ -> true },
    ): List<FloatArray> {
        val canonical = embed(frame)
        shouldContinue(canonical, 0)
        return listOf(canonical)
    }
}

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
