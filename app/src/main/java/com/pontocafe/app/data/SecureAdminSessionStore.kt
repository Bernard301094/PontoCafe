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

class SecureAdminSessionStore(
    context: Context,
    namespace: String = "admin",
) {
    private val safeNamespace = namespace.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    private val prefs = context.getSharedPreferences("pontocafe_${safeNamespace}_secure", Context.MODE_PRIVATE)
    private val keyAlias = "pontocafe_${safeNamespace}_session_key"
    private val tokenKey = "${safeNamespace}_bearer_token"

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cacheLoaded: Boolean = false

    fun hasToken(): Boolean = cachedToken != null || prefs.contains(tokenKey)

    fun save(token: String) {
        require(token.isNotBlank()) { "Sessão vazia." }
        val normalized = token.trim()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(tokenKey, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
            .apply()
        cachedToken = normalized
        cacheLoaded = true
    }

    fun read(): String? {
        if (cacheLoaded) return cachedToken
        val payload = prefs.getString(tokenKey, null)
        if (payload == null) {
            cachedToken = null
            cacheLoaded = true
            return null
        }

        val decrypted = runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > 12)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()

        if (decrypted == null) {
            clear()
            return null
        }

        cachedToken = decrypted
        cacheLoaded = true
        return decrypted
    }

    fun clear() {
        cachedToken = null
        cacheLoaded = true
        prefs.edit().remove(tokenKey).apply()
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
