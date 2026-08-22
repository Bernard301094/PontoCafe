package com.pontocafe.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda somente tokens curtos de ATIVAÇÃO ainda pendentes para que o
 * Administrador possa consultá-los no cartão do aparelho.
 *
 * A credencial longa recebida pelo Ponto depois da ativação nunca passa por
 * este store e continua conhecida apenas pelo próprio dispositivo.
 */
class SecureAdminDeviceActivationTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "pontocafe_admin_device_activation_tokens",
        Context.MODE_PRIVATE,
    )
    private val keyAlias = "pontocafe_admin_device_activation_tokens_key_v1"
    private val indexKey = "device_ids"

    /**
     * Retorna true somente quando o token ficou cifrado e persistido. Falha do
     * AndroidKeyStore nunca derruba a tela administrativa: tentamos recriar a
     * chave uma única vez e, se ainda assim falhar, mantemos o token apenas no
     * estado já exibido pela UI atual, sem fallback inseguro em texto puro.
     */
    @Synchronized
    fun save(deviceId: String, token: String): Boolean {
        val cleanDeviceId = deviceId.trim()
        val cleanToken = token.trim()
        require(cleanDeviceId.isNotBlank()) { "Dispositivo inválido." }
        require(TOKEN_PATTERN.matches(cleanToken)) { "Token de ativação inválido." }

        val payload = encryptWithRecovery(cleanDeviceId, cleanToken) ?: return false
        val ids = storedIds().toMutableSet().apply { add(cleanDeviceId) }
        return runCatching {
            prefs.edit()
                .putString(tokenKey(cleanDeviceId), payload)
                .putStringSet(indexKey, ids)
                .commit()
        }.getOrDefault(false)
    }

    @Synchronized
    fun read(deviceId: String): String? {
        val cleanDeviceId = deviceId.trim()
        if (cleanDeviceId.isBlank()) return null
        val payload = runCatching { prefs.getString(tokenKey(cleanDeviceId), null) }.getOrNull() ?: return null

        val token = decrypt(cleanDeviceId, payload)?.takeIf { TOKEN_PATTERN.matches(it) }
        if (token == null) {
            // Uma chave inválida torna todos os tokens deste namespace ilegíveis.
            // Limpamos somente este armazenamento local e permitimos que novos
            // tokens sejam gerados de forma segura pelo Administrador.
            resetEncryptedState(deleteKeystoreKey = true)
        }
        return token
    }

    @Synchronized
    fun remove(deviceId: String) {
        val cleanDeviceId = deviceId.trim()
        if (cleanDeviceId.isBlank()) return
        val ids = storedIds().toMutableSet().apply { remove(cleanDeviceId) }
        runCatching {
            prefs.edit()
                .remove(tokenKey(cleanDeviceId))
                .putStringSet(indexKey, ids)
                .commit()
        }
    }

    /**
     * Remove segredos que já foram consumidos, bloqueados ou excluídos. O caller
     * deve passar apenas IDs que continuam aguardando ativação no servidor.
     */
    @Synchronized
    fun reconcile(pendingDeviceIds: Set<String>): Boolean {
        val normalized = pendingDeviceIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val currentIds = storedIds()
        val stale = currentIds - normalized
        if (stale.isEmpty()) return true

        return runCatching {
            val editor = prefs.edit()
            stale.forEach { editor.remove(tokenKey(it)) }
            editor.putStringSet(indexKey, currentIds - stale)
            editor.commit()
        }.getOrDefault(false)
    }

    private fun storedIds(): Set<String> = runCatching {
        prefs.getStringSet(indexKey, emptySet())?.toSet().orEmpty()
    }.getOrElse {
        runCatching { prefs.edit().remove(indexKey).commit() }
        emptySet()
    }

    private fun encryptWithRecovery(deviceId: String, token: String): String? {
        runCatching { encrypt(deviceId, token) }.getOrNull()?.let { return it }

        resetEncryptedState(deleteKeystoreKey = true)
        return runCatching { encrypt(deviceId, token) }.getOrNull()
    }

    private fun encrypt(deviceId: String, token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(deviceId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(deviceId: String, payload: String): String? = runCatching {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Token cifrado inválido." }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(deviceId.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun resetEncryptedState(deleteKeystoreKey: Boolean) {
        runCatching {
            val editor = prefs.edit()
            prefs.all.keys
                .asSequence()
                .filter { it.startsWith(TOKEN_KEY_PREFIX) }
                .forEach(editor::remove)
            editor.remove(indexKey)
            editor.commit()
        }

        if (!deleteKeystoreKey) return
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }
    }

    private fun tokenKey(deviceId: String): String = "$TOKEN_KEY_PREFIX$deviceId"

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

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TOKEN_KEY_PREFIX = "activation_token_"
        val TOKEN_PATTERN = Regex("^[A-Za-z0-9]{10}$")
    }
}
