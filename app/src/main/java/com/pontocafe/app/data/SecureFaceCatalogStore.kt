package com.pontocafe.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.pontocafe.app.avatar.PontoAvatarRuntime
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


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
    val templatesRejeitados: Int = 0,
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

enum class LocalFaceRejectionReason {
    EMPTY_CATALOG,
    INVALID_EMBEDDING,
    NO_COMPATIBLE_TEMPLATE,
    BELOW_THRESHOLD,
    AMBIGUOUS,
}

data class LocalFaceEvaluation(
    val match: LocalFaceMatch?,
    val bestCandidate: Colaborador?,
    val bestScore: Double?,
    val secondScore: Double?,
    val margin: Double?,
    val candidateCount: Int,
    val validTemplateCount: Int,
    val rejectionReason: LocalFaceRejectionReason?,
)

data class EnrollmentDuplicateCheck(
    val duplicate: Boolean,
    val matchedCollaborador: Colaborador? = null,
    val score: Double? = null,
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
        val sanitized = sanitizeCatalog(catalog)
        writeEncrypted(sanitized)
        cachedCatalog = sanitized
        cacheLoaded = true
        LocalFaceMatcher.prepareCatalog(sanitized.templates)
        BiometricRuntimeDiagnostics.recordCatalog(
            sanitized.versao,
            sanitized.versaoModelo,
            sanitized.templatesRejeitados,
        )
    }

    private fun writeEncrypted(catalog: CachedFaceCatalog) {
        val plaintext = gson.toJson(catalog).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString(catalogKey, payload).commit()) {
            "Nao foi possivel persistir o catalogo facial atomico."
        }
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
            sanitizeCatalog(gson.fromJson(json, CachedFaceCatalog::class.java))
        }.getOrNull()

        if (catalog == null) {
            clear()
            return null
        }

        // Cura a cópia cifrada depois de colocar entradas ruins em quarentena,
        // evitando recontar os mesmos templates a cada reinício do processo.
        runCatching { writeEncrypted(catalog) }

        cachedCatalog = catalog
        cacheLoaded = true
        LocalFaceMatcher.prepareCatalog(catalog.templates)
        BiometricRuntimeDiagnostics.recordCatalog(
            catalog.versao,
            catalog.versaoModelo,
            catalog.templatesRejeitados,
        )
        return catalog
    }

    @Synchronized
    fun clear() {
        cachedCatalog = null
        cacheLoaded = true
        LocalFaceMatcher.clearPreparedCatalog()
        check(prefs.edit().remove(catalogKey).commit()) {
            "Nao foi possivel remover o catalogo facial cifrado."
        }
    }

    /** Quarantines bad entries without discarding unrelated valid identities. */
    private fun sanitizeCatalog(catalog: CachedFaceCatalog?): CachedFaceCatalog {
        val current = requireNotNull(catalog) { "Catalogo facial ausente." }
        require(current.versao.isNotBlank()) { "Versao do catalogo facial ausente." }
        require(current.modelo.isNotBlank() && current.versaoModelo.isNotBlank())
        require(current.limiar.isFinite() && current.limiar in 0.0..1.0)
        require(current.margem.isFinite() && current.margem in 0.0..1.0)
        require(current.sincronizadoEmMillis > 0L)

        val sourceTemplates = requireNotNull(current.templates)
        val accepted = ArrayList<CachedFaceTemplate>(sourceTemplates.size)
        val seenTemplateIds = HashSet<String>()
        val collaboratorIdentity = HashMap<String, Triple<String, String?, String?>>()
        var newlyRejected = 0

        for (template in sourceTemplates) {
            val sanitized = runCatching {
                val collaborator = requireNotNull(template.colaborador)
                require(runCatching { UUID.fromString(collaborator.id) }.isSuccess)
                require(collaborator.nome.isNotBlank())
                require(template.modelo == current.modelo && template.versaoModelo == current.versaoModelo)
                require(template.atualizadoEm.isNotBlank())

                val normalizedType = template.tipo?.uppercase() ?: "LEGADO"
                require(normalizedType in VALID_TEMPLATE_TYPES)
                val templateId = template.templateId?.trim()?.takeIf(String::isNotEmpty)
                require(templateId == null || templateId !in seenTemplateIds)

                FaceEmbeddingIntegrity.requireValid(
                    requireNotNull(template.embedding).toFloatArray(),
                    FACE_EMBEDDING_DIMENSION,
                )
                template.copy(tipo = normalizedType, templateId = templateId)
            }.getOrNull()
            val identity = sanitized?.colaborador?.let { Triple(it.nome, it.setor, it.turno) }
            val identityConflict = sanitized != null &&
                collaboratorIdentity[sanitized.colaborador.id]?.let { it != identity } == true
            if (sanitized == null || identityConflict) {
                newlyRejected += 1
            } else {
                collaboratorIdentity.putIfAbsent(sanitized.colaborador.id, requireNotNull(identity))
                sanitized.templateId?.let(seenTemplateIds::add)
                accepted += sanitized
            }
        }

        val unsafeIndexes = HashSet<Int>()
        for (left in accepted.indices) {
            for (right in left + 1 until accepted.size) {
                if (accepted[left].embedding != accepted[right].embedding) continue
                unsafeIndexes += right
                if (accepted[left].colaborador.id != accepted[right].colaborador.id) unsafeIndexes += left
            }
        }
        val valid = accepted.filterIndexed { index, _ -> index !in unsafeIndexes }
        newlyRejected += unsafeIndexes.size
        require(sourceTemplates.isEmpty() || valid.isNotEmpty()) {
            "O catalogo facial recebido nao contem nenhum template integro."
        }
        return current.copy(
            templates = valid,
            templatesRejeitados = current.templatesRejeitados.coerceAtLeast(0) + newlyRejected,
        )
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
        private val VALID_TEMPLATE_TYPES = setOf("LEGADO", "CONSOLIDADO", "AMOSTRA")
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
        val result = evaluateDetailed(embedding, catalog).match ?: return null
        PontoAvatarRuntime.recognized(result.colaborador.id, result.colaborador.avatarUrl)
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
        announce: Boolean = true,
    ): LocalFaceResolvedMatch? {
        if (embeddings.isEmpty()) return null

        evaluateDetailed(embeddings[0], catalog).match?.let { primary ->
            if (announce) PontoAvatarRuntime.recognized(primary.colaborador.id, primary.colaborador.avatarUrl)
            return LocalFaceResolvedMatch(
                match = primary,
                embedding = embeddings[0],
                candidateIndex = 0,
            )
        }

        val accepted = ArrayList<LocalFaceResolvedMatch>()
        val evaluations = ArrayList<LocalFaceEvaluation>()
        for (index in 1 until embeddings.size) {
            val embedding = embeddings[index]
            val evaluation = evaluateDetailed(embedding, catalog)
            evaluations += evaluation
            val match = evaluation.match ?: continue
            accepted += LocalFaceResolvedMatch(
                match = match,
                embedding = embedding,
                candidateIndex = index,
            )
        }

        if (accepted.isEmpty()) return null
        if (accepted.asSequence().map { it.match.colaborador.id }.distinct().count() != 1) return null
        val acceptedId = accepted.first().match.colaborador.id
        val contradictoryHighCandidate = evaluations.any { evaluation ->
            val bestCandidateId = evaluation.bestCandidate?.id
            bestCandidateId != null &&
                bestCandidateId != acceptedId &&
                (evaluation.bestScore ?: -1.0) >= catalog.limiar
        }
        if (contradictoryHighCandidate) return null

        val bestFallback = accepted.maxBy { it.match.score }
        if (announce) {
            PontoAvatarRuntime.recognized(
                bestFallback.match.colaborador.id,
                bestFallback.match.colaborador.avatarUrl,
            )
        }
        return bestFallback
    }

    /**
     * Rigor compensatório por pose, em 0.0..1.0, tal como devolvido por
     * [FaceCapturePolicy.identificationPoseStringency]. Zero reproduz exatamente o
     * comportamento anterior — é o padrão, para que nenhum chamador existente mude.
     * Acima de zero, ele apenas **sobe** o limiar e a margem exigidos: um rosto
     * fora da pose nominal precisa de evidência mais forte, nunca mais fraca.
     */
    fun evaluateDetailed(
        embedding: FloatArray,
        catalog: CachedFaceCatalog,
        poseStringency: Double = 0.0,
    ): LocalFaceEvaluation {
        if (catalog.templates.isEmpty()) {
            return rejected(LocalFaceRejectionReason.EMPTY_CATALOG)
        }
        val currentIntegrity = FaceEmbeddingIntegrity.inspect(embedding, FACE_EMBEDDING_DIMENSION)
        if (!currentIntegrity.valid) {
            return rejected(LocalFaceRejectionReason.INVALID_EMBEDDING)
        }
        val currentNorm = requireNotNull(currentIntegrity.norm)

        // O catálogo descriptografado já vive em RAM. Preparamos, uma única vez
        // por lista de templates, os FloatArrays, normas e grupos por pessoa. O
        // caminho por frame calcula apenas os produtos escalares inevitáveis.
        val index = preparedIndex(catalog)
        var best: BestTemplate? = null
        var second: BestTemplate? = null
        var candidateCount = 0
        var compatibleTemplateCount = 0
        for (collaborator in index.collaborators) {
            var collaboratorBest: BestTemplate? = null
            for (prepared in collaborator.templates) {
                if (
                    prepared.template.modelo != catalog.modelo ||
                    prepared.template.versaoModelo != catalog.versaoModelo
                ) continue
                if (prepared.embedding.size != embedding.size) continue
                compatibleTemplateCount += 1
                val score = cosine(prepared, embedding, currentNorm)
                if (!score.isFinite()) continue

                val currentBest = collaboratorBest
                if (currentBest == null || score > currentBest.score) {
                    collaboratorBest = BestTemplate(prepared.template, score)
                }
            }
            val candidate = collaboratorBest ?: continue
            candidateCount += 1
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

        val winner = best ?: return rejected(
            reason = LocalFaceRejectionReason.NO_COMPATIBLE_TEMPLATE,
            candidateCount = candidateCount,
            validTemplateCount = compatibleTemplateCount,
        )
        val secondScore = second?.score
        val margin = secondScore?.let { winner.score - it }
        // A penalidade é limitada e monotônica: no máximo +0.06 de limiar e +0.03
        // de margem na borda da banda estendida, nunca ultrapassando 1.0. Um frame
        // em pose nominal (stringency 0.0) enfrenta exatamente o limiar do catálogo.
        val stringency = poseStringency.coerceIn(0.0, 1.0)
        val requiredThreshold = (catalog.limiar + 0.06 * stringency).coerceAtMost(1.0)
        val requiredMargin = (catalog.margem + 0.03 * stringency).coerceAtMost(1.0)
        val match = LocalFaceMatch(
            colaborador = winner.template.colaborador,
            score = winner.score,
            segundoScore = secondScore,
        )
        val reason = when {
            winner.score < requiredThreshold -> LocalFaceRejectionReason.BELOW_THRESHOLD
            secondScore != null && winner.score - secondScore < requiredMargin ->
                LocalFaceRejectionReason.AMBIGUOUS
            else -> null
        }
        return LocalFaceEvaluation(
            match = match.takeIf { reason == null },
            bestCandidate = winner.template.colaborador,
            bestScore = winner.score,
            secondScore = secondScore,
            margin = margin,
            candidateCount = candidateCount,
            validTemplateCount = compatibleTemplateCount,
            rejectionReason = reason,
        )
    }

    /**
     * Best-effort pre-enrollment duplicate check against the catalog cached on
     * *this* device. Reuses [catalog]'s own server-calibrated recognition
     * threshold as the warning floor by default: a new template that would
     * already clear the bar that makes recognition accept a match is
     * definitionally a duplicate-recognition risk, not an arbitrary new
     * number. This only sees this device's local catalog snapshot — it is not
     * a replacement for a server-side authoritative check across all devices.
     */
    fun evaluateEnrollmentDuplicate(
        candidateEmbedding: FloatArray,
        catalog: CachedFaceCatalog,
        excludeCollaboratorId: String,
        warningThreshold: Double = catalog.limiar,
    ): EnrollmentDuplicateCheck {
        val integrity = FaceEmbeddingIntegrity.inspect(candidateEmbedding, FACE_EMBEDDING_DIMENSION)
        if (!integrity.valid || catalog.templates.isEmpty()) {
            return EnrollmentDuplicateCheck(duplicate = false)
        }
        val currentNorm = requireNotNull(integrity.norm)
        val index = preparedIndex(catalog)
        var best: BestTemplate? = null

        for (collaborator in index.collaborators) {
            for (prepared in collaborator.templates) {
                if (prepared.template.colaborador.id == excludeCollaboratorId) continue
                if (
                    prepared.template.modelo != catalog.modelo ||
                    prepared.template.versaoModelo != catalog.versaoModelo
                ) continue
                if (prepared.embedding.size != candidateEmbedding.size) continue
                val score = cosine(prepared, candidateEmbedding, currentNorm)
                if (!score.isFinite()) continue
                if (best == null || score > best!!.score) {
                    best = BestTemplate(prepared.template, score)
                }
            }
        }

        val winner = best ?: return EnrollmentDuplicateCheck(duplicate = false)
        return EnrollmentDuplicateCheck(
            duplicate = winner.score >= warningThreshold,
            matchedCollaborador = winner.template.colaborador,
            score = winner.score,
        )
    }

    private fun rejected(
        reason: LocalFaceRejectionReason,
        candidateCount: Int = 0,
        validTemplateCount: Int = 0,
    ) = LocalFaceEvaluation(
        match = null,
        bestCandidate = null,
        bestScore = null,
        secondScore = null,
        margin = null,
        candidateCount = candidateCount,
        validTemplateCount = validTemplateCount,
        rejectionReason = reason,
    )

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
            val integrity = FaceEmbeddingIntegrity.inspect(stored, FACE_EMBEDDING_DIMENSION)
            if (!integrity.valid) continue

            grouped.getOrPut(template.colaborador.id) { ArrayList() }
                .add(
                    PreparedTemplate(
                        template = template,
                        embedding = stored,
                        norm = requireNotNull(integrity.norm),
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
