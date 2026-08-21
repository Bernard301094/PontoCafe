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

    fun save(deviceId: String, token: String) {
        val cleanDeviceId = deviceId.trim()
        val cleanToken = token.trim()
        require(cleanDeviceId.isNotBlank()) { "Dispositivo inválido." }
        require(TOKEN_PATTERN.matches(cleanToken)) { "Token de ativação inválido." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(cleanDeviceId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(cleanToken.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)

        val ids = storedIds().toMutableSet().apply { add(cleanDeviceId) }
        prefs.edit()
            .putString(tokenKey(cleanDeviceId), payload)
            .putStringSet(indexKey, ids)
            .apply()
    }

    fun read(deviceId: String): String? {
        val cleanDeviceId = deviceId.trim()
        if (cleanDeviceId.isBlank()) return null
        val payload = prefs.getString(tokenKey(cleanDeviceId), null) ?: return null

        val token = runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > IV_BYTES) { "Token cifrado inválido." }
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(cleanDeviceId.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()?.takeIf { TOKEN_PATTERN.matches(it) }

        if (token == null) remove(cleanDeviceId)
        return token
    }

    fun remove(deviceId: String) {
        val cleanDeviceId = deviceId.trim()
        if (cleanDeviceId.isBlank()) return
        val ids = storedIds().toMutableSet().apply { remove(cleanDeviceId) }
        prefs.edit()
            .remove(tokenKey(cleanDeviceId))
            .putStringSet(indexKey, ids)
            .apply()
    }

    /**
     * Remove segredos que já foram consumidos, bloqueados ou excluídos. O caller
     * deve passar apenas IDs que continuam aguardando ativação no servidor.
     */
    fun reconcile(pendingDeviceIds: Set<String>) {
        val normalized = pendingDeviceIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val currentIds = storedIds()
        val stale = currentIds - normalized
        if (stale.isEmpty()) return

        val editor = prefs.edit()
        stale.forEach { editor.remove(tokenKey(it)) }
        editor.putStringSet(indexKey, currentIds - stale)
        editor.apply()
    }

    private fun storedIds(): Set<String> =
        prefs.getStringSet(indexKey, emptySet())?.toSet().orEmpty()

    private fun tokenKey(deviceId: String): String = "activation_token_$deviceId"

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
        val TOKEN_PATTERN = Regex("^[A-Za-z0-9]{10}$")
    }
}
