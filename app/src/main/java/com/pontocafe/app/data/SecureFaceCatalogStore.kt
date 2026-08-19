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

    fun save(catalog: CachedFaceCatalog) {
        val plaintext = gson.toJson(catalog).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(catalogKey, payload).apply()
        cachedCatalog = catalog
        cacheLoaded = true
    }

    fun read(): CachedFaceCatalog? {
        if (cacheLoaded) return cachedCatalog
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
            gson.fromJson(json, CachedFaceCatalog::class.java)
        }.getOrNull()

        if (catalog == null) {
            clear()
            return null
        }

        cachedCatalog = catalog
        cacheLoaded = true
        return catalog
    }

    fun clear() {
        cachedCatalog = null
        cacheLoaded = true
        prefs.edit().remove(catalogKey).apply()
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
}

object LocalFaceMatcher {
    private data class BestTemplate(
        val template: CachedFaceTemplate,
        val score: Double,
    )

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

        // Templates da mesma pessoa não competem entre si pela margem. Primeiro
        // obtemos o melhor score por colaborador e só então comparamos identidades.
        val bestByCollaborator = HashMap<String, BestTemplate>(catalog.templates.size.coerceAtMost(256))
        for (template in catalog.templates) {
            val stored = template.embedding
            if (stored.size != embedding.size) continue
            val score = cosine(stored, embedding, currentNorm)
            if (!score.isFinite()) continue

            val collaboratorId = template.colaborador.id
            val currentBest = bestByCollaborator[collaboratorId]
            if (currentBest == null || score > currentBest.score) {
                bestByCollaborator[collaboratorId] = BestTemplate(template, score)
            }
        }

        var best: BestTemplate? = null
        var second: BestTemplate? = null
        for (candidate in bestByCollaborator.values) {
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

    private fun cosine(stored: List<Float>, current: FloatArray, currentNorm: Double): Double {
        var dot = 0.0
        var storedNormSquared = 0.0
        for (index in current.indices) {
            val a = stored[index].toDouble()
            val b = current[index].toDouble()
            dot += a * b
            storedNormSquared += a * a
        }
        if (storedNormSquared <= 0.0 || currentNorm <= 0.0) return Double.NaN
        return dot / (sqrt(storedNormSquared) * currentNorm)
    }
}
