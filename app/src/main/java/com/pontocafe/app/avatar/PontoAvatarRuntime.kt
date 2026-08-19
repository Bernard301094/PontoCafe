package com.pontocafe.app.avatar

/**
 * Estado efêmero do último rosto reconhecido.
 *
 * Não contém biometria nem persiste dados. Ele só transporta a URL assinada do
 * avatar entre o matcher local e o comprovante que aparece imediatamente depois
 * de bater o ponto. Um novo reconhecimento substitui o valor anterior.
 */
object PontoAvatarRuntime {
    @Volatile
    var lastRecognizedAvatarUrl: String? = null
        private set

    fun recognized(avatarUrl: String?) {
        lastRecognizedAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        lastRecognizedAvatarUrl = null
    }
}
