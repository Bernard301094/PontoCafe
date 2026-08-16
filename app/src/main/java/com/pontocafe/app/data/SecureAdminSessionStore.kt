package com.pontocafe.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class SavedRestrictedAccount(
    val id: String,
    val name: String,
    val email: String,
    val profile: String,
    val hasSession: Boolean,
)

class SecureAdminSessionStore(
    context: Context,
    namespace: String = "admin",
) {
    private val safeNamespace = namespace.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    private val prefs = context.getSharedPreferences("pontocafe_${safeNamespace}_secure", Context.MODE_PRIVATE)
    private val keyAlias = "pontocafe_${safeNamespace}_session_key"
    private val legacyTokenKey = "${safeNamespace}_bearer_token"
    private val accountIdsKey = "${safeNamespace}_account_ids"
    private val activeAccountKey = "${safeNamespace}_active_account_id"
    private val newLoginModeKey = "${safeNamespace}_new_login_mode"
    private val pendingEmailKey = "${safeNamespace}_pending_login_email"
    private val pendingProfileKey = "${safeNamespace}_pending_login_profile"

    fun hasToken(): Boolean = read() != null

    /**
     * Prepara uma autenticação sem guardar senha. O e-mail serve apenas para
     * identificar qual perfil local receberá a sessão cifrada se o login for aceito.
     * O modo de nova conta é controlado separadamente por beginNewLogin().
     */
    fun prepareLogin(email: String, profile: String) {
        val normalizedEmail = email.trim().lowercase()
        require(normalizedEmail.isNotBlank()) { "E-mail vazio." }
        prefs.edit()
            .putString(pendingEmailKey, normalizedEmail)
            .putString(pendingProfileKey, profile.trim().uppercase())
            .apply()
    }

    fun loginEmailSuggestion(): String =
        prefs.getString(pendingEmailKey, null)?.takeIf { it.isNotBlank() }
            ?: activeAccount()?.email.orEmpty()

    fun save(token: String) {
        require(token.isNotBlank()) { "Sessão vazia." }
        val normalized = token.trim()
        val pendingEmail = prefs.getString(pendingEmailKey, null)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val pendingProfile = prefs.getString(pendingProfileKey, null)?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

        if (pendingEmail != null) {
            val existing = savedAccounts().firstOrNull {
                it.email.equals(pendingEmail, ignoreCase = true) &&
                    (pendingProfile == null || it.profile.equals(pendingProfile, ignoreCase = true))
            }
            val accountId = existing?.id ?: pendingEmail
            val displayName = existing?.name?.takeIf { it.isNotBlank() }
                ?: pendingEmail.substringBefore('@').replace('.', ' ').replace('_', ' ')
                    .split(' ').filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase() else char.toString()
                        }
                    }
                    .ifBlank { pendingEmail }
            saveAccount(
                accountId = accountId,
                name = displayName,
                email = pendingEmail,
                profile = pendingProfile ?: safeNamespace.uppercase(),
                token = normalized,
            )
            prefs.edit().remove(pendingEmailKey).remove(pendingProfileKey).apply()
            return
        }

        val activeId = activeAccountId()
        val key = activeId?.let(::accountTokenKey) ?: legacyTokenKey
        prefs.edit().putString(key, encrypt(normalized)).apply()
    }

    fun saveAccount(
        accountId: String,
        name: String,
        email: String,
        profile: String,
        token: String,
    ) {
        require(accountId.isNotBlank()) { "Conta sem identificador." }
        require(token.isNotBlank()) { "Sessão vazia." }

        val normalizedId = accountId.trim()
        val metadata = JSONObject()
            .put("id", normalizedId)
            .put("name", name.trim().ifBlank { email.trim() })
            .put("email", email.trim().lowercase())
            .put("profile", profile.trim().uppercase())
            .toString()

        val ids = prefs.getStringSet(accountIdsKey, emptySet()).orEmpty().toMutableSet().apply {
            add(normalizedId)
        }

        prefs.edit()
            .putStringSet(accountIdsKey, ids)
            .putString(accountMetadataKey(normalizedId), encrypt(metadata))
            .putString(accountTokenKey(normalizedId), encrypt(token.trim()))
            .putString(activeAccountKey, normalizedId)
            .putBoolean(newLoginModeKey, false)
            .remove(legacyTokenKey)
            .apply()
    }

    fun savedAccounts(): List<SavedRestrictedAccount> {
        val ids = prefs.getStringSet(accountIdsKey, emptySet()).orEmpty()
        return ids.mapNotNull(::readAccount)
            .sortedWith(compareBy<SavedRestrictedAccount>({ it.profile }, { it.name.lowercase() }))
    }

    fun activate(accountId: String): Boolean {
        val account = readAccount(accountId) ?: return false
        prefs.edit()
            .putString(activeAccountKey, account.id)
            .putBoolean(newLoginModeKey, false)
            .remove(pendingEmailKey)
            .remove(pendingProfileKey)
            .apply()
        return account.hasSession
    }

    fun beginNewLogin() {
        prefs.edit()
            .remove(activeAccountKey)
            .remove(pendingEmailKey)
            .remove(pendingProfileKey)
            .putBoolean(newLoginModeKey, true)
            .apply()
    }

    fun activeAccountId(): String? {
        if (prefs.getBoolean(newLoginModeKey, false)) return null
        return prefs.getString(activeAccountKey, null)?.takeIf { it.isNotBlank() }
    }

    fun activeAccount(): SavedRestrictedAccount? {
        val id = prefs.getString(activeAccountKey, null)?.takeIf { it.isNotBlank() } ?: return null
        return readAccount(id)
    }

    fun forgetAccount(accountId: String) {
        val normalizedId = accountId.trim()
        if (normalizedId.isBlank()) return

        val ids = prefs.getStringSet(accountIdsKey, emptySet()).orEmpty().toMutableSet().apply {
            remove(normalizedId)
        }
        val edit = prefs.edit()
            .putStringSet(accountIdsKey, ids)
            .remove(accountMetadataKey(normalizedId))
            .remove(accountTokenKey(normalizedId))

        if (prefs.getString(activeAccountKey, null) == normalizedId) {
            edit.remove(activeAccountKey)
        }
        edit.apply()
    }

    fun read(): String? {
        if (prefs.getBoolean(newLoginModeKey, false)) return null
        val activeId = prefs.getString(activeAccountKey, null)?.takeIf { it.isNotBlank() }
        val storageKey = activeId?.let(::accountTokenKey) ?: legacyTokenKey
        return readEncrypted(storageKey)
    }

    /**
     * Encerra somente a sessão ativa. O perfil permanece salvo no seletor para
     * permitir novo login sem armazenar a senha.
     */
    fun clear() {
        val activeId = prefs.getString(activeAccountKey, null)?.takeIf { it.isNotBlank() }
        val storageKey = activeId?.let(::accountTokenKey) ?: legacyTokenKey
        prefs.edit().remove(storageKey).apply()
    }

    private fun readAccount(accountId: String): SavedRestrictedAccount? {
        val metadata = readEncrypted(accountMetadataKey(accountId)) ?: return null
        val json = runCatching { JSONObject(metadata) }.getOrNull() ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val email = json.optString("email")
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: email
        val profile = json.optString("profile").ifBlank { safeNamespace.uppercase() }
        return SavedRestrictedAccount(
            id = id,
            name = name,
            email = email,
            profile = profile,
            hasSession = readEncrypted(accountTokenKey(id)) != null,
        )
    }

    private fun accountStorageSuffix(accountId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(accountId.trim().lowercase().toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun accountMetadataKey(accountId: String) =
        "${safeNamespace}_account_${accountStorageSuffix(accountId)}_meta"

    private fun accountTokenKey(accountId: String) =
        "${safeNamespace}_account_${accountStorageSuffix(accountId)}_token"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun readEncrypted(storageKey: String): String? {
        val payload = prefs.getString(storageKey, null) ?: return null
        val decrypted = runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > 12)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()

        if (decrypted == null) prefs.edit().remove(storageKey).apply()
        return decrypted
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
