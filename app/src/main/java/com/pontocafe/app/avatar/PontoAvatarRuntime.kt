package com.pontocafe.app.avatar

/**
 * Estado efêmero do último rosto reconhecido.
 *
 * Não contém biometria nem persiste dados. Mantém apenas URLs assinadas do
 * catálogo visual e transporta a URL reconhecida até o comprovante. Atualização
 * ou falha de avatar nunca altera embeddings nem o estado do catálogo facial.
 */
object PontoAvatarRuntime {
    @Volatile
    private var avatarCatalog: Map<String, String>? = null

    @Volatile
    private var avatarOverrides: Map<String, String?> = emptyMap()

    @Volatile
    var lastRecognizedAvatarUrl: String? = null
        private set

    @Synchronized
    fun recognized(collaboratorId: String, fallbackAvatarUrl: String? = null) {
        val currentCatalog = avatarCatalog
        val currentOverrides = avatarOverrides
        lastRecognizedAvatarUrl = when {
            currentOverrides.containsKey(collaboratorId) ->
                currentOverrides[collaboratorId]?.takeIf { it.isNotBlank() }
            currentCatalog != null -> currentCatalog[collaboratorId]?.takeIf { it.isNotBlank() }
            else -> fallbackAvatarUrl?.takeIf { it.isNotBlank() }
        }
    }

    /** Replaces the visual catalog atomically; missing IDs intentionally mean no avatar. */
    @Synchronized
    fun updateCatalog(avatars: Map<String, String>) {
        val sanitizedCatalog = avatars
            .asSequence()
            .filter { (id, url) -> id.isNotBlank() && url.isNotBlank() }
            .associate { it.key to it.value }
        avatarCatalog = sanitizedCatalog
        avatarOverrides = avatarOverrides.filter { (collaboratorId, localUrl) ->
            if (localUrl == null) {
                sanitizedCatalog.containsKey(collaboratorId)
            } else {
                sanitizedCatalog[collaboratorId] != localUrl
            }
        }
    }

    /** Makes a just-returned versioned URL visible immediately in this process. */
    @Synchronized
    fun avatarUpdated(collaboratorId: String, avatarUrl: String?) {
        if (collaboratorId.isBlank()) return
        avatarOverrides = avatarOverrides + (collaboratorId to avatarUrl?.takeIf { it.isNotBlank() })
    }

    @Synchronized
    fun clearCatalog() {
        avatarCatalog = null
        avatarOverrides = emptyMap()
        lastRecognizedAvatarUrl = null
    }

    fun clear() {
        lastRecognizedAvatarUrl = null
    }
}
