package com.pontocafe.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private data class PendingPontoOperation(
    val operationId: String,
    val collaboratorId: String,
    val embeddingFingerprint: String,
    val createdAtMillis: Long,
    val uncertain: Boolean = false,
)

private data class PontoOperationJournalPayload(
    val operations: List<PendingPontoOperation> = emptyList(),
)

/**
 * Diário mínimo e cifrado de operações críticas do Ponto.
 *
 * Não guarda foto, embedding, token de sessão, PIN ou senha. O único vínculo
 * biométrico persistido é um SHA-256 dos bits do embedding já calculado em RAM.
 * Isso permite que a MESMA tentativa atravesse uma resposta de rede incerta e
 * vire evento offline sem receber outro UUID.
 */
class PontoOperationJournal(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun prepare(collaboratorId: String, embedding: FloatArray): String {
        require(collaboratorId.isNotBlank())
        require(embedding.isNotEmpty() && embedding.all { it.isFinite() })

        val now = System.currentTimeMillis()
        val fingerprint = fingerprint(embedding)
        val active = read().operations.filter { now - it.createdAtMillis <= OPERATION_TTL_MILLIS }

        // Uma resposta incerta tem prioridade mesmo se uma nova captura produzir
        // embedding ligeiramente diferente. Primeiro precisamos reconciliar a
        // mutação que pode já ter sido COMMITada pelo servidor.
        val uncertain = active.firstOrNull { it.collaboratorId == collaboratorId && it.uncertain }
        if (uncertain != null) {
            if (active.size != read().operations.size) write(PontoOperationJournalPayload(active))
            return uncertain.operationId
        }

        val sameAttempt = active.firstOrNull {
            it.collaboratorId == collaboratorId && it.embeddingFingerprint == fingerprint
        }
        if (sameAttempt != null) {
            if (active.size != read().operations.size) write(PontoOperationJournalPayload(active))
            return sameAttempt.operationId
        }

        val operation = PendingPontoOperation(
            operationId = UUID.randomUUID().toString(),
            collaboratorId = collaboratorId,
            embeddingFingerprint = fingerprint,
            createdAtMillis = now,
        )
        val next = active.filterNot { it.collaboratorId == collaboratorId } + operation
        write(PontoOperationJournalPayload(next.takeLast(MAX_OPERATIONS)))
        return operation.operationId
    }

    @Synchronized
    fun markUncertain(operationId: String) {
        val current = read()
        val next = current.operations.map {
            if (it.operationId == operationId) it.copy(uncertain = true) else it
        }
        if (next != current.operations) write(current.copy(operations = next))
    }

    @Synchronized
    fun clearUncertain(operationId: String) {
        val current = read()
        val next = current.operations.map {
            if (it.operationId == operationId) it.copy(uncertain = false) else it
        }
        if (next != current.operations) write(current.copy(operations = next))
    }

    @Synchronized
    fun isUncertain(collaboratorId: String): Boolean {
        val now = System.currentTimeMillis()
        val current = read()
        val active = current.operations.filter { now - it.createdAtMillis <= OPERATION_TTL_MILLIS }
        if (active.size != current.operations.size) write(current.copy(operations = active))
        return active.any { it.collaboratorId == collaboratorId && it.uncertain }
    }

    @Synchronized
    fun complete(operationId: String) {
        val current = read()
        val next = current.operations.filterNot { it.operationId == operationId }
        if (next != current.operations) write(current.copy(operations = next))
    }

    @Synchronized
    fun completeForCollaborator(collaboratorId: String) {
        val current = read()
        val next = current.operations.filterNot { it.collaboratorId == collaboratorId }
        if (next != current.operations) write(current.copy(operations = next))
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(PAYLOAD_KEY).apply()
    }

    private fun fingerprint(embedding: FloatArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(Float.SIZE_BYTES)
        embedding.forEach { value ->
            buffer.clear()
            buffer.putInt(java.lang.Float.floatToIntBits(value))
            digest.update(buffer.array())
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun read(): PontoOperationJournalPayload {
        val payload = prefs.getString(PAYLOAD_KEY, null) ?: return PontoOperationJournalPayload()
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > 12)
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            gson.fromJson(json, PontoOperationJournalPayload::class.java) ?: PontoOperationJournalPayload()
        }.getOrElse {
            prefs.edit().remove(PAYLOAD_KEY).apply()
            PontoOperationJournalPayload()
        }
    }

    private fun write(payload: PontoOperationJournalPayload) {
        val plaintext = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val encoded = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(PAYLOAD_KEY, encoded).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
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
        private const val PREFS_NAME = "pontocafe_ponto_operations_secure"
        private const val PAYLOAD_KEY = "operation_journal"
        private const val KEY_ALIAS = "pontocafe_ponto_operations_key"
        private const val OPERATION_TTL_MILLIS = 30L * 60L * 1000L
        private const val MAX_OPERATIONS = 32
    }
}
