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

    fun match(embedding: FloatArray, catalog: CachedFaceCatalog): LocalFaceMatch? {
        if (embedding.isEmpty() || catalog.templates.isEmpty()) return null

        var currentNormSquared = 0.0
        for (value in embedding) {
            val doubleValue = value.toDouble()
            currentNormSquared += doubleValue * doubleValue
        }
        if (currentNormSquared <= 0.0) return null
        val currentNorm = sqrt(currentNormSquared)

        // Vários templates da mesma pessoa representam aparências diferentes
        // (touca, óculos, sem acessórios, ângulos etc.). Eles não podem competir
        // entre si como se fossem pessoas diferentes. Primeiro calculamos apenas
        // o melhor score de cada colaborador. O norm do embedding atual também é
        // calculado uma única vez, evitando repetir o mesmo trabalho para cada rosto.
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

        // O avatar é apenas um efeito visual do match já aprovado. Não participa
        // do score, limiar, margem, liveness nem de qualquer decisão biométrica.
        PontoAvatarRuntime.recognized(winner.template.colaborador.avatarUrl)

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