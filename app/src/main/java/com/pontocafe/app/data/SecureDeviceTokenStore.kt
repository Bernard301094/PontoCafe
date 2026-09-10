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

class SecureDeviceTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pontocafe_secure", Context.MODE_PRIVATE)
    private val keyAlias = "pontocafe_device_token_key"
    private val tokenKey = "device_token"

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cacheLoaded: Boolean = false

    /**
     * Valida a credencial cifrada em vez de confiar apenas na existência da chave
     * do SharedPreferences. Assim uma chave do AndroidKeyStore perdida/corrompida
     * nunca coloca o app em modo Ponto com uma credencial que não pode ser lida.
     */
    fun hasToken(): Boolean = read() != null

    /**
     * Persiste a credencial longa devolvida pelo servidor. Em alguns aparelhos o
     * AndroidKeyStore pode manter uma entrada inválida depois de restauração,
     * troca de assinatura ou falha do provedor. Nesse caso removemos somente o
     * material local desta credencial e tentamos recriar a chave uma única vez.
     *
     * O retorno indica se a credencial ficou realmente persistida. Nenhum segredo
     * é enviado a logs ou armazenado em texto puro como fallback.
     */
    @Synchronized
    fun save(token: String): Boolean {
        require(token.isNotBlank()) { "Token vazio." }
        val normalized = token.trim()
        val payload = encryptWithRecovery(normalized) ?: run {
            cachedToken = null
            cacheLoaded = true
            return false
        }

        val persisted = runCatching {
            prefs.edit().putString(tokenKey, payload).commit()
        }.getOrDefault(false)

        if (!persisted) {
            cachedToken = null
            cacheLoaded = false
            return false
        }

        cachedToken = normalized
        cacheLoaded = true
        return true
    }

    @Synchronized
    fun read(): String? {
        if (cacheLoaded) return cachedToken
        val payload = runCatching { prefs.getString(tokenKey, null) }.getOrNull()
        if (payload == null) {
            cachedToken = null
            cacheLoaded = true
            return null
        }

        val decrypted = decrypt(payload)
        if (decrypted == null || decrypted.isBlank()) {
            // Se a entrada cifrada não puder mais ser aberta, ela não serve como
            // credencial. Limpamos também a chave para permitir uma nova ativação
            // íntegra, sem deixar o aplicativo preso em um estado impossível.
            resetCredentialMaterial(deleteKeystoreKey = true)
            return null
        }

        cachedToken = decrypted
        cacheLoaded = true
        return decrypted
    }

    @Synchronized
    fun clear() {
        cachedToken = null
        cacheLoaded = true
        runCatching { prefs.edit().remove(tokenKey).commit() }
    }

    private fun encryptWithRecovery(value: String): String? {
        runCatching { encrypt(value) }.getOrNull()?.let { return it }

        // Uma única recuperação controlada. Não repetimos indefinidamente e não
        // degradamos para armazenamento sem criptografia.
        resetCredentialMaterial(deleteKeystoreKey = true)
        return runCatching { encrypt(value) }.getOrNull()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String? = runCatching {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES) { "Token cifrado inválido." }
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val encrypted = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun resetCredentialMaterial(deleteKeystoreKey: Boolean) {
        cachedToken = null
        cacheLoaded = true
        runCatching { prefs.edit().remove(tokenKey).commit() }

        if (!deleteKeystoreKey) return
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }
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

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
