package com.pontocafe.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.pontocafe.app.avatar.PontoAvatarRuntime
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.sqrt


data class CachedFaceTemplate(
    val colaborador: Colaborador,
    val embedding: List<Float>,
    val modelo: String,
    val versaoModelo: String,
    val atualizadoEm: String,
    val templateId: String? = null,
    val tipo: String? = null,
)

data class CachedFaceCatalog(
    val versao: String,
    val modelo: String,
    val versaoModelo: String,
    val limiar: Double,
    val margem: Double,
    val templates: List<CachedFaceTemplate>,
    val sincronizadoEmMillis: Long,
) {
    val totalColaboradores: Int
        get() = templates.asSequence().map { it.colaborador.id }.distinct().count()
}

data class LocalFaceMatch(
    val colaborador: Colaborador,
    val score: Double,
    val segundoScore: Double?,
)

data class LocalFaceResolvedMatch(
    val match: LocalFaceMatch,
    val embedding: FloatArray,
    val candidateIndex: Int,
)

class SecureFaceCatalogStore(context: Context) {
    private val prefs = context.getSharedPreferences("pontocafe_face_catalog_secure", Context.MODE_PRIVATE)
    private val keyAlias = "pontocafe_face_catalog_key"
    private val catalogKey = "catalogo_facial"
    private val gson = Gson()

    @Volatile
    private var cachedCatalog: CachedFaceCatalog? = null

    @Volatile
    private var cacheLoaded: Boolean = false

    @Synchronized
    fun save(catalog: CachedFaceCatalog) {
        validateCatalog(catalog)
        val plaintext = gson.toJson(catalog).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(catalogKey, payload).apply()
        cachedCatalog = catalog
        cacheLoaded = true
        LocalFaceMatcher.prepareCatalog(catalog.templates)
    }

    @Synchronized
    fun read(): CachedFaceCatalog? {
        if (cacheLoaded) {
            return cachedCatalog?.also { LocalFaceMatcher.prepareCatalog(it.templates) }
        }
        val payload = prefs.getString(catalogKey, null)
        if (payload == null) {
            cachedCatalog = null
            cacheLoaded = true
            return null
        }

        val catalog = runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > 12)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            validateCatalog(gson.fromJson(json, CachedFaceCatalog::class.java))
        }.getOrNull()

        if (catalog == null) {
            clear()
            return null
        }

        cachedCatalog = catalog
        cacheLoaded = true
        LocalFaceMatcher.prepareCatalog(catalog.templates)
        return catalog
    }

    @Synchronized
    fun clear() {
        cachedCatalog = null
        cacheLoaded = true
        LocalFaceMatcher.clearPreparedCatalog()
        prefs.edit().remove(catalogKey).apply()
    }

    private fun validateCatalog(catalog: CachedFaceCatalog?): CachedFaceCatalog {
        val current = requireNotNull(catalog) { "Catálogo facial ausente." }
        require(current.versao.isNotBlank()) { "Versão do catálogo facial ausente." }
        require(current.modelo.isNotBlank() && current.versaoModelo.isNotBlank()) {
            "Modelo do catálogo facial ausente."
        }
        require(current.limiar.isFinite() && current.limiar in 0.0..1.0) {
            "Limiar facial inválido."
        }
        require(current.margem.isFinite() && current.margem in 0.0..1.0) {
            "Margem facial inválida."
        }
        require(current.sincronizadoEmMillis > 0L) { "Data de sincronização facial inválida." }

        for (template in requireNotNull(current.templates) { "Templates faciais ausentes." }) {
            val collaborator = requireNotNull(template.colaborador) { "Colaborador facial ausente." }
            require(collaborator.id.isNotBlank() && collaborator.nome.isNotBlank()) {
                "Identidade do template facial inválida."
            }
            require(template.modelo.isNotBlank() && template.versaoModelo.isNotBlank()) {
                "Modelo do template facial ausente."
            }
            val embedding = requireNotNull(template.embedding) { "Embedding facial ausente." }
            require(embedding.size in MIN_EMBEDDING_SIZE..MAX_EMBEDDING_SIZE) {
                "Dimensão do embedding facial inválida."
            }
            var normSquared = 0.0
            for (value in embedding) {
                require(value.isFinite()) { "Embedding facial não finito." }
                normSquared += value.toDouble() * value.toDouble()
            }
            require(normSquared.isFinite() && normSquared > 0.0) { "Embedding facial vazio." }
        }
        return current
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val MIN_EMBEDDING_SIZE = 64
        private const val MAX_EMBEDDING_SIZE = 2_048
    }
}

object LocalFaceMatcher {
    private data class BestTemplate(
        val template: CachedFaceTemplate,
        val score: Double,
    )

    private data class PreparedTemplate(
        val template: CachedFaceTemplate,
        val embedding: FloatArray,
        val norm: Double,
    )

    private data class PreparedCollaborator(
        val templates: List<PreparedTemplate>,
    )

    private data class PreparedCatalog(
        val sourceTemplates: List<CachedFaceTemplate>,
        val collaborators: List<PreparedCollaborator>,
    )

    private val indexLock = Any()

    @Volatile
    private var preparedCatalog: PreparedCatalog? = null

    internal fun prepareCatalog(templates: List<CachedFaceTemplate>) {
        if (preparedCatalog?.sourceTemplates === templates) return
        synchronized(indexLock) {
            if (preparedCatalog?.sourceTemplates !== templates) {
                preparedCatalog = buildIndex(templates)
            }
        }
    }

    internal fun clearPreparedCatalog() {
        synchronized(indexLock) { preparedCatalog = null }
    }

    /**
     * Mantém o comportamento histórico para um único embedding.
     */
    fun match(embedding: FloatArray, catalog: CachedFaceCatalog): LocalFaceMatch? {
        val result = evaluate(embedding, catalog) ?: return null
        PontoAvatarRuntime.recognized(result.colaborador.avatarUrl)
        return result
    }

    /**
     * Identificação adaptativa sem baixar limiar nem margem.
     *
     * A tentativa canônica (índice 0) tem prioridade absoluta: quando ela passa,
     * o comportamento é exatamente o mesmo das versões anteriores. Somente se
     * ela falhar avaliamos os recortes alternativos da mesma captura. Cada um
     * precisa passar, sozinho, pelo mesmo limiar e pela mesma margem entre
     * pessoas. O embedding efetivamente vencedor é devolvido para que a API faça
     * novamente a validação autoritativa com o mesmo vetor.
     */
    fun matchBest(
        embeddings: List<FloatArray>,
        catalog: CachedFaceCatalog,
    ): LocalFaceResolvedMatch? {
        if (embeddings.isEmpty()) return null

        evaluate(embeddings[0], catalog)?.let { primary ->
            PontoAvatarRuntime.recognized(primary.colaborador.avatarUrl)
            return LocalFaceResolvedMatch(
                match = primary,
                embedding = embeddings[0],
                candidateIndex = 0,
            )
        }

        var bestFallback: LocalFaceResolvedMatch? = null
        for (index in 1 until embeddings.size) {
            val embedding = embeddings[index]
            val match = evaluate(embedding, catalog) ?: continue
            val current = bestFallback
            if (current == null || match.score > current.match.score) {
                bestFallback = LocalFaceResolvedMatch(
                    match = match,
                    embedding = embedding,
                    candidateIndex = index,
                )
            }
        }

        bestFallback?.let { PontoAvatarRuntime.recognized(it.match.colaborador.avatarUrl) }
        return bestFallback
    }

    private fun evaluate(embedding: FloatArray, catalog: CachedFaceCatalog): LocalFaceMatch? {
        if (embedding.isEmpty() || catalog.templates.isEmpty()) return null

        var currentNormSquared = 0.0
        for (value in embedding) {
            val doubleValue = value.toDouble()
            currentNormSquared += doubleValue * doubleValue
        }
        if (currentNormSquared <= 0.0) return null
        val currentNorm = sqrt(currentNormSquared)

        // O catálogo descriptografado já vive em RAM. Preparamos, uma única vez
        // por lista de templates, os FloatArrays, normas e grupos por pessoa. O
        // caminho por frame calcula apenas os produtos escalares inevitáveis.
        val index = preparedIndex(catalog)
        var best: BestTemplate? = null
        var second: BestTemplate? = null
        for (collaborator in index.collaborators) {
            var collaboratorBest: BestTemplate? = null
            for (prepared in collaborator.templates) {
                if (prepared.embedding.size != embedding.size) continue
                val score = cosine(prepared, embedding, currentNorm)
                if (!score.isFinite()) continue

                val currentBest = collaboratorBest
                if (currentBest == null || score > currentBest.score) {
                    collaboratorBest = BestTemplate(prepared.template, score)
                }
            }
            val candidate = collaboratorBest ?: continue
            when {
                best == null || candidate.score > best!!.score -> {
                    second = best
                    best = candidate
                }
                second == null || candidate.score > second!!.score -> {
                    second = candidate
                }
            }
        }

        val winner = best ?: return null
        val secondScore = second?.score
        if (winner.score < catalog.limiar) return null
        if (secondScore != null && winner.score - secondScore < catalog.margem) return null

        return LocalFaceMatch(
            colaborador = winner.template.colaborador,
            score = winner.score,
            segundoScore = secondScore,
        )
    }

    private fun preparedIndex(catalog: CachedFaceCatalog): PreparedCatalog {
        preparedCatalog?.takeIf { it.sourceTemplates === catalog.templates }?.let { return it }
        return synchronized(indexLock) {
            preparedCatalog?.takeIf { it.sourceTemplates === catalog.templates }
                ?: buildIndex(catalog.templates).also { preparedCatalog = it }
        }
    }

    private fun buildIndex(templates: List<CachedFaceTemplate>): PreparedCatalog {
        val grouped = linkedMapOf<String, MutableList<PreparedTemplate>>()
        for (template in templates) {
            if (template.embedding.isEmpty()) continue
            val stored = template.embedding.toFloatArray()
            var normSquared = 0.0
            for (value in stored) {
                val doubleValue = value.toDouble()
                normSquared += doubleValue * doubleValue
            }
            if (!normSquared.isFinite() || normSquared <= 0.0) continue

            grouped.getOrPut(template.colaborador.id) { ArrayList() }
                .add(
                    PreparedTemplate(
                        template = template,
                        embedding = stored,
                        norm = sqrt(normSquared),
                    ),
                )
        }
        return PreparedCatalog(
            sourceTemplates = templates,
            collaborators = grouped.values.map(::PreparedCollaborator),
        )
    }

    private fun cosine(stored: PreparedTemplate, current: FloatArray, currentNorm: Double): Double {
        var dot = 0.0
        for (index in current.indices) {
            val a = stored.embedding[index].toDouble()
            val b = current[index].toDouble()
            dot += a * b
        }
        if (stored.norm <= 0.0 || currentNorm <= 0.0) return Double.NaN
        return dot / (stored.norm * currentNorm)
    }
}
